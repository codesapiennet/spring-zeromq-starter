package com.example.zeromq.compute;

/**
 * Represents a compute kernel that can process raw float arrays.
 */
@FunctionalInterface
public interface ComputeKernel {

    /**
     * Execute kernel over flattened batch data.
     *
     * @param flatInput flattened input array (batchSize * vectorSize)
     * @param batchSize number of vectors in batch
     * @param vectorSize dimension of each vector
     * @return flattened output array of same length
     */
    float[] execute(float[] flatInput, int batchSize, int vectorSize);
} 