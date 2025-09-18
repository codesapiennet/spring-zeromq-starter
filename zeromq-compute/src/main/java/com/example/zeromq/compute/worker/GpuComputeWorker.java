package com.example.zeromq.compute.worker;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.compute.ComputeResult;
import com.example.zeromq.compute.ComputeTask;
import com.example.zeromq.compute.gpu.CudaComputeEngine;
import com.example.zeromq.core.DenseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.concurrent.TimeUnit;

/**
 * GPU compute worker that listens for compute tasks and executes them using the
 * configured compute engine (CUDA). Falls back to CPU when GPU is unavailable.
 */
@Component
@ConditionalOnProperty(name = "spring.zeromq.compute.gpu.enabled", havingValue = "true")
public class GpuComputeWorker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GpuComputeWorker.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final CudaComputeEngine computeEngine;
    private final WorkerManager workerManager;
    private final com.example.zeromq.autoconfig.ZeroMqProperties properties;

    private volatile String pullerMatrixId;
    private volatile String pullerMlId;
    private final String workerId = "gpu-worker-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    public GpuComputeWorker(ZeroMqTemplate zeroMqTemplate, CudaComputeEngine computeEngine, WorkerManager workerManager, com.example.zeromq.autoconfig.ZeroMqProperties properties) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.computeEngine = computeEngine;
        this.workerManager = workerManager;
        this.properties = properties;
    }

    @PostConstruct
    private void registerWithManager() {
        try {
            workerManager.registerWorker(workerId, this::startWorker, this::stopWorker, 1);
        } catch (Exception e) {
            log.warn("Failed to register GPU worker with WorkerManager: {}", e.getMessage());
        }
    }

    @Override
    public void run(String... args) {
        log.info("Starting GPU compute worker. GPU available: {}", computeEngine.isGpuAvailable());

        startWorker();

        // Keep the application running; sockets are managed by ZeroMqTemplate threads
        try {
            while (true) {
                TimeUnit.SECONDS.sleep(60);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("GPU compute worker interrupted, shutting down");
            stopWorker();
        }
    }

    public synchronized void startWorker() {
        if (pullerMatrixId == null) {
            pullerMatrixId = zeroMqTemplate.pull(properties.getNamed().getComputeMatrixPull(), ComputeTask.class, task -> {
                try {
                    processComputeTask(task);
                } catch (Exception e) {
                    log.error("Failed to handle compute task: {}", e.getMessage(), e);
                }
            });
        }
        if (pullerMlId == null) {
            pullerMlId = zeroMqTemplate.pull(properties.getNamed().getComputeMlPull(), ComputeTask.class, task -> {
                try {
                    processMLInferenceTask(task);
                } catch (Exception e) {
                    log.error("Failed to handle ML task: {}", e.getMessage(), e);
                }
            });
        }
        log.info("GPU worker {} subscriptions started (matrixPull={}, mlPull={})", workerId, pullerMatrixId, pullerMlId);
    }

    public synchronized void stopWorker() {
        if (pullerMatrixId != null) {
            try { zeroMqTemplate.stopPull(pullerMatrixId); } catch (Exception e) { log.warn("Failed to stop matrix pull {}: {}", pullerMatrixId, e.getMessage()); } finally { pullerMatrixId = null; }
        }
        if (pullerMlId != null) {
            try { zeroMqTemplate.stopPull(pullerMlId); } catch (Exception e) { log.warn("Failed to stop ml pull {}: {}", pullerMlId, e.getMessage()); } finally { pullerMlId = null; }
        }
        log.info("GPU worker {} subscriptions stopped", workerId);
    }

    private void processComputeTask(ComputeTask task) {
        long start = System.nanoTime();
        try {
            if (!"matrix_vector_multiply".equals(task.getOperation())) {
                log.warn("Unsupported compute operation: {}", task.getOperation());
                publishError(task.getTaskId(), "Unsupported operation: " + task.getOperation());
                return;
            }

            DenseVector result = computeEngine.matrixVectorMultiply(task.getMatrix(), task.getVector()).get();
            long duration = System.nanoTime() - start;

            ComputeResult computeResult = ComputeResult.builder()
                    .taskId(task.getTaskId())
                    .success(true)
                    .data(result)
                    .executionTimeNanos(duration)
                    .deviceInfo(getDeviceInfo())
                    .build();

            zeroMqTemplate.publish(properties.getNamed().getComputeResultPublish(), "compute.result", computeResult);
            log.info("Completed task {} in {} ms", task.getTaskId(), TimeUnit.NANOSECONDS.toMillis(duration));
        } catch (Exception e) {
            log.error("Failed to execute compute task {}: {}", task.getTaskId(), e.getMessage(), e);
            publishError(task.getTaskId(), e.getMessage());
        }
    }

    private void processMLInferenceTask(ComputeTask task) {
        long start = System.nanoTime();
        try {
            if (!"ml_inference".equals(task.getOperation())) {
                log.warn("Unsupported ML operation: {}", task.getOperation());
                publishError(task.getTaskId(), "Unsupported ML operation: " + task.getOperation());
                return;
            }

            // Currently CudaComputeEngine.neuralNetworkInference throws UnsupportedOperationException
            // We'll invoke it and capture the exception to return a failure result.
            DenseVector output = computeEngine.neuralNetworkInference(task.getVector(), task.getModelPath()).get();

            long duration = System.nanoTime() - start;
            ComputeResult computeResult = ComputeResult.builder()
                    .taskId(task.getTaskId())
                    .success(true)
                    .data(output)
                    .executionTimeNanos(duration)
                    .deviceInfo(getDeviceInfo())
                    .build();

            zeroMqTemplate.publish(properties.getNamed().getComputeResultPublish(), "compute.result", computeResult);
            log.info("Completed ML task {} in {} ms", task.getTaskId(), TimeUnit.NANOSECONDS.toMillis(duration));
        } catch (Exception e) {
            log.error("ML inference failed for task {}: {}", task.getTaskId(), e.getMessage());
            publishError(task.getTaskId(), e.getMessage());
        }
    }

    private void publishError(String taskId, String error) {
        ComputeResult computeResult = ComputeResult.builder()
                .taskId(taskId)
                .success(false)
                .error(error)
                .build();
        try {
            zeroMqTemplate.publish(properties.getNamed().getComputeResultPublish(), "compute.result", computeResult);
        } catch (Exception e) {
            log.error("Failed to publish error result for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    private String getDeviceInfo() {
        try {
            return String.format("%s[%s]", computeEngine.getClass().getSimpleName(),
                    (computeEngine instanceof CudaComputeEngine) ? ((CudaComputeEngine) computeEngine).getDeviceName() : "CPU-Fallback");
        } catch (Exception e) {
            return computeEngine.getClass().getSimpleName();
        }
    }
} 