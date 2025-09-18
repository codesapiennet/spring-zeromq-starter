package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Push/Pull example: creates a PULL handler that processes a simple task message and a PUSH that sends one task.
 */
@Component
public class PushPullExample {

    private static final Logger log = LoggerFactory.getLogger(PushPullExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public PushPullExample(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        String pullEndpoint = properties.getNamed().getPushPullPull();
        String pushEndpoint = properties.getNamed().getPushPullPush();

        zeroMqTemplate.pull(pullEndpoint, ExampleMessage.class, message -> {
            log.info("PULL handler processed message id={} payload={}", message.getId(), message.getPayload());
        });

        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        ExampleMessage task = new ExampleMessage(java.util.UUID.randomUUID().toString(), "task-payload", System.currentTimeMillis());
        log.info("PUSH sending task id={}", task.getId());
        zeroMqTemplate.push(pushEndpoint, task);
    }
} 