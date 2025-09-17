package com.example.zeromq.compute.cpu;

import com.example.zeromq.compute.ComputeEngine;
import com.example.zeromq.compute.ComputeKernel;
import com.example.zeromq.core.BatchVector;
import com.example.zeromq.core.DenseVector;
import com.example.zeromq.autoconfig.ZeroMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Simple multi-threaded CPU compute engine that parallelizes work across a fixed
 * thread pool. This engine provides a predictable non-SIMD fallback for machines
 * where the JDK Vector API is not desirable or available.
 */
@Component
@ConditionalOnProperty(name = "spring.zeromq.compute.multithreaded.enabled", havingValue = "true", matchIfMissing = false)
public class MultiThreadedEngine extends ComputeEngine {

    private static final Logger log = LoggerFactory.getLogger(MultiThreadedEngine.class);

    private final ExecutorService executor;
    private final int threads;
    private final boolean usingVirtualThreads;

    public MultiThreadedEngine(ZeroMqProperties properties) {
        boolean useVirtual = false;
        try {
            useVirtual = properties != null && properties.getCompute() != null
                    && properties.getCompute().getMultithreaded() != null
                    && properties.getCompute().getMultithreaded().isUseVirtualThreads();
        } catch (Exception ignored) {}

        this.usingVirtualThreads = useVirtual;

        if (useVirtual) {
            this.threads = 0;
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            log.info("Initialized MultiThreadedEngine with virtual-thread executor");
        } else {
            this.threads = Math.max(1, Runtime.getRuntime().availableProcessors());
            this.executor = Executors.newFixedThreadPool(threads);
            log.info("Initialized MultiThreadedEngine with {} platform threads", threads);
        }

        this.preferredBackend = ComputeBackend.CPU_MULTI_THREAD;
    }

    @Override
    public CompletableFuture<DenseVector> matrixVectorMultiply(float[][] matrix, DenseVector vector) {
        return CompletableFuture.supplyAsync(() -> {
            int rows = matrix.length;
            int cols = matrix[0].length;
            float[] result = new float[rows];

            if (!usingVirtualThreads) {
                IntStream.range(0, rows).parallel().forEach(r -> {
                    float sum = 0.0f;
                    float[] row = matrix[r];
                    for (int c = 0; c < cols; c++) sum += row[c] * vector.getData()[c];
                    result[r] = sum;
                });
                return new DenseVector(result);
            }

            // Partition rows using executor and the compatibility helper (cancels remaining tasks on first failure)
            int partitions = Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() * 2), rows);
            int chunk = (rows + partitions - 1) / partitions;
            java.util.List<java.util.concurrent.Callable<DenseVector>> tasks = new java.util.ArrayList<>();
            for (int p = 0; p < partitions; p++) {
                int start = p * chunk;
                int end = Math.min(rows, start + chunk);
                if (start >= end) break;
                final int s = start, e = end;
                tasks.add(() -> {
                    float[] part = new float[e - s];
                    for (int i = s; i < e; i++) {
                        float sum = 0.0f;
                        float[] row = matrix[i];
                        for (int j = 0; j < cols; j++) sum += row[j] * vector.getData()[j];
                        part[i - s] = sum;
                    }
                    return new DenseVector(part);
                });
            }

            try {
                var parts = com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(executor, tasks);

                // Collect sub-results and preallocate a single output buffer to avoid many intermediate arrays
                java.util.List<float[]> partArrays = new java.util.ArrayList<>(parts.size());
                int totalLen = 0;
                for (var dv : parts) {
                    float[] data = dv.getData();
                    partArrays.add(data);
                    totalLen += dv.getDimensions();
                }

                float[] accumulated = com.example.zeromq.core.BufferPool.INSTANCE.acquire(totalLen);
                int offset = 0;
                for (float[] part : partArrays) {
                    System.arraycopy(part, 0, accumulated, offset, part.length);
                    offset += part.length;
                }

                // Defensive copy to preserve immutability of DenseVector and allow releasing the pooled buffer
                float[] resultCopy = java.util.Arrays.copyOf(accumulated, totalLen);
                com.example.zeromq.core.BufferPool.INSTANCE.release(accumulated);
                return new DenseVector(resultCopy);
            } catch (Exception e) {
                log.error("Virtual-thread matrixVectorMultiply failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Float> dotProduct(DenseVector v1, DenseVector v2) {
        return CompletableFuture.supplyAsync(() -> {
            int n = v1.getDimensions();
            if (!usingVirtualThreads) {
                double[] partials = IntStream.range(0, threads)
                        .mapToDouble(part -> {
                            int start = (int) ((long) part * n / threads);
                            int end = (int) (((long) (part + 1) * n) / threads);
                            double sum = 0.0;
                            for (int i = start; i < end; i++) sum += (double) v1.getData()[i] * v2.getData()[i];
                            return sum;
                        }).toArray();
                double total = 0.0;
                for (double p : partials) total += p;
                return (float) total;
            }

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
                    for (int i = s; i < e; i++) sum += (double) v1.getData()[i] * v2.getData()[i];
                    return (float) sum;
                });
            }

            try {
                var results = com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(executor, tasks);
                double total = 0.0;
                for (Float f : results) total += f;
                return (float) total;
            } catch (Exception e) {
                log.error("Virtual-thread dotProduct failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<DenseVector> elementwiseOperation(DenseVector v1, DenseVector v2, Operation op) {
        return CompletableFuture.supplyAsync(() -> {
            final int n = v1.getDimensions();
            float[] a = v1.getData();
            float[] b = v2.getData();
            float[] out = new float[n];

            if (!usingVirtualThreads) {
                IntStream.range(0, n).parallel().forEach(i -> {
                    switch (op) {
                        case ADD -> out[i] = a[i] + b[i];
                        case SUBTRACT -> out[i] = a[i] - b[i];
                        case MULTIPLY -> out[i] = a[i] * b[i];
                        case DIVIDE -> out[i] = b[i] == 0.0f ? Float.NaN : a[i] / b[i];
                        case RELU -> out[i] = Math.max(0.0f, a[i]);
                        case SIGMOID -> out[i] = (float) (1.0 / (1.0 + Math.exp(-a[i])));
                        case TANH -> out[i] = (float) Math.tanh(a[i]);
                        default -> throw new UnsupportedOperationException("Operation not implemented: " + op);
                    }
                });
                return new DenseVector(out);
            }

            int partitions = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
            int chunk = (n + partitions - 1) / partitions;
            java.util.List<java.util.concurrent.Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int p = 0; p < partitions; p++) {
                int start = p * chunk;
                int end = Math.min(n, start + chunk);
                if (start >= end) break;
                final int s = start, e = end;
                tasks.add(() -> {
                    for (int i = s; i < e; i++) {
                        switch (op) {
                            case ADD -> out[i] = a[i] + b[i];
                            case SUBTRACT -> out[i] = a[i] - b[i];
                            case MULTIPLY -> out[i] = a[i] * b[i];
                            case DIVIDE -> out[i] = b[i] == 0.0f ? Float.NaN : a[i] / b[i];
                            case RELU -> out[i] = Math.max(0.0f, a[i]);
                            case SIGMOID -> out[i] = (float) (1.0 / (1.0 + Math.exp(-a[i])));
                            case TANH -> out[i] = (float) Math.tanh(a[i]);
                            default -> throw new UnsupportedOperationException("Operation not implemented: " + op);
                        }
                    }
                    return null;
                });
            }

            try {
                com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(executor, tasks);
                return new DenseVector(out);
            } catch (Exception e) {
                log.error("Virtual-thread elementwiseOperation failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<BatchVector> batchProcess(BatchVector input, ComputeKernel kernel) {
        return CompletableFuture.supplyAsync(() -> {
            var vectors = input.getVectors();
            var results = new DenseVector[vectors.length];

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
                com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(executor, tasks);
                return new BatchVector(results);
            } catch (Exception e) {
                log.error("Virtual-thread batchProcess failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<DenseVector> neuralNetworkInference(DenseVector input, String modelPath) {
        return CompletableFuture.supplyAsync(() -> {
            log.warn("neuralNetworkInference on MultiThreadedEngine is a CPU fallback for model={}", modelPath);
            return input;
        }, executor);
    }

    @Override
    public CompletableFuture<DenseVector> convolution2D(float[][][] image, float[][][] filters) {
        return CompletableFuture.supplyAsync(() -> {
            log.warn("convolution2D on MultiThreadedEngine is unoptimized CPU fallback");
            int outH = image.length;
            int outW = image[0].length;
            float[] flat = new float[outH * outW];
            return new DenseVector(flat);
        }, executor);
    }

    @Override
    public CompletableFuture<Float> cosineSimilarity(DenseVector v1, DenseVector v2) {
        if (!usingVirtualThreads) {
            CompletableFuture<Float> dotFut = dotProduct(v1, v2);
            CompletableFuture<Float> n1Fut = CompletableFuture.supplyAsync(() -> {
                double s = 0.0;
                for (float f : v1.getData()) s += (double) f * f;
                return (float) Math.sqrt(s);
            }, executor);
            CompletableFuture<Float> n2Fut = CompletableFuture.supplyAsync(() -> {
                double s = 0.0;
                for (float f : v2.getData()) s += (double) f * f;
                return (float) Math.sqrt(s);
            }, executor);

            return dotFut.thenCombine(n1Fut, (dot, n1) -> dot / (n1 * n2Fut.join()));
        }

        // Virtual-thread structured approach: compute dot and norms in subtasks
        return CompletableFuture.supplyAsync(() -> {
            java.util.List<java.util.concurrent.Callable<Float>> tasks = new java.util.ArrayList<>();
            // dot
            tasks.add(() -> dotProduct(v1, v2).join());
            // n1
            tasks.add(() -> {
                double s = 0.0;
                for (float f : v1.getData()) s += (double) f * f;
                return (float) Math.sqrt(s);
            });
            // n2
            tasks.add(() -> {
                double s = 0.0;
                for (float f : v2.getData()) s += (double) f * f;
                return (float) Math.sqrt(s);
            });

            try {
                var res = com.example.zeromq.compute.util.ConcurrencyUtils.invokeAllCancelOnFailure(executor, tasks);
                float dotVal = res.get(0);
                float n1Val = res.get(1);
                float n2Val = res.get(2);
                return dotVal / (n1Val * n2Val);
            } catch (Exception e) {
                log.error("Virtual-thread cosineSimilarity failed: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public boolean isGpuAvailable() { return false; }

    @Override
    public ComputeStats getPerformanceStats() { return new ComputeStats(false, threads); }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down MultiThreadedEngine executor");
        try {
            executor.shutdown();
        } catch (UnsupportedOperationException e) {
            // Some virtual-thread executors may not support shutdown in older JDKs; try close if available
            try {
                if (executor instanceof AutoCloseable) ((AutoCloseable) executor).close();
            } catch (Exception ignored) {}
        }
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 