package com.example.zeromq.autoconfig;

import com.example.zeromq.core.ZmqContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuator.health.AbstractHealthIndicator;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator for ZeroMQ components.
 * 
 * <p>This health indicator monitors the status of ZeroMQ infrastructure including:
 * <ul>
 * <li>ZeroMQ context availability and health</li>
 * <li>Socket creation and binding capabilities</li>
 * <li>Message routing and delivery functionality</li>
 * <li>Security mechanism status</li>
 * <li>Performance metrics and thresholds</li>
 * </ul>
 * 
 * <p>The health check performs lightweight operations to verify ZeroMQ
 * functionality without impacting production traffic. It provides detailed
 * diagnostic information for troubleshooting.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class ZeroMqHealthIndicator extends AbstractHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqHealthIndicator.class);
    
    private final ZmqContextHolder contextHolder;
    private final ZeroMqProperties properties;
    
    // Health check state
    private Instant lastHealthCheck = Instant.now();
    private volatile boolean lastCheckSuccessful = true;
    private volatile String lastErrorMessage = null;
    private volatile long consecutiveFailures = 0;

    /**
     * Create a new ZeroMQ health indicator.
     * 
     * @param contextHolder the ZMQ context holder
     * @param properties the ZeroMQ properties
     */
    public ZeroMqHealthIndicator(ZmqContextHolder contextHolder, ZeroMqProperties properties) {
        super("ZeroMQ health check failed");
        this.contextHolder = contextHolder;
        this.properties = properties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        long startTime = System.nanoTime();
        
        try {
            log.debug("Starting ZeroMQ health check");
            
            // Basic context health check
            checkContextHealth(builder);
            
            // Socket creation test
            checkSocketCreation(builder);
            
            // Message routing test (if enabled)
            if (properties.getMonitoring().getHealthCheck().isEnabled()) {
                checkMessageRouting(builder);
            }
            
            // Security configuration check
            checkSecurityConfiguration(builder);
            
            // Performance metrics
            addPerformanceMetrics(builder, startTime);
            
            // Overall status
            builder.status(Status.UP)
                   .withDetail("message", "ZeroMQ is healthy")
                   .withDetail("lastCheck", Instant.now())
                   .withDetail("consecutiveSuccesses", resetFailureCounter());
            
            lastCheckSuccessful = true;
            lastErrorMessage = null;
            
            log.debug("ZeroMQ health check completed successfully");
            
        } catch (Exception e) {
            consecutiveFailures++;
            lastCheckSuccessful = false;
            lastErrorMessage = e.getMessage();
            
            log.warn("ZeroMQ health check failed (attempt {}): {}", consecutiveFailures, e.getMessage());
            
            builder.status(Status.DOWN)
                   .withDetail("error", e.getMessage())
                   .withDetail("consecutiveFailures", consecutiveFailures)
                   .withDetail("lastSuccessfulCheck", lastHealthCheck)
                   .withException(e);
            
            // Add diagnostic information
            addDiagnosticInfo(builder, e);
        } finally {
            lastHealthCheck = Instant.now();
        }
    }

    /**
     * Check the health of the ZeroMQ context.
     */
    private void checkContextHealth(Health.Builder builder) {
        if (!contextHolder.isAvailable()) {
            throw new RuntimeException("ZeroMQ context is not available");
        }
        
        ZContext context = contextHolder.getContext();
        if (context.isClosed()) {
            throw new RuntimeException("ZeroMQ context is closed");
        }
        
        // Get context information
        builder.withDetail("context.available", true)
               .withDetail("context.closed", false)
               .withDetail("context.sockets", context.getSockets().size())
               .withDetail("context.ioThreads", context.getIoThreads())
               .withDetail("context.maxSockets", context.getMaxSockets());
        
        log.debug("Context health check passed: {} sockets active", context.getSockets().size());
    }

    /**
     * Test socket creation capability.
     */
    private void checkSocketCreation(Health.Builder builder) {
        ZContext context = contextHolder.getContext();
        
        try {
            // Test creating different socket types
            ZMQ.Socket testPub = context.createSocket(ZMQ.PUB);
            ZMQ.Socket testSub = context.createSocket(ZMQ.SUB);
            
            if (testPub == null || testSub == null) {
                throw new RuntimeException("Failed to create test sockets");
            }
            
            // Clean up test sockets
            testPub.close();
            testSub.close();
            
            builder.withDetail("socketCreation.capable", true)
                   .withDetail("socketCreation.testedTypes", new String[]{"PUB", "SUB"});
            
            log.debug("Socket creation test passed");
            
        } catch (Exception e) {
            builder.withDetail("socketCreation.capable", false)
                   .withDetail("socketCreation.error", e.getMessage());
            throw new RuntimeException("Socket creation test failed: " + e.getMessage(), e);
        }
    }

    /**
     * Test message routing with a loopback connection.
     */
    private void checkMessageRouting(Health.Builder builder) {
        Duration timeout = properties.getMonitoring().getHealthCheck().getTimeout();
        
        CompletableFuture<Boolean> routingTest = CompletableFuture.supplyAsync(() -> {
            ZContext context = contextHolder.getContext();
            ZMQ.Socket sender = null;
            ZMQ.Socket receiver = null;
            
            try {
                // Create test sockets
                sender = context.createSocket(ZMQ.PUSH);
                receiver = context.createSocket(ZMQ.PULL);
                
                // Use inproc transport for testing
                String endpoint = "inproc://health-check-" + System.nanoTime();
                
                sender.bind(endpoint);
                receiver.connect(endpoint);
                
                // Give sockets time to connect
                Thread.sleep(10);
                
                // Send test message
                String testMessage = "health-check-" + System.currentTimeMillis();
                sender.send(testMessage);
                
                // Receive with timeout
                receiver.setReceiveTimeOut((int) timeout.toMillis());
                String received = receiver.recvStr();
                
                boolean success = testMessage.equals(received);
                
                log.debug("Message routing test {}: sent='{}', received='{}'", 
                         success ? "passed" : "failed", testMessage, received);
                
                return success;
                
            } catch (Exception e) {
                log.debug("Message routing test failed: {}", e.getMessage());
                return false;
            } finally {
                if (sender != null) sender.close();
                if (receiver != null) receiver.close();
            }
        });
        
        try {
            boolean routingWorking = routingTest.get(timeout.toMillis() + 1000, TimeUnit.MILLISECONDS);
            
            builder.withDetail("messageRouting.working", routingWorking)
                   .withDetail("messageRouting.testTimeout", timeout.toString());
            
            if (!routingWorking) {
                throw new RuntimeException("Message routing test failed - messages not delivered");
            }
            
        } catch (Exception e) {
            builder.withDetail("messageRouting.working", false)
                   .withDetail("messageRouting.error", e.getMessage());
            throw new RuntimeException("Message routing test failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check security configuration status.
     */
    private void checkSecurityConfiguration(Health.Builder builder) {
        ZeroMqProperties.Security security = properties.getSecurity();
        
        builder.withDetail("security.mechanism", security.getMechanism())
               .withDetail("security.profile", security.getProfile());
        
        switch (security.getMechanism()) {
            case CURVE -> {
                ZeroMqProperties.Security.Curve curveProps = security.getCurve();
                builder.withDetail("security.curve.mutualAuth", curveProps.isMutualAuth())
                       .withDetail("security.curve.serverKeyConfigured", 
                                 curveProps.getServerPublicKey() != null && !curveProps.getServerPublicKey().isEmpty())
                       .withDetail("security.curve.clientKeyConfigured", 
                                 curveProps.getClientPublicKey() != null && !curveProps.getClientPublicKey().isEmpty());
            }
            case PLAIN -> {
                ZeroMqProperties.Security.Plain plainProps = security.getPlain();
                builder.withDetail("security.plain.userConfigured", 
                                 plainProps.getUsername() != null && !plainProps.getUsername().isEmpty());
            }
            case NONE -> {
                builder.withDetail("security.warning", "No security mechanism configured");
                
                // Warn if this looks like production
                if (security.getProfile().toLowerCase().contains("prod")) {
                    builder.withDetail("security.productionWarning", 
                                     "Insecure configuration detected in production profile");
                }
            }
        }
        
        log.debug("Security configuration check completed: {}", security.getMechanism());
    }

    /**
     * Add performance metrics to the health report.
     */
    private void addPerformanceMetrics(Health.Builder builder, long startTime) {
        long duration = System.nanoTime() - startTime;
        
        ZContext context = contextHolder.getContext();
        
        Map<String, Object> performance = new HashMap<>();
        performance.put("healthCheckDurationMs", duration / 1_000_000.0);
        performance.put("contextIoThreads", context.getIoThreads());
        performance.put("activeSockets", context.getSockets().size());
        performance.put("maxSockets", context.getMaxSockets());
        
        // JVM memory info related to ZeroMQ
        Runtime runtime = Runtime.getRuntime();
        performance.put("jvm.totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        performance.put("jvm.freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        performance.put("jvm.usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        
        builder.withDetail("performance", performance);
        
        // Performance warnings
        if (duration > Duration.ofSeconds(1).toNanos()) {
            builder.withDetail("performance.warning", "Health check took longer than 1 second");
        }
        
        if (context.getSockets().size() > 100) {
            builder.withDetail("performance.warning", "High number of active sockets detected");
        }
    }

    /**
     * Add diagnostic information for troubleshooting.
     */
    private void addDiagnosticInfo(Health.Builder builder, Exception error) {
        Map<String, Object> diagnostic = new HashMap<>();
        
        diagnostic.put("errorType", error.getClass().getSimpleName());
        diagnostic.put("errorMessage", error.getMessage());
        diagnostic.put("lastSuccessfulCheck", lastHealthCheck);
        diagnostic.put("consecutiveFailures", consecutiveFailures);
        
        // ZeroMQ version info
        diagnostic.put("zeromq.version", ZMQ.getVersionString());
        diagnostic.put("zeromq.fullVersion", ZMQ.getFullVersion());
        
        // Configuration summary
        diagnostic.put("config.enabled", properties.isEnabled());
        diagnostic.put("config.securityMechanism", properties.getSecurity().getMechanism());
        diagnostic.put("config.monitoringEnabled", properties.getMonitoring().isEnabled());
        
        // System info
        diagnostic.put("system.availableProcessors", Runtime.getRuntime().availableProcessors());
        diagnostic.put("system.javaVersion", System.getProperty("java.version"));
        
        builder.withDetail("diagnostic", diagnostic);
    }

    /**
     * Reset the failure counter and return the previous count.
     */
    private long resetFailureCounter() {
        long previous = consecutiveFailures;
        consecutiveFailures = 0;
        return previous;
    }

    /**
     * Get the current health status summary.
     * 
     * @return health status summary
     */
    public Map<String, Object> getHealthSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("healthy", lastCheckSuccessful);
        summary.put("lastCheck", lastHealthCheck);
        summary.put("consecutiveFailures", consecutiveFailures);
        summary.put("lastError", lastErrorMessage);
        summary.put("contextAvailable", contextHolder.isAvailable());
        
        if (contextHolder.isAvailable()) {
            ZContext context = contextHolder.getContext();
            summary.put("activeSockets", context.getSockets().size());
            summary.put("contextClosed", context.isClosed());
        }
        
        return summary;
    }
} 