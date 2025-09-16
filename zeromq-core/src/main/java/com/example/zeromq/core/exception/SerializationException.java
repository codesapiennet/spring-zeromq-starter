package com.example.zeromq.core.exception;

/**
 * Exception thrown when message serialization or deserialization fails.
 * 
 * <p>This exception is typically thrown by MessageConverter implementations
 * when they encounter errors during the conversion process between Java
 * objects and byte arrays for ZeroMQ message transport.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class SerializationException extends ZeroMQException {

    private static final long serialVersionUID = 1L;

    /**
     * The type that was being serialized/deserialized when the error occurred.
     */
    private final Class<?> targetType;

    /**
     * Construct a new SerializationException with the specified detail message.
     * 
     * @param message the detail message
     */
    public SerializationException(String message) {
        super(message);
        this.targetType = null;
    }

    /**
     * Construct a new SerializationException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the underlying cause of the serialization failure
     */
    public SerializationException(String message, Throwable cause) {
        super(message, cause);
        this.targetType = null;
    }

    /**
     * Construct a new SerializationException with the specified detail message,
     * cause, and target type.
     * 
     * @param message the detail message
     * @param cause the underlying cause of the serialization failure
     * @param targetType the type that was being processed when the error occurred
     */
    public SerializationException(String message, Throwable cause, Class<?> targetType) {
        super(message, cause);
        this.targetType = targetType;
    }

    /**
     * Construct a new SerializationException with the specified detail message and target type.
     * 
     * @param message the detail message
     * @param targetType the type that was being processed when the error occurred
     */
    public SerializationException(String message, Class<?> targetType) {
        super(message);
        this.targetType = targetType;
    }

    /**
     * Returns the type that was being serialized/deserialized when the error occurred.
     * 
     * @return the target type, or null if not available
     */
    public Class<?> getTargetType() {
        return targetType;
    }

    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (targetType != null) {
            return baseMessage + " (target type: " + targetType.getName() + ")";
        }
        return baseMessage;
    }
} 