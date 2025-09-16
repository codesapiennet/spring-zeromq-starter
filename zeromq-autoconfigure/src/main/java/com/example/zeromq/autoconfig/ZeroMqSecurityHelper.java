package com.example.zeromq.autoconfig;

import com.example.zeromq.core.ZAuthKeyGenerator;
import com.example.zeromq.core.ZmqSecurityConfig;
import com.example.zeromq.core.exception.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for simplified ZeroMQ security configuration.
 * 
 * <p>This helper provides convenient methods for creating secure ZeroMQ configurations
 * from Spring Boot properties, generating key pairs, and validating security settings.
 * It bridges the gap between configuration properties and low-level security APIs.
 * 
 * <p>The helper automatically handles key generation, validation, and profile-based
 * security enforcement while providing clear error messages for misconfigurations.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class ZeroMqSecurityHelper {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqSecurityHelper.class);
    
    private final ZeroMqProperties properties;
    private final ConcurrentHashMap<String, ZmqSecurityConfig.CurveConfig> curveConfigCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ZmqSecurityConfig.PlainConfig> plainConfigCache = new ConcurrentHashMap<>();

    /**
     * Create a new security helper with the given properties.
     * 
     * @param properties the ZeroMQ configuration properties
     */
    public ZeroMqSecurityHelper(ZeroMqProperties properties) {
        this.properties = Objects.requireNonNull(properties, "Properties must not be null");
    }

    /**
     * Create a CURVE configuration from properties.
     * 
     * @return CURVE configuration for client-side connections
     * @throws SecurityException if configuration is invalid
     */
    public ZmqSecurityConfig.CurveConfig createClientCurveConfig() {
        return createClientCurveConfig("default");
    }

    /**
     * Create a named CURVE configuration from properties.
     * 
     * @param configName the configuration name for caching
     * @return CURVE configuration for client-side connections
     * @throws SecurityException if configuration is invalid
     */
    public ZmqSecurityConfig.CurveConfig createClientCurveConfig(String configName) {
        Objects.requireNonNull(configName, "Config name must not be null");
        
        return curveConfigCache.computeIfAbsent(configName + "_client", key -> {
            ZeroMqProperties.Security.Curve curveProps = properties.getSecurity().getCurve();
            
            validateCurveProperties(curveProps, false);
            
            log.debug("Creating client CURVE configuration: {}", configName);
            
            return ZmqSecurityConfig.CurveConfig.forClient(
                curveProps.getServerPublicKey(),
                curveProps.getClientPublicKey(),
                curveProps.getClientSecretKey()
            );
        });
    }

    /**
     * Create a server CURVE configuration from properties.
     * 
     * @return CURVE configuration for server-side binding
     * @throws SecurityException if configuration is invalid
     */
    public ZmqSecurityConfig.CurveConfig createServerCurveConfig() {
        return createServerCurveConfig("default");
    }

    /**
     * Create a named server CURVE configuration from properties.
     * 
     * @param configName the configuration name for caching
     * @return CURVE configuration for server-side binding
     * @throws SecurityException if configuration is invalid
     */
    public ZmqSecurityConfig.CurveConfig createServerCurveConfig(String configName) {
        Objects.requireNonNull(configName, "Config name must not be null");
        
        return curveConfigCache.computeIfAbsent(configName + "_server", key -> {
            ZeroMqProperties.Security.Curve curveProps = properties.getSecurity().getCurve();
            
            validateCurveProperties(curveProps, true);
            
            log.debug("Creating server CURVE configuration: {}", configName);
            
            if (curveProps.isMutualAuth()) {
                return ZmqSecurityConfig.CurveConfig.forMutualAuth(
                    curveProps.getServerPublicKey(),
                    curveProps.getServerSecretKey(),
                    curveProps.getClientPublicKey(),
                    curveProps.getClientSecretKey(),
                    true // isServer
                );
            } else {
                return ZmqSecurityConfig.CurveConfig.forServer(
                    curveProps.getServerPublicKey(),
                    curveProps.getServerSecretKey()
                );
            }
        });
    }

    /**
     * Create a PLAIN authentication configuration from properties.
     * 
     * @param isServer whether this is for a server-side socket
     * @return PLAIN authentication configuration
     * @throws SecurityException if configuration is invalid
     */
    public ZmqSecurityConfig.PlainConfig createPlainConfig(boolean isServer) {
        return createPlainConfig("default", isServer);
    }

    /**
     * Create a named PLAIN authentication configuration from properties.
     * 
     * @param configName the configuration name for caching
     * @param isServer whether this is for a server-side socket
     * @return PLAIN authentication configuration
     * @throws SecurityException if configuration is invalid
     */
    public ZmqSecurityConfig.PlainConfig createPlainConfig(String configName, boolean isServer) {
        Objects.requireNonNull(configName, "Config name must not be null");
        
        String cacheKey = configName + "_" + (isServer ? "server" : "client");
        
        return plainConfigCache.computeIfAbsent(cacheKey, key -> {
            ZeroMqProperties.Security.Plain plainProps = properties.getSecurity().getPlain();
            
            validatePlainProperties(plainProps);
            
            log.debug("Creating {} PLAIN configuration: {}", isServer ? "server" : "client", configName);
            
            return new ZmqSecurityConfig.PlainConfig(
                plainProps.getUsername(),
                plainProps.getPassword(),
                isServer
            );
        });
    }

    /**
     * Generate and configure new CURVE key pairs for development.
     * 
     * <p><strong>WARNING:</strong> This method is intended for development and testing only.
     * Generated keys are logged and should never be used in production.
     * 
     * @return a key pair generation result with both server and client keys
     */
    public CurveKeyPairResult generateDevelopmentKeyPairs() {
        warnIfProduction();
        
        log.warn("Generating development CURVE key pairs - DO NOT USE IN PRODUCTION");
        
        ZAuthKeyGenerator.CurveKeyPair serverKeys = ZAuthKeyGenerator.generateServerKeyPair();
        ZAuthKeyGenerator.CurveKeyPair clientKeys = ZAuthKeyGenerator.generateClientKeyPair();
        
        log.warn("Development keys generated:");
        log.warn("  Server Public:  {}", serverKeys.getPublicKey());
        log.warn("  Server Secret:  {}", serverKeys.getSecretKey());
        log.warn("  Client Public:  {}", clientKeys.getPublicKey());
        log.warn("  Client Secret:  {}", clientKeys.getSecretKey());
        log.warn("COPY THESE KEYS TO YOUR CONFIGURATION - THEY WILL NOT BE SHOWN AGAIN");
        
        return new CurveKeyPairResult(serverKeys, clientKeys);
    }

    /**
     * Validate the current security configuration for production readiness.
     * 
     * @throws SecurityException if configuration is not production-ready
     */
    public void validateProductionSecurity() {
        ZeroMqProperties.Security security = properties.getSecurity();
        String profile = security.getProfile();
        
        if (isProductionProfile(profile)) {
            log.info("Validating production security configuration");
            
            // Enforce CURVE encryption in production
            if (security.getMechanism() != ZeroMqProperties.Security.Mechanism.CURVE) {
                throw new SecurityException(
                    "Production profile requires CURVE encryption. Current mechanism: " + security.getMechanism(),
                    security.getMechanism().name()
                );
            }
            
            // Validate CURVE keys are properly configured
            ZeroMqProperties.Security.Curve curveProps = security.getCurve();
            validateCurveProperties(curveProps, true); // Validate as server
            validateCurveProperties(curveProps, false); // Validate as client
            
            log.info("Production security validation passed");
        }
    }

    /**
     * Check if the current configuration allows insecure connections.
     * 
     * @return true if insecure connections are allowed
     */
    public boolean isInsecureAllowed() {
        ZeroMqProperties.Security security = properties.getSecurity();
        return security.getMechanism() == ZeroMqProperties.Security.Mechanism.NONE ||
               (!isProductionProfile(security.getProfile()) && 
                security.getMechanism() == ZeroMqProperties.Security.Mechanism.PLAIN);
    }

    /**
     * Get a human-readable description of the current security configuration.
     * 
     * @return security configuration description
     */
    public String getSecurityDescription() {
        ZeroMqProperties.Security security = properties.getSecurity();
        
        StringBuilder desc = new StringBuilder();
        desc.append("Security Profile: ").append(security.getProfile())
            .append(", Mechanism: ").append(security.getMechanism());
        
        switch (security.getMechanism()) {
            case CURVE -> {
                ZeroMqProperties.Security.Curve curveProps = security.getCurve();
                desc.append(", Mutual Auth: ").append(curveProps.isMutualAuth());
            }
            case PLAIN -> {
                ZeroMqProperties.Security.Plain plainProps = security.getPlain();
                desc.append(", Username: ").append(plainProps.getUsername());
            }
            case NONE -> desc.append(" (INSECURE)");
        }
        
        return desc.toString();
    }

    /**
     * Clear all cached security configurations.
     */
    public void clearCache() {
        curveConfigCache.clear();
        plainConfigCache.clear();
        log.debug("Security configuration cache cleared");
    }

    /**
     * Validate CURVE properties for completeness and correctness.
     */
    private void validateCurveProperties(ZeroMqProperties.Security.Curve curveProps, boolean isServer) {
        if (!StringUtils.hasText(curveProps.getServerPublicKey())) {
            throw new SecurityException("Server public key is required for CURVE authentication", "CURVE");
        }
        
        if (isServer && !StringUtils.hasText(curveProps.getServerSecretKey())) {
            throw new SecurityException("Server secret key is required for server-side CURVE", "CURVE");
        }
        
        if (!isServer && !StringUtils.hasText(curveProps.getClientSecretKey())) {
            throw new SecurityException("Client secret key is required for client-side CURVE", "CURVE");
        }
        
        // Validate key formats
        if (!ZAuthKeyGenerator.isValidZ85Key(curveProps.getServerPublicKey())) {
            throw new SecurityException("Invalid server public key format - must be valid Z85", "CURVE");
        }
        
        if (isServer && !ZAuthKeyGenerator.isValidZ85Key(curveProps.getServerSecretKey())) {
            throw new SecurityException("Invalid server secret key format - must be valid Z85", "CURVE");
        }
        
        if (!isServer && StringUtils.hasText(curveProps.getClientSecretKey()) &&
            !ZAuthKeyGenerator.isValidZ85Key(curveProps.getClientSecretKey())) {
            throw new SecurityException("Invalid client secret key format - must be valid Z85", "CURVE");
        }
    }

    /**
     * Validate PLAIN properties for completeness.
     */
    private void validatePlainProperties(ZeroMqProperties.Security.Plain plainProps) {
        if (!StringUtils.hasText(plainProps.getUsername())) {
            throw new SecurityException("Username is required for PLAIN authentication", "PLAIN");
        }
        
        if (!StringUtils.hasText(plainProps.getPassword())) {
            throw new SecurityException("Password is required for PLAIN authentication", "PLAIN");
        }
        
        if (plainProps.getPassword().length() < 8) {
            log.warn("PLAIN password is shorter than 8 characters - consider using stronger passwords");
        }
    }

    /**
     * Check if the given profile is a production profile.
     */
    private boolean isProductionProfile(String profile) {
        return profile != null && 
               (profile.toLowerCase().contains("prod") || 
                profile.toLowerCase().contains("production"));
    }

    /**
     * Warn if this appears to be a production environment.
     */
    private void warnIfProduction() {
        if (isProductionProfile(properties.getSecurity().getProfile())) {
            log.error("SECURITY WARNING: Generating keys in production profile! This is DANGEROUS!");
        }
    }

    /**
     * Result of CURVE key pair generation.
     */
    public static final class CurveKeyPairResult {
        private final ZAuthKeyGenerator.CurveKeyPair serverKeys;
        private final ZAuthKeyGenerator.CurveKeyPair clientKeys;

        public CurveKeyPairResult(ZAuthKeyGenerator.CurveKeyPair serverKeys, 
                                 ZAuthKeyGenerator.CurveKeyPair clientKeys) {
            this.serverKeys = serverKeys;
            this.clientKeys = clientKeys;
        }

        public ZAuthKeyGenerator.CurveKeyPair getServerKeys() { return serverKeys; }
        public ZAuthKeyGenerator.CurveKeyPair getClientKeys() { return clientKeys; }

        /**
         * Get properties-formatted configuration string.
         * 
         * @return YAML configuration snippet
         */
        public String toConfigurationString() {
            return String.format("""
                spring:
                  zeromq:
                    security:
                      mechanism: CURVE
                      curve:
                        server-public-key: %s
                        server-secret-key: %s
                        client-public-key: %s
                        client-secret-key: %s
                """,
                serverKeys.getPublicKey(),
                serverKeys.getSecretKey(),
                clientKeys.getPublicKey(),
                clientKeys.getSecretKey()
            );
        }
    }
} 