package com.example.zeromq.core;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sparse vector implementation for high-dimensional data with many zero values.
 * 
 * <p>SparseVector stores only non-zero values with their indices, making it
 * memory-efficient for vectors where most elements are zero. This is particularly
 * useful for text processing, recommendation systems, and high-dimensional
 * machine learning features.
 * 
 * <p>This class is immutable and thread-safe.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public final class SparseVector implements Vector {

    private final int dimensions;
    private final Map<Integer, Float> indices;
    private final int nonZeroCount;

    /**
     * Create a new SparseVector with the specified dimensions and non-zero values.
     * 
     * @param dimensions the total number of dimensions (must be positive)
     * @param indices a map of index -> value for non-zero elements
     * @throws IllegalArgumentException if dimensions is not positive or indices are invalid
     */
    public SparseVector(int dimensions, Map<Integer, Float> indices) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        Objects.requireNonNull(indices, "Indices map must not be null");

        this.dimensions = dimensions;
        
        // Validate and filter indices
        Map<Integer, Float> filteredIndices = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : indices.entrySet()) {
            Integer index = entry.getKey();
            Float value = entry.getValue();
            
            if (index == null) {
                throw new IllegalArgumentException("Index cannot be null");
            }
            if (index < 0 || index >= dimensions) {
                throw new IllegalArgumentException(
                    String.format("Index %d is out of bounds [0, %d)", index, dimensions));
            }
            if (value == null) {
                throw new IllegalArgumentException("Value cannot be null");
            }
            
            // Only store truly non-zero values
            if (!Float.valueOf(0.0f).equals(value) && !Float.isNaN(value)) {
                filteredIndices.put(index, value);
            }
        }
        
        this.indices = Collections.unmodifiableMap(filteredIndices);
        this.nonZeroCount = this.indices.size();
    }

    /**
     * Create a SparseVector from arrays of indices and values.
     * 
     * @param dimensions the total number of dimensions
     * @param indices array of indices for non-zero values
     * @param values array of non-zero values
     * @throws IllegalArgumentException if arrays have different lengths or contain invalid data
     */
    public SparseVector(int dimensions, int[] indices, float[] values) {
        if (indices.length != values.length) {
            throw new IllegalArgumentException("Indices and values arrays must have the same length");
        }
        
        Map<Integer, Float> indexMap = new HashMap<>();
        for (int i = 0; i < indices.length; i++) {
            indexMap.put(indices[i], values[i]);
        }
        
        // Delegate to main constructor for validation
        SparseVector temp = new SparseVector(dimensions, indexMap);
        this.dimensions = temp.dimensions;
        this.indices = temp.indices;
        this.nonZeroCount = temp.nonZeroCount;
    }

    /**
     * Create a sparse vector from a dense array, storing only non-zero values.
     * 
     * @param denseData the dense array data
     * @param tolerance values with absolute value less than this are considered zero
     * @return a new SparseVector
     * @throws IllegalArgumentException if denseData is null or empty
     */
    public static SparseVector fromDense(float[] denseData, float tolerance) {
        Objects.requireNonNull(denseData, "Dense data must not be null");
        if (denseData.length == 0) {
            throw new IllegalArgumentException("Dense data must not be empty");
        }
        
        Map<Integer, Float> indices = new HashMap<>();
        for (int i = 0; i < denseData.length; i++) {
            if (Math.abs(denseData[i]) > tolerance) {
                indices.put(i, denseData[i]);
            }
        }
        
        return new SparseVector(denseData.length, indices);
    }

    /**
     * Create a sparse vector from a dense array with default tolerance.
     * 
     * @param denseData the dense array data
     * @return a new SparseVector
     */
    public static SparseVector fromDense(float[] denseData) {
        return fromDense(denseData, 1e-10f);
    }

    /**
     * Create a sparse vector from a DenseVector.
     * 
     * @param denseVector the dense vector to convert
     * @param tolerance values with absolute value less than this are considered zero
     * @return a new SparseVector
     */
    public static SparseVector fromDense(DenseVector denseVector, float tolerance) {
        return fromDense(denseVector.getData(), tolerance);
    }

    /**
     * Create a sparse vector from a DenseVector with default tolerance.
     * 
     * @param denseVector the dense vector to convert
     * @return a new SparseVector
     */
    public static SparseVector fromDense(DenseVector denseVector) {
        return fromDense(denseVector.getData());
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    @Override
    public float[] toArray() {
        float[] result = new float[dimensions];
        for (Map.Entry<Integer, Float> entry : indices.entrySet()) {
            result[entry.getKey()] = entry.getValue();
        }
        return result;
    }

    /**
     * Get the map of non-zero indices and their values.
     * 
     * @return an unmodifiable map of index -> value
     */
    public Map<Integer, Float> getIndices() {
        return indices;
    }

    /**
     * Get the number of non-zero elements.
     * 
     * @return the count of non-zero elements
     */
    public int getNonZeroCount() {
        return nonZeroCount;
    }

    /**
     * Get the sparsity ratio (fraction of zero elements).
     * 
     * @return sparsity ratio between 0 and 1
     */
    public double getSparsity() {
        return 1.0 - ((double) nonZeroCount / dimensions);
    }

    /**
     * Get the value at a specific index.
     * 
     * @param index the index to query
     * @return the value at the index (0.0 if not stored)
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public float get(int index) {
        if (index < 0 || index >= dimensions) {
            throw new IndexOutOfBoundsException(
                String.format("Index %d is out of bounds [0, %d)", index, dimensions));
        }
        return indices.getOrDefault(index, 0.0f);
    }

    /**
     * Create a new SparseVector with a value set at a specific index.
     * 
     * @param index the index to set
     * @param value the new value
     * @return a new SparseVector with the updated value
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public SparseVector set(int index, float value) {
        if (index < 0 || index >= dimensions) {
            throw new IndexOutOfBoundsException(
                String.format("Index %d is out of bounds [0, %d)", index, dimensions));
        }
        
        Map<Integer, Float> newIndices = new HashMap<>(this.indices);
        if (value == 0.0f || Float.isNaN(value)) {
            newIndices.remove(index);
        } else {
            newIndices.put(index, value);
        }
        
        return new SparseVector(dimensions, newIndices);
    }

    /**
     * Add another sparse vector to this vector element-wise.
     * 
     * @param other the vector to add (must have same dimensions)
     * @return a new SparseVector containing the sum
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public SparseVector add(SparseVector other) {
        if (this.dimensions != other.dimensions) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        Map<Integer, Float> result = new HashMap<>(this.indices);
        
        for (Map.Entry<Integer, Float> entry : other.indices.entrySet()) {
            int index = entry.getKey();
            float value = entry.getValue();
            float currentValue = result.getOrDefault(index, 0.0f);
            float newValue = currentValue + value;
            
            if (newValue == 0.0f) {
                result.remove(index);
            } else {
                result.put(index, newValue);
            }
        }
        
        return new SparseVector(dimensions, result);
    }

    /**
     * Subtract another sparse vector from this vector element-wise.
     * 
     * @param other the vector to subtract (must have same dimensions)
     * @return a new SparseVector containing the difference
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public SparseVector subtract(SparseVector other) {
        if (this.dimensions != other.dimensions) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        Map<Integer, Float> result = new HashMap<>(this.indices);
        
        for (Map.Entry<Integer, Float> entry : other.indices.entrySet()) {
            int index = entry.getKey();
            float value = entry.getValue();
            float currentValue = result.getOrDefault(index, 0.0f);
            float newValue = currentValue - value;
            
            if (newValue == 0.0f) {
                result.remove(index);
            } else {
                result.put(index, newValue);
            }
        }
        
        return new SparseVector(dimensions, result);
    }

    /**
     * Multiply this vector by a scalar.
     * 
     * @param scalar the scalar value to multiply by
     * @return a new SparseVector containing the scaled values
     */
    public SparseVector multiply(float scalar) {
        if (scalar == 0.0f) {
            return new SparseVector(dimensions, Collections.emptyMap());
        }
        
        Map<Integer, Float> result = new HashMap<>();
        for (Map.Entry<Integer, Float> entry : indices.entrySet()) {
            result.put(entry.getKey(), entry.getValue() * scalar);
        }
        
        return new SparseVector(dimensions, result);
    }

    /**
     * Optimized dot product calculation for SparseVector.
     * 
     * @param other the other SparseVector
     * @return the dot product
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public float dotProduct(SparseVector other) {
        if (this.dimensions != other.dimensions) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        // Iterate through the smaller vector for efficiency
        Map<Integer, Float> smaller, larger;
        if (this.nonZeroCount <= other.nonZeroCount) {
            smaller = this.indices;
            larger = other.indices;
        } else {
            smaller = other.indices;
            larger = this.indices;
        }
        
        float result = 0.0f;
        for (Map.Entry<Integer, Float> entry : smaller.entrySet()) {
            int index = entry.getKey();
            float value = entry.getValue();
            Float otherValue = larger.get(index);
            if (otherValue != null) {
                result += value * otherValue;
            }
        }
        
        return result;
    }

    @Override
    public double norm() {
        double sum = 0.0;
        for (Float value : indices.values()) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    @Override
    public double normL1() {
        double sum = 0.0;
        for (Float value : indices.values()) {
            sum += Math.abs(value);
        }
        return sum;
    }

    /**
     * Convert this sparse vector to a dense vector.
     * 
     * @return a new DenseVector with the same values
     */
    public DenseVector toDense() {
        return new DenseVector(toArray());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SparseVector other = (SparseVector) obj;
        return dimensions == other.dimensions && 
               Objects.equals(indices, other.indices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensions, indices);
    }

    @Override
    public String toString() {
        if (nonZeroCount <= 5) {
            String indexStr = indices.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> String.format("%d:%.3f", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
            return String.format("SparseVector[dims=%d, nnz=%d, values={%s}]", 
                dimensions, nonZeroCount, indexStr);
        } else {
            List<String> sortedEntries = indices.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(3)
                .map(entry -> String.format("%d:%.3f", entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
            
            return String.format("SparseVector[dims=%d, nnz=%d, sparsity=%.2f%%, sample={%s, ...}]", 
                dimensions, nonZeroCount, getSparsity() * 100, String.join(", ", sortedEntries));
        }
    }

    @Override
    public String getDescription() {
        return String.format("SparseVector[dims=%d, nnz=%d, sparsity=%.2f%%, norm=%.4f]", 
            dimensions, nonZeroCount, getSparsity() * 100, norm());
    }
} 