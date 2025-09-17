package com.example.zeromq.compute;

import com.example.zeromq.core.BatchVector;
import com.example.zeromq.core.DenseVector;

import java.util.Objects;

/**
 * Serializable description of a compute task to be sent to workers.
 */
public final class ComputeTask {

    private final String taskId;
    private final String operation;
    private final float[][] matrix;
    private final DenseVector vector;
    private final BatchVector batchInputs;
    private final ComputeEngine.ComputeBackend preferredBackend;
    private final boolean cpuIntensive;
    private final boolean requiresGpu;
    private final String modelPath;

    private ComputeTask(Builder builder) {
        this.taskId = builder.taskId;
        this.operation = builder.operation;
        this.matrix = builder.matrix;
        this.vector = builder.vector;
        this.batchInputs = builder.batchInputs;
        this.preferredBackend = builder.preferredBackend;
        this.cpuIntensive = builder.cpuIntensive;
        this.requiresGpu = builder.requiresGpu;
        this.modelPath = builder.modelPath;
    }

    public String getTaskId() { return taskId; }
    public String getOperation() { return operation; }
    public float[][] getMatrix() { return matrix; }
    public DenseVector getVector() { return vector; }
    public BatchVector getBatchInputs() { return batchInputs; }
    public ComputeEngine.ComputeBackend getPreferredBackend() { return preferredBackend; }
    public boolean isCpuIntensive() { return cpuIntensive; }
    public boolean requiresGpu() { return requiresGpu; }
    public String getModelPath() { return modelPath; }

    public int getVectorSize() {
        if (vector != null) return vector.getDimensions();
        if (batchInputs != null) return batchInputs.getDimensions();
        return 0;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String taskId;
        private String operation;
        private float[][] matrix;
        private DenseVector vector;
        private BatchVector batchInputs;
        private ComputeEngine.ComputeBackend preferredBackend;
        private boolean cpuIntensive;
        private boolean requiresGpu;
        private String modelPath;

        private Builder() {}

        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder operation(String operation) { this.operation = operation; return this; }
        public Builder matrix(float[][] matrix) { this.matrix = matrix; return this; }
        public Builder vector(DenseVector vector) { this.vector = vector; return this; }
        public Builder batchInputs(BatchVector batchInputs) { this.batchInputs = batchInputs; return this; }
        public Builder preferredBackend(ComputeEngine.ComputeBackend backend) { this.preferredBackend = backend; return this; }
        public Builder cpuIntensive(boolean cpuIntensive) { this.cpuIntensive = cpuIntensive; return this; }
        public Builder requiresGpu(boolean requiresGpu) { this.requiresGpu = requiresGpu; return this; }
        public Builder modelPath(String modelPath) { this.modelPath = modelPath; return this; }

        public ComputeTask build() {
            Objects.requireNonNull(taskId, "taskId must not be null");
            Objects.requireNonNull(operation, "operation must not be null");
            return new ComputeTask(this);
        }
    }
} 