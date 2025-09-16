package com.example.zeromq.core.exception;

/**
 * Base exception for all ZeroMQ-related errors in the Spring ZeroMQ framework.
 * 
 * <p>This exception serves as the root of the exception hierarchy for all
 * ZeroMQ-specific errors, providing a consistent error handling mechanism
 * across the entire framework.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class ZeroMQException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Construct a new ZeroMQException with the specified detail message.
     * 
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method)
     */
    public ZeroMQException(String message) {
        super(message);
    }

    /**
     * Construct a new ZeroMQException with the specified detail message and cause.
     * 
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method)
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link #getCause()} method). (A null value is permitted,
     *              and indicates that the cause is nonexistent or unknown.)
     */
    public ZeroMQException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct a new ZeroMQException with the specified cause and a detail
     * message of (cause==null ? null : cause.toString()) (which typically
     * contains the class and detail message of cause).
     * 
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link #getCause()} method). (A null value is permitted,
     *              and indicates that the cause is nonexistent or unknown.)
     */
    public ZeroMQException(Throwable cause) {
        super(cause);
    }
} 