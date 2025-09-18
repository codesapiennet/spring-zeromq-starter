package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.core.JacksonMessageConverter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lightweight user service that replies to requests from an API gateway.
 */
@Component
public class MicroservicesUserService {

    private static final Logger log = LoggerFactory.getLogger(MicroservicesUserService.class);

    private final ZeroMqTemplate zeroMqTemplate;

    public MicroservicesUserService(ZeroMqTemplate zeroMqTemplate) {
        this.zeroMqTemplate = zeroMqTemplate;
    }

    @PostConstruct
    public void start() {
        String bindEndpoint = "tcp://*:5610";

        // Start a REP server that handles ExampleMessage requests
        zeroMqTemplate.reply(bindEndpoint, requestBytes -> {
            try {
                JacksonMessageConverter converter = new JacksonMessageConverter();
                ExampleMessage req = converter.fromBytes(requestBytes, ExampleMessage.class);
                log.info("UserService received request for id={}", req.getId());

                // Build a simple response payload
                String response = String.format("user:{id:%s,status:active}", req.getId());
                return converter.toBytes(response);
            } catch (Exception e) {
                log.error("Failed to process request: {}", e.getMessage(), e);
                try {
                    return new JacksonMessageConverter().toBytes("error");
                } catch (Exception ex) {
                    return ("error").getBytes();
                }
            }
        });
    }
} 