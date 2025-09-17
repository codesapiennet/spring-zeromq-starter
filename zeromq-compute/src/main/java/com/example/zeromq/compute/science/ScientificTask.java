package com.example.zeromq.compute.science;

import com.example.zeromq.core.BatchVector;
import com.example.zeromq.core.DenseVector;

import java.util.Map;
import java.util.Objects;

/**
 * Descriptor for scientific computing tasks. Lightweight, immutable and suitable
 * for transport in the existing messaging pipeline.
 */
public final class ScientificTask {

    private final String taskId;
    private final String taskType;
    private final DenseVector inputVector;
    private final BatchVector batchInput;
    private final Map<String, Object> parameters;

    private ScientificTask(Builder builder) {
        this.taskId = builder.taskId;
        this.taskType = builder.taskType;
        this.inputVector = builder.inputVector;
        this.batchInput = builder.batchInput;
        this.parameters = builder.parameters;
    }

    public String getTaskId() { return taskId; }
    public String getTaskType() { return taskType; }
    public DenseVector getInputVector() { return inputVector; }
    public BatchVector getBatchInput() { return batchInput; }
    public Map<String, Object> getParameters() { return parameters; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String taskId;
        private String taskType;
        private DenseVector inputVector;
        private BatchVector batchInput;
        private Map<String, Object> parameters;

        private Builder() {}

        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder taskType(String taskType) { this.taskType = taskType; return this; }
        public Builder inputVector(DenseVector inputVector) { this.inputVector = inputVector; return this; }
        public Builder batchInput(BatchVector batchInput) { this.batchInput = batchInput; return this; }
        public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }

        public ScientificTask build() {
            Objects.requireNonNull(taskId, "taskId must not be null");
            Objects.requireNonNull(taskType, "taskType must not be null");
            return new ScientificTask(this);
        }
    }
} 