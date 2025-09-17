package com.example.zeromq.compute;

import com.example.zeromq.core.DenseVector;

import java.util.Objects;

/**
 * Lightweight description object for ML inference tasks. Mirrors patterns used by {@link ComputeTask}
 * and is safe to send over ZeroMQ as a payload via existing templates.
 */
public final class MLInferenceTask {

    private final String taskId;
    private final DenseVector input;
    private final String modelPath;
    private final boolean requiresGpu;
    private final int topK;

    private MLInferenceTask(Builder builder) {
        this.taskId = builder.taskId;
        this.input = builder.input;
        this.modelPath = builder.modelPath;
        this.requiresGpu = builder.requiresGpu;
        this.topK = builder.topK;
    }

    public String getTaskId() { return taskId; }
    public DenseVector getInput() { return input; }
    public String getModelPath() { return modelPath; }
    public boolean requiresGpu() { return requiresGpu; }
    public int getTopK() { return topK; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String taskId;
        private DenseVector input;
        private String modelPath;
        private boolean requiresGpu = false;
        private int topK = 1;

        private Builder() {}

        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder input(DenseVector input) { this.input = input; return this; }
        public Builder modelPath(String modelPath) { this.modelPath = modelPath; return this; }
        public Builder requiresGpu(boolean requiresGpu) { this.requiresGpu = requiresGpu; return this; }
        public Builder topK(int topK) { this.topK = topK; return this; }

        public MLInferenceTask build() {
            Objects.requireNonNull(taskId, "taskId must not be null");
            Objects.requireNonNull(input, "input must not be null");
            Objects.requireNonNull(modelPath, "modelPath must not be null");
            if (topK < 1) throw new IllegalArgumentException("topK must be >= 1");
            return new MLInferenceTask(this);
        }
    }
} 