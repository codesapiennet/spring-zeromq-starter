package com.example.zeromq.sample;

import com.example.zeromq.autoconfig.ZeroMqProperties;
import com.example.zeromq.autoconfig.ZeroMqSecurityHelper;
import com.example.zeromq.core.ZAuthKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Lightweight encrypted server helper used by the sample application to demonstrate
 * ZeroMQ security configuration. This component does not bind sockets by default and
 * only logs configuration and optionally generates development CURVE keys when enabled.
 */
@Component
@ConditionalOnProperty(name = "sampleapp.server.enabled", havingValue = "true", matchIfMissing = false)
public class EncryptedServer {

    private static final Logger log = LoggerFactory.getLogger(EncryptedServer.class);

    private final ZeroMqSecurityHelper securityHelper;
    private final ZeroMqProperties properties;

    public EncryptedServer(ZeroMqSecurityHelper securityHelper, ZeroMqProperties properties) {
        this.securityHelper = securityHelper;
        this.properties = properties;
    }

    @PostConstruct
    private void init() {
        try {
            log.info("EncryptedServer initializing - securityDescription={}", securityHelper.getSecurityDescription());

            // Generate development keys only when running in a dev profile and explicitly enabled via env var
            boolean isDevProfile = "dev".equalsIgnoreCase(properties.getSecurity().getProfile());
            boolean generateDevKeys = isDevProfile && "true".equalsIgnoreCase(System.getenv("SAMPLEAPP_GENERATE_DEV_KEYS"));

            if (generateDevKeys) {
                log.warn("sample-app is configured to generate development CURVE keys - only use in development");
                ZAuthKeyGenerator.CurveKeyPair serverKeys = ZAuthKeyGenerator.generateServerKeyPair();
                ZAuthKeyGenerator.CurveKeyPair clientKeys = ZAuthKeyGenerator.generateClientKeyPair();
                log.info("Generated development CURVE server public key: {}", serverKeys.getPublicKey());
                log.info("Generated development CURVE client public key: {}", clientKeys.getPublicKey());
            }

        } catch (Exception e) {
            log.error("EncryptedServer initialization failed: {}", e.getMessage(), e);
        }
    }
} 