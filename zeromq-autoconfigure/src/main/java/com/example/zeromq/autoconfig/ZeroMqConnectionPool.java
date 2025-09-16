package com.example.zeromq.autoconfig;

import com.example.zeromq.core.ZmqSocketFactory;
import com.example.zeromq.core.exception.ZeroMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.zeromq.ZMQ;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connection pool for managing ZeroMQ socket connections efficiently.
 * 
 * <p>This pool provides connection reuse, lifecycle management, and resource
 * optimization for ZeroMQ sockets. It helps reduce the overhead of socket
 * creation/destruction in high-throughput applications while maintaining
 * connection health and preventing resource leaks.
 * 
 * <p>Features include:
 * <ul>
 * <li>Connection pooling with configurable min/max sizes</li>
 * <li>Idle connection cleanup with configurable timeouts</li>
 * <li>Connection validation and health checking</li>
 * <li>Metrics and monitoring integration</li>
 * <li>Graceful shutdown and resource cleanup</li>
 * </ul>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class ZeroMqConnectionPool implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqConnectionPool.class);
    
    private final ZmqSocketFactory socketFactory;
    private final ZeroMqProperties.Pool poolConfig;
    
    // Connection pools by endpoint and socket type
    private final ConcurrentHashMap<String, BlockingQueue<PooledConnection>> connectionPools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();
    
    // Pool management
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicLong connectionIdGenerator = new AtomicLong(1);
    private volatile boolean shutdownInitiated = false;
    
    // Statistics
    private final AtomicLong connectionsCreated = new AtomicLong(0);
    private final AtomicLong connectionsDestroyed = new AtomicLong(0);
    private final AtomicLong connectionsReused = new AtomicLong(0);
    private final AtomicLong connectionTimeouts = new AtomicLong(0);

    /**
     * Create a new connection pool.
     * 
     * @param socketFactory the socket factory for creating connections
     * @param poolConfig the pool configuration
     */
    public ZeroMqConnectionPool(ZmqSocketFactory socketFactory, ZeroMqProperties.Pool poolConfig) {
        this.socketFactory = Objects.requireNonNull(socketFactory, "Socket factory must not be null");
        this.poolConfig = Objects.requireNonNull(poolConfig, "Pool config must not be null");
        
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "zeromq-pool-cleanup");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (poolConfig.isEnabled()) {
            startCleanupTask();
            log.info("ZeroMQ connection pool initialized: min={}, max={}, maxIdleTime={}", 
                    poolConfig.getMinSize(), poolConfig.getMaxSize(), poolConfig.getMaxIdleTime());
        } else {
            log.info("ZeroMQ connection pool disabled");
        }
    }

    /**
     * Get a connection from the pool or create a new one.
     * 
     * @param endpoint the endpoint to connect to
     * @param socketType the ZMQ socket type
     * @param correlationId correlation ID for logging
     * @return a pooled connection
     * @throws ZeroMQException if connection creation fails
     */
    public PooledConnection getConnection(String endpoint, int socketType, String correlationId) {
        if (!poolConfig.isEnabled()) {
            // Pool disabled, create direct connection
            ZMQ.Socket socket = socketFactory.createSocket(socketType, correlationId);
            return new PooledConnection(socket, endpoint, socketType, correlationId, false);
        }
        
        String poolKey = createPoolKey(endpoint, socketType);
        
        // Try to get from pool first
        BlockingQueue<PooledConnection> pool = connectionPools.get(poolKey);
        if (pool != null) {
            PooledConnection connection = pool.poll();
            if (connection != null && connection.isValid()) {
                connection.markReused();
                connectionsReused.incrementAndGet();
                
                log.trace("Reused pooled connection: endpoint={}, type={}, connectionId={}", 
                         endpoint, getSocketTypeName(socketType), connection.getConnectionId());
                
                return connection;
            } else if (connection != null) {
                // Connection was invalid, destroy it
                connection.destroy();
                decrementConnectionCount(poolKey);
            }
        }
        
        // Create new connection
        return createNewConnection(endpoint, socketType, correlationId, poolKey);
    }

    /**
     * Return a connection to the pool or destroy it if pool is full.
     * 
     * @param connection the connection to return
     */
    public void returnConnection(PooledConnection connection) {
        if (!poolConfig.isEnabled() || !connection.isPooled()) {
            connection.destroy();
            return;
        }
        
        String poolKey = connection.getPoolKey();
        
        if (shutdownInitiated || !connection.isValid()) {
            connection.destroy();
            decrementConnectionCount(poolKey);
            return;
        }
        
        BlockingQueue<PooledConnection> pool = connectionPools.computeIfAbsent(
            poolKey, k -> new LinkedBlockingQueue<>());
        
        if (!pool.offer(connection)) {
            // Pool is full, destroy the connection
            connection.destroy();
            decrementConnectionCount(poolKey);
            
            log.trace("Pool full, destroyed connection: poolKey={}, connectionId={}", 
                     poolKey, connection.getConnectionId());
        } else {
            connection.markReturned();
            
            log.trace("Returned connection to pool: poolKey={}, connectionId={}, poolSize={}", 
                     poolKey, connection.getConnectionId(), pool.size());
        }
    }

    /**
     * Create a new connection and manage pool size limits.
     */
    private PooledConnection createNewConnection(String endpoint, int socketType, 
                                               String correlationId, String poolKey) {
        // Check max pool size
        AtomicInteger count = connectionCounts.computeIfAbsent(poolKey, k -> new AtomicInteger(0));
        
        if (count.get() >= poolConfig.getMaxSize()) {
            // Wait for a connection to become available
            BlockingQueue<PooledConnection> pool = connectionPools.get(poolKey);
            if (pool != null) {
                try {
                    PooledConnection connection = pool.poll(
                        poolConfig.getValidationTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    
                    if (connection != null && connection.isValid()) {
                        connection.markReused();
                        connectionsReused.incrementAndGet();
                        return connection;
                    } else if (connection != null) {
                        connection.destroy();
                        decrementConnectionCount(poolKey);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ZeroMQException("Interrupted while waiting for connection", e);
                }
            }
            
            connectionTimeouts.incrementAndGet();
            throw new ZeroMQException("Pool exhausted and timeout waiting for connection: " + poolKey);
        }
        
        // Create new connection
        ZMQ.Socket socket = socketFactory.createSocket(socketType, correlationId);
        count.incrementAndGet();
        connectionsCreated.incrementAndGet();
        
        PooledConnection connection = new PooledConnection(socket, endpoint, socketType, 
                                                          correlationId, true);
        connection.setPoolKey(poolKey);
        
        log.debug("Created new pooled connection: endpoint={}, type={}, connectionId={}, poolSize={}", 
                 endpoint, getSocketTypeName(socketType), connection.getConnectionId(), count.get());
        
        return connection;
    }

    /**
     * Start the cleanup task for idle connections.
     */
    private void startCleanupTask() {
        Duration idleTimeout = poolConfig.getMaxIdleTime();
        long cleanupInterval = Math.max(idleTimeout.toMillis() / 2, 30000); // At least 30 seconds
        
        cleanupExecutor.scheduleWithFixedDelay(this::cleanupIdleConnections, 
                cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS);
        
        log.debug("Started connection cleanup task with interval: {}ms", cleanupInterval);
    }

    /**
     * Clean up idle connections that have exceeded the maximum idle time.
     */
    private void cleanupIdleConnections() {
        if (shutdownInitiated) {
            return;
        }
        
        try {
            int totalCleaned = 0;
            Instant cutoffTime = Instant.now().minus(poolConfig.getMaxIdleTime());
            
            for (Map.Entry<String, BlockingQueue<PooledConnection>> entry : connectionPools.entrySet()) {
                String poolKey = entry.getKey();
                BlockingQueue<PooledConnection> pool = entry.getValue();
                
                int cleaned = cleanupPoolIdleConnections(poolKey, pool, cutoffTime);
                totalCleaned += cleaned;
            }
            
            if (totalCleaned > 0) {
                log.debug("Cleaned up {} idle connections", totalCleaned);
            }
            
        } catch (Exception e) {
            log.warn("Error during connection cleanup: {}", e.getMessage());
        }
    }

    /**
     * Clean up idle connections in a specific pool.
     */
    private int cleanupPoolIdleConnections(String poolKey, BlockingQueue<PooledConnection> pool, 
                                         Instant cutoffTime) {
        int cleaned = 0;
        int minSize = poolConfig.getMinSize();
        
        // Keep minimum number of connections
        while (pool.size() > minSize) {
            PooledConnection connection = pool.peek();
            
            if (connection == null) {
                break;
            }
            
            if (connection.getLastUsed().isBefore(cutoffTime) || !connection.isValid()) {
                pool.remove(connection);
                connection.destroy();
                decrementConnectionCount(poolKey);
                cleaned++;
                
                log.trace("Cleaned up idle connection: poolKey={}, connectionId={}, lastUsed={}", 
                         poolKey, connection.getConnectionId(), connection.getLastUsed());
            } else {
                break; // Connections are roughly ordered by last used time
            }
        }
        
        return cleaned;
    }

    /**
     * Create a unique key for the connection pool.
     */
    private String createPoolKey(String endpoint, int socketType) {
        return endpoint + ":" + socketType;
    }

    /**
     * Get human-readable socket type name.
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
            default -> "UNKNOWN_" + socketType;
        };
    }

    /**
     * Decrement the connection count for a pool.
     */
    private void decrementConnectionCount(String poolKey) {
        AtomicInteger count = connectionCounts.get(poolKey);
        if (count != null) {
            count.decrementAndGet();
        }
        connectionsDestroyed.incrementAndGet();
    }

    /**
     * Get pool statistics.
     * 
     * @return pool statistics map
     */
    public Map<String, Object> getPoolStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Overall statistics
        stats.put("connectionsCreated", connectionsCreated.get());
        stats.put("connectionsDestroyed", connectionsDestroyed.get());
        stats.put("connectionsReused", connectionsReused.get());
        stats.put("connectionTimeouts", connectionTimeouts.get());
        stats.put("totalActivePools", connectionPools.size());
        
        // Per-pool statistics
        Map<String, Map<String, Object>> poolStats = new HashMap<>();
        connectionPools.forEach((poolKey, pool) -> {
            Map<String, Object> poolData = new HashMap<>();
            poolData.put("poolSize", pool.size());
            poolData.put("totalConnections", connectionCounts.getOrDefault(poolKey, new AtomicInteger(0)).get());
            poolStats.put(poolKey, poolData);
        });
        stats.put("pools", poolStats);
        
        // Configuration
        stats.put("configuration", Map.of(
            "enabled", poolConfig.isEnabled(),
            "minSize", poolConfig.getMinSize(),
            "maxSize", poolConfig.getMaxSize(),
            "maxIdleTime", poolConfig.getMaxIdleTime().toString()
        ));
        
        return stats;
    }

    @Override
    public void destroy() throws Exception {
        log.info("Shutting down ZeroMQ connection pool");
        shutdownInitiated = true;
        
        // Shutdown cleanup executor
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Close all pooled connections
        int closedConnections = 0;
        for (BlockingQueue<PooledConnection> pool : connectionPools.values()) {
            while (!pool.isEmpty()) {
                PooledConnection connection = pool.poll();
                if (connection != null) {
                    connection.destroy();
                    closedConnections++;
                }
            }
        }
        
        connectionPools.clear();
        connectionCounts.clear();
        
        log.info("ZeroMQ connection pool shutdown completed. Closed {} connections", closedConnections);
    }

    /**
     * Represents a pooled ZeroMQ socket connection.
     */
    public static class PooledConnection {
        private final ZMQ.Socket socket;
        private final String endpoint;
        private final int socketType;
        private final String correlationId;
        private final boolean pooled;
        private final long connectionId;
        private final Instant createdTime;
        
        private volatile Instant lastUsed;
        private volatile String poolKey;
        private volatile boolean destroyed = false;

        public PooledConnection(ZMQ.Socket socket, String endpoint, int socketType, 
                              String correlationId, boolean pooled) {
            this.socket = socket;
            this.endpoint = endpoint;
            this.socketType = socketType;
            this.correlationId = correlationId;
            this.pooled = pooled;
            this.connectionId = System.nanoTime(); // Simple ID generation
            this.createdTime = Instant.now();
            this.lastUsed = Instant.now();
        }

        public ZMQ.Socket getSocket() { return socket; }
        public String getEndpoint() { return endpoint; }
        public int getSocketType() { return socketType; }
        public String getCorrelationId() { return correlationId; }
        public boolean isPooled() { return pooled; }
        public long getConnectionId() { return connectionId; }
        public Instant getCreatedTime() { return createdTime; }
        public Instant getLastUsed() { return lastUsed; }
        public String getPoolKey() { return poolKey; }

        public void setPoolKey(String poolKey) { this.poolKey = poolKey; }
        
        public void markReused() { this.lastUsed = Instant.now(); }
        public void markReturned() { this.lastUsed = Instant.now(); }

        /**
         * Check if this connection is still valid.
         * 
         * @return true if the connection is valid
         */
        public boolean isValid() {
            return !destroyed && socket != null;
        }

        /**
         * Destroy this connection and clean up resources.
         */
        public void destroy() {
            if (!destroyed && socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    // Log but don't throw - we're cleaning up
                }
                destroyed = true;
            }
        }

        @Override
        public String toString() {
            return String.format("PooledConnection[id=%d, endpoint=%s, type=%d, pooled=%s, valid=%s]",
                    connectionId, endpoint, socketType, pooled, isValid());
        }
    }
} 