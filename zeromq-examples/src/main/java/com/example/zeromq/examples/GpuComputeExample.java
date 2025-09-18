package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.compute.ComputeTask;
import com.example.zeromq.compute.ComputeResult;
import com.example.zeromq.compute.ComputeEngine;
import com.example.zeromq.core.DenseVector;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * GPU compute example: creates a small matrix-vector multiplication task and submits it to the worker queue.
 */
@Component
public class GpuComputeExample {

    private static final Logger log = LoggerFactory.getLogger(GpuComputeExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public GpuComputeExample(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        String pushEndpoint = properties.getNamed().getComputeMatrixPush(); // tcp://*:5580
        String resultSubscribe = properties.getNamed().getComputeResultSubscribe(); // tcp://localhost:5590

        CountDownLatch latch = new CountDownLatch(1);

        // Listen for compute results
        zeroMqTemplate.subscribe(resultSubscribe, "compute.result", ComputeResult.class, result -> {
            try {
                if (result.isSuccess()) {
                    log.info("Received compute result for task {}: {}", result.getTaskId(), result.getData());
                } else {
                    log.warn("Compute task {} failed: {}", result.getTaskId(), result.getError());
                }
            } catch (Exception e) {
                log.error("Error handling compute result: {}", e.getMessage(), e);
            } finally {
                latch.countDown();
            }
        });

        // Small pause to ensure subscriber active
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Prepare a tiny matrix and vector
        float[][] matrix = new float[][] { {1f, 2f}, {3f, 4f} };
        DenseVector vector = new DenseVector(new float[] { 1f, 1f });

        ComputeTask task = ComputeTask.builder()
                .taskId(java.util.UUID.randomUUID().toString())
                .operation("matrix_vector_multiply")
                .matrix(matrix)
                .vector(vector)
                .preferredBackend(ComputeEngine.ComputeBackend.GPU_CUDA)
                .requiresGpu(true)
                .build();

        log.info("Submitting compute task {} to {}", task.getTaskId(), pushEndpoint);
        zeroMqTemplate.push(pushEndpoint, task);

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                log.warn("Did not receive compute result within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 