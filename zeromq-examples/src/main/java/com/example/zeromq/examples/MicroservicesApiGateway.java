package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simple API Gateway that sends a request to the user service and logs the response.
 */
@Component
public class MicroservicesApiGateway {

    private static final Logger log = LoggerFactory.getLogger(MicroservicesApiGateway.class);

    private final ZeroMqTemplate zeroMqTemplate;

    public MicroservicesApiGateway(ZeroMqTemplate zeroMqTemplate) {
        this.zeroMqTemplate = zeroMqTemplate;
    }

    @PostConstruct
    public void run() {
        String connectEndpoint = "tcp://localhost:5610";

        ExampleMessage req = new ExampleMessage(java.util.UUID.randomUUID().toString(), "whoami", System.currentTimeMillis());
        log.info("API Gateway sending request id={}", req.getId());

        try {
            String response = zeroMqTemplate.request(connectEndpoint, req, String.class);
            log.info("API Gateway received response: {}", response);
        } catch (Exception e) {
            log.error("API request failed: {}", e.getMessage(), e);
        }
    }
} 