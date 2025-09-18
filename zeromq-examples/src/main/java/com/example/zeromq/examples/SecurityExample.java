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
            var curve = properties.getSecurity().getCurve();
            com.example.zeromq.core.ZmqSecurityConfig.CurveConfig cfg;
            if (curve.isMutualAuth()) {
                cfg = com.example.zeromq.core.ZmqSecurityConfig.CurveConfig.forMutualAuth(
                        curve.getServerPublicKey(), curve.getServerSecretKey(), curve.getClientPublicKey(), curve.getClientSecretKey(), true);
            } else {
                cfg = com.example.zeromq.core.ZmqSecurityConfig.CurveConfig.forServer(curve.getServerPublicKey(), curve.getServerSecretKey());
            }

            log.info("Publishing using CURVE-protected socket to {}", endpoint);
            zeroMqTemplate.publish(endpoint, topic, new ExampleMessage(java.util.UUID.randomUUID().toString(), "secure", System.currentTimeMillis()), cfg);
        } else if (mech == ZeroMqProperties.Security.Mechanism.PLAIN) {
            log.info("Publishing using PLAIN-auth socket to {}", endpoint);
            zeroMqTemplate.publish(endpoint, topic, new ExampleMessage(java.util.UUID.randomUUID().toString(), "plain", System.currentTimeMillis()));
        } else {
            log.info("Publishing without security to {}", endpoint);
            zeroMqTemplate.publish(endpoint, topic, new ExampleMessage(java.util.UUID.randomUUID().toString(), "none", System.currentTimeMillis()));
        }
    }
} 