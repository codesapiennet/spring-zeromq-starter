package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.autoconfig.ZeroMqProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Security example showing how to conditionally use PLAIN or CURVE when publishing.
 * This example does not generate keys; it reads configured properties and chooses the security mechanism.
 */
@Component
public class SecurityExample {

    private static final Logger log = LoggerFactory.getLogger(SecurityExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final ZeroMqProperties properties;

    public SecurityExample(ZeroMqTemplate zeroMqTemplate, ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        String topic = "v1.security.example";
        String endpoint = properties.getNamed().getSecurityPubsub();

        ZeroMqProperties.Security.Mechanism mech = properties.getSecurity().getMechanism();
        log.info("Configured security mechanism: {}", mech);

        if (mech == ZeroMqProperties.Security.Mechanism.CURVE) {
            // Use CURVE - construct a simple curve config wrapper used by ZeroMqTemplate (optional)
            var curve = properties.getSecurity().getCurve();
            com.example.zeromq.core.ZmqSecurityConfig.CurveConfig cfg = new com.example.zeromq.core.ZmqSecurityConfig.CurveConfig(
                    curve.getServerPublicKey(), curve.getServerSecretKey(), curve.getClientPublicKey(), curve.getClientSecretKey());
            log.info("Publishing using CURVE-protected socket to {}", endpoint);
            zeroMqTemplate.publish(endpoint, topic, new ExampleMessage(java.util.UUID.randomUUID().toString(), "secure", System.currentTimeMillis()), cfg);
        } else if (mech == ZeroMqProperties.Security.Mechanism.PLAIN) {
            log.info("Publishing using PLAIN-auth socket to {}", endpoint);
            // publish without curve config; server side will enforce PLAIN
            zeroMqTemplate.publish(endpoint, topic, new ExampleMessage(java.util.UUID.randomUUID().toString(), "plain", System.currentTimeMillis()));
        } else {
            log.info("Publishing without security to {}", endpoint);
            zeroMqTemplate.publish(endpoint, topic, new ExampleMessage(java.util.UUID.randomUUID().toString(), "none", System.currentTimeMillis()));
        }
    }
} 