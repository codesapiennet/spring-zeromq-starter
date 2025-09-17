package com.example.zeromq.autoconfig;

import com.example.zeromq.core.ZmqSocketFactory;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics collector for ZeroMQ operations using Micrometer.
 * 
 * <p>This collector automatically registers and maintains metrics for:
 * <ul>
 * <li>Socket creation and lifecycle statistics</li>
 * <li>Message send/receive counters and timers</li>
 * <li>Connection pool utilization</li>
 * <li>Security operation metrics</li>
 * <li>Error rates and failure patterns</li>
 * <li>Performance and throughput measurements</li>
 * </ul>
 * 
 * <p>All metrics are properly tagged for filtering and aggregation in
 * monitoring systems like Prometheus, InfluxDB, or CloudWatch.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class ZeroMqMetricsCollector implements MeterBinder, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqMetricsCollector.class);
    
    private final ZmqSocketFactory socketFactory;
    private final ZeroMqProperties properties;
    private final ScheduledExecutorService scheduler;
    
    // Metrics
    private Counter socketsCreated;
    private Counter socketsClosed;
    private Counter messagesPublished;
    private Counter messagesReceived;
    private Counter securityOperations;
    private Counter errors;
    private Timer messageProcessingTime;
    private Timer socketCreationTime;
    private Gauge activeSockets;
    private Gauge memoryUsage;
    
    // Internal counters
    private final LongAdder socketCreationCounter = new LongAdder();
    private final LongAdder socketCloseCounter = new LongAdder();
    private final LongAdder messagePublishCounter = new LongAdder();
    private final LongAdder messageReceiveCounter = new LongAdder();
    private final LongAdder securityOpCounter = new LongAdder();
    private final LongAdder errorCounter = new LongAdder();
    private final AtomicLong lastMetricsCollection = new AtomicLong(System.currentTimeMillis());

    /**
     * Create a new metrics collector.
     * 
     * @param socketFactory the socket factory to monitor
     * @param meterRegistry the meter registry to register metrics with
     * @param properties the configuration properties
     */
    public ZeroMqMetricsCollector(ZmqSocketFactory socketFactory, 
                                 MeterRegistry meterRegistry,
                                 ZeroMqProperties properties) {
        this.socketFactory = socketFactory;
        this.properties = properties;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "zeromq-metrics-collector");
            t.setDaemon(true);
            return t;
        });
        
        // Bind metrics immediately
        bindTo(meterRegistry);
        
        // Start periodic collection
        startPeriodicCollection();
        
        log.info("ZeroMQ metrics collector initialized with interval: {}", 
                properties.getMonitoring().getMetricsInterval());
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // Socket lifecycle metrics
        socketsCreated = Counter.builder("zeromq.sockets.created")
                .description("Total number of ZeroMQ sockets created")
                .tag("component", "zeromq")
                .register(registry);
        
        socketsClosed = Counter.builder("zeromq.sockets.closed")
                .description("Total number of ZeroMQ sockets closed")
                .tag("component", "zeromq")
                .register(registry);
        
        // Message flow metrics
        messagesPublished = Counter.builder("zeromq.messages.published")
                .description("Total number of messages published")
                .tag("component", "zeromq")
                .register(registry);
        
        messagesReceived = Counter.builder("zeromq.messages.received")
                .description("Total number of messages received")
                .tag("component", "zeromq")
                .register(registry);
        
        // Security metrics
        securityOperations = Counter.builder("zeromq.security.operations")
                .description("Total number of security operations performed")
                .tag("component", "zeromq")
                .register(registry);
        
        // Error tracking
        errors = Counter.builder("zeromq.errors")
                .description("Total number of ZeroMQ errors")
                .tag("component", "zeromq")
                .register(registry);
        
        // Timing metrics
        messageProcessingTime = Timer.builder("zeromq.message.processing.time")
                .description("Time taken to process messages")
                .tag("component", "zeromq")
                .register(registry);
        
        socketCreationTime = Timer.builder("zeromq.socket.creation.time")
                .description("Time taken to create sockets")
                .tag("component", "zeromq")
                .register(registry);
        
        // Gauge metrics
        activeSockets = Gauge.builder("zeromq.sockets.active", this, ZeroMqMetricsCollector::getActiveSocketCount)
                .description("Current number of active ZeroMQ sockets")
                .tag("component", "zeromq")
                .register(registry);
        
        memoryUsage = Gauge.builder("zeromq.memory.usage.bytes", this, ZeroMqMetricsCollector::getEstimatedMemoryUsage)
                .description("Estimated ZeroMQ memory usage")
                .tag("component", "zeromq")
                .register(registry);
        
        // Socket type specific metrics
        registerSocketTypeMetrics(registry);
        
        // Performance metrics
        registerPerformanceMetrics(registry);
        
        log.debug("ZeroMQ metrics registered with meter registry");
    }

    /**
     * Register socket type specific metrics.
     */
    private void registerSocketTypeMetrics(MeterRegistry registry) {
        String[] socketTypes = {"PUB", "SUB", "PUSH", "PULL", "REQ", "REP", "DEALER", "ROUTER"};
        
        for (String type : socketTypes) {
            Counter.builder("zeromq.sockets.created.by.type")
                    .description("Sockets created by type")
                    .tag("component", "zeromq")
                    .tag("socket.type", type)
                    .register(registry);
        }
    }

    /**
     * Register performance and throughput metrics.
     */
    private void registerPerformanceMetrics(MeterRegistry registry) {
        // Throughput metrics
        Gauge.builder("zeromq.throughput.messages.per.second", this, ZeroMqMetricsCollector::getCurrentThroughput)
                .description("Message throughput per second")
                .tag("component", "zeromq")
                .register(registry);
        
        // Connection pool metrics
        Gauge.builder("zeromq.pool.utilization", this, ZeroMqMetricsCollector::getPoolUtilization)
                .description("Connection pool utilization percentage")
                .tag("component", "zeromq")
                .register(registry);
        
        // Security metrics
        Gauge.builder("zeromq.security.curve.enabled", this, ZeroMqMetricsCollector::isCurveEnabled)
                .description("Whether CURVE security is enabled (1=yes, 0=no)")
                .tag("component", "zeromq")
                .register(registry);
    }

    /**
     * Start periodic metrics collection.
     */
    private void startPeriodicCollection() {
        Duration interval = properties.getMonitoring().getMetricsInterval();
        
        scheduler.scheduleAtFixedRate(this::collectMetrics, 
                interval.toMillis(), 
                interval.toMillis(), 
                TimeUnit.MILLISECONDS);
        
        log.debug("Started periodic metrics collection every {}", interval);
    }

    /**
     * Collect metrics from various sources.
     */
    private void collectMetrics() {
        try {
            log.trace("Collecting ZeroMQ metrics");
            
            // Update socket factory statistics
            updateSocketFactoryMetrics();
            
            // Update memory usage estimates
            updateMemoryMetrics();
            
            // Update throughput calculations
            updateThroughputMetrics();
            
            lastMetricsCollection.set(System.currentTimeMillis());
            
        } catch (Exception e) {
            log.warn("Error collecting ZeroMQ metrics: {}", e.getMessage());
            recordError("metrics_collection", e);
        }
    }

    /**
     * Update socket factory related metrics.
     */
    private void updateSocketFactoryMetrics() {
        Map<String, Long> stats = socketFactory.getSocketCreationStats();
        
        // Update counters based on factory statistics
        stats.forEach((socketType, count) -> {
            // This would typically be done through event listeners in the socket factory
            // For now, we'll track the cumulative counts
        });
        
        long totalCreated = socketFactory.getTotalSocketsCreated();
        if (totalCreated > socketCreationCounter.sum()) {
            long newSockets = totalCreated - socketCreationCounter.sum();
            socketsCreated.increment(newSockets);
            socketCreationCounter.add(newSockets);
        }
    }

    /**
     * Update memory usage estimates.
     */
    private void updateMemoryMetrics() {
        // This is an estimation since ZeroMQ doesn't expose detailed memory stats
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        // Estimate ZeroMQ usage as a fraction of total used memory
        // This is very rough and would need refinement for production use
        double zmqFraction = Math.min(0.1, getActiveSocketCount() * 0.001); // Very rough estimate
        
        // The gauge will be updated automatically
    }

    /**
     * Update throughput metrics.
     */
    private void updateThroughputMetrics() {
        long currentTime = System.currentTimeMillis();
        long lastCollection = lastMetricsCollection.get();
        long timeDelta = currentTime - lastCollection;
        
        if (timeDelta > 0) {
            // Calculate messages per second
            long totalMessages = messagePublishCounter.sum() + messageReceiveCounter.sum();
            // This would need to track deltas properly for accurate throughput
        }
    }

    // Public methods for recording events

    /**
     * Record a socket creation event.
     * 
     * @param socketType the type of socket created
     * @param creationTime the time taken to create the socket
     */
    public void recordSocketCreation(String socketType, Duration creationTime) {
        socketsCreated.increment();
        socketCreationTime.record(creationTime);
        socketCreationCounter.increment();
        
        log.trace("Recorded socket creation: type={}, duration={}", socketType, creationTime);
    }

    /**
     * Record a socket closure event.
     * 
     * @param socketType the type of socket closed
     */
    public void recordSocketClosure(String socketType) {
        socketsClosed.increment();
        socketCloseCounter.increment();
        
        log.trace("Recorded socket closure: type={}", socketType);
    }

    /**
     * Record a message publication event.
     * 
     * @param topic the topic (if applicable)
     * @param messageSize the message size in bytes
     * @param processingTime the time taken to publish
     */
    public void recordMessagePublication(String topic, int messageSize, Duration processingTime) {
        messagesPublished.increment();
        messageProcessingTime.record(processingTime);
        messagePublishCounter.increment();
        
        log.trace("Recorded message publication: topic={}, size={}, duration={}", 
                 topic, messageSize, processingTime);
    }

    /**
     * Record a message reception event.
     * 
     * @param topic the topic (if applicable)
     * @param messageSize the message size in bytes
     * @param processingTime the time taken to process
     */
    public void recordMessageReception(String topic, int messageSize, Duration processingTime) {
        messagesReceived.increment();
        messageProcessingTime.record(processingTime);
        messageReceiveCounter.increment();
        
        log.trace("Recorded message reception: topic={}, size={}, duration={}", 
                 topic, messageSize, processingTime);
    }

    /**
     * Record a security operation.
     * 
     * @param operation the type of security operation
     * @param mechanism the security mechanism used
     */
    public void recordSecurityOperation(String operation, String mechanism) {
        securityOperations.increment();
        securityOpCounter.increment();
        
        log.trace("Recorded security operation: operation={}, mechanism={}", operation, mechanism);
    }

    /**
     * Record an error event.
     * 
     * @param errorType the type of error
     * @param cause the cause of the error
     */
    public void recordError(String errorType, Throwable cause) {
        errors.increment();
        errorCounter.increment();
        
        log.trace("Recorded error: type={}, cause={}", errorType, cause.getClass().getSimpleName());
    }

    // Gauge value providers

    public double getActiveSocketCount() {
        return socketCreationCounter.sum() - socketCloseCounter.sum();
    }

    public double getEstimatedMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Very rough estimate: each socket uses about 64KB on average
        double estimatedZmqMemory = getActiveSocketCount() * 65536;
        
        return Math.min(estimatedZmqMemory, usedMemory * 0.1); // Cap at 10% of used memory
    }

    public double getCurrentThroughput() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastCollection = currentTime - lastMetricsCollection.get();
        
        if (timeSinceLastCollection <= 0) {
            return 0.0;
        }
        
        long totalMessages = messagePublishCounter.sum() + messageReceiveCounter.sum();
        return (totalMessages * 1000.0) / timeSinceLastCollection; // messages per second
    }

    public double getPoolUtilization() {
        int maxPoolSize = properties.getPool().getMaxSize();
        double activeConnections = getActiveSocketCount();
        
        if (maxPoolSize <= 0) {
            return 0.0;
        }
        
        return (activeConnections / maxPoolSize) * 100.0;
    }

    public double isCurveEnabled() {
        return properties.getSecurity().getMechanism() == ZeroMqProperties.Security.Mechanism.CURVE ? 1.0 : 0.0;
    }

    /**
     * Categorize message size for tagging.
     */
    private String categorizeSizeSize(int size) {
        if (size < 1024) return "small";
        if (size < 10240) return "medium";
        if (size < 102400) return "large";
        return "xlarge";
    }

    /**
     * Get current metrics summary.
     * 
     * @return metrics summary map
     */
    public Map<String, Object> getMetricsSummary() {
        return Map.of(
            "socketsCreated", socketCreationCounter.sum(),
            "socketsClosed", socketCloseCounter.sum(),
            "messagesPublished", messagePublishCounter.sum(),
            "messagesReceived", messageReceiveCounter.sum(),
            "securityOperations", securityOpCounter.sum(),
            "errors", errorCounter.sum(),
            "activeSockets", getActiveSocketCount(),
            "lastCollection", lastMetricsCollection.get()
        );
    }

    @Override
    public void close() {
        log.info("Shutting down ZeroMQ metrics collector");
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("ZeroMQ metrics collector shutdown completed");
    }
} 