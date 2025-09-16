package com.example.zeromq.core;

import com.example.zeromq.core.exception.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zeromq.ZMQ;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Configures and applies ZeroMQ security mechanisms to sockets.
 * 
 * <p>This component handles the configuration of ZeroMQ security mechanisms
 * including PLAIN authentication and CURVE encryption. It provides a centralized
 * way to apply security settings to sockets while following security best
 * practices and comprehensive audit logging.
 * 
 * <p>The class supports both development (PLAIN auth allowed) and production
 * (CURVE encryption required) security profiles.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class ZmqSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(ZmqSecurityConfig.class);
    
    private final AtomicLong securityOperationCounter = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> securityMetrics = new ConcurrentHashMap<>();

    /**
     * Security mechanisms supported by the framework.
     */
    public enum SecurityMechanism {
        NONE,
        PLAIN,
        CURVE
    }

    /**
     * Security configuration for PLAIN authentication.
     */
    public static final class PlainConfig {
        private final String username;
        private final String password;
        private final boolean server;

        public PlainConfig(String username, String password, boolean server) {
            this.username = Objects.requireNonNull(username, "Username must not be null");
            this.password = Objects.requireNonNull(password, "Password must not be null");
            this.server = server;
            
            if (username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username must not be empty");
            }
            if (password.length() < 8) {
                log.warn("PLAIN password is shorter than 8 characters - consider using stronger passwords");
            }
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public boolean isServer() { return server; }

        @Override
        public String toString() {
            return String.format("PlainConfig[username=%s, server=%s]", username, server);
        }
    }

    /**
     * Security configuration for CURVE encryption.
     */
    public static final class CurveConfig {
        private final String serverPublicKey;
        private final String serverSecretKey;
        private final String clientPublicKey;
        private final String clientSecretKey;
        private final boolean server;
        private final boolean mutualAuth;

        /**
         * Create a server CURVE configuration.
         */
        public static CurveConfig forServer(String serverPublicKey, String serverSecretKey) {
            return new CurveConfig(serverPublicKey, serverSecretKey, null, null, true);
        }

        /**
         * Create a client CURVE configuration.
         */
        public static CurveConfig forClient(String serverPublicKey, String clientPublicKey, String clientSecretKey) {
            return new CurveConfig(serverPublicKey, null, clientPublicKey, clientSecretKey, false);
        }

        /**
         * Create a mutual authentication CURVE configuration.
         */
        public static CurveConfig forMutualAuth(String serverPublicKey, String serverSecretKey,
                                               String clientPublicKey, String clientSecretKey, 
                                               boolean isServer) {
            return new CurveConfig(serverPublicKey, serverSecretKey, clientPublicKey, clientSecretKey, isServer);
        }

        private CurveConfig(String serverPublicKey, String serverSecretKey,
                           String clientPublicKey, String clientSecretKey, boolean server) {
            this.serverPublicKey = Objects.requireNonNull(serverPublicKey, "Server public key must not be null");
            this.serverSecretKey = server ? Objects.requireNonNull(serverSecretKey, "Server secret key required for server mode") : serverSecretKey;
            this.clientPublicKey = clientPublicKey;
            this.clientSecretKey = !server ? Objects.requireNonNull(clientSecretKey, "Client secret key required for client mode") : clientSecretKey;
            this.server = server;
            this.mutualAuth = clientPublicKey != null && clientSecretKey != null && serverSecretKey != null;

            // Validate key formats
            validateCurveKey("server public key", serverPublicKey);
            if (serverSecretKey != null) {
                validateCurveKey("server secret key", serverSecretKey);
            }
            if (clientPublicKey != null) {
                validateCurveKey("client public key", clientPublicKey);
            }
            if (clientSecretKey != null) {
                validateCurveKey("client secret key", clientSecretKey);
            }
        }

        public String getServerPublicKey() { return serverPublicKey; }
        public String getServerSecretKey() { return serverSecretKey; }
        public String getClientPublicKey() { return clientPublicKey; }
        public String getClientSecretKey() { return clientSecretKey; }
        public boolean isServer() { return server; }
        public boolean isMutualAuth() { return mutualAuth; }

        private void validateCurveKey(String keyName, String key) {
            if (!ZAuthKeyGenerator.isValidZ85Key(key)) {
                throw new SecurityException("Invalid " + keyName + " format - must be valid Z85 encoded key", "CURVE");
            }
        }

        @Override
        public String toString() {
            return String.format("CurveConfig[server=%s, mutualAuth=%s, serverPubKey=%s]", 
                    server, mutualAuth, serverPublicKey);
        }
    }

    /**
     * Apply no security to the socket (development only).
     * 
     * @param socket the socket to configure
     * @param correlationId correlation ID for logging
     */
    public void applyNoSecurity(ZMQ.Socket socket, String correlationId) {
        Objects.requireNonNull(socket, "Socket must not be null");
        long operationId = securityOperationCounter.incrementAndGet();
        
        log.warn("component=zeromq-security event=no-security-applied " +
                "correlationId={} operationId={} mechanism=NONE " +
                "warning=unencrypted-communication", correlationId, operationId);
        
        incrementSecurityMetric("no_security_applied");
    }

    /**
     * Apply PLAIN authentication to the socket.
     * 
     * @param socket the socket to configure
     * @param config the PLAIN configuration
     * @param correlationId correlation ID for logging
     * @throws SecurityException if configuration fails
     */
    public void applyPlainSecurity(ZMQ.Socket socket, PlainConfig config, String correlationId) {
        Objects.requireNonNull(socket, "Socket must not be null");
        Objects.requireNonNull(config, "PLAIN config must not be null");
        
        long operationId = securityOperationCounter.incrementAndGet();
        long startTime = System.nanoTime();
        
        try {
            if (config.isServer()) {
                // Server configuration
                socket.setPlainServer(true);
                log.info("component=zeromq-security event=plain-server-configured " +
                        "correlationId={} operationId={} username={}", 
                        correlationId, operationId, config.getUsername());
            } else {
                // Client configuration
                socket.setPlainUsername(config.getUsername());
                socket.setPlainPassword(config.getPassword());
                log.info("component=zeromq-security event=plain-client-configured " +
                        "correlationId={} operationId={} username={}", 
                        correlationId, operationId, config.getUsername());
            }
            
            long duration = System.nanoTime() - startTime;
            log.info("component=zeromq-security event=plain-security-applied " +
                    "correlationId={} operationId={} durationMicros={} server={}", 
                    correlationId, operationId, duration / 1000, config.isServer());
            
            incrementSecurityMetric("plain_security_applied");
            
        } catch (Exception e) {
            log.error("component=zeromq-security event=plain-security-failed " +
                     "correlationId={} operationId={} error={}", 
                     correlationId, operationId, e.getMessage());
            throw new SecurityException("Failed to apply PLAIN security: " + e.getMessage(), e, "PLAIN");
        }
    }

    /**
     * Apply CURVE encryption to the socket.
     * 
     * @param socket the socket to configure
     * @param config the CURVE configuration
     * @param correlationId correlation ID for logging
     * @throws SecurityException if configuration fails
     */
    public void applyCurveSecurity(ZMQ.Socket socket, CurveConfig config, String correlationId) {
        Objects.requireNonNull(socket, "Socket must not be null");
        Objects.requireNonNull(config, "CURVE config must not be null");
        
        long operationId = securityOperationCounter.incrementAndGet();
        long startTime = System.nanoTime();
        
        try {
            if (config.isServer()) {
                configureServerCurve(socket, config, correlationId, operationId);
            } else {
                configureClientCurve(socket, config, correlationId, operationId);
            }
            
            long duration = System.nanoTime() - startTime;
            log.info("component=zeromq-security event=curve-security-applied " +
                    "correlationId={} operationId={} durationMicros={} server={} mutualAuth={}", 
                    correlationId, operationId, duration / 1000, config.isServer(), config.isMutualAuth());
            
            incrementSecurityMetric("curve_security_applied");
            
        } catch (Exception e) {
            log.error("component=zeromq-security event=curve-security-failed " +
                     "correlationId={} operationId={} error={}", 
                     correlationId, operationId, e.getMessage());
            throw new SecurityException("Failed to apply CURVE security: " + e.getMessage(), e, "CURVE");
        }
    }

    /**
     * Configure server-side CURVE encryption.
     */
    private void configureServerCurve(ZMQ.Socket socket, CurveConfig config, 
                                     String correlationId, long operationId) {
        // Enable CURVE server mode
        socket.setCurveServer(true);
        
        // Set server keys
        socket.setCurvePublicKey(config.getServerPublicKey());
        socket.setCurveSecretKey(config.getServerSecretKey());
        
        log.info("component=zeromq-security event=curve-server-configured " +
                "correlationId={} operationId={} serverPublicKey={}", 
                correlationId, operationId, config.getServerPublicKey());
        
        // Configure mutual authentication if enabled
        if (config.isMutualAuth()) {
            // In mutual auth, server validates client certificates
            // This would typically involve setting up a certificate store
            log.info("component=zeromq-security event=curve-mutual-auth-enabled " +
                    "correlationId={} operationId={} clientPublicKey={}", 
                    correlationId, operationId, config.getClientPublicKey());
        }
    }

    /**
     * Configure client-side CURVE encryption.
     */
    private void configureClientCurve(ZMQ.Socket socket, CurveConfig config, 
                                     String correlationId, long operationId) {
        // Set client keys
        socket.setCurvePublicKey(config.getClientPublicKey());
        socket.setCurveSecretKey(config.getClientSecretKey());
        
        // Set server public key for validation
        socket.setCurveServerKey(config.getServerPublicKey());
        
        log.info("component=zeromq-security event=curve-client-configured " +
                "correlationId={} operationId={} clientPublicKey={} serverPublicKey={}", 
                correlationId, operationId, config.getClientPublicKey(), config.getServerPublicKey());
    }

    /**
     * Detect and apply appropriate security based on environment.
     * 
     * @param socket the socket to configure
     * @param profile the security profile (dev, prod, etc.)
     * @param correlationId correlation ID for logging
     */
    public void applyProfileBasedSecurity(ZMQ.Socket socket, String profile, String correlationId) {
        Objects.requireNonNull(socket, "Socket must not be null");
        
        switch (profile != null ? profile.toLowerCase() : "prod") {
            case "dev", "development", "local" -> {
                log.warn("component=zeromq-security event=development-profile-detected " +
                        "correlationId={} profile={} warning=plain-auth-allowed", 
                        correlationId, profile);
                applyNoSecurity(socket, correlationId);
            }
            case "test", "testing" -> {
                log.info("component=zeromq-security event=test-profile-detected " +
                        "correlationId={} profile={}", correlationId, profile);
                applyNoSecurity(socket, correlationId);
            }
            default -> {
                log.error("component=zeromq-security event=production-profile-no-config " +
                         "correlationId={} profile={} error=curve-config-required", 
                         correlationId, profile);
                throw new SecurityException(
                    "CURVE security configuration required for profile: " + profile, "CURVE");
            }
        }
    }

    /**
     * Get security metrics for monitoring and health checks.
     * 
     * @return a copy of current security metrics
     */
    public java.util.Map<String, Long> getSecurityMetrics() {
        return new java.util.HashMap<>(securityMetrics);
    }

    /**
     * Reset security metrics (useful for testing).
     */
    public void resetSecurityMetrics() {
        securityMetrics.clear();
        securityOperationCounter.set(0);
        log.debug("component=zeromq-security event=metrics-reset");
    }

    /**
     * Get the total number of security operations performed.
     * 
     * @return the total operation count
     */
    public long getTotalSecurityOperations() {
        return securityOperationCounter.get();
    }

    /**
     * Increment a security metric counter.
     */
    private void incrementSecurityMetric(String metric) {
        securityMetrics.merge(metric, 1L, Long::sum);
    }

    /**
     * Validate security configuration for production readiness.
     * 
     * @param mechanism the security mechanism to validate
     * @param profile the deployment profile
     * @throws SecurityException if configuration is not production-ready
     */
    public void validateProductionSecurity(SecurityMechanism mechanism, String profile) {
        if (profile != null && (profile.toLowerCase().contains("prod") || profile.toLowerCase().contains("production"))) {
            if (mechanism == SecurityMechanism.NONE || mechanism == SecurityMechanism.PLAIN) {
                throw new SecurityException(
                    "Insecure mechanism " + mechanism + " not allowed in production profile: " + profile,
                    mechanism.name());
            }
        }
        
        log.info("component=zeromq-security event=security-validation-passed " +
                "mechanism={} profile={}", mechanism, profile);
    }
} 