package com.example.zeromq.core;

import com.example.zeromq.core.exception.SerializationException;

/**
 * Interface for converting between Java objects and byte arrays for ZeroMQ message transport.
 * 
 * <p>MessageConverter implementations handle the serialization and deserialization of objects
 * to enable them to be sent over ZeroMQ sockets. The framework provides built-in converters
 * for JSON (Jackson), vectors, and other common data types.
 * 
 * <p>Implementations must be thread-safe as they may be called concurrently from multiple
 * ZeroMQ socket operations.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public interface MessageConverter {

    /**
     * Determine if this converter can handle the given class type.
     * 
     * <p>This method is called by the framework to select an appropriate converter
     * for a given object type. Converters should return {@code true} only for
     * types they can reliably serialize and deserialize.
     * 
     * @param type the class type to check
     * @return {@code true} if this converter supports the given type, {@code false} otherwise
     */
    boolean supports(Class<?> type);

    /**
     * Serialize an object to a byte array for transmission over ZeroMQ.
     * 
     * <p>The returned byte array should contain all necessary information to
     * reconstruct the original object using {@link #fromBytes(byte[], Class)}.
     * 
     * @param obj the object to serialize (must not be null)
     * @return the serialized byte representation
     * @throws SerializationException if serialization fails
     * @throws IllegalArgumentException if the object type is not supported
     */
    byte[] toBytes(Object obj) throws SerializationException;

    /**
     * Deserialize a byte array back to a Java object.
     * 
     * <p>This method reconstructs an object from its byte array representation
     * that was created by {@link #toBytes(Object)}.
     * 
     * @param <T> the expected return type
     * @param data the byte array to deserialize (must not be null)
     * @param targetType the expected class of the deserialized object
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     * @throws IllegalArgumentException if the target type is not supported
     */
    <T> T fromBytes(byte[] data, Class<T> targetType) throws SerializationException;

    /**
     * Get a description of this converter for logging and debugging purposes.
     * 
     * <p>The default implementation returns the class name.
     * 
     * @return a human-readable description of this converter
     */
    default String getDescription() {
        return getClass().getSimpleName();
    }

    /**
     * Get the priority of this converter when multiple converters support the same type.
     * 
     * <p>Higher values indicate higher priority. The framework will use the converter
     * with the highest priority when multiple converters claim to support a type.
     * 
     * <p>Default priority is 0. Built-in converters use negative priorities to allow
     * custom converters to override them.
     * 
     * @return the priority of this converter (higher values = higher priority)
     */
    default int getPriority() {
        return 0;
    }
} 