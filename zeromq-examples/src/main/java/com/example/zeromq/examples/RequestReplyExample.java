package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simple request-reply example: starts a REP server that echoes requests and a REQ client that sends one request.
 */
@Component
public class RequestReplyExample {

    private static final Logger log = LoggerFactory.getLogger(RequestReplyExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public RequestReplyExample(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        String bindEndpoint = properties.getNamed().getReqrepBind();
        String connectEndpoint = properties.getNamed().getReqrepConnect();

        // Start a REP server
        zeroMqTemplate.reply(bindEndpoint, requestBytes -> {
            // echo back the received payload as acknowledgement
            String received = new String(requestBytes);
            log.info("REP server received: {}", received);
            String reply = "echo:" + received;
            return reply.getBytes();
        });

        // Small pause to ensure server is listening
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Send a request and log the response
        ExampleMessage req = new ExampleMessage(java.util.UUID.randomUUID().toString(), "hello-req", System.currentTimeMillis());
        log.info("REQ client sending request id={}", req.getId());
        try {
            String response = zeroMqTemplate.request(connectEndpoint, req, String.class);
            log.info("REQ client received response={}", response);
        } catch (Exception e) {
            log.error("Request failed: {}", e.getMessage(), e);
        }
    }
} 