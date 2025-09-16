package com.example.zeromq.annotation.processor;

import com.example.zeromq.annotation.EnableZeroMQ;
import com.example.zeromq.annotation.container.ZeroMQMessageListenerContainerFactory;
import com.example.zeromq.autoconfig.ZeroMqTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Processor for managing ZeroMQ subscriber method lifecycles.
 * 
 * <p>This processor handles the registration, lifecycle management, and
 * execution coordination of methods annotated with {@code @ZeroMQSubscriber}.
 * It works closely with the annotation post processor to manage active
 * subscribers and their associated resources.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class ZeroMQSubscriberProcessor implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ZeroMQSubscriberProcessor.class);
    
    private final ZeroMqTemplate template;
    private final ZeroMQMessageListenerContainerFactory containerFactory;
    
    // Configuration
    private int defaultConcurrency = 1;
    private EnableZeroMQ.ErrorHandling defaultErrorHandling = EnableZeroMQ.ErrorHandling.LOG_AND_CONTINUE;
    private boolean metricsEnabled = true;
    
    // Active subscribers
    private final ConcurrentHashMap<String, SubscriberRegistration> activeSubscribers = new ConcurrentHashMap<>();
    private final AtomicLong subscriptionIdGenerator = new AtomicLong(1);
    
    // Statistics
    private final AtomicLong subscribersRegistered = new AtomicLong(0);
    private final AtomicLong subscribersStarted = new AtomicLong(0);
    private final AtomicLong subscribersStopped = new AtomicLong(0);
    private final AtomicLong subscriberErrors = new AtomicLong(0);

    /**
     * Create a new subscriber processor.
     * 
     * @param template the ZeroMQ template
     * @param containerFactory the container factory for creating listeners
     */
    public ZeroMQSubscriberProcessor(ZeroMqTemplate template, 
                                   ZeroMQMessageListenerContainerFactory containerFactory) {
        this.template = template;
        this.containerFactory = containerFactory;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("ZeroMQ subscriber processor initialized with {} active subscribers", 
                activeSubscribers.size());
    }

    /**
     * Register a new subscriber method.
     * 
     * @param targetBean the bean containing the method
     * @param method the subscriber method
     * @param endpoint the endpoint to subscribe to
     * @param topics the topics to subscribe to
     * @param concurrency the concurrency level
     * @return the subscription ID
     */
    public String registerSubscriber(Object targetBean, Method method, String endpoint, 
                                   java.util.List<String> topics, int concurrency) {
        String subscriptionId = "sub-" + subscriptionIdGenerator.getAndIncrement();
        
        try {
            SubscriberRegistration registration = new SubscriberRegistration(
                subscriptionId, targetBean, method, endpoint, topics, concurrency);
            
            // Configure error handling and metrics
            registration.setErrorHandling(defaultErrorHandling);
            registration.setMetricsEnabled(metricsEnabled);
            
            // Store the registration
            activeSubscribers.put(subscriptionId, registration);
            subscribersRegistered.incrementAndGet();
            
            log.info("Registered subscriber: {} for method {}.{}", 
                    subscriptionId, targetBean.getClass().getSimpleName(), method.getName());
            
            return subscriptionId;
            
        } catch (Exception e) {
            subscriberErrors.incrementAndGet();
            log.error("Failed to register subscriber for method {}.{}: {}", 
                     targetBean.getClass().getSimpleName(), method.getName(), e.getMessage(), e);
            throw new RuntimeException("Subscriber registration failed", e);
        }
    }

    /**
     * Start a subscriber by ID.
     * 
     * @param subscriptionId the subscription ID
     * @return true if the subscriber was started
     */
    public boolean startSubscriber(String subscriptionId) {
        SubscriberRegistration registration = activeSubscribers.get(subscriptionId);
        if (registration == null) {
            log.warn("No subscriber found with ID: {}", subscriptionId);
            return false;
        }
        
        try {
            if (registration.isRunning()) {
                log.warn("Subscriber {} is already running", subscriptionId);
                return true;
            }
            
            registration.start();
            subscribersStarted.incrementAndGet();
            
            log.info("Started subscriber: {}", subscriptionId);
            return true;
            
        } catch (Exception e) {
            subscriberErrors.incrementAndGet();
            log.error("Failed to start subscriber {}: {}", subscriptionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Stop a subscriber by ID.
     * 
     * @param subscriptionId the subscription ID
     * @return true if the subscriber was stopped
     */
    public boolean stopSubscriber(String subscriptionId) {
        SubscriberRegistration registration = activeSubscribers.get(subscriptionId);
        if (registration == null) {
            log.warn("No subscriber found with ID: {}", subscriptionId);
            return false;
        }
        
        try {
            if (!registration.isRunning()) {
                log.warn("Subscriber {} is not running", subscriptionId);
                return true;
            }
            
            registration.stop();
            subscribersStopped.incrementAndGet();
            
            log.info("Stopped subscriber: {}", subscriptionId);
            return true;
            
        } catch (Exception e) {
            subscriberErrors.incrementAndGet();
            log.error("Failed to stop subscriber {}: {}", subscriptionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Unregister a subscriber completely.
     * 
     * @param subscriptionId the subscription ID
     * @return true if the subscriber was unregistered
     */
    public boolean unregisterSubscriber(String subscriptionId) {
        SubscriberRegistration registration = activeSubscribers.remove(subscriptionId);
        if (registration == null) {
            log.warn("No subscriber found with ID: {}", subscriptionId);
            return false;
        }
        
        try {
            // Stop the subscriber if it's running
            if (registration.isRunning()) {
                registration.stop();
                subscribersStopped.incrementAndGet();
            }
            
            log.info("Unregistered subscriber: {}", subscriptionId);
            return true;
            
        } catch (Exception e) {
            subscriberErrors.incrementAndGet();
            log.error("Failed to unregister subscriber {}: {}", subscriptionId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all active subscriber IDs.
     * 
     * @return set of subscription IDs
     */
    public java.util.Set<String> getActiveSubscriberIds() {
        return new java.util.HashSet<>(activeSubscribers.keySet());
    }

    /**
     * Get subscriber information.
     * 
     * @param subscriptionId the subscription ID
     * @return subscriber information or null if not found
     */
    public SubscriberInfo getSubscriberInfo(String subscriptionId) {
        SubscriberRegistration registration = activeSubscribers.get(subscriptionId);
        if (registration == null) {
            return null;
        }
        
        return new SubscriberInfo(
            subscriptionId,
            registration.getTargetBean().getClass().getSimpleName(),
            registration.getMethod().getName(),
            registration.getEndpoint(),
            registration.getTopics(),
            registration.getConcurrency(),
            registration.isRunning(),
            registration.getStartTime(),
            registration.getMessageCount(),
            registration.getErrorCount()
        );
    }

    /**
     * Start all registered subscribers.
     */
    public void startAllSubscribers() {
        log.info("Starting all {} registered subscribers", activeSubscribers.size());
        
        activeSubscribers.forEach((subscriptionId, registration) -> {
            try {
                if (!registration.isRunning()) {
                    registration.start();
                    subscribersStarted.incrementAndGet();
                    log.debug("Started subscriber: {}", subscriptionId);
                }
            } catch (Exception e) {
                subscriberErrors.incrementAndGet();
                log.error("Failed to start subscriber {}: {}", subscriptionId, e.getMessage(), e);
            }
        });
        
        log.info("Completed startup of all subscribers");
    }

    /**
     * Stop all registered subscribers.
     */
    public void stopAllSubscribers() {
        log.info("Stopping all {} registered subscribers", activeSubscribers.size());
        
        activeSubscribers.forEach((subscriptionId, registration) -> {
            try {
                if (registration.isRunning()) {
                    registration.stop();
                    subscribersStopped.incrementAndGet();
                    log.debug("Stopped subscriber: {}", subscriptionId);
                }
            } catch (Exception e) {
                subscriberErrors.incrementAndGet();
                log.error("Failed to stop subscriber {}: {}", subscriptionId, e.getMessage(), e);
            }
        });
        
        log.info("Completed shutdown of all subscribers");
    }

    /**
     * Get processor statistics.
     * 
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        
        stats.put("subscribersRegistered", subscribersRegistered.get());
        stats.put("activeSubscribers", activeSubscribers.size());
        stats.put("subscribersStarted", subscribersStarted.get());
        stats.put("subscribersStopped", subscribersStopped.get());
        stats.put("subscriberErrors", subscriberErrors.get());
        
        // Count running subscribers
        long runningCount = activeSubscribers.values().stream()
            .mapToLong(reg -> reg.isRunning() ? 1L : 0L)
            .sum();
        stats.put("runningSubscribers", runningCount);
        
        // Total message and error counts
        long totalMessages = activeSubscribers.values().stream()
            .mapToLong(SubscriberRegistration::getMessageCount)
            .sum();
        long totalErrors = activeSubscribers.values().stream()
            .mapToLong(SubscriberRegistration::getErrorCount)
            .sum();
        
        stats.put("totalMessagesProcessed", totalMessages);
        stats.put("totalProcessingErrors", totalErrors);
        
        return stats;
    }

    @Override
    public void destroy() throws Exception {
        log.info("Shutting down ZeroMQ subscriber processor");
        stopAllSubscribers();
        activeSubscribers.clear();
        log.info("ZeroMQ subscriber processor shutdown completed");
    }

    // Configuration setters

    public void setDefaultConcurrency(int defaultConcurrency) {
        this.defaultConcurrency = defaultConcurrency;
    }

    public void setDefaultErrorHandling(EnableZeroMQ.ErrorHandling defaultErrorHandling) {
        this.defaultErrorHandling = defaultErrorHandling;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    /**
     * Represents a registered subscriber method.
     */
    private static class SubscriberRegistration {
        private final String subscriptionId;
        private final Object targetBean;
        private final Method method;
        private final String endpoint;
        private final java.util.List<String> topics;
        private final int concurrency;
        private final java.time.Instant registrationTime;
        
        private volatile boolean running = false;
        private volatile java.time.Instant startTime = null;
        private EnableZeroMQ.ErrorHandling errorHandling;
        private boolean metricsEnabled = true;
        
        // Statistics
        private final AtomicLong messageCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);

        public SubscriberRegistration(String subscriptionId, Object targetBean, Method method,
                                    String endpoint, java.util.List<String> topics, int concurrency) {
            this.subscriptionId = subscriptionId;
            this.targetBean = targetBean;
            this.method = method;
            this.endpoint = endpoint;
            this.topics = new java.util.ArrayList<>(topics);
            this.concurrency = concurrency;
            this.registrationTime = java.time.Instant.now();
            
            // Make method accessible
            method.setAccessible(true);
        }

        public void start() {
            if (running) {
                return;
            }
            
            // TODO: In a full implementation, this would:
            // 1. Create actual ZMQ sockets and connections
            // 2. Set up message listeners with proper error handling
            // 3. Configure retry and dead letter queue logic
            // 4. Start background threads for message processing
            
            running = true;
            startTime = java.time.Instant.now();
        }

        public void stop() {
            if (!running) {
                return;
            }
            
            // TODO: In a full implementation, this would:
            // 1. Gracefully shutdown message listeners
            // 2. Wait for in-flight messages to complete
            // 3. Close connections and release resources
            
            running = false;
        }

        public void processMessage(Object message) {
            if (!running) {
                return;
            }
            
            try {
                // Simple method invocation - in a full implementation, this would include:
                // - Parameter type conversion and mapping
                // - Error handling according to configured strategy
                // - Metrics collection
                // - Retry logic
                
                method.invoke(targetBean, message);
                messageCount.incrementAndGet();
                
            } catch (Exception e) {
                errorCount.incrementAndGet();
                // Handle according to error handling strategy
                handleError(e, message);
            }
        }
        
        private void handleError(Exception error, Object message) {
            // TODO: Implement full error handling strategy
            log.error("Error processing message in subscriber {}: {}", subscriptionId, error.getMessage(), error);
        }

        // Getters
        public String getSubscriptionId() { return subscriptionId; }
        public Object getTargetBean() { return targetBean; }
        public Method getMethod() { return method; }
        public String getEndpoint() { return endpoint; }
        public java.util.List<String> getTopics() { return new java.util.ArrayList<>(topics); }
        public int getConcurrency() { return concurrency; }
        public boolean isRunning() { return running; }
        public java.time.Instant getStartTime() { return startTime; }
        public long getMessageCount() { return messageCount.get(); }
        public long getErrorCount() { return errorCount.get(); }
        public java.time.Instant getRegistrationTime() { return registrationTime; }

        // Setters
        public void setErrorHandling(EnableZeroMQ.ErrorHandling errorHandling) { this.errorHandling = errorHandling; }
        public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }
    }

    /**
     * Information about a subscriber.
     */
    public static class SubscriberInfo {
        private final String subscriptionId;
        private final String beanClass;
        private final String methodName;
        private final String endpoint;
        private final java.util.List<String> topics;
        private final int concurrency;
        private final boolean running;
        private final java.time.Instant startTime;
        private final long messageCount;
        private final long errorCount;

        public SubscriberInfo(String subscriptionId, String beanClass, String methodName,
                            String endpoint, java.util.List<String> topics, int concurrency,
                            boolean running, java.time.Instant startTime, long messageCount, long errorCount) {
            this.subscriptionId = subscriptionId;
            this.beanClass = beanClass;
            this.methodName = methodName;
            this.endpoint = endpoint;
            this.topics = new java.util.ArrayList<>(topics);
            this.concurrency = concurrency;
            this.running = running;
            this.startTime = startTime;
            this.messageCount = messageCount;
            this.errorCount = errorCount;
        }

        // Getters
        public String getSubscriptionId() { return subscriptionId; }
        public String getBeanClass() { return beanClass; }
        public String getMethodName() { return methodName; }
        public String getEndpoint() { return endpoint; }
        public java.util.List<String> getTopics() { return new java.util.ArrayList<>(topics); }
        public int getConcurrency() { return concurrency; }
        public boolean isRunning() { return running; }
        public java.time.Instant getStartTime() { return startTime; }
        public long getMessageCount() { return messageCount; }
        public long getErrorCount() { return errorCount; }

        @Override
        public String toString() {
            return String.format("SubscriberInfo[id=%s, method=%s.%s, endpoint=%s, running=%s, messages=%d]",
                    subscriptionId, beanClass, methodName, endpoint, running, messageCount);
        }
    }
} 