package com.example.zeromq.compute;

/**
 * Result wrapper returned by compute workers.
 */
public final class ComputeResult {

    private final String taskId;
    private final boolean success;
    private final Object data;
    private final String error;
    private final long executionTimeNanos;
    private final String deviceInfo;

    private ComputeResult(Builder builder) {
        this.taskId = builder.taskId;
        this.success = builder.success;
        this.data = builder.data;
        this.error = builder.error;
        this.executionTimeNanos = builder.executionTimeNanos;
        this.deviceInfo = builder.deviceInfo;
    }

    public String getTaskId() { return taskId; }
    public boolean isSuccess() { return success; }
    public Object getData() { return data; }
    public String getError() { return error; }
    public long getExecutionTimeNanos() { return executionTimeNanos; }
    public String getDeviceInfo() { return deviceInfo; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String taskId;
        private boolean success;
        private Object data;
        private String error;
        private long executionTimeNanos;
        private String deviceInfo;

        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder data(Object data) { this.data = data; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder executionTimeNanos(long nanos) { this.executionTimeNanos = nanos; return this; }
        public Builder deviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; return this; }
        public ComputeResult build() { return new ComputeResult(this); }
    }
} 