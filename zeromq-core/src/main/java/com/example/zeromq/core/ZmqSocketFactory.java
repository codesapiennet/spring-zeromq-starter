package com.example.zeromq.core;

import com.example.zeromq.core.exception.ZeroMQException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.zeromq.ZMQ;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central factory for creating and configuring ZeroMQ sockets with integrated security.
 * 
 * <p>This factory provides a unified way to create ZeroMQ sockets with automatic
 * security configuration, monitoring, and proper resource management. It integrates
 * with the ZmqContextHolder for context management and ZmqSecurityConfig for
 * security policy enforcement.
 * 
 * <p>All sockets created by this factory are automatically instrumented with
 * Micrometer metrics for monitoring and observability.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
@ConditionalOnBean(ZmqContextHolder.class)
public class ZmqSocketFactory {

    private static final Logger log = LoggerFactory.getLogger(ZmqSocketFactory.class);
    
    private final ZmqContextHolder contextHolder;
    private final ZmqSecurityConfig securityConfig;
    private final MeterRegistry meterRegistry;
    
    // Metrics tracking
    private final AtomicLong socketCreationCounter = new AtomicLong(0);
    private final ConcurrentHashMap<Integer, Long> socketTypeCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> socketCreationTimers = new ConcurrentHashMap<>();
    
    // Micrometer metrics (optional)
    private final Counter socketCreatedCounter;
    private final Timer socketCreationTimer;

    /**
     * Create a new ZmqSocketFactory with required dependencies.
     * 
     * @param contextHolder the ZMQ context holder
     * @param securityConfig the security configuration
     */
    public ZmqSocketFactory(ZmqContextHolder contextHolder, ZmqSecurityConfig securityConfig) {
        this(contextHolder, securityConfig, null);
    }

    /**
     * Create a new ZmqSocketFactory with optional metrics registry.
     * 
     * @param contextHolder the ZMQ context holder
     * @param securityConfig the security configuration
     * @param meterRegistry optional Micrometer registry for metrics
     */
    public ZmqSocketFactory(ZmqContextHolder contextHolder, 
                           ZmqSecurityConfig securityConfig, 
                           MeterRegistry meterRegistry) {
        this.contextHolder = Objects.requireNonNull(contextHolder, "Context holder must not be null");
        this.securityConfig = Objects.requireNonNull(securityConfig, "Security config must not be null");
        this.meterRegistry = meterRegistry;
        
        // Initialize Micrometer metrics if registry is available
        if (meterRegistry != null) {
            this.socketCreatedCounter = Counter.builder("zeromq.sockets.created")
                .description("Total number of ZeroMQ sockets created")
                .register(meterRegistry);
            this.socketCreationTimer = Timer.builder("zeromq.socket.creation.duration")
                .description("Time taken to create ZeroMQ sockets")
                .register(meterRegistry);
        } else {
            this.socketCreatedCounter = null;
            this.socketCreationTimer = null;
        }
        
        log.info("ZmqSocketFactory initialized with security config and {} metrics", 
                meterRegistry != null ? "enabled" : "disabled");
    }

    /**
     * Create a socket with no security (development only).
     * 
     * @param socketType the ZMQ socket type (ZMQ.PUB, ZMQ.SUB, etc.)
     * @param correlationId correlation ID for logging and tracing
     * @return a configured ZMQ socket
     * @throws ZeroMQException if socket creation fails
     */
    public ZMQ.Socket createSocket(int socketType, String correlationId) {
        return createSocket(socketType, (ZmqSecurityConfig.PlainConfig) null, correlationId);
    }

    /**
     * Create a socket with PLAIN authentication.
     * 
     * @param socketType the ZMQ socket type
     * @param plainConfig PLAIN authentication configuration
     * @param correlationId correlation ID for logging and tracing
     * @return a configured ZMQ socket with PLAIN auth
     * @throws ZeroMQException if socket creation fails
     */
    public ZMQ.Socket createSocket(int socketType, ZmqSecurityConfig.PlainConfig plainConfig, String correlationId) {
        long startTime = System.nanoTime();
        long operationId = socketCreationCounter.incrementAndGet();
        
        try {
            // Create the socket
            ZMQ.Socket socket = createBasicSocket(socketType, correlationId, operationId);
            
            // Apply security configuration
            if (plainConfig != null) {
                securityConfig.applyPlainSecurity(socket, plainConfig, correlationId);
            } else {
                securityConfig.applyNoSecurity(socket, correlationId);
            }
            
            recordSocketCreation(socketType, startTime, "PLAIN");
            
            log.info("component=zeromq-factory event=socket-created " +
                    "correlationId={} operationId={} socketType={} security=PLAIN", 
                    correlationId, operationId, getSocketTypeName(socketType));
            
            return socket;
            
        } catch (Exception e) {
            log.error("component=zeromq-factory event=socket-creation-failed " +
                     "correlationId={} operationId={} socketType={} error={}", 
                     correlationId, operationId, getSocketTypeName(socketType), e.getMessage());
            throw new ZeroMQException("Failed to create socket with PLAIN auth: " + e.getMessage(), e);
        }
    }

    /**
     * Create a socket with CURVE encryption.
     * 
     * @param socketType the ZMQ socket type
     * @param curveConfig CURVE encryption configuration
     * @param correlationId correlation ID for logging and tracing
     * @return a configured ZMQ socket with CURVE encryption
     * @throws ZeroMQException if socket creation fails
     */
    public ZMQ.Socket createSocket(int socketType, ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        Objects.requireNonNull(curveConfig, "CURVE config must not be null");
        
        long startTime = System.nanoTime();
        long operationId = socketCreationCounter.incrementAndGet();
        
        try {
            // Create the socket
            ZMQ.Socket socket = createBasicSocket(socketType, correlationId, operationId);
            
            // Apply CURVE security
            securityConfig.applyCurveSecurity(socket, curveConfig, correlationId);
            
            recordSocketCreation(socketType, startTime, "CURVE");
            
            log.info("component=zeromq-factory event=socket-created " +
                    "correlationId={} operationId={} socketType={} security=CURVE mutualAuth={}", 
                    correlationId, operationId, getSocketTypeName(socketType), curveConfig.isMutualAuth());
            
            return socket;
            
        } catch (Exception e) {
            log.error("component=zeromq-factory event=socket-creation-failed " +
                     "correlationId={} operationId={} socketType={} error={}", 
                     correlationId, operationId, getSocketTypeName(socketType), e.getMessage());
            throw new ZeroMQException("Failed to create socket with CURVE encryption: " + e.getMessage(), e);
        }
    }

    /**
     * Create a basic socket without security configuration.
     */
    private ZMQ.Socket createBasicSocket(int socketType, String correlationId, long operationId) {
        if (!contextHolder.isAvailable()) {
            throw new ZeroMQException("ZeroMQ context is not available");
        }
        
        ZMQ.Socket socket = contextHolder.getContext().createSocket(socketType);
        if (socket == null) {
            throw new ZeroMQException("Failed to create socket - context returned null");
        }
        
        // Configure socket options for optimal performance and reliability
        configureSocketDefaults(socket, socketType);
        
        // Track socket type metrics
        socketTypeCounters.merge(socketType, 1L, Long::sum);
        
        log.debug("component=zeromq-factory event=basic-socket-created " +
                 "correlationId={} operationId={} socketType={}", 
                 correlationId, operationId, getSocketTypeName(socketType));
        
        return socket;
    }

    /**
     * Configure default socket options for reliability and performance.
     */
    private void configureSocketDefaults(ZMQ.Socket socket, int socketType) {
        try {
            // Set reasonable defaults for all socket types
            socket.setLinger(1000);  // 1 second linger on close
            socket.setReceiveTimeOut(30000);  // 30 second receive timeout
            socket.setSendTimeOut(30000);     // 30 second send timeout
            
            // Socket-specific optimizations
            switch (socketType) {
                case ZMQ.PUB:
                    socket.setSndHWM(10000);  // Back-pressure handling
                    break;
                case ZMQ.SUB:
                    socket.setRcvHWM(10000);  // Back-pressure handling
                    break;
                case ZMQ.PUSH:
                    socket.setSndHWM(10000);
                    break;
                case ZMQ.PULL:
                    socket.setRcvHWM(10000);
                    break;
                case ZMQ.REQ:
                    // REQ sockets use default timeout settings
                    break;
                case ZMQ.REP:
                    // REP sockets don't need special configuration
                    break;
                case ZMQ.DEALER:
                case ZMQ.ROUTER:
                    socket.setSndHWM(10000);
                    socket.setRcvHWM(10000);
                    break;
            }
            
            log.debug("Socket defaults configured for type: {}", getSocketTypeName(socketType));
            
        } catch (Exception e) {
            log.warn("Failed to configure socket defaults: {}", e.getMessage());
            // Continue anyway - socket will work with ZMQ defaults
        }
    }

    /**
     * Record socket creation metrics.
     */
    private void recordSocketCreation(int socketType, long startTime, String securityType) {
        long duration = System.nanoTime() - startTime;
        
        // Internal metrics
        String timerKey = getSocketTypeName(socketType) + "_" + securityType;
        socketCreationTimers.computeIfAbsent(timerKey, k -> 
            Timer.builder("socket.creation." + k.toLowerCase())
                 .description("Socket creation time for " + k)
                 .register(meterRegistry != null ? meterRegistry : io.micrometer.core.instrument.Metrics.globalRegistry)
        ).record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
        
        // Micrometer metrics
        if (socketCreatedCounter != null) {
            Counter.builder("zeromq.socket.created")
                .tags("socket.type", getSocketTypeName(socketType),
                      "security.type", securityType)
                .register(meterRegistry)
                .increment();
        }
        
        if (socketCreationTimer != null) {
            socketCreationTimer.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    // Convenience methods for common socket types

    /**
     * Create a PUB socket with the specified security configuration.
     * 
     * @param curveConfig the CURVE security configuration
     * @param correlationId correlation ID for logging and metrics
     * @return configured PUB socket
     */
    public ZMQ.Socket createPublisher(ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        return createSocket(ZMQ.PUB, curveConfig, correlationId);
    }

    /**
     * Create a SUB socket with the specified security configuration.
     * 
     * @param curveConfig the CURVE security configuration
     * @param correlationId correlation ID for logging and metrics
     * @return configured SUB socket
     */
    public ZMQ.Socket createSubscriber(ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        return createSocket(ZMQ.SUB, curveConfig, correlationId);
    }

    /**
     * Create a REQ socket with the specified security configuration.
     * 
     * @param curveConfig the CURVE security configuration
     * @param correlationId correlation ID for logging and metrics
     * @return configured REQ socket
     */
    public ZMQ.Socket createRequester(ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        return createSocket(ZMQ.REQ, curveConfig, correlationId);
    }

    /**
     * Create a REP socket with the specified security configuration.
     * 
     * @param curveConfig the CURVE security configuration
     * @param correlationId correlation ID for logging and metrics
     * @return configured REP socket
     */
    public ZMQ.Socket createReplier(ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        return createSocket(ZMQ.REP, curveConfig, correlationId);
    }

    /**
     * Create a PUSH socket with the specified security configuration.
     * 
     * @param curveConfig the CURVE security configuration
     * @param correlationId correlation ID for logging and metrics
     * @return configured PUSH socket
     */
    public ZMQ.Socket createPusher(ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        return createSocket(ZMQ.PUSH, curveConfig, correlationId);
    }

    /**
     * Create a PULL socket with the specified security configuration.
     * 
     * @param curveConfig the CURVE security configuration
     * @param correlationId correlation ID for logging and metrics
     * @return configured PULL socket
     */
    public ZMQ.Socket createPuller(ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        return createSocket(ZMQ.PULL, curveConfig, correlationId);
    }

    /**
     * Create a DEALER socket with the specified security configuration.
     * 
     * @param curveConfig the CURVE security configuration
     * @param correlationId correlation ID for logging and metrics
     * @return configured DEALER socket
     */
    public ZMQ.Socket createDealer(ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        return createSocket(ZMQ.DEALER, curveConfig, correlationId);
    }

    /**
     * Create a ROUTER socket with the specified security configuration.
     * 
     * @param curveConfig the CURVE security configuration
     * @param correlationId correlation ID for logging and metrics
     * @return configured ROUTER socket
     */
    public ZMQ.Socket createRouter(ZmqSecurityConfig.CurveConfig curveConfig, String correlationId) {
        return createSocket(ZMQ.ROUTER, curveConfig, correlationId);
    }

    /**
     * Get socket creation statistics for monitoring.
     * 
     * @return a map of socket type to creation count
     */
    public java.util.Map<String, Long> getSocketCreationStats() {
        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        socketTypeCounters.forEach((type, count) -> 
            stats.put(getSocketTypeName(type), count));
        return stats;
    }

    /**
     * Get the total number of sockets created by this factory.
     * 
     * @return the total socket creation count
     */
    public long getTotalSocketsCreated() {
        return socketCreationCounter.get();
    }

    /**
     * Reset all metrics (useful for testing).
     */
    public void resetMetrics() {
        socketCreationCounter.set(0);
        socketTypeCounters.clear();
        socketCreationTimers.clear();
        log.debug("component=zeromq-factory event=metrics-reset");
    }

    /**
     * Get a human-readable name for a socket type.
     */
    private String getSocketTypeName(int socketType) {
        return switch (socketType) {
            case ZMQ.PUB -> "PUB";
            case ZMQ.SUB -> "SUB";
            case ZMQ.REQ -> "REQ";
            case ZMQ.REP -> "REP";
            case ZMQ.PUSH -> "PUSH";
            case ZMQ.PULL -> "PULL";
            case ZMQ.DEALER -> "DEALER";
            case ZMQ.ROUTER -> "ROUTER";
            case ZMQ.PAIR -> "PAIR";
            case ZMQ.XPUB -> "XPUB";
            case ZMQ.XSUB -> "XSUB";
            case ZMQ.STREAM -> "STREAM";
            default -> "UNKNOWN_" + socketType;
        };
    }
} 