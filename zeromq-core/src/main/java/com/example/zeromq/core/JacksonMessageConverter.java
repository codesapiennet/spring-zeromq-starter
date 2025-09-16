package com.example.zeromq.core;

import com.example.zeromq.core.exception.SerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * JSON message converter using Jackson for object serialization and deserialization.
 * 
 * <p>This converter handles the majority of Java objects by converting them to/from
 * JSON using Jackson's ObjectMapper. It supports POJOs, collections, maps, and
 * other standard Java types.
 * 
 * <p>This implementation is thread-safe and provides high performance JSON
 * processing with comprehensive error handling and logging.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class JacksonMessageConverter implements MessageConverter {

    private static final Logger log = LoggerFactory.getLogger(JacksonMessageConverter.class);
    
    /**
     * Priority for Jackson converter - set lower than custom converters
     * to allow specialized converters to take precedence.
     */
    private static final int JACKSON_PRIORITY = -100;

    private final ObjectMapper objectMapper;

    /**
     * Create a new JacksonMessageConverter with a default ObjectMapper.
     */
    public JacksonMessageConverter() {
        this(createDefaultObjectMapper());
    }

    /**
     * Create a new JacksonMessageConverter with a custom ObjectMapper.
     * 
     * @param objectMapper the ObjectMapper to use for JSON processing
     */
    public JacksonMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
        log.debug("JacksonMessageConverter initialized with ObjectMapper: {}", 
                this.objectMapper.getClass().getSimpleName());
    }

    /**
     * Create a default ObjectMapper with recommended settings for ZeroMQ messaging.
     * 
     * @return a configured ObjectMapper instance
     */
    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Configure for robust messaging
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(com.fasterxml.jackson.core.JsonGenerator.Feature.IGNORE_UNKNOWN, true);
        
        // Enable support for Java 8 time types
        mapper.findAndRegisterModules();
        
        return mapper;
    }

    @Override
    public boolean supports(Class<?> type) {
        // Exclude types that have specialized converters
        if (isVectorType(type) || isBinaryType(type)) {
            return false;
        }
        
        // Jackson can handle most Java objects
        try {
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            JavaType javaType = typeFactory.constructType(type);
            return objectMapper.canSerialize(type) || 
                   isPrimitiveOrWrapper(type) ||
                   Collection.class.isAssignableFrom(type) ||
                   Map.class.isAssignableFrom(type) ||
                   type.isArray() ||
                   hasDefaultConstructor(type);
        } catch (Exception e) {
            log.debug("Jackson cannot determine support for type {}: {}", type, e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] toBytes(Object obj) throws SerializationException {
        if (obj == null) {
            throw new SerializationException("Cannot serialize null object");
        }
        
        try {
            long startTime = System.nanoTime();
            byte[] result = objectMapper.writeValueAsBytes(obj);
            long duration = System.nanoTime() - startTime;
            
            if (log.isDebugEnabled()) {
                log.debug("Serialized {} to {} bytes in {}μs", 
                        obj.getClass().getSimpleName(), result.length, duration / 1000);
            }
            
            return result;
            
        } catch (JsonProcessingException e) {
            String errorMsg = String.format("Failed to serialize object of type %s: %s", 
                    obj.getClass().getName(), e.getMessage());
            log.error("Serialization failed: {}", errorMsg);
            throw new SerializationException(errorMsg, e, obj.getClass());
        }
    }

    @Override
    public <T> T fromBytes(byte[] data, Class<T> targetType) throws SerializationException {
        if (data == null || data.length == 0) {
            throw new SerializationException("Cannot deserialize null or empty data", targetType);
        }
        
        try {
            long startTime = System.nanoTime();
            T result = objectMapper.readValue(data, targetType);
            long duration = System.nanoTime() - startTime;
            
            if (log.isDebugEnabled()) {
                log.debug("Deserialized {} bytes to {} in {}μs", 
                        data.length, targetType.getSimpleName(), duration / 1000);
            }
            
            return result;
            
        } catch (IOException e) {
            String errorMsg = String.format("Failed to deserialize %d bytes to type %s: %s", 
                    data.length, targetType.getName(), e.getMessage());
            log.error("Deserialization failed: {}", errorMsg);
            throw new SerializationException(errorMsg, e, targetType);
        }
    }

    /**
     * Deserialize to a TypeReference for complex generic types.
     * 
     * @param <T> the target type
     * @param data the data to deserialize
     * @param typeReference the TypeReference describing the target type
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    public <T> T fromBytes(byte[] data, TypeReference<T> typeReference) throws SerializationException {
        if (data == null || data.length == 0) {
            throw new SerializationException("Cannot deserialize null or empty data");
        }
        
        try {
            long startTime = System.nanoTime();
            T result = objectMapper.readValue(data, typeReference);
            long duration = System.nanoTime() - startTime;
            
            if (log.isDebugEnabled()) {
                log.debug("Deserialized {} bytes to {} in {}μs", 
                        data.length, typeReference.getType().getTypeName(), duration / 1000);
            }
            
            return result;
            
        } catch (IOException e) {
            String errorMsg = String.format("Failed to deserialize %d bytes to type %s: %s", 
                    data.length, typeReference.getType().getTypeName(), e.getMessage());
            log.error("Deserialization failed: {}", errorMsg);
            throw new SerializationException(errorMsg, e);
        }
    }

    /**
     * Parse JSON data to a JsonNode for flexible processing.
     * 
     * @param data the JSON data
     * @return a JsonNode representation
     * @throws SerializationException if parsing fails
     */
    public JsonNode parseToJsonNode(byte[] data) throws SerializationException {
        if (data == null || data.length == 0) {
            throw new SerializationException("Cannot parse null or empty JSON data");
        }
        
        try {
            return objectMapper.readTree(data);
        } catch (IOException e) {
            String errorMsg = String.format("Failed to parse %d bytes as JSON: %s", 
                    data.length, e.getMessage());
            log.error("JSON parsing failed: {}", errorMsg);
            throw new SerializationException(errorMsg, e);
        }
    }

    @Override
    public int getPriority() {
        return JACKSON_PRIORITY;
    }

    @Override
    public String getDescription() {
        return "JacksonMessageConverter[ObjectMapper=" + objectMapper.getClass().getSimpleName() + "]";
    }

    /**
     * Get the underlying ObjectMapper for advanced configuration.
     * 
     * @return the ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Check if a type is a vector type that should be handled by specialized converters.
     */
    private boolean isVectorType(Class<?> type) {
        return Vector.class.isAssignableFrom(type) ||
               DenseVector.class.isAssignableFrom(type) ||
               type.getPackage() != null && 
               type.getPackage().getName().contains("zeromq.core") &&
               type.getSimpleName().contains("Vector");
    }

    /**
     * Check if a type is a binary type that should not be JSON serialized.
     */
    private boolean isBinaryType(Class<?> type) {
        return byte[].class.equals(type) ||
               java.nio.ByteBuffer.class.isAssignableFrom(type) ||
               java.io.InputStream.class.isAssignableFrom(type) ||
               java.io.OutputStream.class.isAssignableFrom(type);
    }

    /**
     * Check if a type is a primitive or wrapper type.
     */
    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
               type == Boolean.class ||
               type == Byte.class ||
               type == Character.class ||
               type == Short.class ||
               type == Integer.class ||
               type == Long.class ||
               type == Float.class ||
               type == Double.class ||
               type == String.class;
    }

    /**
     * Check if a class has a default constructor for deserialization.
     */
    private boolean hasDefaultConstructor(Class<?> type) {
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
} 