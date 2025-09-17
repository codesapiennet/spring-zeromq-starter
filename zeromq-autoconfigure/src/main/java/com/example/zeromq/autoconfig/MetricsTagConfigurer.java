package com.example.zeromq.autoconfig;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Adds cluster-scoped common tags to all metrics (environment, instance).
 */
@Component
public class MetricsTagConfigurer implements MeterRegistryCustomizer<MeterRegistry> {

    private static final Logger log = LoggerFactory.getLogger(MetricsTagConfigurer.class);

    private final Environment environment;

    public MetricsTagConfigurer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void customize(MeterRegistry registry) {
        String envTag = resolveEnvironmentTag();
        String instanceTag = resolveInstanceId();

        try {
            registry.config().commonTags("environment", envTag, "instance", instanceTag);
            log.info("Registered common metric tags: environment={} instance={}", envTag, instanceTag);
        } catch (Exception e) {
            log.warn("Failed to register common metric tags: {}", e.getMessage());
        }
    }

    private String resolveEnvironmentTag() {
        String prop = System.getProperty("zeromq.metrics.environment");
        if (prop != null && !prop.isBlank()) return prop;
        String profiles = environment.getProperty("spring.profiles.active");
        if (profiles != null && !profiles.isBlank()) return profiles;
        String env = environment.getProperty("environment");
        if (env != null && !env.isBlank()) return env;
        String envVar = System.getenv("ENVIRONMENT");
        if (envVar != null && !envVar.isBlank()) return envVar;
        return "unknown";
    }

    private String resolveInstanceId() {
        String prop = System.getProperty("zeromq.instance.id");
        if (prop != null && !prop.isBlank()) return prop;
        String host = environment.getProperty("HOSTNAME");
        if (host != null && !host.isBlank()) return host;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // fallthrough
        }
        String envVar = System.getenv("HOSTNAME");
        if (envVar != null && !envVar.isBlank()) return envVar;
        return "unknown-instance";
    }
} 