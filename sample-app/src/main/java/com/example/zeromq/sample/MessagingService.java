package com.example.zeromq.sample;

import com.example.zeromq.autoconfig.ZeroMqProperties;
import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.core.DenseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Simple messaging service used by the sample application to demonstrate sending messages
 * via ZeroMqTemplate. Operations are non-blocking and safe for demonstration purposes.
 */
@Service
@ConditionalOnProperty(name = "sampleapp.messaging.enabled", havingValue = "true", matchIfMissing = false)
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final ZeroMqProperties properties;

    public MessagingService(ZeroMqTemplate zeroMqTemplate, ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    private void init() {
        log.info("MessagingService initializing. Sample messaging is enabled.");
        try {
            // Send a sample publish (non-blocking)
            DenseVector vec = DenseVector.random(8);
            zeroMqTemplate.publish(properties.getNamed().getMessagingPublish(), "sample.topic", vec);
            log.info("Published sample vector to {} sample.topic", properties.getNamed().getMessagingPublish());

            // Push a sample message to a worker queue (non-blocking)
            zeroMqTemplate.push(properties.getNamed().getWorkerPush(), "hello-worker");
            log.info("Pushed sample message to {}", properties.getNamed().getWorkerPush());
        } catch (Exception e) {
            log.error("MessagingService demo operations failed: {}", e.getMessage(), e);
        }
    }
} 