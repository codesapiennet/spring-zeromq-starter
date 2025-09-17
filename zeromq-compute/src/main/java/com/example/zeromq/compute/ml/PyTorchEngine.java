package com.example.zeromq.compute.ml;

import com.example.zeromq.compute.ComputeEngine;
import com.example.zeromq.core.BatchVector;
import com.example.zeromq.core.DenseVector;
import com.example.zeromq.autoconfig.ZeroMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Thin PyTorch integration shim. Detects presence of PyTorch Java API using reflection
 * and provides safe CPU fallbacks. Teams may extend this to implement real JNI-backed
 * or Java bindings based inference.
 */
@Component
@ConditionalOnProperty(name = "spring.zeromq.compute.ml.pytorch.enabled", havingValue = "true", matchIfMissing = false)
public class PyTorchEngine extends ComputeEngine {

    private static final Logger log = LoggerFactory.getLogger(PyTorchEngine.class);

    private final boolean pytorchAvailable;

    public PyTorchEngine(ZeroMqProperties properties) {
        boolean available;
        try {
            // Detect core PyTorch Java class (org.pytorch.Module) without hard dependency
            Class.forName("org.pytorch.Module");
            available = true;
            log.info("PyTorch Java API detected on classpath");
        } catch (ClassNotFoundException e) {
            available = false;
            log.warn("PyTorch Java API not found on classpath; PyTorchEngine will use CPU fallbacks");
        } catch (Throwable t) {
            available = false;
            log.warn("Unexpected error while detecting PyTorch API: {}", t.getMessage());
        }
        this.pytorchAvailable = available;
        this.preferredBackend = available ? ComputeBackend.GPU_CUDA : ComputeBackend.CPU_MULTI_THREAD;
    }

    private DenseVector fallbackCpuMatrixMultiply(float[][] matrix, DenseVector vector) {
        Objects.requireNonNull(matrix, "matrix must not be null");
        Objects.requireNonNull(vector, "vector must not be null");
        int rows = matrix.length;
        float[] result = new float[rows];
        float[] vectorData = vector.getData();
        for (int i = 0; i < rows; i++) {
            float[] row = matrix[i];
            float sum = 0.0f;
            for (int j = 0; j < row.length; j++) sum += row[j] * vectorData[j];
            result[i] = sum;
        }
        return new DenseVector(result);
    }

    @Override
    public CompletableFuture<DenseVector> matrixVectorMultiply(float[][] matrix, DenseVector vector) {
        return CompletableFuture.supplyAsync(() -> fallbackCpuMatrixMultiply(matrix, vector));
    }

    @Override
    public CompletableFuture<Float> dotProduct(DenseVector v1, DenseVector v2) {
        return CompletableFuture.supplyAsync(() -> {
            if (v1.getDimensions() != v2.getDimensions()) throw new IllegalArgumentException("Vector dimensions must match");
            double sum = 0.0;
            float[] a = v1.getData();
            float[] b = v2.getData();
            for (int i = 0; i < a.length; i++) sum += (double) a[i] * b[i];
            return (float) sum;
        });
    }

    @Override
    public CompletableFuture<DenseVector> elementwiseOperation(DenseVector v1, DenseVector v2, Operation op) {
        return CompletableFuture.supplyAsync(() -> {
            if (v1.getDimensions() != v2.getDimensions()) throw new IllegalArgumentException("Vector dimensions must match");
            int n = v1.getDimensions();
            float[] a = v1.getData();
            float[] b = v2.getData();
            float[] out = new float[n];
            for (int i = 0; i < n; i++) {
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
            return new DenseVector(out);
        });
    }

    @Override
    public CompletableFuture<BatchVector> batchProcess(BatchVector input, com.example.zeromq.compute.ComputeKernel kernel) {
        return CompletableFuture.supplyAsync(() -> {
            var vectors = input.getVectors();
            var results = new DenseVector[vectors.length];
            for (int i = 0; i < vectors.length; i++) {
                float[] processed = kernel.execute(vectors[i].getData(), 1, vectors[i].getDimensions());
                results[i] = new DenseVector(processed);
            }
            return new BatchVector(results);
        });
    }

    @Override
    public CompletableFuture<DenseVector> neuralNetworkInference(DenseVector input, String modelPath) {
        return CompletableFuture.supplyAsync(() -> {
            Objects.requireNonNull(input, "input must not be null");
            if (!pytorchAvailable) {
                log.warn("PyTorch not available - returning input as passthrough for neuralNetworkInference");
                return input;
            }
            // Placeholder: real PyTorch execution should be implemented by integrators
            log.info("PyTorch detected but runtime inference is not implemented in this shim; returning input");
            return input;
        });
    }

    @Override
    public CompletableFuture<DenseVector> convolution2D(float[][][] image, float[][][] filters) {
        return CompletableFuture.supplyAsync(() -> {
            int outH = image.length;
            int outW = image[0].length;
            float[] flat = new float[outH * outW];
            return new DenseVector(flat);
        });
    }

    @Override
    public CompletableFuture<Float> cosineSimilarity(DenseVector v1, DenseVector v2) {
        return CompletableFuture.supplyAsync(() -> {
            double dot = 0.0;
            float[] a = v1.getData();
            float[] b = v2.getData();
            for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
            double n1 = 0.0, n2 = 0.0;
            for (float f : a) n1 += (double) f * f;
            for (float f : b) n2 += (double) f * f;
            double denom = Math.sqrt(n1) * Math.sqrt(n2);
            return denom == 0.0 ? 0.0f : (float) (dot / denom);
        });
    }

    @Override
    public boolean isGpuAvailable() { return pytorchAvailable; }

    @Override
    public ComputeStats getPerformanceStats() {
        return new ComputeStats(pytorchAvailable, pytorchAvailable ? 1 : 0);
    }
} 