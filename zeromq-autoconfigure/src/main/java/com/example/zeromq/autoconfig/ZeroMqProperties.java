package com.example.zeromq.autoconfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for ZeroMQ messaging framework.
 * 
 * <p>This class provides comprehensive configuration options for all aspects
 * of the ZeroMQ messaging system including security, performance tuning,
 * vector processing, and monitoring.
 * 
 * <p>Properties are validated automatically and provide sensible defaults
 * for production deployment.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "spring.zeromq")
@Validated
public class ZeroMqProperties {

    /**
     * Whether ZeroMQ integration is enabled.
     */
    private boolean enabled = true;

    /**
     * Default endpoints for health checks and monitoring.
     */
    private List<String> endpoints = new ArrayList<>();

    /**
     * Security configuration.
     */
    @Valid
    @NotNull
    private Security security = new Security();

    /**
     * Socket configuration options.
     */
    @Valid
    @NotNull
    private Socket socket = new Socket();

    /**
     * Vector processing configuration.
     */
    @Valid
    @NotNull
    private Vector vector = new Vector();

    /**
     * Performance and monitoring configuration.
     */
    @Valid
    @NotNull
    private Monitoring monitoring = new Monitoring();

    /**
     * Connection pool configuration.
     */
    @Valid
    @NotNull
    private Pool pool = new Pool();

    /**
     * Compute-related configuration.
     */
    @Valid
    @NotNull
    private Compute compute = new Compute();

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getEndpoints() { return endpoints; }
    public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Socket getSocket() { return socket; }
    public void setSocket(Socket socket) { this.socket = socket; }

    public Vector getVector() { return vector; }
    public void setVector(Vector vector) { this.vector = vector; }

    public Monitoring getMonitoring() { return monitoring; }
    public void setMonitoring(Monitoring monitoring) { this.monitoring = monitoring; }

    public Pool getPool() { return pool; }
    public void setPool(Pool pool) { this.pool = pool; }

    public Compute getCompute() { return compute; }
    public void setCompute(Compute compute) { this.compute = compute; }

    /**
     * Security configuration properties.
     */
    public static class Security {
        /**
         * Security mechanism to use.
         */
        @NotNull
        private Mechanism mechanism = Mechanism.NONE;

        /**
         * PLAIN authentication configuration.
         */
        @Valid
        private Plain plain = new Plain();

        /**
         * CURVE encryption configuration.
         */
        @Valid
        private Curve curve = new Curve();

        /**
         * Security profile (dev, test, prod).
         */
        private String profile = "prod";

        public enum Mechanism { NONE, PLAIN, CURVE }

        // Getters and setters
        public Mechanism getMechanism() { return mechanism; }
        public void setMechanism(Mechanism mechanism) { this.mechanism = mechanism; }

        public Plain getPlain() { return plain; }
        public void setPlain(Plain plain) { this.plain = plain; }

        public Curve getCurve() { return curve; }
        public void setCurve(Curve curve) { this.curve = curve; }

        public String getProfile() { return profile; }
        public void setProfile(String profile) { this.profile = profile; }

        /**
         * PLAIN authentication properties.
         */
        public static class Plain {
            @NotBlank
            private String username = "";
            @NotBlank
            private String password = "";

            public String getUsername() { return username; }
            public void setUsername(String username) { this.username = username; }

            public String getPassword() { return password; }
            public void setPassword(String password) { this.password = password; }
        }

        /**
         * CURVE encryption properties.
         */
        public static class Curve {
            @NotBlank
            private String serverPublicKey = "";
            @NotBlank
            private String serverSecretKey = "";
            private String clientPublicKey = "";
            private String clientSecretKey = "";
            private boolean mutualAuth = false;

            // List of allowed client public keys (Z85 encoded) used by the ZAP authenticator
            private java.util.List<String> allowedClientPublicKeys = new java.util.ArrayList<>();

            public String getServerPublicKey() { return serverPublicKey; }
            public void setServerPublicKey(String serverPublicKey) { this.serverPublicKey = serverPublicKey; }

            public String getServerSecretKey() { return serverSecretKey; }
            public void setServerSecretKey(String serverSecretKey) { this.serverSecretKey = serverSecretKey; }

            public String getClientPublicKey() { return clientPublicKey; }
            public void setClientPublicKey(String clientPublicKey) { this.clientPublicKey = clientPublicKey; }

            public String getClientSecretKey() { return clientSecretKey; }
            public void setClientSecretKey(String clientSecretKey) { this.clientSecretKey = clientSecretKey; }

            public boolean isMutualAuth() { return mutualAuth; }
            public void setMutualAuth(boolean mutualAuth) { this.mutualAuth = mutualAuth; }

            public java.util.List<String> getAllowedClientPublicKeys() { return allowedClientPublicKeys; }
            public void setAllowedClientPublicKeys(java.util.List<String> allowedClientPublicKeys) { this.allowedClientPublicKeys = allowedClientPublicKeys; }
        }
    }

    /**
     * Socket configuration properties.
     */
    public static class Socket {
        /**
         * Default socket linger time.
         */
        private Duration linger = Duration.ofSeconds(1);

        /**
         * Default receive timeout.
         */
        private Duration receiveTimeout = Duration.ofSeconds(30);

        /**
         * Default send timeout.
         */
        private Duration sendTimeout = Duration.ofSeconds(30);

        /**
         * High water mark for send operations.
         */
        @Min(1)
        @Max(1000000)
        private int sendHighWaterMark = 10000;

        /**
         * High water mark for receive operations.
         */
        @Min(1)
        @Max(1000000)
        private int receiveHighWaterMark = 10000;

        /**
         * Socket-specific configuration overrides.
         */
        private Map<String, String> options = new HashMap<>();

        // Getters and setters
        public Duration getLinger() { return linger; }
        public void setLinger(Duration linger) { this.linger = linger; }

        public Duration getReceiveTimeout() { return receiveTimeout; }
        public void setReceiveTimeout(Duration receiveTimeout) { this.receiveTimeout = receiveTimeout; }

        public Duration getSendTimeout() { return sendTimeout; }
        public void setSendTimeout(Duration sendTimeout) { this.sendTimeout = sendTimeout; }

        public int getSendHighWaterMark() { return sendHighWaterMark; }
        public void setSendHighWaterMark(int sendHighWaterMark) { this.sendHighWaterMark = sendHighWaterMark; }

        public int getReceiveHighWaterMark() { return receiveHighWaterMark; }
        public void setReceiveHighWaterMark(int receiveHighWaterMark) { this.receiveHighWaterMark = receiveHighWaterMark; }

        public Map<String, String> getOptions() { return options; }
        public void setOptions(Map<String, String> options) { this.options = options; }
    }

    /**
     * Vector processing configuration properties.
     */
    public static class Vector {
        /**
         * Whether vector optimizations are enabled.
         */
        private boolean enabled = true;

        /**
         * Compression configuration for large vectors.
         */
        @Valid
        private Compression compression = new Compression();

        /**
         * Batch processing configuration.
         */
        @Valid
        private Batch batch = new Batch();

        /**
         * Memory management configuration.
         */
        @Valid
        private Memory memory = new Memory();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Compression getCompression() { return compression; }
        public void setCompression(Compression compression) { this.compression = compression; }

        public Batch getBatch() { return batch; }
        public void setBatch(Batch batch) { this.batch = batch; }

        public Memory getMemory() { return memory; }
        public void setMemory(Memory memory) { this.memory = memory; }

        /**
         * Vector compression settings.
         */
        public static class Compression {
            private boolean enabled = true;
            @Min(64)
            @Max(1048576) // 1MB
            private int threshold = 1024;
            @NotNull
            private Algorithm algorithm = Algorithm.GZIP;

            public enum Algorithm { GZIP, LZ4, SNAPPY }

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public int getThreshold() { return threshold; }
            public void setThreshold(int threshold) { this.threshold = threshold; }

            public Algorithm getAlgorithm() { return algorithm; }
            public void setAlgorithm(Algorithm algorithm) { this.algorithm = algorithm; }
        }

        /**
         * Batch processing settings.
         */
        public static class Batch {
            @Min(1)
            @Max(10000)
            private int maxSize = 100;

            private Duration timeout = Duration.ofSeconds(1);

            public int getMaxSize() { return maxSize; }
            public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

            public Duration getTimeout() { return timeout; }
            public void setTimeout(Duration timeout) { this.timeout = timeout; }
        }

        /**
         * Memory management settings.
         */
        public static class Memory {
            @Min(10)
            @Max(100000)
            private int poolSize = 1000;

            @Min(1)
            @Max(10000000)
            private int maxDimensions = 100000;

            public int getPoolSize() { return poolSize; }
            public void setPoolSize(int poolSize) { this.poolSize = poolSize; }

            public int getMaxDimensions() { return maxDimensions; }
            public void setMaxDimensions(int maxDimensions) { this.maxDimensions = maxDimensions; }
        }
    }

    /**
     * Monitoring and metrics configuration.
     */
    public static class Monitoring {
        /**
         * Whether monitoring is enabled.
         */
        private boolean enabled = true;

        /**
         * Metrics collection interval.
         */
        private Duration metricsInterval = Duration.ofSeconds(5);

        /**
         * Whether to monitor memory usage.
         */
        private boolean memoryMonitoring = true;

        /**
         * Whether to export metrics to external systems.
         */
        private boolean exportMetrics = true;

        /**
         * Health check configuration.
         */
        @Valid
        private HealthCheck healthCheck = new HealthCheck();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Duration getMetricsInterval() { return metricsInterval; }
        public void setMetricsInterval(Duration metricsInterval) { this.metricsInterval = metricsInterval; }

        public boolean isMemoryMonitoring() { return memoryMonitoring; }
        public void setMemoryMonitoring(boolean memoryMonitoring) { this.memoryMonitoring = memoryMonitoring; }

        public boolean isExportMetrics() { return exportMetrics; }
        public void setExportMetrics(boolean exportMetrics) { this.exportMetrics = exportMetrics; }

        public HealthCheck getHealthCheck() { return healthCheck; }
        public void setHealthCheck(HealthCheck healthCheck) { this.healthCheck = healthCheck; }

        /**
         * Health check settings.
         */
        public static class HealthCheck {
            private boolean enabled = true;
            private Duration timeout = Duration.ofSeconds(5);
            private Duration interval = Duration.ofSeconds(30);

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public Duration getTimeout() { return timeout; }
            public void setTimeout(Duration timeout) { this.timeout = timeout; }

            public Duration getInterval() { return interval; }
            public void setInterval(Duration interval) { this.interval = interval; }
        }
    }

    /**
     * Connection pool configuration.
     */
    public static class Pool {
        /**
         * Whether connection pooling is enabled.
         */
        private boolean enabled = true;

        /**
         * Maximum number of connections in the pool.
         */
        @Min(1)
        @Max(1000)
        private int maxSize = 50;

        /**
         * Minimum number of connections to maintain.
         */
        @Min(0)
        @Max(100)
        private int minSize = 5;

        /**
         * Maximum idle time before closing connections.
         */
        private Duration maxIdleTime = Duration.ofMinutes(10);

        /**
         * Connection validation timeout.
         */
        private Duration validationTimeout = Duration.ofSeconds(2);

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

        public int getMinSize() { return minSize; }
        public void setMinSize(int minSize) { this.minSize = minSize; }

        public Duration getMaxIdleTime() { return maxIdleTime; }
        public void setMaxIdleTime(Duration maxIdleTime) { this.maxIdleTime = maxIdleTime; }

        public Duration getValidationTimeout() { return validationTimeout; }
        public void setValidationTimeout(Duration validationTimeout) { this.validationTimeout = validationTimeout; }
    }

    /**
     * Compute-related configuration.
     */
    public static class Compute {
        @Valid
        @NotNull
        private Multithreaded multithreaded = new Multithreaded();

        public Multithreaded getMultithreaded() { return multithreaded; }
        public void setMultithreaded(Multithreaded multithreaded) { this.multithreaded = multithreaded; }

        public static class Multithreaded {
            /**
             * When true, use virtual threads for multithreaded executor. Default=false.
             */
            private boolean useVirtualThreads = false;

            public boolean isUseVirtualThreads() { return useVirtualThreads; }
            public void setUseVirtualThreads(boolean useVirtualThreads) { this.useVirtualThreads = useVirtualThreads; }
        }
    }
} 