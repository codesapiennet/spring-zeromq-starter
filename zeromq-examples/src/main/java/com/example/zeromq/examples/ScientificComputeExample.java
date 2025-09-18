package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.compute.science.ScientificTask;
import com.example.zeromq.compute.ComputeResult;
import com.example.zeromq.core.BatchVector;
import com.example.zeromq.core.DenseVector;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Scientific computing example: submits a ScientificTask and waits for compute.result.
 */
@Component
public class ScientificComputeExample {

    private static final Logger log = LoggerFactory.getLogger(ScientificComputeExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public ScientificComputeExample(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        String pushEndpoint = properties.getNamed().getComputeMatrixPush();
        String resultSubscribe = properties.getNamed().getComputeResultSubscribe();

        CountDownLatch latch = new CountDownLatch(1);

        zeroMqTemplate.subscribe(resultSubscribe, "compute.result", ComputeResult.class, result -> {
            try {
                if (result.isSuccess()) {
                    log.info("Scientific task {} completed: {}", result.getTaskId(), result.getData());
                } else {
                    log.warn("Scientific task {} failed: {}", result.getTaskId(), result.getError());
                }
            } catch (Exception e) {
                log.error("Error handling compute result: {}", e.getMessage(), e);
            } finally {
                latch.countDown();
            }
        });

        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        BatchVector batch = BatchVector.random(8, 32);
        ScientificTask task = ScientificTask.builder()
                .taskId(java.util.UUID.randomUUID().toString())
                .taskType("fft")
                .batchInput(batch)
                .parameters(Map.of("precision", "single"))
                .build();

        log.info("Submitting scientific task {} to {}", task.getTaskId(), pushEndpoint);
        zeroMqTemplate.push(pushEndpoint, task);

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                log.warn("Did not receive scientific compute result within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 