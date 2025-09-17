package com.example.zeromq.autoconfig;

import com.example.zeromq.core.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for ZeroMQ Spring Boot integration.
 * 
 * <p>This configuration class automatically sets up all necessary ZeroMQ components
 * when the starter is included in a Spring Boot application. It provides intelligent
 * defaults while allowing extensive customization through configuration properties.
 * 
 * <p>The auto-configuration is activated when ZeroMQ classes are present on the
 * classpath and can be disabled by setting {@code spring.zeromq.enabled=false}.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@AutoConfiguration
@ConditionalOnClass({ZmqContextHolder.class, org.zeromq.ZMQ.class})
@ConditionalOnProperty(prefix = "spring.zeromq", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ZeroMqProperties.class)
public class ZeroMqAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqAutoConfiguration.class);

    /**
     * ZeroMQ context holder for managing the global ZMQ context.
     * 
     * @return ZmqContextHolder bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZmqContextHolder zmqContextHolder() {
        log.info("Creating ZeroMQ context holder");
        return new ZmqContextHolder();
    }

    /**
     * Security configuration for ZeroMQ sockets.
     * 
     * @return ZmqSecurityConfig bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZmqSecurityConfig zmqSecurityConfig() {
        log.info("Creating ZeroMQ security configuration");
        return new ZmqSecurityConfig();
    }

    /**
     * Socket factory with integrated security and metrics.
     * 
     * @param contextHolder the ZMQ context holder
     * @param securityConfig the security configuration
     * @param meterRegistry optional metrics registry
     * @return ZmqSocketFactory bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZmqSocketFactory zmqSocketFactory(ZmqContextHolder contextHolder,
                                           ZmqSecurityConfig securityConfig,
                                           @Autowired(required = false) MeterRegistry meterRegistry) {
        log.info("Creating ZeroMQ socket factory with {} metrics", 
                meterRegistry != null ? "enabled" : "disabled");
        return new ZmqSocketFactory(contextHolder, securityConfig, meterRegistry);
    }

    /**
     * Jackson message converter for JSON serialization.
     * 
     * @return JacksonMessageConverter bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "jacksonMessageConverter")
    public JacksonMessageConverter jacksonMessageConverter() {
        log.debug("Creating Jackson message converter");
        return new JacksonMessageConverter();
    }

    /**
     * Vector message converter for binary vector serialization.
     * 
     * @return VectorMessageConverter bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.zeromq.vector", name = "enabled", havingValue = "true", matchIfMissing = true)
    public VectorMessageConverter vectorMessageConverter() {
        log.debug("Creating vector message converter");
        return new VectorMessageConverter();
    }

    /**
     * High-level ZeroMQ template for easy messaging operations.
     * 
     * @param socketFactory the socket factory
     * @param messageConverters list of available message converters
     * @param properties configuration properties
     * @return ZeroMqTemplate bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZeroMqTemplate zeroMqTemplate(ZmqSocketFactory socketFactory,
                                        List<MessageConverter> messageConverters,
                                        ZeroMqProperties properties) {
        log.info("Creating ZeroMQ template with {} message converters", messageConverters.size());
        return new ZeroMqTemplate(socketFactory, messageConverters, properties);
    }

    /**
     * Security helper for creating secure configurations.
     * 
     * @param properties configuration properties
     * @return ZeroMqSecurityHelper bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZeroMqSecurityHelper zeroMqSecurityHelper(ZeroMqProperties properties) {
        log.debug("Creating ZeroMQ security helper");
        return new ZeroMqSecurityHelper(properties);
    }



    /**
     * Metrics collector for ZeroMQ operations (requires Micrometer).
     * 
     * @param socketFactory the socket factory
     * @param meterRegistry the meter registry
     * @param properties configuration properties
     * @return ZeroMqMetricsCollector bean
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.zeromq.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ZeroMqMetricsCollector zeroMqMetricsCollector(ZmqSocketFactory socketFactory,
                                                        MeterRegistry meterRegistry,
                                                        ZeroMqProperties properties) {
        log.debug("Creating ZeroMQ metrics collector");
        return new ZeroMqMetricsCollector(socketFactory, meterRegistry, properties);
    }

    /**
     * Connection pool manager for reusing socket connections.
     * 
     * @param socketFactory the socket factory
     * @param properties configuration properties
     * @return ZeroMqConnectionPool bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.zeromq.pool", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ZeroMqConnectionPool zeroMqConnectionPool(ZmqSocketFactory socketFactory,
                                                    ZeroMqProperties properties) {
        log.info("Creating ZeroMQ connection pool with max-size: {}", 
                properties.getPool().getMaxSize());
        return new ZeroMqConnectionPool(socketFactory, properties.getPool());
    }

    /**
     * Vector processing service for high-performance vector operations.
     * 
     * @param properties configuration properties
     * @return VectorProcessingService bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.zeromq.vector", name = "enabled", havingValue = "true", matchIfMissing = true)
    public VectorProcessingService vectorProcessingService(ZeroMqProperties properties) {
        log.debug("Creating vector processing service");
        return new VectorProcessingService(properties.getVector());
    }

    /**
     * Health indicator for ZeroMQ components (requires Actuator).
     * Uses reflection to avoid compile-time dependency on actuator classes.
     * 
     * @param contextHolder the ZMQ context holder
     * @param properties configuration properties
     * @return HealthIndicator bean or null if actuator not available
     */
    @Bean(name = "zeroMqHealthIndicator")
    @ConditionalOnClass(name = "org.springframework.boot.actuator.health.HealthIndicator")
    @ConditionalOnMissingBean(name = "zeroMqHealthIndicator")
    @ConditionalOnProperty(prefix = "spring.zeromq.monitoring.health-check", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Object zeroMqHealthIndicator(ZmqContextHolder contextHolder, ZeroMqProperties properties) {
        try {
            log.debug("Creating ZeroMQ health indicator using reflection");
            
            // Load actuator classes via reflection
            Class<?> healthIndicatorClass = Class.forName("org.springframework.boot.actuator.health.HealthIndicator");
            Class<?> abstractHealthIndicatorClass = Class.forName("org.springframework.boot.actuator.health.AbstractHealthIndicator");
            
            // Create a simple health indicator implementation
            return java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ healthIndicatorClass },
                (proxy, method, args) -> {
                    if ("health".equals(method.getName())) {
                        return createHealthStatus(contextHolder, properties);
                    }
                    return null;
                }
            );
            
        } catch (Exception e) {
            log.warn("Failed to create ZeroMQ health indicator: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Create health status using reflection to avoid compile-time dependencies.
     */
    private Object createHealthStatus(ZmqContextHolder contextHolder, ZeroMqProperties properties) {
        try {
            Class<?> healthClass = Class.forName("org.springframework.boot.actuator.health.Health");
            Class<?> builderClass = Class.forName("org.springframework.boot.actuator.health.Health$Builder");
            Class<?> statusClass = Class.forName("org.springframework.boot.actuator.health.Status");
            
            // Get UP status
            Object upStatus = statusClass.getField("UP").get(null);
            
            // Create Health.Builder
            Object builder = healthClass.getMethod("up").invoke(null);
            
            // Add basic ZeroMQ health information
            builder = builderClass.getMethod("withDetail", String.class, Object.class)
                    .invoke(builder, "zeromq.enabled", properties.isEnabled());
            
            if (contextHolder.isAvailable()) {
                builder = builderClass.getMethod("withDetail", String.class, Object.class)
                        .invoke(builder, "context.available", true);
                builder = builderClass.getMethod("withDetail", String.class, Object.class)
                        .invoke(builder, "context.closed", contextHolder.getContext().isClosed());
                builder = builderClass.getMethod("withDetail", String.class, Object.class)
                        .invoke(builder, "active.sockets", contextHolder.getContext().getSockets().size());
            } else {
                builder = builderClass.getMethod("status", statusClass)
                        .invoke(builder, statusClass.getField("DOWN").get(null));
                builder = builderClass.getMethod("withDetail", String.class, Object.class)
                        .invoke(builder, "context.available", false);
            }
            
            // Build and return health
            return builderClass.getMethod("build").invoke(builder);
            
        } catch (Exception e) {
            log.debug("Error creating health status: {}", e.getMessage());
            // Return a simple UP status as fallback
            try {
                Class<?> healthClass = Class.forName("org.springframework.boot.actuator.health.Health");
                return healthClass.getMethod("up").invoke(null);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * Configuration for development profile with relaxed security.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.zeromq.security", name = "profile", havingValue = "dev")
    public ZeroMqDevelopmentConfiguration developmentConfiguration() {
        log.warn("ZeroMQ running in DEVELOPMENT mode - security restrictions relaxed");
        return new ZeroMqDevelopmentConfiguration();
    }

    /**
     * Configuration for production profile with strict security requirements.
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.zeromq.security", name = "profile", havingValue = "prod", matchIfMissing = true)
    public ZeroMqProductionConfiguration productionConfiguration(ZeroMqProperties properties) {
        log.info("ZeroMQ running in PRODUCTION mode - strict security enforced");
        return new ZeroMqProductionConfiguration(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.zeromq.security", name = "mechanism", havingValue = "CURVE")
    @ConditionalOnMissingBean
    public com.example.zeromq.core.ZmqZapAuthenticator zmqZapAuthenticator(ZmqContextHolder contextHolder,
                                                                          ZeroMqProperties properties) {
        log.info("Creating ZeroMQ ZAP authenticator (CURVE) for inproc://zeromq.zap.01");
        java.util.List<String> allowed = properties.getSecurity().getCurve().getAllowedClientPublicKeys();
        com.example.zeromq.core.ZmqZapAuthenticator authenticator = new com.example.zeromq.core.ZmqZapAuthenticator(contextHolder, allowed);
        authenticator.start();
        return authenticator;
    }
} 