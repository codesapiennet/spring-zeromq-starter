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
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public VectorProcessingService(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    private void init() {
        log.info("VectorProcessingService initializing - sending a small batch for demo");
        try {
            BatchVector batch = BatchVector.random(4, 8);
            zeroMqTemplate.publish(properties.getNamed().getVectorPublish(), "vector.batch", batch);
            log.info("Published demo BatchVector to {} topic=vector.batch", properties.getNamed().getVectorPublish());
        } catch (Exception e) {
            log.error("VectorProcessingService demo publish failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Send a single vector for processing.
     */
    public void submitVector(DenseVector vector) {
        zeroMqTemplate.publish(properties.getNamed().getVectorPublish(), "vector.single", vector);
    }
} 