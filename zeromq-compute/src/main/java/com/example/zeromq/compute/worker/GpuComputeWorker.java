package com.example.zeromq.compute.worker;

import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.compute.ComputeResult;
import com.example.zeromq.compute.ComputeTask;
import com.example.zeromq.compute.gpu.CudaComputeEngine;
import com.example.zeromq.core.JacksonMessageConverter;
import com.example.zeromq.core.DenseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
    private final JacksonMessageConverter jsonConverter = new JacksonMessageConverter();

    public GpuComputeWorker(ZeroMqTemplate zeroMqTemplate, CudaComputeEngine computeEngine) {
        this.zeroMqTemplate = zeroMqTemplate;
        this.computeEngine = computeEngine;
    }

    @Override
    public void run(String... args) {
        log.info("Starting GPU compute worker. GPU available: {}", computeEngine.isGpuAvailable());

        // Pull matrix-vector multiply tasks (task publisher binds to tcp://*:5580)
        zeroMqTemplate.pull("tcp://localhost:5580", rawTask -> {
            try {
                ComputeTask task = jsonConverter.fromBytes(rawTask, ComputeTask.class);
                processComputeTask(task);
            } catch (Exception e) {
                log.error("Failed to deserialize/handle compute task: {}", e.getMessage(), e);
            }
        });

        // Pull ML inference tasks (publisher may push MLInference tasks to tcp://*:5581)
        zeroMqTemplate.pull("tcp://localhost:5581", rawTask -> {
            try {
                // Try to parse as ComputeTask for ML inference as well (lightweight approach)
                ComputeTask task = jsonConverter.fromBytes(rawTask, ComputeTask.class);
                processMLInferenceTask(task);
            } catch (Exception e) {
                log.error("Failed to deserialize/handle ML task: {}", e.getMessage(), e);
            }
        });

        // Keep the application running; sockets are managed by ZeroMqTemplate threads
        try {
            while (true) {
                TimeUnit.SECONDS.sleep(60);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("GPU compute worker interrupted, shutting down");
        }
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

            zeroMqTemplate.publish("tcp://*:5590", "compute.result", computeResult);
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
            zeroMqTemplate.publish("tcp://*:5590", "compute.result", computeResult);
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