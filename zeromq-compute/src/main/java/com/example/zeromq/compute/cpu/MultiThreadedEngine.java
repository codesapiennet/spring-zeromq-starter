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

            // Use structured concurrency with virtual threads to partition rows
            int partitions = Math.min(Math.max(1, Runtime.getRuntime().availableProcessors() * 2), rows);
            int chunk = (rows + partitions - 1) / partitions;
            try (var scope = new java.util.concurrent.StructuredTaskScope.ShutdownOnFailure()) {
                java.util.List<java.util.concurrent.StructuredTaskScope.Subtask<DenseVector>> subs = new java.util.ArrayList<>();
                for (int p = 0; p < partitions; p++) {
                    int start = p * chunk;
                    int end = Math.min(rows, start + chunk);
                    if (start >= end) break;
                    subs.add(scope.fork(() -> {
                        float[] part = new float[end - start];
                        for (int i = start; i < end; i++) {
                            float sum = 0.0f;
                            float[] row = matrix[i];
                            for (int j = 0; j < cols; j++) sum += row[j] * vector.getData()[j];
                            part[i - start] = sum;
                        }
                        return new DenseVector(part);
                    }));
                }

                scope.join();
                scope.throwIfFailed();

                // Collect sub-results and preallocate a single output buffer to avoid many intermediate arrays
                java.util.List<float[]> parts = new java.util.ArrayList<>(subs.size());
                int totalLen = 0;
                for (var sub : subs) {
                    try {
                        DenseVector dv = sub.get();
                        float[] data = dv.getData();
                        parts.add(data);
                        totalLen += dv.getDimensions();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                float[] accumulated = new float[totalLen];
                int offset = 0;
                for (float[] part : parts) {
                    System.arraycopy(part, 0, accumulated, offset, part.length);
                    offset += part.length;
                }

                return new DenseVector(accumulated);
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
            try (var scope = new java.util.concurrent.StructuredTaskScope.ShutdownOnFailure()) {
                java.util.List<java.util.concurrent.StructuredTaskScope.Subtask<java.lang.Float>> subs = new java.util.ArrayList<>();
                int chunk = (n + partitions - 1) / partitions;
                for (int p = 0; p < partitions; p++) {
                    int start = p * chunk;
                    int end = Math.min(n, start + chunk);
                    if (start >= end) break;
                    subs.add(scope.fork(() -> {
                        double sum = 0.0;
                        for (int i = start; i < end; i++) sum += (double) v1.getData()[i] * v2.getData()[i];
                        return (float) sum;
                    }));
                }

                scope.join();
                scope.throwIfFailed();

                double total = 0.0;
                for (var s : subs) {
                    try {
                        total += s.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
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
            try (var scope = new java.util.concurrent.StructuredTaskScope.ShutdownOnFailure()) {
                java.util.List<java.util.concurrent.StructuredTaskScope.Subtask<Void>> subs = new java.util.ArrayList<>();
                for (int p = 0; p < partitions; p++) {
                    int start = p * chunk;
                    int end = Math.min(n, start + chunk);
                    if (start >= end) break;
                    subs.add(scope.fork(() -> {
                        for (int i = start; i < end; i++) {
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
                    }));
                }

                scope.join();
                scope.throwIfFailed();

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

            try (var scope = new java.util.concurrent.StructuredTaskScope.ShutdownOnFailure()) {
                java.util.List<java.util.concurrent.StructuredTaskScope.Subtask<Void>> subs = new java.util.ArrayList<>();
                for (int i = 0; i < vectors.length; i++) {
                    final int idx = i;
                    subs.add(scope.fork(() -> {
                        float[] processed = kernel.execute(vectors[idx].getData(), 1, vectors[idx].getDimensions());
                        results[idx] = new DenseVector(processed);
                        return null;
                    }));
                }

                scope.join();
                scope.throwIfFailed();
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
            try (var scope = new java.util.concurrent.StructuredTaskScope.ShutdownOnFailure()) {
                var dot = scope.fork(() -> dotProduct(v1, v2).join());
                var n1 = scope.fork(() -> {
                    double s = 0.0;
                    for (float f : v1.getData()) s += (double) f * f;
                    return (float) Math.sqrt(s);
                });
                var n2 = scope.fork(() -> {
                    double s = 0.0;
                    for (float f : v2.getData()) s += (double) f * f;
                    return (float) Math.sqrt(s);
                });

                scope.join();
                scope.throwIfFailed();

                try {
                    float dotVal = dot.get();
                    float n1Val = n1.get();
                    float n2Val = n2.get();
                    return dotVal / (n1Val * n2Val);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
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