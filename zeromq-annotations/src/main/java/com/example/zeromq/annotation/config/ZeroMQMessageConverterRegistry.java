package com.example.zeromq.annotation.config;

import com.example.zeromq.core.MessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for message converters used in ZeroMQ annotation processing.
 * 
 * <p>This registry manages different message converters that can handle
 * serialization and deserialization of various message types. Converters
 * are prioritized and the first compatible converter is used for each
 * message type.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class ZeroMQMessageConverterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ZeroMQMessageConverterRegistry.class);
    
    private final List<MessageConverter> converters = new ArrayList<>();
    private final ConcurrentHashMap<Class<?>, MessageConverter> converterCache = new ConcurrentHashMap<>();

    /**
     * Add a message converter to the registry.
     * 
     * @param converter the message converter to add
     */
    public void addConverter(MessageConverter converter) {
        if (converter == null) {
            throw new IllegalArgumentException("Converter must not be null");
        }
        
        synchronized (converters) {
            converters.add(converter);
            // Clear cache when converters change
            converterCache.clear();
        }
        
        log.debug("Added message converter: {}", converter.getClass().getSimpleName());
    }

    /**
     * Add a message converter at a specific position.
     * 
     * @param index the position to insert at
     * @param converter the message converter to add
     */
    public void addConverter(int index, MessageConverter converter) {
        if (converter == null) {
            throw new IllegalArgumentException("Converter must not be null");
        }
        
        synchronized (converters) {
            converters.add(index, converter);
            // Clear cache when converters change
            converterCache.clear();
        }
        
        log.debug("Added message converter at position {}: {}", index, converter.getClass().getSimpleName());
    }

    /**
     * Remove a message converter from the registry.
     * 
     * @param converter the converter to remove
     * @return true if the converter was removed
     */
    public boolean removeConverter(MessageConverter converter) {
        boolean removed;
        synchronized (converters) {
            removed = converters.remove(converter);
            if (removed) {
                // Clear cache when converters change
                converterCache.clear();
            }
        }
        
        if (removed) {
            log.debug("Removed message converter: {}", converter.getClass().getSimpleName());
        }
        
        return removed;
    }

    /**
     * Get all registered converters.
     * 
     * @return list of message converters
     */
    public List<MessageConverter> getConverters() {
        synchronized (converters) {
            return new ArrayList<>(converters);
        }
    }

    /**
     * Find a suitable converter for the given message type.
     * 
     * @param messageType the message type to convert
     * @return a suitable converter or null if none found
     */
    public MessageConverter findConverter(Class<?> messageType) {
        if (messageType == null) {
            return null;
        }
        
        // Check cache first
        MessageConverter cached = converterCache.get(messageType);
        if (cached != null) {
            return cached;
        }
        
        // Find compatible converter
        MessageConverter found = null;
        synchronized (converters) {
            for (MessageConverter converter : converters) {
                if (converter.canConvert(messageType)) {
                    found = converter;
                    break;
                }
            }
        }
        
        // Cache the result (including null results)
        if (found != null) {
            converterCache.put(messageType, found);
            log.trace("Found converter for {}: {}", messageType.getSimpleName(), 
                     found.getClass().getSimpleName());
        } else {
            log.trace("No converter found for message type: {}", messageType.getSimpleName());
        }
        
        return found;
    }

    /**
     * Convert a message to bytes using the appropriate converter.
     * 
     * @param message the message to convert
     * @return the serialized message bytes
     * @throws RuntimeException if conversion fails
     */
    public byte[] convertToBytes(Object message) {
        if (message == null) {
            return new byte[0];
        }
        
        MessageConverter converter = findConverter(message.getClass());
        if (converter == null) {
            throw new RuntimeException("No converter found for message type: " + message.getClass().getName());
        }
        
        try {
            return converter.toBytes(message);
        } catch (Exception e) {
            throw new RuntimeException("Message conversion failed for type " + message.getClass().getName(), e);
        }
    }

    /**
     * Convert bytes to a message object using the appropriate converter.
     * 
     * @param bytes the bytes to convert
     * @param targetType the target message type
     * @param <T> the target type
     * @return the deserialized message
     * @throws RuntimeException if conversion fails
     */
    @SuppressWarnings("unchecked")
    public <T> T convertFromBytes(byte[] bytes, Class<T> targetType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        
        MessageConverter converter = findConverter(targetType);
        if (converter == null) {
            throw new RuntimeException("No converter found for target type: " + targetType.getName());
        }
        
        try {
            Object result = converter.fromBytes(bytes, targetType);
            return (T) result;
        } catch (Exception e) {
            throw new RuntimeException("Message conversion failed for type " + targetType.getName(), e);
        }
    }

    /**
     * Clear the converter cache.
     */
    public void clearCache() {
        int cacheSize = converterCache.size();
        converterCache.clear();
        log.debug("Cleared converter cache ({} entries)", cacheSize);
    }

    /**
     * Get registry statistics.
     * 
     * @return statistics map
     */
    public java.util.Map<String, Object> getStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        synchronized (converters) {
            stats.put("totalConverters", converters.size());
            
            List<String> converterNames = converters.stream()
                .map(c -> c.getClass().getSimpleName())
                .toList();
            stats.put("converterTypes", converterNames);
        }
        
        stats.put("cacheSize", converterCache.size());
        
        return stats;
    }

    /**
     * Check if the registry is empty.
     * 
     * @return true if no converters are registered
     */
    public boolean isEmpty() {
        synchronized (converters) {
            return converters.isEmpty();
        }
    }

    /**
     * Get the number of registered converters.
     * 
     * @return the number of converters
     */
    public int size() {
        synchronized (converters) {
            return converters.size();
        }
    }

    /**
     * Clear all converters from the registry.
     */
    public void clear() {
        int count;
        synchronized (converters) {
            count = converters.size();
            converters.clear();
            converterCache.clear();
        }
        
        log.debug("Cleared {} message converters", count);
    }
} 