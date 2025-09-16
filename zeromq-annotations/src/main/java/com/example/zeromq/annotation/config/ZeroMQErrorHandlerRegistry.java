package com.example.zeromq.annotation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Registry for custom error handlers in ZeroMQ annotation processing.
 * 
 * <p>This registry allows applications to register custom error handling
 * strategies that can be used when message processing fails. Error handlers
 * can be registered by name and then referenced in {@code @ZeroMQSubscriber}
 * annotations.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class ZeroMQErrorHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ZeroMQErrorHandlerRegistry.class);
    
    private final ConcurrentHashMap<String, ErrorHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Register a custom error handler.
     * 
     * @param name the handler name
     * @param handler the error handler
     */
    public void registerHandler(String name, ErrorHandler handler) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Handler name must not be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler must not be null");
        }
        
        ErrorHandler previous = handlers.put(name, handler);
        
        if (previous != null) {
            log.warn("Replaced existing error handler: {}", name);
        } else {
            log.debug("Registered error handler: {}", name);
        }
    }

    /**
     * Get an error handler by name.
     * 
     * @param name the handler name
     * @return the error handler or null if not found
     */
    public ErrorHandler getHandler(String name) {
        return handlers.get(name);
    }

    /**
     * Remove an error handler.
     * 
     * @param name the handler name
     * @return true if the handler was removed
     */
    public boolean removeHandler(String name) {
        ErrorHandler removed = handlers.remove(name);
        if (removed != null) {
            log.debug("Removed error handler: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Get all registered handler names.
     * 
     * @return set of handler names
     */
    public java.util.Set<String> getHandlerNames() {
        return new java.util.HashSet<>(handlers.keySet());
    }

    /**
     * Clear all registered handlers.
     */
    public void clear() {
        int count = handlers.size();
        handlers.clear();
        log.debug("Cleared {} error handlers", count);
    }

    /**
     * Interface for custom error handlers.
     */
    @FunctionalInterface
    public interface ErrorHandler {
        /**
         * Handle an error that occurred during message processing.
         * 
         * @param context the error context
         * @param error the error that occurred
         */
        void handleError(ErrorContext context, Throwable error);
    }

    /**
     * Context information for error handling.
     */
    public static class ErrorContext {
        private final String endpoint;
        private final String topic;
        private final Object message;
        private final String methodName;
        private final String beanName;
        private final long timestamp;
        private final String correlationId;

        public ErrorContext(String endpoint, String topic, Object message, 
                           String methodName, String beanName, String correlationId) {
            this.endpoint = endpoint;
            this.topic = topic;
            this.message = message;
            this.methodName = methodName;
            this.beanName = beanName;
            this.correlationId = correlationId;
            this.timestamp = System.currentTimeMillis();
        }

        public String getEndpoint() { return endpoint; }
        public String getTopic() { return topic; }
        public Object getMessage() { return message; }
        public String getMethodName() { return methodName; }
        public String getBeanName() { return beanName; }
        public long getTimestamp() { return timestamp; }
        public String getCorrelationId() { return correlationId; }

        @Override
        public String toString() {
            return String.format("ErrorContext[endpoint=%s, topic=%s, method=%s.%s, correlationId=%s]",
                    endpoint, topic, beanName, methodName, correlationId);
        }
    }

    // Built-in error handlers

    /**
     * Error handler that logs and continues processing.
     */
    public static final ErrorHandler LOG_AND_CONTINUE = (context, error) -> {
        log.error("Message processing failed for {}: {}", context, error.getMessage(), error);
    };

    /**
     * Error handler that logs and stops processing.
     */
    public static final ErrorHandler LOG_AND_STOP = (context, error) -> {
        log.error("Message processing failed for {} - stopping subscriber: {}", context, error.getMessage(), error);
        // TODO: In a full implementation, this would stop the subscriber
    };

    /**
     * Error handler that silently ignores errors.
     */
    public static final ErrorHandler IGNORE = (context, error) -> {
        // Do nothing - silently ignore the error
    };

    /**
     * Error handler that implements retry logic.
     */
    public static class RetryErrorHandler implements ErrorHandler {
        private final int maxRetries;
        private final long retryDelayMs;
        private final ConcurrentHashMap<String, Integer> retryCounters = new ConcurrentHashMap<>();

        public RetryErrorHandler(int maxRetries, long retryDelayMs) {
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
        }

        @Override
        public void handleError(ErrorContext context, Throwable error) {
            String key = context.getCorrelationId();
            int attempts = retryCounters.compute(key, (k, v) -> v == null ? 1 : v + 1);

            if (attempts <= maxRetries) {
                log.warn("Message processing failed for {} (attempt {}/{}): {} - will retry in {}ms",
                        context, attempts, maxRetries, error.getMessage(), retryDelayMs);

                // Schedule retry
                // TODO: In a full implementation, this would schedule message reprocessing
                
            } else {
                log.error("Message processing failed for {} after {} attempts: {}",
                        context, maxRetries, error.getMessage(), error);
                retryCounters.remove(key);
            }
        }
    }

    /**
     * Error handler that sends failed messages to a dead letter queue.
     */
    public static class DeadLetterQueueErrorHandler implements ErrorHandler {
        private final String deadLetterEndpoint;
        private final BiConsumer<String, Object> sender;

        public DeadLetterQueueErrorHandler(String deadLetterEndpoint, BiConsumer<String, Object> sender) {
            this.deadLetterEndpoint = deadLetterEndpoint;
            this.sender = sender;
        }

        @Override
        public void handleError(ErrorContext context, Throwable error) {
            log.warn("Message processing failed for {} - sending to dead letter queue: {}",
                    context, error.getMessage());

            try {
                // Create dead letter message with context
                DeadLetterMessage dlqMessage = new DeadLetterMessage(
                    context.getMessage(),
                    context.getEndpoint(),
                    context.getTopic(),
                    error.getMessage(),
                    context.getTimestamp(),
                    context.getCorrelationId()
                );

                sender.accept(deadLetterEndpoint, dlqMessage);

                log.debug("Sent message to dead letter queue: {}", deadLetterEndpoint);

            } catch (Exception dlqError) {
                log.error("Failed to send message to dead letter queue {}: {}",
                        deadLetterEndpoint, dlqError.getMessage(), dlqError);
            }
        }

        /**
         * Message wrapper for dead letter queue.
         */
        public static class DeadLetterMessage {
            private final Object originalMessage;
            private final String originalEndpoint;
            private final String originalTopic;
            private final String errorMessage;
            private final long failureTimestamp;
            private final String correlationId;

            public DeadLetterMessage(Object originalMessage, String originalEndpoint, String originalTopic,
                                   String errorMessage, long failureTimestamp, String correlationId) {
                this.originalMessage = originalMessage;
                this.originalEndpoint = originalEndpoint;
                this.originalTopic = originalTopic;
                this.errorMessage = errorMessage;
                this.failureTimestamp = failureTimestamp;
                this.correlationId = correlationId;
            }

            public Object getOriginalMessage() { return originalMessage; }
            public String getOriginalEndpoint() { return originalEndpoint; }
            public String getOriginalTopic() { return originalTopic; }
            public String getErrorMessage() { return errorMessage; }
            public long getFailureTimestamp() { return failureTimestamp; }
            public String getCorrelationId() { return correlationId; }
        }
    }
} 