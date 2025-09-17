package com.example.zeromq.autoconfig;

import com.example.zeromq.core.*;
import com.example.zeromq.core.exception.ZeroMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.zeromq.ZMQ;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * High-level template for ZeroMQ messaging operations.
 * 
 * <p>This template provides a user-friendly API for all ZeroMQ messaging patterns
 * while handling the complexity of socket management, message conversion, security,
 * and error handling. It supports both synchronous and asynchronous operations.
 * 
 * <p>The template automatically selects appropriate message converters based on
 * the data types being sent, manages socket lifecycles, and provides comprehensive
 * error handling and logging.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class ZeroMqTemplate {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqTemplate.class);
    
    private final ZmqSocketFactory socketFactory;
    private final List<MessageConverter> messageConverters;
    private final ZeroMqProperties properties;
    private final Executor asyncExecutor;
    
    // Performance tracking
    private final AtomicLong operationCounter = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> patternCounters = new ConcurrentHashMap<>();
    
    // Active subscriptions and handlers
    private final ConcurrentHashMap<String, SubscriptionInfo> activeSubscriptions = new ConcurrentHashMap<>();

    /**
     * Create a new ZeroMqTemplate.
     * 
     * @param socketFactory the socket factory for creating connections
     * @param messageConverters list of available message converters
     * @param properties configuration properties
     */
    public ZeroMqTemplate(ZmqSocketFactory socketFactory, 
                         List<MessageConverter> messageConverters,
                         ZeroMqProperties properties) {
        this.socketFactory = Objects.requireNonNull(socketFactory, "Socket factory must not be null");
        this.messageConverters = Objects.requireNonNull(messageConverters, "Message converters must not be null");
        this.properties = Objects.requireNonNull(properties, "Properties must not be null");
        this.asyncExecutor = ForkJoinPool.commonPool();
        
        log.info("ZeroMQ template initialized with {} message converters", messageConverters.size());
    }

    // ========== PUB/SUB Pattern ==========

    /**
     * Send a message using the publish-subscribe pattern.
     * 
     * @param endpoint the endpoint to bind to (e.g., "tcp://*:5555")
     * @param topic the topic to publish to
     * @param message the message to send
     * @throws ZeroMQException if publishing fails
     */
    public void publish(String endpoint, String topic, Object message) {
        publish(endpoint, topic, message, null);
    }

    /**
     * Send a message using the publish-subscribe pattern with security.
     * 
     * @param endpoint the endpoint to bind to
     * @param topic the topic to publish to  
     * @param message the message to send
     * @param curveConfig optional CURVE security configuration
     * @throws ZeroMQException if publishing fails
     */
    public void publish(String endpoint, String topic, Object message, ZmqSecurityConfig.CurveConfig curveConfig) {
        validateEndpoint(endpoint);
        validateTopic(topic);
        Objects.requireNonNull(message, "Message must not be null");
        
        String correlationId = generateCorrelationId();
        long operationId = operationCounter.incrementAndGet();
        incrementPatternCounter("publish");
        
        try {
            log.debug("component=zeromq-template event=publish-start " +
                     "correlationId={} operationId={} endpoint={} topic={}", 
                     correlationId, operationId, endpoint, topic);
            
            ZMQ.Socket socket = curveConfig != null 
                ? socketFactory.createSocket(ZMQ.PUB, curveConfig, correlationId)
                : socketFactory.createSocket(ZMQ.PUB, correlationId);
            
            try {
                socket.bind(endpoint);
                
                // Convert message to bytes
                MessageConverter converter = findConverter(message.getClass());
                byte[] data = converter.toBytes(message);
                
                // Send multipart message: topic + data
                socket.sendMore(topic);
                socket.send(data);
                
                log.debug("component=zeromq-template event=publish-completed " +
                         "correlationId={} operationId={} bytes={}", 
                         correlationId, operationId, data.length);
                
            } finally {
                socket.close();
            }
            
        } catch (Exception e) {
            log.error("component=zeromq-template event=publish-failed " +
                     "correlationId={} operationId={} error={}", 
                     correlationId, operationId, e.getMessage());
            throw new ZeroMQException("Publish operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Subscribe to messages on a topic with a message handler.
     * 
     * @param endpoint the endpoint to connect to (e.g., "tcp://localhost:5555")
     * @param topic the topic to subscribe to
     * @param messageType the expected message type
     * @param handler the message handler
     * @param <T> the message type
     * @return subscription ID for managing the subscription
     */
    public <T> String subscribe(String endpoint, String topic, Class<T> messageType, 
                               Consumer<T> handler) {
        return subscribe(endpoint, topic, messageType, handler, null);
    }

    /**
     * Subscribe to messages on a topic with security and a message handler.
     * 
     * @param endpoint the endpoint to connect to
     * @param topic the topic to subscribe to
     * @param messageType the expected message type
     * @param handler the message handler
     * @param curveConfig optional CURVE security configuration
     * @param <T> the message type
     * @return subscription ID for managing the subscription
     */
    public <T> String subscribe(String endpoint, String topic, Class<T> messageType,
                               Consumer<T> handler, ZmqSecurityConfig.CurveConfig curveConfig) {
        validateEndpoint(endpoint);
        validateTopic(topic);
        Objects.requireNonNull(messageType, "Message type must not be null");
        Objects.requireNonNull(handler, "Handler must not be null");
        
        String subscriptionId = UUID.randomUUID().toString();
        String correlationId = generateCorrelationId();
        incrementPatternCounter("subscribe");
        
        log.info("component=zeromq-template event=subscription-start " +
                "correlationId={} subscriptionId={} endpoint={} topic={}", 
                correlationId, subscriptionId, endpoint, topic);
        
        // Create subscription in background thread
        CompletableFuture.runAsync(() -> {
            try {
                ZMQ.Socket socket = curveConfig != null 
                    ? socketFactory.createSocket(ZMQ.SUB, curveConfig, correlationId)
                    : socketFactory.createSocket(ZMQ.SUB, correlationId);
                
                socket.connect(endpoint);
                socket.subscribe(topic.getBytes(ZMQ.CHARSET));
                
                SubscriptionInfo subInfo = new SubscriptionInfo(socket, endpoint, topic, correlationId);
                activeSubscriptions.put(subscriptionId, subInfo);
                
                MessageConverter converter = findConverter(messageType);
                
                log.info("component=zeromq-template event=subscription-active " +
                        "correlationId={} subscriptionId={}", correlationId, subscriptionId);
                
                // Message receiving loop
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String receivedTopic = socket.recvStr(ZMQ.DONTWAIT);
                        if (receivedTopic != null) {
                            byte[] data = socket.recv(ZMQ.DONTWAIT);
                            if (data != null) {
                                T message = converter.fromBytes(data, messageType);
                                handler.accept(message);
                                
                                log.trace("component=zeromq-template event=message-received " +
                                         "correlationId={} subscriptionId={} topic={} bytes={}", 
                                         correlationId, subscriptionId, receivedTopic, data.length);
                            }
                        } else {
                            // No message available, sleep briefly to avoid busy waiting
                            Thread.sleep(10);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("component=zeromq-template event=message-processing-error " +
                                 "correlationId={} subscriptionId={} error={}", 
                                 correlationId, subscriptionId, e.getMessage());
                        // Continue processing other messages
                    }
                }
                
            } catch (Exception e) {
                log.error("component=zeromq-template event=subscription-failed " +
                         "correlationId={} subscriptionId={} error={}", 
                         correlationId, subscriptionId, e.getMessage());
            } finally {
                activeSubscriptions.remove(subscriptionId);
                log.info("component=zeromq-template event=subscription-ended " +
                        "correlationId={} subscriptionId={}", correlationId, subscriptionId);
            }
        }, asyncExecutor);
        
        return subscriptionId;
    }

    /**
     * Unsubscribe from a topic.
     * 
     * @param subscriptionId the subscription ID returned by subscribe()
     * @return true if the subscription was found and stopped
     */
    public boolean unsubscribe(String subscriptionId) {
        Objects.requireNonNull(subscriptionId, "Subscription ID must not be null");
        
        SubscriptionInfo subInfo = activeSubscriptions.remove(subscriptionId);
        if (subInfo != null) {
            try {
                subInfo.socket.close();
                log.info("component=zeromq-template event=unsubscribe-completed " +
                        "subscriptionId={} correlationId={}", subscriptionId, subInfo.correlationId);
                return true;
            } catch (Exception e) {
                log.error("component=zeromq-template event=unsubscribe-failed " +
                         "subscriptionId={} error={}", subscriptionId, e.getMessage());
            }
        }
        return false;
    }

    // ========== REQ/REP Pattern ==========

    /**
     * Send a synchronous request and wait for a reply.
     * 
     * @param endpoint the endpoint to connect to
     * @param request the request object
     * @param responseType the expected response type
     * @param <T> the response type
     * @return the response
     * @throws ZeroMQException if the request fails
     */
    public <T> T request(String endpoint, Object request, Class<T> responseType) {
        return request(endpoint, request, responseType, null);
    }

    /**
     * Send a synchronous request with security and wait for a reply.
     * 
     * @param endpoint the endpoint to connect to
     * @param request the request object
     * @param responseType the expected response type
     * @param curveConfig optional CURVE security configuration
     * @param <T> the response type
     * @return the response
     * @throws ZeroMQException if the request fails
     */
    public <T> T request(String endpoint, Object request, Class<T> responseType, 
                        ZmqSecurityConfig.CurveConfig curveConfig) {
        validateEndpoint(endpoint);
        Objects.requireNonNull(request, "Request must not be null");
        Objects.requireNonNull(responseType, "Response type must not be null");
        
        String correlationId = generateCorrelationId();
        long operationId = operationCounter.incrementAndGet();
        incrementPatternCounter("request");
        
        try {
            log.debug("component=zeromq-template event=request-start " +
                     "correlationId={} operationId={} endpoint={}", 
                     correlationId, operationId, endpoint);
            
            ZMQ.Socket socket = curveConfig != null 
                ? socketFactory.createSocket(ZMQ.REQ, curveConfig, correlationId)
                : socketFactory.createSocket(ZMQ.REQ, correlationId);
            
            try {
                socket.connect(endpoint);
                
                // Send request
                MessageConverter requestConverter = findConverter(request.getClass());
                byte[] requestData = requestConverter.toBytes(request);
                socket.send(requestData);
                
                // Receive response
                byte[] responseData = socket.recv();
                MessageConverter responseConverter = findConverter(responseType);
                T response = responseConverter.fromBytes(responseData, responseType);
                
                log.debug("component=zeromq-template event=request-completed " +
                         "correlationId={} operationId={} requestBytes={} responseBytes={}", 
                         correlationId, operationId, requestData.length, responseData.length);
                
                return response;
                
            } finally {
                socket.close();
            }
            
        } catch (Exception e) {
            log.error("component=zeromq-template event=request-failed " +
                     "correlationId={} operationId={} error={}", 
                     correlationId, operationId, e.getMessage());
            throw new ZeroMQException("Request operation failed: " + e.getMessage(), e);
        }
    }

    // ========== PUSH/PULL Pattern ==========

    /**
     * Push a message for load-balanced distribution.
     * 
     * @param endpoint the endpoint to bind to
     * @param message the message to push
     * @throws ZeroMQException if pushing fails
     */
    public void push(String endpoint, Object message) {
        push(endpoint, message, null);
    }

    /**
     * Push a message with security for load-balanced distribution.
     * 
     * @param endpoint the endpoint to bind to
     * @param message the message to push
     * @param curveConfig optional CURVE security configuration
     * @throws ZeroMQException if pushing fails
     */
    public void push(String endpoint, Object message, ZmqSecurityConfig.CurveConfig curveConfig) {
        validateEndpoint(endpoint);
        Objects.requireNonNull(message, "Message must not be null");
        
        String correlationId = generateCorrelationId();
        long operationId = operationCounter.incrementAndGet();
        incrementPatternCounter("push");
        
        try {
            ZMQ.Socket socket = curveConfig != null 
                ? socketFactory.createSocket(ZMQ.PUSH, curveConfig, correlationId)
                : socketFactory.createSocket(ZMQ.PUSH, correlationId);
            
            try {
                socket.bind(endpoint);
                
                MessageConverter converter = findConverter(message.getClass());
                byte[] data = converter.toBytes(message);
                socket.send(data);
                
                log.debug("component=zeromq-template event=push-completed " +
                         "correlationId={} operationId={} bytes={}", 
                         correlationId, operationId, data.length);
                
            } finally {
                socket.close();
            }
            
        } catch (Exception e) {
            log.error("component=zeromq-template event=push-failed " +
                     "correlationId={} operationId={} error={}", 
                     correlationId, operationId, e.getMessage());
            throw new ZeroMQException("Push operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Pull messages from a load-balanced queue.
     * 
     * @param endpoint the endpoint to connect to
     * @param messageType the expected message type
     * @param handler the message handler
     * @param <T> the message type
     * @return subscription ID for managing the puller
     */
    public <T> String pull(String endpoint, Class<T> messageType, Consumer<T> handler) {
        return pull(endpoint, messageType, handler, null);
    }

    /**
     * Pull messages with security from a load-balanced queue.
     * 
     * @param endpoint the endpoint to connect to
     * @param messageType the expected message type
     * @param handler the message handler
     * @param curveConfig optional CURVE security configuration
     * @param <T> the message type
     * @return subscription ID for managing the puller
     */
    public <T> String pull(String endpoint, Class<T> messageType, Consumer<T> handler,
                          ZmqSecurityConfig.CurveConfig curveConfig) {
        validateEndpoint(endpoint);
        Objects.requireNonNull(messageType, "Message type must not be null");
        Objects.requireNonNull(handler, "Handler must not be null");
        
        String pullerId = UUID.randomUUID().toString();
        String correlationId = generateCorrelationId();
        incrementPatternCounter("pull");
        
        CompletableFuture.runAsync(() -> {
            try {
                ZMQ.Socket socket = curveConfig != null 
                    ? socketFactory.createSocket(ZMQ.PULL, curveConfig, correlationId)
                    : socketFactory.createSocket(ZMQ.PULL, correlationId);
                
                socket.connect(endpoint);
                
                SubscriptionInfo pullInfo = new SubscriptionInfo(socket, endpoint, "PULL", correlationId);
                activeSubscriptions.put(pullerId, pullInfo);
                
                MessageConverter converter = findConverter(messageType);
                
                log.info("component=zeromq-template event=pull-active " +
                        "correlationId={} pullerId={} endpoint={}", 
                        correlationId, pullerId, endpoint);
                
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        byte[] data = socket.recv(ZMQ.DONTWAIT);
                        if (data != null) {
                            T message = converter.fromBytes(data, messageType);
                            handler.accept(message);
                            
                            log.trace("component=zeromq-template event=message-pulled " +
                                     "correlationId={} pullerId={} bytes={}", 
                                     correlationId, pullerId, data.length);
                        } else {
                            Thread.sleep(10);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("component=zeromq-template event=pull-processing-error " +
                                 "correlationId={} pullerId={} error={}", 
                                 correlationId, pullerId, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.error("component=zeromq-template event=pull-failed " +
                         "correlationId={} pullerId={} error={}", 
                         correlationId, pullerId, e.getMessage());
            } finally {
                activeSubscriptions.remove(pullerId);
            }
        }, asyncExecutor);
        
        return pullerId;
    }

    /**
     * Stop a pull operation.
     * 
     * @param pullerId the puller ID returned by pull()
     * @return true if the puller was found and stopped
     */
    public boolean stopPull(String pullerId) {
        return unsubscribe(pullerId); // Same implementation
    }

    // ========== Utility Methods ==========

    /**
     * Find an appropriate message converter for the given type.
     * 
     * @param type the object type
     * @return a message converter that supports the type
     * @throws ZeroMQException if no suitable converter is found
     */
    private MessageConverter findConverter(Class<?> type) {
        return messageConverters.stream()
                .filter(converter -> converter.supports(type))
                .max((c1, c2) -> Integer.compare(c1.getPriority(), c2.getPriority()))
                .orElseThrow(() -> new ZeroMQException("No message converter found for type: " + type.getName()));
    }

    /**
     * Validate endpoint format.
     */
    private void validateEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalArgumentException("Endpoint must not be empty");
        }
        if (!endpoint.matches("^(tcp|ipc|inproc|pgm|epgm)://.*")) {
            throw new IllegalArgumentException("Invalid endpoint format: " + endpoint);
        }
    }

    /**
     * Validate topic format.
     */
    private void validateTopic(String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("Topic must not be empty");
        }
    }

    /**
     * Generate a correlation ID for request tracing.
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Increment pattern-specific counter.
     */
    private void incrementPatternCounter(String pattern) {
        patternCounters.computeIfAbsent(pattern, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Get operation statistics.
     * 
     * @return a map of operation counts by pattern
     */
    public java.util.Map<String, Long> getOperationStats() {
        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("totalOperations", operationCounter.get());
        patternCounters.forEach((pattern, count) -> stats.put(pattern, count.get()));
        stats.put("activeSubscriptions", (long) activeSubscriptions.size());
        return stats;
    }

    /**
     * Close all active subscriptions and clean up resources.
     */
    public void shutdown() {
        log.info("Shutting down ZeroMQ template with {} active subscriptions", activeSubscriptions.size());
        
        activeSubscriptions.values().forEach(subInfo -> {
            try {
                subInfo.socket.close();
            } catch (Exception e) {
                log.warn("Error closing subscription socket: {}", e.getMessage());
            }
        });
        
        activeSubscriptions.clear();
        log.info("ZeroMQ template shutdown completed");
    }

    /**
     * Information about an active subscription.
     */
    private static class SubscriptionInfo {
        final ZMQ.Socket socket;
        final String endpoint;
        final String topic;
        final String correlationId;
        final long startTime;

        SubscriptionInfo(ZMQ.Socket socket, String endpoint, String topic, String correlationId) {
            this.socket = socket;
            this.endpoint = endpoint;
            this.topic = topic;
            this.correlationId = correlationId;
            this.startTime = System.currentTimeMillis();
        }
    }
} 