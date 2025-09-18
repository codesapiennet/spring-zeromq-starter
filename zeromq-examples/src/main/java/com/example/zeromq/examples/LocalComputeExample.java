package com.example.zeromq.examples;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.compute.ComputeTask;
import com.example.zeromq.core.DenseVector;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Local example that publishes a matrix-vector multiply task and waits for a result.
 * Placed in the `zeromq-examples` module per project structure in TODO.md.
 */
@Component
public class LocalComputeExample {

    private static final Logger log = LoggerFactory.getLogger(LocalComputeExample.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    public LocalComputeExample(ZeroMqTemplate zeroMqTemplate, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void runExample() {
        // Simple 3x3 matrix and vector
        float[][] matrix = new float[][]{
            {1f, 2f, 3f},
            {4f, 5f, 6f},
            {7f, 8f, 9f}
        };
        DenseVector vector = new DenseVector(new float[]{1f, 1f, 1f});

        String taskId = java.util.UUID.randomUUID().toString();
        ComputeTask task = ComputeTask.builder()
                .taskId(taskId)
                .operation("matrix_vector_multiply")
                .matrix(matrix)
                .vector(vector)
                .preferredBackend(com.example.zeromq.compute.ComputeEngine.ComputeBackend.CPU_MULTI_THREAD)
                .build();

        CountDownLatch latch = new CountDownLatch(1);

        zeroMqTemplate.subscribe(properties.getNamed().getComputeResultSubscribe(), "compute.result", com.example.zeromq.compute.ComputeResult.class, result -> {
            try {
                if (taskId.equals(result.getTaskId())) {
                    log.info("Received compute result for task {}: success={}, data={}", taskId, result.isSuccess(), result.getData());
                    latch.countDown();
                }
            } catch (Exception e) {
                log.error("Failed to handle compute result: {}", e.getMessage(), e);
            }
        });

        log.info("Publishing local compute task {}", taskId);
        zeroMqTemplate.push(properties.getNamed().getComputeMatrixPush(), task);

        try {
            boolean ok = latch.await(5, TimeUnit.SECONDS);
            if (!ok) log.warn("Did not receive compute result within timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 