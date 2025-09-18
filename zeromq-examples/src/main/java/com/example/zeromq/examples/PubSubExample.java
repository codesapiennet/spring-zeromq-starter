package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Basic pub/sub example that subscribes to a topic and then publishes a single message.
 * Useful for manual testing of ZeroMQ connectivity and message converters.
 */
@Component
public class PubSubExample {

    private static final Logger log = LoggerFactory.getLogger(PubSubExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public PubSubExample(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        String topic = "v1.pubsub.example";
        String subscribeEndpoint = properties.getNamed().getPubsubSubscribe();
        String publishEndpoint = properties.getNamed().getPubsubPublish();

        CountDownLatch latch = new CountDownLatch(1);

        zeroMqTemplate.subscribe(subscribeEndpoint, topic, ExampleMessage.class, message -> {
            try {
                log.info("Subscriber received message: {}", message);
                latch.countDown();
            } catch (Exception e) {
                log.error("Failed to process incoming pub/sub message: {}", e.getMessage(), e);
            }
        });

        // Small pause to allow subscription background thread to become active (best-effort)
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ExampleMessage msg = new ExampleMessage(java.util.UUID.randomUUID().toString(),
                "Hello from PubSubExample",
                System.currentTimeMillis());

        log.info("Publishing message id={} to topic={}", msg.getId(), topic);
        zeroMqTemplate.publish(publishEndpoint, topic, msg);

        try {
            boolean received = latch.await(3, TimeUnit.SECONDS);
            if (!received) {
                log.warn("Did not receive published message within timeout; check endpoint/ports and converters");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 