package com.example.zeromq.compute;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.core.DenseVector;
import com.example.zeromq.core.BatchVector;
import com.example.zeromq.core.JacksonMessageConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to distribute compute tasks to worker pools using ZeroMQ messaging.
 */
@Component
public class DistributedComputeService {

    private static final Logger log = LoggerFactory.getLogger(DistributedComputeService.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final Map<String, CompletableFuture<Object>> pendingTasks = new ConcurrentHashMap<>();
    private final JacksonMessageConverter jsonConverter = new JacksonMessageConverter();

    public DistributedComputeService(ZeroMqTemplate zeroMqTemplate) {
        this.zeroMqTemplate = zeroMqTemplate;
    }

    @PostConstruct
    private void setupResultHandlers() {
        // Subscribe to compute results published by workers
        zeroMqTemplate.subscribe("tcp://localhost:5590", "compute.result", ComputeResult.class, result -> {
            try {
                CompletableFuture<Object> future = pendingTasks.remove(result.getTaskId());
                if (future != null) {
                    if (result.isSuccess()) {
                        future.complete(result.getData());
                    } else {
                        future.completeExceptionally(new RuntimeException(result.getError()));
                    }
                } else {
                    log.warn("No pending task found for result taskId={}", result.getTaskId());
                }
            } catch (Exception e) {
                log.error("Failed to process compute result: {}", e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<DenseVector> distributeMatrixMultiplication(float[][] matrix, DenseVector vector) {
        String taskId = UUID.randomUUID().toString();
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        pendingTasks.put(taskId, resultFuture);

        ComputeTask task = ComputeTask.builder()
                .taskId(taskId)
                .operation("matrix_vector_multiply")
                .matrix(matrix)
                .vector(vector)
                .preferredBackend(ComputeEngine.ComputeBackend.GPU_CUDA)
                .build();

        try {
            byte[] payload = jsonConverter.toBytes(task);
            zeroMqTemplate.push("tcp://*:5580", task);
        } catch (Exception e) {
            pendingTasks.remove(taskId);
            resultFuture.completeExceptionally(e);
        }

        return resultFuture.thenApply(r -> (DenseVector) r);
    }

    public CompletableFuture<BatchVector> distributeMLInference(BatchVector inputs, String modelPath) {
        String taskId = UUID.randomUUID().toString();
        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        pendingTasks.put(taskId, resultFuture);

        ComputeTask task = ComputeTask.builder()
                .taskId(taskId)
                .operation("ml_inference")
                .batchInputs(inputs)
                .modelPath(modelPath)
                .requiresGpu(true)
                .build();

        try {
            zeroMqTemplate.push("tcp://*:5581", task);
        } catch (Exception e) {
            pendingTasks.remove(taskId);
            resultFuture.completeExceptionally(e);
        }

        return resultFuture.thenApply(r -> (BatchVector) r);
    }

} 