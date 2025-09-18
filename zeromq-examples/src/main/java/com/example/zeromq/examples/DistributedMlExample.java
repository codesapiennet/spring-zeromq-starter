package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.compute.MLInferenceTask;
import com.example.zeromq.compute.ComputeResult;
import com.example.zeromq.core.DenseVector;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Distributed ML example: submits an MLInferenceTask and waits for results on the compute result topic.
 */
@Component
public class DistributedMlExample {

    private static final Logger log = LoggerFactory.getLogger(DistributedMlExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public DistributedMlExample(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        String pushEndpoint = properties.getNamed().getComputeMlPush(); // tcp://*:5581
        String resultSubscribe = properties.getNamed().getComputeResultSubscribe(); // tcp://localhost:5590

        CountDownLatch latch = new CountDownLatch(1);

        zeroMqTemplate.subscribe(resultSubscribe, "compute.result", ComputeResult.class, result -> {
            try {
                if (result.isSuccess()) {
                    log.info("ML inference result for task {}: {}", result.getTaskId(), result.getData());
                } else {
                    log.warn("ML inference failed for task {}: {}", result.getTaskId(), result.getError());
                }
            } catch (Exception e) {
                log.error("Error processing compute result: {}", e.getMessage(), e);
            } finally {
                latch.countDown();
            }
        });

        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Create a dummy input vector
        DenseVector input = DenseVector.random(64);

        MLInferenceTask task = MLInferenceTask.builder()
                .taskId(java.util.UUID.randomUUID().toString())
                .input(input)
                .modelPath("models/example.onnx")
                .requiresGpu(false)
                .topK(3)
                .build();

        log.info("Submitting ML inference task {} to {}", task.getTaskId(), pushEndpoint);
        zeroMqTemplate.push(pushEndpoint, task);

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                log.warn("Did not receive ML inference result within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 