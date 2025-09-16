package com.example.zeromq.core.exception;

/**
 * Exception thrown when ZeroMQ security operations fail.
 * 
 * <p>This exception is thrown when security-related operations such as
 * CURVE key generation, encryption setup, or authentication failures occur
 * in the ZeroMQ messaging framework.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class SecurityException extends ZeroMQException {

    private static final long serialVersionUID = 1L;

    /**
     * The security mechanism that was being used when the error occurred.
     */
    private final String mechanism;

    /**
     * Construct a new SecurityException with the specified detail message.
     * 
     * @param message the detail message
     */
    public SecurityException(String message) {
        super(message);
        this.mechanism = null;
    }

    /**
     * Construct a new SecurityException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the underlying cause of the security failure
     */
    public SecurityException(String message, Throwable cause) {
        super(message, cause);
        this.mechanism = null;
    }

    /**
     * Construct a new SecurityException with the specified detail message and mechanism.
     * 
     * @param message the detail message
     * @param mechanism the security mechanism (PLAIN, CURVE, etc.)
     */
    public SecurityException(String message, String mechanism) {
        super(message);
        this.mechanism = mechanism;
    }

    /**
     * Construct a new SecurityException with the specified detail message,
     * cause, and mechanism.
     * 
     * @param message the detail message
     * @param cause the underlying cause of the security failure
     * @param mechanism the security mechanism (PLAIN, CURVE, etc.)
     */
    public SecurityException(String message, Throwable cause, String mechanism) {
        super(message, cause);
        this.mechanism = mechanism;
    }

    /**
     * Returns the security mechanism that was being used when the error occurred.
     * 
     * @return the security mechanism, or null if not available
     */
    public String getMechanism() {
        return mechanism;
    }

    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (mechanism != null) {
            return baseMessage + " (mechanism: " + mechanism + ")";
        }
        return baseMessage;
    }
} 