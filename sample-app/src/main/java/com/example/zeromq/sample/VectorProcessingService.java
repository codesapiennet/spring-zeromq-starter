package com.example.zeromq.sample;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.core.BatchVector;
import com.example.zeromq.core.DenseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Demonstration service that sends vectors for processing using ZeroMqTemplate.
 */
@Service
@ConditionalOnProperty(name = "sampleapp.vectorprocessing.enabled", havingValue = "true", matchIfMissing = false)
public class VectorProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VectorProcessingService.class);

    private final ZeroMqTemplate zeroMqTemplate;

    public VectorProcessingService(ZeroMqTemplate zeroMqTemplate) {
        this.zeroMqTemplate = zeroMqTemplate;
    }

    @PostConstruct
    private void init() {
        log.info("VectorProcessingService initializing - sending a small batch for demo");
        try {
            BatchVector batch = BatchVector.random(4, 8);
            zeroMqTemplate.publish("tcp://*:6020", "vector.batch", batch);
            log.info("Published demo BatchVector to tcp://*:6020 topic=vector.batch");
        } catch (Exception e) {
            log.error("VectorProcessingService demo publish failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Send a single vector for processing.
     */
    public void submitVector(DenseVector vector) {
        zeroMqTemplate.publish("tcp://*:6020", "vector.single", vector);
    }
} 