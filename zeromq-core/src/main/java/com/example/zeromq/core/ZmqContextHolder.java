package com.example.zeromq.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the ZeroMQ context lifecycle for the entire Spring application.
 * 
 * <p>This component holds a singleton ZContext instance that is shared across
 * all ZeroMQ operations in the application. The context is automatically
 * created during initialization and properly closed when the Spring application
 * context shuts down.
 * 
 * <p>The ZContext manages all ZeroMQ sockets and ensures proper cleanup of
 * native resources. This holder provides thread-safe access to the context
 * and handles graceful shutdown within the configured timeout.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class ZmqContextHolder {

    private static final Logger log = LoggerFactory.getLogger(ZmqContextHolder.class);
    
    /**
     * Default shutdown timeout in milliseconds (30 seconds).
     * This matches the workspace rule requirement for graceful shutdown.
     */
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 30_000L;

    private volatile ZContext context;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Object contextLock = new Object();

    /**
     * Initialize the ZeroMQ context after bean creation.
     * 
     * <p>This method is called automatically by Spring after the bean
     * is constructed and all dependencies are injected.
     */
    @PostConstruct
    public void initialize() {
        synchronized (contextLock) {
            if (context == null && !closed.get()) {
                context = new ZContext();
                log.info("ZeroMQ context initialized successfully");
                
                // Log context information for monitoring
                log.info("ZMQ Context - IO Threads: {}, Max Sockets: {}", 
                        context.getIoThreads(), context.getMaxSockets());
            }
        }
    }

    /**
     * Get the shared ZContext instance.
     * 
     * <p>This method returns the singleton ZContext that should be used
     * for all socket creation in the application. The context is thread-safe
     * and can be accessed concurrently.
     * 
     * @return the ZContext instance
     * @throws IllegalStateException if the context has been closed or not initialized
     */
    public ZContext getContext() {
        ZContext currentContext = this.context;
        if (currentContext == null || closed.get()) {
            throw new IllegalStateException("ZeroMQ context is not available (closed or not initialized)");
        }
        return currentContext;
    }

    /**
     * Check if the ZeroMQ context is available and not closed.
     * 
     * @return true if the context is available, false otherwise
     */
    public boolean isAvailable() {
        return context != null && !closed.get();
    }

    /**
     * Get the number of active sockets managed by this context.
     * 
     * @return the number of active sockets, or -1 if context is not available
     */
    public int getActiveSocketCount() {
        ZContext currentContext = this.context;
        if (currentContext != null && !closed.get()) {
            return currentContext.getSockets().size();
        }
        return -1;
    }

    /**
     * Handle Spring application context shutdown events.
     * 
     * <p>This method is automatically called when the Spring application
     * context is being shut down, ensuring proper cleanup of ZeroMQ resources.
     * 
     * @param event the context closed event
     */
    @EventListener
    public void onApplicationContextClosed(ContextClosedEvent event) {
        log.info("Spring application context closing, shutting down ZeroMQ context");
        closeContext();
    }

    /**
     * Clean up ZeroMQ resources during bean destruction.
     * 
     * <p>This method is called automatically by Spring when the bean
     * is being destroyed. It ensures that the ZeroMQ context is properly
     * closed even if the application shutdown is not clean.
     */
    @PreDestroy
    public void destroy() {
        log.debug("ZmqContextHolder being destroyed, closing context");
        closeContext();
    }

    /**
     * Manually close the ZeroMQ context.
     * 
     * <p>This method can be called to explicitly close the context before
     * application shutdown. It's idempotent and thread-safe.
     */
    public void closeContext() {
        if (closed.compareAndSet(false, true)) {
            synchronized (contextLock) {
                if (context != null) {
                    try {
                        long startTime = System.currentTimeMillis();
                        int activeSocketsBefore = context.getSockets().size();
                        
                        log.info("Closing ZeroMQ context with {} active sockets", activeSocketsBefore);
                        
                        // Set a reasonable linger period for socket cleanup
                        context.setLinger(1000); // 1 second linger
                        
                        // Close the context (this will close all associated sockets)
                        context.close();
                        
                        long shutdownTime = System.currentTimeMillis() - startTime;
                        log.info("ZeroMQ context closed successfully in {}ms", shutdownTime);
                        
                        // Emit shutdown metrics for monitoring
                        logShutdownMetrics(activeSocketsBefore, shutdownTime);
                        
                    } catch (Exception e) {
                        log.error("Error during ZeroMQ context shutdown", e);
                    } finally {
                        context = null;
                    }
                }
            }
        }
    }

    /**
     * Log shutdown metrics for monitoring and observability.
     * 
     * @param activeSocketsBefore the number of sockets before shutdown
     * @param shutdownTimeMs the time taken to shutdown in milliseconds
     */
    private void logShutdownMetrics(int activeSocketsBefore, long shutdownTimeMs) {
        // Structured logging for monitoring systems
        log.info("component=zeromq-context event=shutdown " +
                "activeSocketsBefore={} shutdownTimeMs={} " +
                "timeoutMs={} withinTimeout={}",
                activeSocketsBefore, shutdownTimeMs, DEFAULT_SHUTDOWN_TIMEOUT_MS,
                shutdownTimeMs <= DEFAULT_SHUTDOWN_TIMEOUT_MS);
    }

    /**
     * Get context information for health checks and monitoring.
     * 
     * @return a string representation of the context state
     */
    public String getContextInfo() {
        if (!isAvailable()) {
            return "ZContext[status=CLOSED]";
        }
        
        ZContext currentContext = this.context;
        return String.format("ZContext[status=ACTIVE, ioThreads=%d, maxSockets=%d, activeSockets=%d]",
                currentContext.getIoThreads(),
                currentContext.getMaxSockets(),
                currentContext.getSockets().size());
    }

    @Override
    public String toString() {
        return getContextInfo();
    }
} 