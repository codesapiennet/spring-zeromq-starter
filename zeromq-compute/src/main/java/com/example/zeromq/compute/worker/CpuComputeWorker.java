package com.example.zeromq.compute.worker;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.compute.ComputeResult;
import com.example.zeromq.compute.ComputeTask;
import com.example.zeromq.compute.cpu.OptimizedCpuComputeEngine;
import com.example.zeromq.core.DenseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.concurrent.TimeUnit;

/**
 * CPU compute worker that listens for compute tasks and executes them using the
 * optimized CPU engine. This worker is enabled by default for local validation.
 */
@Component
@ConditionalOnProperty(name = "spring.zeromq.compute.cpu.enabled", havingValue = "true", matchIfMissing = true)
public class CpuComputeWorker implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CpuComputeWorker.class);

    private final ZeroMqTemplate zeroMqTemplate;
    private final OptimizedCpuComputeEngine computeEngine;
    private final WorkerManager workerManager;

    private volatile String pullerMatrixId;
    private volatile String pullerMlId;
    private final String workerId = "cpu-worker-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    public CpuComputeWorker(ZeroMqTemplate zeroMqTemplate, OptimizedCpuComputeEngine computeEngine, WorkerManager workerManager) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.computeEngine = computeEngine;
        this.workerManager = workerManager;
    }

    @PostConstruct
    private void registerWithManager() {
        try {
            workerManager.registerWorker(workerId, this::startWorker, this::stopWorker, Runtime.getRuntime().availableProcessors());
        } catch (Exception e) {
            log.warn("Failed to register CPU worker with WorkerManager: {}", e.getMessage());
        }
    }

    @Override
    public void run(String... args) {
        log.info("Starting CPU compute worker using {}", computeEngine.getClass().getSimpleName());

        startWorker();

        try {
            while (true) TimeUnit.SECONDS.sleep(60);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("CPU compute worker interrupted, shutting down");
            stopWorker();
        }
    }

    /**
     * Start subscriptions for this worker. Safe to call multiple times.
     */
    public synchronized void startWorker() {
        if (pullerMatrixId == null) {
            pullerMatrixId = zeroMqTemplate.pull("tcp://localhost:5580", ComputeTask.class, task -> {
                try {
                    processComputeTask(task);
                } catch (Exception e) {
                    log.error("Failed to handle compute task: {}", e.getMessage(), e);
                }
            });
        }
        if (pullerMlId == null) {
            pullerMlId = zeroMqTemplate.pull("tcp://localhost:5581", ComputeTask.class, task -> {
                try {
                    processMLInferenceTask(task);
                } catch (Exception e) {
                    log.error("Failed to handle ML task: {}", e.getMessage(), e);
                }
            });
        }
        log.info("CPU worker {} subscriptions started (matrixPull={}, mlPull={})", workerId, pullerMatrixId, pullerMlId);
    }

    /**
     * Stop subscriptions for this worker.
     */
    public synchronized void stopWorker() {
        if (pullerMatrixId != null) {
            try {
                zeroMqTemplate.stopPull(pullerMatrixId);
            } catch (Exception e) {
                log.warn("Failed to stop matrix pull {}: {}", pullerMatrixId, e.getMessage());
            } finally {
                pullerMatrixId = null;
            }
        }
        if (pullerMlId != null) {
            try {
                zeroMqTemplate.stopPull(pullerMlId);
            } catch (Exception e) {
                log.warn("Failed to stop ml pull {}: {}", pullerMlId, e.getMessage());
            } finally {
                pullerMlId = null;
            }
        }
        log.info("CPU worker {} subscriptions stopped", workerId);
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

            zeroMqTemplate.publish("tcp://*:5590", "compute.result", computeResult);
            log.info("Completed CPU task {} in {} ms", task.getTaskId(), TimeUnit.NANOSECONDS.toMillis(duration));
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

            // For CPU worker we don't have NN inference; echo input as fallback
            DenseVector output = task.getVector();

            long duration = System.nanoTime() - start;
            ComputeResult computeResult = ComputeResult.builder()
                    .taskId(task.getTaskId())
                    .success(true)
                    .data(output)
                    .executionTimeNanos(duration)
                    .deviceInfo(getDeviceInfo())
                    .build();

            zeroMqTemplate.publish("tcp://*:5590", "compute.result", computeResult);
            log.info("Completed CPU ML task {} in {} ms", task.getTaskId(), TimeUnit.NANOSECONDS.toMillis(duration));
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
            zeroMqTemplate.publish("tcp://*:5590", "compute.result", computeResult);
        } catch (Exception e) {
            log.error("Failed to publish error result for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    private String getDeviceInfo() {
        return computeEngine.getClass().getSimpleName();
    }
} 