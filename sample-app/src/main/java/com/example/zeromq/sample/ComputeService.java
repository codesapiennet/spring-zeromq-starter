package com.example.zeromq.sample;

import com.example.zeromq.compute.ComputeTask;
import com.example.zeromq.autoconfig.ZeroMqTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service that submits compute tasks to the compute queue using ZeroMqTemplate.
 */
@Service
@ConditionalOnProperty(name = "sampleapp.compute.enabled", havingValue = "true", matchIfMissing = false)
public class ComputeService {

    private static final Logger log = LoggerFactory.getLogger(ComputeService.class);

    private final ZeroMqTemplate zeroMqTemplate;

    public ComputeService(ZeroMqTemplate zeroMqTemplate) {
        this.zeroMqTemplate = zeroMqTemplate;
    }

    /**
     * Submit a compute task to the compute queue. This method is fire-and-forget;
     * workers will pick up tasks from the configured endpoint.
     */
    public void submitComputeTask(ComputeTask task) {
        try {
            zeroMqTemplate.push("tcp://*:5580", task);
            log.info("Submitted compute task {} to tcp://*:5580", task.getTaskId());
        } catch (Exception e) {
            log.error("Failed to submit compute task {}: {}", task == null ? "<null>" : task.getTaskId(), e.getMessage(), e);
        }
    }
} 