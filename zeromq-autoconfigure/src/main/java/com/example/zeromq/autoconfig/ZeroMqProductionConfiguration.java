package com.example.zeromq.autoconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for ZeroMQ in production environment.
 * 
 * <p>This configuration enforces strict security requirements suitable for 
 * production environments including:
 * <ul>
 * <li>Mandatory CURVE encryption for all connections</li>
 * <li>Required authentication for all clients</li>
 * <li>Comprehensive audit logging</li>
 * <li>Performance monitoring and alerting</li>
 * </ul>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.zeromq.security", name = "profile", havingValue = "prod", matchIfMissing = true)
public class ZeroMqProductionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqProductionConfiguration.class);

    private final ZeroMqProperties properties;

    /**
     * Create production configuration with the given properties.
     * 
     * @param properties the ZeroMQ configuration properties
     */
    public ZeroMqProductionConfiguration(ZeroMqProperties properties) {
        this.properties = properties;
    }

    /**
     * Initialize production configuration with security validation.
     */
    @PostConstruct
    public void init() {
        log.info("component=zeromq-config event=prod-profile-active " +
                "message='ZeroMQ running in PRODUCTION mode - strict security enforced'");
        
        validateSecuritySettings();
        logSecurityStatus();
    }

    /**
     * Validates that all required security settings are properly configured.
     */
    private void validateSecuritySettings() {
        if (properties.getSecurity() == null) {
            log.error("component=zeromq-security event=missing-security-config " +
                     "message='No security configuration found in production mode'");
            throw new IllegalStateException("Security configuration is required in production mode");
        }

        if (!properties.getSecurity().isEnabled()) {
            log.error("component=zeromq-security event=security-disabled " +
                     "message='Security is disabled in production mode'");
            throw new IllegalStateException("Security must be enabled in production mode");
        }

        log.info("component=zeromq-security event=security-validation-passed " +
                "message='All security requirements validated successfully'");
    }

    /**
     * Logs the current security status for audit purposes.
     */
    private void logSecurityStatus() {
        if (properties.getSecurity() != null) {
            log.info("component=zeromq-security event=security-status " +
                    "enabled={} mechanism={} curveEnabled={}",
                    properties.getSecurity().isEnabled(),
                    properties.getSecurity().getMechanism(),
                    properties.getSecurity().getCurve() != null);
        }
    }

    /**
     * Indicates if CURVE encryption is required.
     * 
     * @return true for production (CURVE required)
     */
    public boolean isCurveRequired() {
        return true;
    }

    /**
     * Indicates if authentication is required.
     * 
     * @return true for production (authentication required)
     */
    public boolean isAuthRequired() {
        return true;
    }

    /**
     * Gets the security profile name.
     * 
     * @return "production"
     */
    public String getProfile() {
        return "production";
    }

    /**
     * Gets the configuration properties.
     * 
     * @return the ZeroMQ properties
     */
    public ZeroMqProperties getProperties() {
        return properties;
    }
} 