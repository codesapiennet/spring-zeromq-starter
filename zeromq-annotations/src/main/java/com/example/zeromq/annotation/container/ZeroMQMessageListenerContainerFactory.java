package com.example.zeromq.annotation.container;

import com.example.zeromq.annotation.EnableZeroMQ;
import com.example.zeromq.autoconfig.ZeroMqTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for creating ZeroMQ message listener containers.
 * 
 * <p>This factory manages the lifecycle of message listener containers,
 * providing a centralized way to create, configure, and manage containers
 * for different subscriber patterns and configurations.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class ZeroMQMessageListenerContainerFactory {

    private static final Logger log = LoggerFactory.getLogger(ZeroMQMessageListenerContainerFactory.class);
    
    private final ZeroMqTemplate template;
    private final ConcurrentHashMap<String, ZeroMQMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final AtomicLong containerIdGenerator = new AtomicLong(1);
    
    // Default configuration
    private int defaultConcurrency = 1;
    private boolean connectionPoolingEnabled = true;
    private boolean vectorProcessingEnabled = true;
    private EnableZeroMQ.ErrorHandling defaultErrorHandling = EnableZeroMQ.ErrorHandling.LOG_AND_CONTINUE;
    private EnableZeroMQ.Mode mode = EnableZeroMQ.Mode.AUTO;

    /**
     * Create a new container factory.
     * 
     * @param template the ZeroMQ template
     */
    public ZeroMQMessageListenerContainerFactory(ZeroMqTemplate template) {
        this.template = template;
    }

    /**
     * Create a message listener container.
     * 
     * @param endpoint the endpoint configuration
     * @return a new message listener container
     */
    public ZeroMQMessageListenerContainer createContainer(MessageListenerEndpoint endpoint) {
        String containerId = "container-" + containerIdGenerator.getAndIncrement();
        
        ZeroMQMessageListenerContainer container = new ZeroMQMessageListenerContainer(
            containerId, template, endpoint);
        
        // Apply default configuration
        container.setConcurrency(endpoint.getConcurrency() > 0 ? endpoint.getConcurrency() : defaultConcurrency);
        container.setConnectionPoolingEnabled(connectionPoolingEnabled);
        container.setVectorProcessingEnabled(vectorProcessingEnabled);
        container.setErrorHandling(defaultErrorHandling);
        container.setMode(mode);
        
        containers.put(containerId, container);
        
        log.debug("Created message listener container: {}", containerId);
        
        return container;
    }

    /**
     * Get a container by ID.
     * 
     * @param containerId the container ID
     * @return the container or null if not found
     */
    public ZeroMQMessageListenerContainer getContainer(String containerId) {
        return containers.get(containerId);
    }

    /**
     * Remove and stop a container.
     * 
     * @param containerId the container ID
     * @return true if the container was found and stopped
     */
    public boolean removeContainer(String containerId) {
        ZeroMQMessageListenerContainer container = containers.remove(containerId);
        if (container != null) {
            try {
                container.stop();
                log.debug("Stopped and removed container: {}", containerId);
                return true;
            } catch (Exception e) {
                log.warn("Error stopping container {}: {}", containerId, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Get all container IDs.
     * 
     * @return set of container IDs
     */
    public java.util.Set<String> getContainerIds() {
        return new java.util.HashSet<>(containers.keySet());
    }

    /**
     * Get container statistics.
     * 
     * @return statistics map
     */
    public java.util.Map<String, Object> getStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalContainers", containers.size());
        stats.put("nextContainerId", containerIdGenerator.get());
        
        long runningContainers = containers.values().stream()
            .mapToLong(container -> container.isRunning() ? 1L : 0L)
            .sum();
        stats.put("runningContainers", runningContainers);
        
        return stats;
    }

    /**
     * Shutdown all containers.
     */
    public void shutdown() {
        log.info("Shutting down {} message listener containers", containers.size());
        
        containers.values().parallelStream().forEach(container -> {
            try {
                container.stop();
            } catch (Exception e) {
                log.warn("Error stopping container {}: {}", container.getContainerId(), e.getMessage());
            }
        });
        
        containers.clear();
        log.info("Message listener container factory shutdown completed");
    }

    // Configuration setters

    public void setDefaultConcurrency(int defaultConcurrency) {
        this.defaultConcurrency = defaultConcurrency;
    }

    public void setConnectionPoolingEnabled(boolean connectionPoolingEnabled) {
        this.connectionPoolingEnabled = connectionPoolingEnabled;
    }

    public void setVectorProcessingEnabled(boolean vectorProcessingEnabled) {
        this.vectorProcessingEnabled = vectorProcessingEnabled;
    }

    public void setDefaultErrorHandling(EnableZeroMQ.ErrorHandling defaultErrorHandling) {
        this.defaultErrorHandling = defaultErrorHandling;
    }

    public void setMode(EnableZeroMQ.Mode mode) {
        this.mode = mode;
    }

    /**
     * Configuration for a message listener endpoint.
     */
    public static class MessageListenerEndpoint {
        private String id;
        private String endpoint;
        private java.util.List<String> topics = new java.util.ArrayList<>();
        private int concurrency = 1;
        private Object messageListener;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        
        public java.util.List<String> getTopics() { return topics; }
        public void setTopics(java.util.List<String> topics) { this.topics = topics; }
        
        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
        
        public Object getMessageListener() { return messageListener; }
        public void setMessageListener(Object messageListener) { this.messageListener = messageListener; }
    }

    /**
     * A message listener container for ZeroMQ.
     */
    public static class ZeroMQMessageListenerContainer {
        private static final Logger log = LoggerFactory.getLogger(ZeroMQMessageListenerContainer.class);
        
        private final String containerId;
        private final ZeroMqTemplate template;
        private final MessageListenerEndpoint endpoint;
        
        private volatile boolean running = false;
        private int concurrency = 1;
        private boolean connectionPoolingEnabled = true;
        private boolean vectorProcessingEnabled = true;
        private EnableZeroMQ.ErrorHandling errorHandling = EnableZeroMQ.ErrorHandling.LOG_AND_CONTINUE;
        private EnableZeroMQ.Mode mode = EnableZeroMQ.Mode.AUTO;

        public ZeroMQMessageListenerContainer(String containerId, ZeroMqTemplate template, 
                                            MessageListenerEndpoint endpoint) {
            this.containerId = containerId;
            this.template = template;
            this.endpoint = endpoint;
        }

        /**
         * Start the container.
         */
        public void start() {
            if (running) {
                log.warn("Container {} is already running", containerId);
                return;
            }
            
            log.info("Starting message listener container: {}", containerId);
            
            try {
                // TODO: In a full implementation, this would:
                // 1. Create message listeners based on endpoint configuration
                // 2. Set up error handlers and retry logic
                // 3. Configure concurrency and connection pooling
                // 4. Start background threads for message processing
                
                running = true;
                log.info("Message listener container started: {}", containerId);
                
            } catch (Exception e) {
                log.error("Failed to start container {}: {}", containerId, e.getMessage(), e);
                throw new RuntimeException("Container startup failed", e);
            }
        }

        /**
         * Stop the container.
         */
        public void stop() {
            if (!running) {
                return;
            }
            
            log.info("Stopping message listener container: {}", containerId);
            
            try {
                // TODO: In a full implementation, this would:
                // 1. Stop all message listeners gracefully
                // 2. Wait for in-flight messages to complete
                // 3. Close connections and release resources
                
                running = false;
                log.info("Message listener container stopped: {}", containerId);
                
            } catch (Exception e) {
                log.error("Error stopping container {}: {}", containerId, e.getMessage(), e);
            }
        }

        /**
         * Check if the container is running.
         */
        public boolean isRunning() {
            return running;
        }

        public String getContainerId() { return containerId; }
        
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
        public void setConnectionPoolingEnabled(boolean enabled) { this.connectionPoolingEnabled = enabled; }
        public void setVectorProcessingEnabled(boolean enabled) { this.vectorProcessingEnabled = enabled; }
        public void setErrorHandling(EnableZeroMQ.ErrorHandling errorHandling) { this.errorHandling = errorHandling; }
        public void setMode(EnableZeroMQ.Mode mode) { this.mode = mode; }
        
        /**
         * Get the configured endpoint address for this container.
         *
         * @return endpoint address or null if not available
         */
        public String getEndpointAddress() { return endpoint != null ? endpoint.getEndpoint() : null; }

        /**
         * Get a copy of configured topics for this container.
         *
         * @return list of topics
         */
        public java.util.List<String> getEndpointTopics() { return endpoint != null ? new java.util.ArrayList<>(endpoint.getTopics()) : java.util.List.of(); }
    }
} 