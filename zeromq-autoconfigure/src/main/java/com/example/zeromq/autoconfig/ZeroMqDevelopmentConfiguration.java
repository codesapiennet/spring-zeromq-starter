package com.example.zeromq.autoconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for ZeroMQ in development environment.
 * 
 * <p>This configuration provides relaxed security settings suitable for 
 * development environments where ease of use takes precedence over strict security.
 * 
 * <p><strong>Warning:</strong> This configuration should never be used in production
 * environments as it may disable important security features.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.zeromq.security", name = "profile", havingValue = "dev")
public class ZeroMqDevelopmentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqDevelopmentConfiguration.class);

    /**
     * Initialize development configuration with appropriate warnings.
     */
    @PostConstruct
    public void init() {
        log.warn("component=zeromq-config event=dev-profile-active " +
                "message='ZeroMQ running in DEVELOPMENT mode - security restrictions relaxed'");
        log.warn("component=zeromq-security event=dev-mode-warning " +
                "message='Development mode should NEVER be used in production'");
    }

    /**
     * Indicates if CURVE encryption is required.
     * 
     * @return false for development (plain connections allowed)
     */
    public boolean isCurveRequired() {
        return false;
    }

    /**
     * Indicates if authentication is required.
     * 
     * @return false for development (anonymous connections allowed)
     */
    public boolean isAuthRequired() {
        return false;
    }

    /**
     * Gets the security profile name.
     * 
     * @return "development"
     */
    public String getProfile() {
        return "development";
    }
} 