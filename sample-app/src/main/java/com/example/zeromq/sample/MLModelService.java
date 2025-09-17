package com.example.zeromq.sample;

import com.example.zeromq.compute.ComputeEngine;
import com.example.zeromq.core.DenseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Small service that delegates neural network inference to the available ComputeEngine.
 * If no GPU/ML engine is available, it will return the input as a safe fallback.
 */
@Service
@ConditionalOnProperty(name = "sampleapp.ml.enabled", havingValue = "true", matchIfMissing = false)
public class MLModelService {

    private static final Logger log = LoggerFactory.getLogger(MLModelService.class);

    private final ComputeEngine computeEngine;

    public MLModelService(ComputeEngine computeEngine) {
        this.computeEngine = computeEngine;
    }

    /**
     * Run inference asynchronously and return a CompletableFuture of the result.
     */
    public CompletableFuture<DenseVector> inferAsync(DenseVector input, String modelPath) {
        try {
            return computeEngine.neuralNetworkInference(input, modelPath);
        } catch (Exception e) {
            log.error("Inference delegation failed: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(input);
        }
    }
} 