package com.example.zeromq.compute.cpu;

import com.example.zeromq.compute.ComputeEngine;
import com.example.zeromq.core.DenseVector;
import com.example.zeromq.core.BatchVector;
import com.example.zeromq.compute.ComputeKernel;
import com.example.zeromq.autoconfig.ZeroMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.stream.IntStream;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * CPU-optimized compute engine that leverages the JDK Vector API and ForkJoin pool
 * for parallel, SIMD-capable operations. This implementation is safe for CPU-only
 * environments and acts as a high-performance fallback when GPUs are unavailable.
 */
@Component
public class OptimizedCpuComputeEngine extends ComputeEngine {

    private static final Logger log = LoggerFactory.getLogger(OptimizedCpuComputeEngine.class);

    private final ForkJoinPool computePool;
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;
    private final boolean usingVirtualThreads;

    public OptimizedCpuComputeEngine(ZeroMqProperties properties) {
        int optimalThreads = Math.min(Runtime.getRuntime().availableProcessors(), ForkJoinPool.getCommonPoolParallelism());
        this.computePool = new ForkJoinPool(Math.max(1, optimalThreads));
        this.preferredBackend = ComputeBackend.CPU_VECTORIZED;
        boolean useVirtual = false;
        try {
            useVirtual = properties != null && properties.getCompute() != null
                    && properties.getCompute().getMultithreaded() != null
                    && properties.getCompute().getMultithreaded().isUseVirtualThreads();
        } catch (Exception ignored) {}
        this.usingVirtualThreads = useVirtual;
        log.info("Initialized OptimizedCpuComputeEngine with {} threads and vector species {} (useVirtualThreads={})",
                optimalThreads, SPECIES.length(), usingVirtualThreads);
    }

    @Override
    public CompletableFuture<Float> dotProduct(DenseVector v1, DenseVector v2) {
        return CompletableFuture.supplyAsync(() -> {
            if (!usingVirtualThreads) {
                return computePool.submit(new VectorizedDotProduct(v1.getData(), v2.getData(), 0, v1.getDimensions())).join();
            }

            int n = v1.getDimensions();
            int partitions = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
            int chunk = (n + partitions - 1) / partitions;
            java.util.List<java.util.concurrent.Callable<Float>> tasks = new java.util.ArrayList<>();
            for (int p = 0; p < partitions; p++) {
                int start = p * chunk;
                int end = Math.min(n, start + chunk);
                if (start >= end) break;
                final int s = start, e = end;
                tasks.add(() -> {
                    double sum = 0.0;
                    float[] a = v1.getData();
                    float[] b = v2.getData();
                    for (int i = s; i < e; i++) sum += (double) a[i] * b[i];
                    return (float) sum;
                });
            }

            try {
                var results = com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(computePool, tasks);
                double total = 0.0;
                for (Float f : results) total += f;
                return (float) total;
            } catch (Exception e) {
                log.error("Virtual-thread dotProduct failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, computePool);
    }

    @Override
    public CompletableFuture<DenseVector> elementwiseOperation(DenseVector v1, DenseVector v2, Operation op) {
        return CompletableFuture.supplyAsync(() -> {
            float[] data1 = v1.getData();
            float[] data2 = v2.getData();
            int length = data1.length;
            float[] result = new float[length];

            // For non-linear ops that require math functions, use a parallel scalar implementation
            if (op == Operation.SIGMOID) {
                IntStream.range(0, length).parallel().forEach(i ->
                    result[i] = (float) (1.0 / (1.0 + Math.exp(-data1[i])))
                );
                return new DenseVector(result);
            } else if (op == Operation.TANH) {
                IntStream.range(0, length).parallel().forEach(i ->
                    result[i] = (float) Math.tanh(data1[i])
                );
                return new DenseVector(result);
            }

            if (!usingVirtualThreads) {
                int i = 0;
                int upperBound = SPECIES.loopBound(length);

                for (; i < upperBound; i += SPECIES.length()) {
                    var va = FloatVector.fromArray(SPECIES, data1, i);
                    var vb = FloatVector.fromArray(SPECIES, data2, i);
                    FloatVector vc;
                    switch (op) {
                        case ADD -> vc = va.add(vb);
                        case SUBTRACT -> vc = va.sub(vb);
                        case MULTIPLY -> vc = va.mul(vb);
                        case DIVIDE -> vc = va.div(vb);
                        case RELU -> vc = va.max(FloatVector.broadcast(SPECIES, 0.0f));
                        default -> throw new UnsupportedOperationException("Operation not implemented: " + op);
                    }
                    vc.intoArray(result, i);
                }

                for (int j = (SPECIES.loopBound(length)); j < length; j++) {
                    result[j] = switch (op) {
                        case ADD -> data1[j] + data2[j];
                        case SUBTRACT -> data1[j] - data2[j];
                        case MULTIPLY -> data1[j] * data2[j];
                        case DIVIDE -> {
                            float denom = data2[j];
                            yield denom == 0.0f ? Float.NaN : data1[j] / denom;
                        }
                        case RELU -> Math.max(0.0f, data1[j]);
                        case SIGMOID -> (float) (1.0 / (1.0 + Math.exp(-data1[j])));
                        case TANH -> (float) Math.tanh(data1[j]);
                        default -> throw new UnsupportedOperationException("Operation not implemented: " + op);
                    };
                }

                return new DenseVector(result);
            }

            // Virtual-thread partitioned execution using structured concurrency
            int partitions = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
            int chunk = (length + partitions - 1) / partitions;
            java.util.List<java.util.concurrent.Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int p = 0; p < partitions; p++) {
                int start = p * chunk;
                int end = Math.min(length, start + chunk);
                if (start >= end) break;
                final int s = start, e = end;
                tasks.add(() -> {
                    int i = s;
                    int upper = Math.min(e, SPECIES.loopBound(e));
                    // process SIMD within chunk
                    for (; i < upper; i += SPECIES.length()) {
                        var va = FloatVector.fromArray(SPECIES, data1, i);
                        var vb = FloatVector.fromArray(SPECIES, data2, i);
                        FloatVector vc;
                        switch (op) {
                            case ADD -> vc = va.add(vb);
                            case SUBTRACT -> vc = va.sub(vb);
                            case MULTIPLY -> vc = va.mul(vb);
                            case DIVIDE -> vc = va.div(vb);
                            case RELU -> vc = va.max(FloatVector.broadcast(SPECIES, 0.0f));
                            default -> throw new UnsupportedOperationException("Operation not implemented: " + op);
                        }
                        vc.intoArray(result, i);
                    }
                    for (; i < e; i++) {
                        result[i] = switch (op) {
                            case ADD -> data1[i] + data2[i];
                            case SUBTRACT -> data1[i] - data2[i];
                            case MULTIPLY -> data1[i] * data2[i];
                            case DIVIDE -> {
                                float denom = data2[i];
                                yield denom == 0.0f ? Float.NaN : data1[i] / denom;
                            }
                            case RELU -> Math.max(0.0f, data1[i]);
                            case SIGMOID -> (float) (1.0 / (1.0 + Math.exp(-data1[i])));
                            case TANH -> (float) Math.tanh(data1[i]);
                            default -> throw new UnsupportedOperationException("Operation not implemented: " + op);
                        };
                    }
                    return null;
                });
            }

            try {
                com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(computePool, tasks);
                return new DenseVector(result);
            } catch (Exception e) {
                log.error("Virtual-thread elementwiseOperation failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, computePool);
    }

    @Override
    public CompletableFuture<DenseVector> matrixVectorMultiply(float[][] matrix, DenseVector vector) {
        return CompletableFuture.supplyAsync(() -> {
            if (!usingVirtualThreads) {
                return computePool.submit(new ParallelMatrixVectorMultiply(matrix, vector.getData(), 0, matrix.length)).join();
            }

            int rows = matrix.length;
            int partitions = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
            int chunk = (rows + partitions - 1) / partitions;
            float[] result = new float[rows];
            java.util.List<java.util.concurrent.Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int p = 0; p < partitions; p++) {
                int start = p * chunk;
                int end = Math.min(rows, start + chunk);
                if (start >= end) break;
                final int s = start, e = end;
                tasks.add(() -> {
                    for (int i = s; i < e; i++) {
                        float sum = 0.0f;
                        float[] row = matrix[i];
                        int j = 0;
                        int upperBound = SPECIES.loopBound(row.length);
                        FloatVector vsum = FloatVector.zero(SPECIES);
                        for (; j < upperBound; j += SPECIES.length()) {
                            var vr = FloatVector.fromArray(SPECIES, row, j);
                            var vv = FloatVector.fromArray(SPECIES, vector.getData(), j);
                            vsum = vr.fma(vv, vsum);
                        }
                        sum += vsum.reduceLanes(VectorOperators.ADD);
                        for (; j < row.length; j++) sum += row[j] * vector.getData()[j];
                        result[i] = sum;
                    }
                    return null;
                });
            }

            try {
                com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(computePool, tasks);
                return new DenseVector(result);
            } catch (Exception e) {
                log.error("Virtual-thread matrixVectorMultiply failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, computePool);
    }

    @Override
    public CompletableFuture<BatchVector> batchProcess(BatchVector input, ComputeKernel kernel) {
        return CompletableFuture.supplyAsync(() -> {
            DenseVector[] vectors = input.getVectors();
            DenseVector[] results = new DenseVector[vectors.length];

            if (!usingVirtualThreads) {
                IntStream.range(0, vectors.length).parallel().forEach(i -> {
                    float[] processed = kernel.execute(vectors[i].getData(), 1, vectors[i].getDimensions());
                    results[i] = new DenseVector(processed);
                });
                return new BatchVector(results);
            }

            java.util.List<java.util.concurrent.Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < vectors.length; i++) {
                final int idx = i;
                tasks.add(() -> {
                    float[] processed = kernel.execute(vectors[idx].getData(), 1, vectors[idx].getDimensions());
                    results[idx] = new DenseVector(processed);
                    return null;
                });
            }

            try {
                com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(computePool, tasks);
                return new BatchVector(results);
            } catch (Exception e) {
                log.error("Virtual-thread batchProcess failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, computePool);
    }

    @Override
    public CompletableFuture<DenseVector> neuralNetworkInference(DenseVector input, String modelPath) {
        // CPU implementation would call into an ONNX/TensorFlow CPU runtime; placeholder fallback
        return CompletableFuture.supplyAsync(() -> {
            log.warn("neuralNetworkInference on CPU is a fallback - consider enabling GPU for production workloads");
            // simple identity passthrough
            return input;
        }, computePool);
    }

    @Override
    public CompletableFuture<DenseVector> convolution2D(float[][][] image, float[][][] filters) {
        // Placeholder: Real implementation would use optimized libraries
        return CompletableFuture.supplyAsync(() -> {
            log.warn("2D convolution fallback on CPU - unoptimized");
            int outH = image.length;
            int outW = image[0].length;
            float[] flat = new float[outH * outW];
            return new DenseVector(flat);
        }, computePool);
    }

    @Override
    public CompletableFuture<Float> cosineSimilarity(DenseVector v1, DenseVector v2) {
        if (!usingVirtualThreads) {
            return dotProduct(v1, v2).thenCombine(CompletableFuture.supplyAsync(() -> {
                double n1 = 0.0;
                for (float f : v1.getData()) n1 += (double) f * f;
                return Math.sqrt(n1);
            }, computePool), (dot, norm1) -> (float) (dot / (norm1 * computeNorm(v2))));
        }

        return CompletableFuture.supplyAsync(() -> {
            java.util.List<java.util.concurrent.Callable<Object>> tasks = new java.util.ArrayList<>();
            tasks.add(() -> dotProduct(v1, v2).join());
            tasks.add(() -> {
                double s = 0.0;
                for (float f : v1.getData()) s += (double) f * f;
                return (float) Math.sqrt(s);
            });
            tasks.add(() -> computeNorm(v2));

            try {
                var res = com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(computePool, tasks);
                float dotVal = (Float) res.get(0);
                float n1 = (Float) res.get(1);
                double n2 = (Double) res.get(2);
                return (float) (dotVal / (n1 * n2));
            } catch (Exception e) {
                log.error("Virtual-thread cosineSimilarity failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, computePool);
    }

    private double computeNorm(DenseVector v) {
        double sum = 0.0;
        for (float f : v.getData()) sum += (double) f * f;
        return Math.sqrt(sum);
    }

    @Override
    public boolean isGpuAvailable() { return false; }

    @Override
    public ComputeStats getPerformanceStats() {
        return new ComputeStats(false, computePool.getParallelism());
    }

    /* ----- ForkJoin tasks ----- */
    private static class VectorizedDotProduct extends RecursiveTask<Float> {
        private final float[] a, b;
        private final int start, end;
        private static final int THRESHOLD = 1000;

        VectorizedDotProduct(float[] a, float[] b, int start, int end) {
            this.a = a; this.b = b; this.start = start; this.end = end;
        }

        @Override
        protected Float compute() {
            if (end - start <= THRESHOLD) {
                return computeDirectly();
            } else {
                int mid = (start + end) >>> 1;
                var left = new VectorizedDotProduct(a, b, start, mid);
                var right = new VectorizedDotProduct(a, b, mid, end);
                left.fork();
                return right.compute() + left.join();
            }
        }

        private Float computeDirectly() {
            float sum = 0.0f;
            int i = start;
            int length = end;
            int upperBound = SPECIES.loopBound(length - start) + start;

            FloatVector vsum = FloatVector.zero(SPECIES);
            for (; i < upperBound; i += SPECIES.length()) {
                var va = FloatVector.fromArray(SPECIES, a, i);
                var vb = FloatVector.fromArray(SPECIES, b, i);
                vsum = va.fma(vb, vsum);
            }
            sum += vsum.reduceLanes(VectorOperators.ADD);

            for (; i < length; i++) {
                sum += a[i] * b[i];
            }
            return sum;
        }
    }

    private static class ParallelMatrixVectorMultiply extends RecursiveTask<DenseVector> {
        private final float[][] matrix;
        private final float[] vector;
        private final int startRow, endRow;
        private static final int THRESHOLD = 100;

        ParallelMatrixVectorMultiply(float[][] matrix, float[] vector, int startRow, int endRow) {
            this.matrix = matrix; this.vector = vector; this.startRow = startRow; this.endRow = endRow;
        }

        @Override
        protected DenseVector compute() {
            if (endRow - startRow <= THRESHOLD) {
                return computeDirectly();
            } else {
                int midRow = (startRow + endRow) >>> 1;
                var upper = new ParallelMatrixVectorMultiply(matrix, vector, startRow, midRow);
                var lower = new ParallelMatrixVectorMultiply(matrix, vector, midRow, endRow);
                upper.fork();
                var lowerResult = lower.compute();
                var upperResult = upper.join();

                float[] upperData = upperResult.getData();
                float[] lowerData = lowerResult.getData();
                int totalLen = upperData.length + lowerData.length;
                float[] combined = com.example.zeromq.core.BufferPool.INSTANCE.acquire(totalLen);
                System.arraycopy(upperData, 0, combined, 0, upperData.length);
                System.arraycopy(lowerData, 0, combined, upperData.length, lowerData.length);
                float[] resultCopy = java.util.Arrays.copyOf(combined, totalLen);
                com.example.zeromq.core.BufferPool.INSTANCE.release(combined);
                return new DenseVector(resultCopy);
            }
        }

        private DenseVector computeDirectly() {
            int rows = endRow - startRow;
            float[] result = new float[rows];
            for (int i = startRow; i < endRow; i++) {
                float[] row = matrix[i];
                float sum = 0.0f;
                int j = 0;
                int upperBound = SPECIES.loopBound(row.length);
                FloatVector vsum = FloatVector.zero(SPECIES);
                for (; j < upperBound; j += SPECIES.length()) {
                    var vr = FloatVector.fromArray(SPECIES, row, j);
                    var vv = FloatVector.fromArray(SPECIES, vector, j);
                    vsum = vr.fma(vv, vsum);
                }
                sum += vsum.reduceLanes(VectorOperators.ADD);
                for (; j < row.length; j++) {
                    sum += row[j] * vector[j];
                }
                result[i - startRow] = sum;
            }
            return new DenseVector(result);
        }
    }
} 