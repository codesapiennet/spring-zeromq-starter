package com.example.zeromq.core;

/**
 * Base interface for vector data structures used in machine learning and scientific computing.
 * 
 * <p>The Vector interface provides a common abstraction for different types of vectors
 * (dense, sparse, named, batch) that can be efficiently transmitted over ZeroMQ and
 * processed by compute engines.
 * 
 * <p>All vector implementations are expected to be immutable for thread safety,
 * though specific implementations may provide mutable operations that return
 * new vector instances.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public interface Vector {

    /**
     * Get the number of dimensions (length) of this vector.
     * 
     * @return the number of dimensions, always non-negative
     */
    int getDimensions();

    /**
     * Convert this vector to a dense float array representation.
     * 
     * <p>For sparse vectors, this will expand all zero values explicitly.
     * For dense vectors, this returns a copy of the internal data.
     * 
     * @return a dense float array representation of this vector
     */
    float[] toArray();

    /**
     * Calculate the Euclidean (L2) norm of this vector.
     * 
     * <p>The norm is calculated as sqrt(sum of squares of all elements).
     * 
     * @return the L2 norm of this vector, always non-negative
     */
    default double norm() {
        float[] data = toArray();
        double sum = 0.0;
        for (float value : data) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    /**
     * Calculate the Manhattan (L1) norm of this vector.
     * 
     * <p>The L1 norm is calculated as the sum of absolute values of all elements.
     * 
     * @return the L1 norm of this vector, always non-negative
     */
    default double normL1() {
        float[] data = toArray();
        double sum = 0.0;
        for (float value : data) {
            sum += Math.abs(value);
        }
        return sum;
    }

    /**
     * Calculate the dot product of this vector with another vector.
     * 
     * <p>Both vectors must have the same dimensions.
     * 
     * @param other the other vector to compute dot product with
     * @return the dot product result
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    default float dotProduct(Vector other) {
        if (this.getDimensions() != other.getDimensions()) {
            throw new IllegalArgumentException(
                String.format("Vector dimensions must match: %d vs %d", 
                    this.getDimensions(), other.getDimensions()));
        }
        
        float[] thisData = this.toArray();
        float[] otherData = other.toArray();
        
        float result = 0.0f;
        for (int i = 0; i < thisData.length; i++) {
            result += thisData[i] * otherData[i];
        }
        return result;
    }

    /**
     * Calculate the cosine similarity between this vector and another vector.
     * 
     * <p>Cosine similarity is calculated as: dot(a,b) / (norm(a) * norm(b))
     * 
     * @param other the other vector to compute cosine similarity with
     * @return the cosine similarity value between -1 and 1
     * @throws IllegalArgumentException if vectors have different dimensions
     * @throws ArithmeticException if either vector has zero norm
     */
    default double cosineSimilarity(Vector other) {
        double dotProduct = this.dotProduct(other);
        double thisNorm = this.norm();
        double otherNorm = other.norm();
        
        if (thisNorm == 0.0 || otherNorm == 0.0) {
            throw new ArithmeticException("Cannot compute cosine similarity with zero-norm vector");
        }
        
        return dotProduct / (thisNorm * otherNorm);
    }

    /**
     * Check if this vector contains only zero values.
     * 
     * @return true if all elements are zero, false otherwise
     */
    default boolean isZero() {
        return norm() == 0.0;
    }

    /**
     * Get a human-readable description of this vector for debugging.
     * 
     * @return a string representation of this vector
     */
    default String getDescription() {
        return String.format("%s[dims=%d, norm=%.4f]", 
            getClass().getSimpleName(), getDimensions(), norm());
    }
} 