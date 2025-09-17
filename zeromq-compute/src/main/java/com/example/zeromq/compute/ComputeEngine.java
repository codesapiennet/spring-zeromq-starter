package com.example.zeromq.compute;

import com.example.zeromq.core.DenseVector;
import com.example.zeromq.core.BatchVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction layer for GPU/CPU compute operations.
 * Automatically selects optimal compute backend based on workload characteristics.
 */
@Component
public abstract class ComputeEngine {

    private static final Logger log = LoggerFactory.getLogger(ComputeEngine.class);

    public enum ComputeBackend {
        CPU_SINGLE_THREAD,
        CPU_MULTI_THREAD,
        CPU_VECTORIZED,
        GPU_CUDA,
        GPU_OPENCL,
        GPU_ROCM,
        TPU_CORAL
    }

    protected ComputeBackend preferredBackend;
    protected int deviceId = 0;
    protected boolean enableProfiling = false;

    /** Core compute operations */
    public abstract CompletableFuture<DenseVector> matrixVectorMultiply(float[][] matrix, DenseVector vector);
    public abstract CompletableFuture<Float> dotProduct(DenseVector v1, DenseVector v2);
    public abstract CompletableFuture<DenseVector> elementwiseOperation(DenseVector v1, DenseVector v2, Operation op);
    public abstract CompletableFuture<BatchVector> batchProcess(BatchVector input, ComputeKernel kernel);

    /** ML-specific operations */
    public abstract CompletableFuture<DenseVector> neuralNetworkInference(DenseVector input, String modelPath);
    public abstract CompletableFuture<DenseVector> convolution2D(float[][][] image, float[][][] filters);
    public abstract CompletableFuture<Float> cosineSimilarity(DenseVector v1, DenseVector v2);

    /** Utility methods */
    public ComputeBackend getOptimalBackend(ComputeTask task) {
        if (task != null && task.getVectorSize() > 10000 && isGpuAvailable()) {
            return ComputeBackend.GPU_CUDA;
        } else if (task != null && task.isCpuIntensive() && Runtime.getRuntime().availableProcessors() > 4) {
            return ComputeBackend.CPU_MULTI_THREAD;
        }
        return ComputeBackend.CPU_SINGLE_THREAD;
    }

    public abstract boolean isGpuAvailable();
    public abstract ComputeStats getPerformanceStats();

    public enum Operation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE, RELU, SIGMOID, TANH
    }

    /**
     * Lightweight performance stats container. Implementations may extend.
     */
    public static class ComputeStats {
        private final boolean gpuAvailable;
        private final int activeDevices;

        public ComputeStats(boolean gpuAvailable, int activeDevices) {
            this.gpuAvailable = gpuAvailable;
            this.activeDevices = activeDevices;
        }

        public boolean isGpuAvailable() { return gpuAvailable; }
        public int getActiveDevices() { return activeDevices; }
    }

} 