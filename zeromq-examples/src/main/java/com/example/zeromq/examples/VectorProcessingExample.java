package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.core.BatchVector;
import com.example.zeromq.core.DenseVector;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Vector processing example: publishes a batch of vectors and subscribes to consume them.
 */
@Component
public class VectorProcessingExample {

    private static final Logger log = LoggerFactory.getLogger(VectorProcessingExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public VectorProcessingExample(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        String publishEndpoint = properties.getNamed().getVectorPublish(); // typically tcp://*:6020
        String subscribeEndpoint = properties.getNamed().getVectorSubscribe(); // connect to the publish bind
        String topic = "vector.batch";

        CountDownLatch latch = new CountDownLatch(1);

        // Subscribe to receive the batch back for verification
        zeroMqTemplate.subscribe(subscribeEndpoint, topic, BatchVector.class, batch -> {
            try {
                log.info("Received BatchVector: {}", batch.getDescription());
                log.info("Mean vector norm: {}", batch.getStatistics().get("meanNorm"));
            } catch (Exception e) {
                log.error("Error processing BatchVector: {}", e.getMessage(), e);
            } finally {
                latch.countDown();
            }
        });

        // Small pause to ensure the subscriber thread is active
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Create a demo batch and publish it
        BatchVector batch = BatchVector.random(4, 128);
        log.info("Publishing demo BatchVector to {} topic={}", publishEndpoint, topic);
        zeroMqTemplate.publish(publishEndpoint, topic, batch);

        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                log.warn("Did not receive BatchVector within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 