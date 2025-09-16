package com.example.zeromq.annotation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics collector for ZeroMQ annotation-based operations.
 * 
 * <p>This collector tracks various metrics related to declarative messaging
 * using annotations, including method invocations, message publishing,
 * subscription activity, and error rates. It provides insights into the
 * performance and health of annotation-driven messaging.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class ZeroMQAnnotationMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(ZeroMQAnnotationMetricsCollector.class);
    
    private final boolean enabled;
    private final Instant startTime = Instant.now();
    
    // Publisher metrics
    private final AtomicLong publisherInvocations = new AtomicLong(0);
    private final AtomicLong publisherSuccesses = new AtomicLong(0);
    private final AtomicLong publisherFailures = new AtomicLong(0);
    private final LongAdder publisherTotalTime = new LongAdder();
    
    // Subscriber metrics
    private final AtomicLong subscriberInvocations = new AtomicLong(0);
    private final AtomicLong subscriberSuccesses = new AtomicLong(0);
    private final AtomicLong subscriberFailures = new AtomicLong(0);
    private final LongAdder subscriberTotalTime = new LongAdder();
    
    // Message metrics
    private final AtomicLong messagesPublished = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final LongAdder totalMessageSize = new LongAdder();
    
    // Error metrics by type
    private final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    // Method-specific metrics
    private final ConcurrentHashMap<String, MethodMetrics> methodMetrics = new ConcurrentHashMap<>();
    
    // Endpoint metrics
    private final ConcurrentHashMap<String, EndpointMetrics> endpointMetrics = new ConcurrentHashMap<>();

    /**
     * Create a new annotation metrics collector.
     * 
     * @param enabled whether metrics collection is enabled
     */
    public ZeroMQAnnotationMetricsCollector(boolean enabled) {
        this.enabled = enabled;
        log.info("ZeroMQ annotation metrics collector initialized (enabled: {})", enabled);
    }

    /**
     * Record a publisher method invocation.
     * 
     * @param methodName the method name
     * @param endpoint the endpoint
     * @param executionTime the execution time
     * @param success whether the invocation was successful
     */
    public void recordPublisherInvocation(String methodName, String endpoint, 
                                        Duration executionTime, boolean success) {
        if (!enabled) return;
        
        publisherInvocations.incrementAndGet();
        publisherTotalTime.add(executionTime.toNanos());
        
        if (success) {
            publisherSuccesses.incrementAndGet();
        } else {
            publisherFailures.incrementAndGet();
        }
        
        // Record method-specific metrics
        getMethodMetrics(methodName).recordInvocation(executionTime, success);
        
        // Record endpoint-specific metrics
        getEndpointMetrics(endpoint).recordPublish(executionTime, success);
        
        log.trace("Recorded publisher invocation: method={}, endpoint={}, time={}ms, success={}", 
                 methodName, endpoint, executionTime.toMillis(), success);
    }

    /**
     * Record a subscriber method invocation.
     * 
     * @param methodName the method name
     * @param endpoint the endpoint
     * @param executionTime the execution time
     * @param success whether the invocation was successful
     */
    public void recordSubscriberInvocation(String methodName, String endpoint, 
                                         Duration executionTime, boolean success) {
        if (!enabled) return;
        
        subscriberInvocations.incrementAndGet();
        subscriberTotalTime.add(executionTime.toNanos());
        
        if (success) {
            subscriberSuccesses.incrementAndGet();
        } else {
            subscriberFailures.incrementAndGet();
        }
        
        // Record method-specific metrics
        getMethodMetrics(methodName).recordInvocation(executionTime, success);
        
        // Record endpoint-specific metrics
        getEndpointMetrics(endpoint).recordReceive(executionTime, success);
        
        log.trace("Recorded subscriber invocation: method={}, endpoint={}, time={}ms, success={}", 
                 methodName, endpoint, executionTime.toMillis(), success);
    }

    /**
     * Record message publishing activity.
     * 
     * @param endpoint the endpoint
     * @param messageSize the message size in bytes
     */
    public void recordMessagePublished(String endpoint, int messageSize) {
        if (!enabled) return;
        
        messagesPublished.incrementAndGet();
        totalMessageSize.add(messageSize);
        
        getEndpointMetrics(endpoint).recordMessageSize(messageSize);
        
        log.trace("Recorded message published: endpoint={}, size={} bytes", endpoint, messageSize);
    }

    /**
     * Record message receiving activity.
     * 
     * @param endpoint the endpoint
     * @param messageSize the message size in bytes
     */
    public void recordMessageReceived(String endpoint, int messageSize) {
        if (!enabled) return;
        
        messagesReceived.incrementAndGet();
        totalMessageSize.add(messageSize);
        
        getEndpointMetrics(endpoint).recordMessageSize(messageSize);
        
        log.trace("Recorded message received: endpoint={}, size={} bytes", endpoint, messageSize);
    }

    /**
     * Record an error occurrence.
     * 
     * @param errorType the type of error
     * @param methodName the method name where error occurred
     * @param endpoint the endpoint where error occurred
     */
    public void recordError(String errorType, String methodName, String endpoint) {
        if (!enabled) return;
        
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        
        if (methodName != null) {
            getMethodMetrics(methodName).recordError(errorType);
        }
        
        if (endpoint != null) {
            getEndpointMetrics(endpoint).recordError(errorType);
        }
        
        log.trace("Recorded error: type={}, method={}, endpoint={}", errorType, methodName, endpoint);
    }

    /**
     * Get or create method metrics.
     */
    private MethodMetrics getMethodMetrics(String methodName) {
        return methodMetrics.computeIfAbsent(methodName, k -> new MethodMetrics());
    }

    /**
     * Get or create endpoint metrics.
     */
    private EndpointMetrics getEndpointMetrics(String endpoint) {
        return endpointMetrics.computeIfAbsent(endpoint, k -> new EndpointMetrics());
    }

    /**
     * Get comprehensive metrics summary.
     * 
     * @return metrics summary map
     */
    public Map<String, Object> getMetrics() {
        if (!enabled) {
            return Map.of("enabled", false, "message", "Metrics collection is disabled");
        }
        
        long totalInvocations = publisherInvocations.get() + subscriberInvocations.get();
        long totalSuccesses = publisherSuccesses.get() + subscriberSuccesses.get();
        long totalFailures = publisherFailures.get() + subscriberFailures.get();
        long totalExecutionTime = publisherTotalTime.sum() + subscriberTotalTime.sum();
        
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        
        // Overall metrics
        metrics.put("enabled", true);
        metrics.put("startTime", startTime);
        metrics.put("uptime", Duration.between(startTime, Instant.now()).toString());
        
        // Invocation metrics
        Map<String, Object> invocations = new ConcurrentHashMap<>();
        invocations.put("total", totalInvocations);
        invocations.put("successes", totalSuccesses);
        invocations.put("failures", totalFailures);
        invocations.put("successRate", totalInvocations > 0 ? (double) totalSuccesses / totalInvocations : 0.0);
        invocations.put("averageExecutionTimeMs", totalInvocations > 0 ? 
                       (totalExecutionTime / 1_000_000.0) / totalInvocations : 0.0);
        metrics.put("invocations", invocations);
        
        // Publisher metrics
        Map<String, Object> publishers = new ConcurrentHashMap<>();
        publishers.put("invocations", publisherInvocations.get());
        publishers.put("successes", publisherSuccesses.get());
        publishers.put("failures", publisherFailures.get());
        publishers.put("successRate", publisherInvocations.get() > 0 ? 
                      (double) publisherSuccesses.get() / publisherInvocations.get() : 0.0);
        publishers.put("averageExecutionTimeMs", publisherInvocations.get() > 0 ? 
                      (publisherTotalTime.sum() / 1_000_000.0) / publisherInvocations.get() : 0.0);
        metrics.put("publishers", publishers);
        
        // Subscriber metrics
        Map<String, Object> subscribers = new ConcurrentHashMap<>();
        subscribers.put("invocations", subscriberInvocations.get());
        subscribers.put("successes", subscriberSuccesses.get());
        subscribers.put("failures", subscriberFailures.get());
        subscribers.put("successRate", subscriberInvocations.get() > 0 ? 
                       (double) subscriberSuccesses.get() / subscriberInvocations.get() : 0.0);
        subscribers.put("averageExecutionTimeMs", subscriberInvocations.get() > 0 ? 
                       (subscriberTotalTime.sum() / 1_000_000.0) / subscriberInvocations.get() : 0.0);
        metrics.put("subscribers", subscribers);
        
        // Message metrics
        Map<String, Object> messages = new ConcurrentHashMap<>();
        messages.put("published", messagesPublished.get());
        messages.put("received", messagesReceived.get());
        messages.put("totalSizeBytes", totalMessageSize.sum());
        long totalMessages = messagesPublished.get() + messagesReceived.get();
        messages.put("averageSizeBytes", totalMessages > 0 ? totalMessageSize.sum() / totalMessages : 0);
        metrics.put("messages", messages);
        
        // Error metrics
        Map<String, Long> errors = new ConcurrentHashMap<>();
        errorCounts.forEach((errorType, count) -> errors.put(errorType, count.get()));
        metrics.put("errors", errors);
        
        // Method-specific metrics
        Map<String, Map<String, Object>> methodStats = new ConcurrentHashMap<>();
        methodMetrics.forEach((methodName, methodMetric) -> 
            methodStats.put(methodName, methodMetric.getMetrics()));
        metrics.put("methods", methodStats);
        
        // Endpoint-specific metrics
        Map<String, Map<String, Object>> endpointStats = new ConcurrentHashMap<>();
        endpointMetrics.forEach((endpoint, endpointMetric) -> 
            endpointStats.put(endpoint, endpointMetric.getMetrics()));
        metrics.put("endpoints", endpointStats);
        
        return metrics;
    }

    /**
     * Reset all metrics.
     */
    public void resetMetrics() {
        if (!enabled) return;
        
        publisherInvocations.set(0);
        publisherSuccesses.set(0);
        publisherFailures.set(0);
        publisherTotalTime.reset();
        
        subscriberInvocations.set(0);
        subscriberSuccesses.set(0);
        subscriberFailures.set(0);
        subscriberTotalTime.reset();
        
        messagesPublished.set(0);
        messagesReceived.set(0);
        totalMessageSize.reset();
        
        errorCounts.clear();
        methodMetrics.clear();
        endpointMetrics.clear();
        
        log.info("Reset all annotation metrics");
    }

    /**
     * Check if metrics collection is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Metrics for a specific method.
     */
    private static class MethodMetrics {
        private final AtomicLong invocations = new AtomicLong(0);
        private final AtomicLong successes = new AtomicLong(0);
        private final AtomicLong failures = new AtomicLong(0);
        private final LongAdder totalTime = new LongAdder();
        private final ConcurrentHashMap<String, AtomicLong> errors = new ConcurrentHashMap<>();
        
        void recordInvocation(Duration executionTime, boolean success) {
            invocations.incrementAndGet();
            totalTime.add(executionTime.toNanos());
            
            if (success) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
        }
        
        void recordError(String errorType) {
            errors.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new ConcurrentHashMap<>();
            metrics.put("invocations", invocations.get());
            metrics.put("successes", successes.get());
            metrics.put("failures", failures.get());
            metrics.put("successRate", invocations.get() > 0 ? 
                       (double) successes.get() / invocations.get() : 0.0);
            metrics.put("averageExecutionTimeMs", invocations.get() > 0 ? 
                       (totalTime.sum() / 1_000_000.0) / invocations.get() : 0.0);
            
            Map<String, Long> errorMap = new ConcurrentHashMap<>();
            errors.forEach((errorType, count) -> errorMap.put(errorType, count.get()));
            metrics.put("errors", errorMap);
            
            return metrics;
        }
    }

    /**
     * Metrics for a specific endpoint.
     */
    private static class EndpointMetrics {
        private final AtomicLong publishes = new AtomicLong(0);
        private final AtomicLong receives = new AtomicLong(0);
        private final AtomicLong publishSuccesses = new AtomicLong(0);
        private final AtomicLong receiveSuccesses = new AtomicLong(0);
        private final LongAdder publishTime = new LongAdder();
        private final LongAdder receiveTime = new LongAdder();
        private final LongAdder messageSize = new LongAdder();
        private final AtomicLong messageCount = new AtomicLong(0);
        private final ConcurrentHashMap<String, AtomicLong> errors = new ConcurrentHashMap<>();
        
        void recordPublish(Duration executionTime, boolean success) {
            publishes.incrementAndGet();
            publishTime.add(executionTime.toNanos());
            
            if (success) {
                publishSuccesses.incrementAndGet();
            }
        }
        
        void recordReceive(Duration executionTime, boolean success) {
            receives.incrementAndGet();
            receiveTime.add(executionTime.toNanos());
            
            if (success) {
                receiveSuccesses.incrementAndGet();
            }
        }
        
        void recordMessageSize(int size) {
            messageSize.add(size);
            messageCount.incrementAndGet();
        }
        
        void recordError(String errorType) {
            errors.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new ConcurrentHashMap<>();
            metrics.put("publishes", publishes.get());
            metrics.put("receives", receives.get());
            metrics.put("publishSuccessRate", publishes.get() > 0 ? 
                       (double) publishSuccesses.get() / publishes.get() : 0.0);
            metrics.put("receiveSuccessRate", receives.get() > 0 ? 
                       (double) receiveSuccesses.get() / receives.get() : 0.0);
            metrics.put("averagePublishTimeMs", publishes.get() > 0 ? 
                       (publishTime.sum() / 1_000_000.0) / publishes.get() : 0.0);
            metrics.put("averageReceiveTimeMs", receives.get() > 0 ? 
                       (receiveTime.sum() / 1_000_000.0) / receives.get() : 0.0);
            metrics.put("averageMessageSizeBytes", messageCount.get() > 0 ? 
                       messageSize.sum() / messageCount.get() : 0);
            
            Map<String, Long> errorMap = new ConcurrentHashMap<>();
            errors.forEach((errorType, count) -> errorMap.put(errorType, count.get()));
            metrics.put("errors", errorMap);
            
            return metrics;
        }
    }
} 