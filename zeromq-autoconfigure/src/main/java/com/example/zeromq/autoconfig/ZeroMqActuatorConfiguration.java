package com.example.zeromq.autoconfig;

import com.example.zeromq.core.ZmqContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for ZeroMQ Actuator integration.
 * 
 * <p>This configuration is only loaded when Spring Boot Actuator classes are present
 * on the classpath. It provides health indicators and other actuator-specific beans
 * for monitoring ZeroMQ components.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@AutoConfiguration
@ConditionalOnClass(name = {
    "org.springframework.boot.actuator.health.HealthIndicator",
    "org.springframework.boot.actuator.health.AbstractHealthIndicator"
})
public class ZeroMqActuatorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ZeroMqActuatorConfiguration.class);

    /**
     * Health indicator for ZeroMQ components (requires Actuator).
     * 
     * @param contextHolder the ZMQ context holder
     * @param properties configuration properties
     * @return ZeroMqHealthIndicator bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.zeromq.monitoring.health-check", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ZeroMqHealthIndicator zeroMqHealthIndicator(ZmqContextHolder contextHolder,
                                                      ZeroMqProperties properties) {
        log.debug("Creating ZeroMQ health indicator");
        return new ZeroMqHealthIndicator(contextHolder, properties);
    }
} 