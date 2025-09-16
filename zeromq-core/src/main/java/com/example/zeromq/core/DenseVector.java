package com.example.zeromq.core;

import java.util.Arrays;
import java.util.Objects;

/**
 * Dense vector implementation for machine learning and scientific computing.
 * 
 * <p>DenseVector stores all values explicitly in a float array, making it efficient
 * for vectors where most values are non-zero. It provides optimized operations
 * for common linear algebra operations.
 * 
 * <p>This class is immutable and thread-safe.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public final class DenseVector implements Vector {

    private final float[] data;

    /**
     * Create a new DenseVector from a float array.
     * 
     * <p>The input array is copied to ensure immutability.
     * 
     * @param data the vector data (must not be null)
     * @throws IllegalArgumentException if data is null or empty
     */
    public DenseVector(float[] data) {
        Objects.requireNonNull(data, "Vector data must not be null");
        if (data.length == 0) {
            throw new IllegalArgumentException("Vector data must not be empty");
        }
        this.data = Arrays.copyOf(data, data.length);
    }

    /**
     * Create a new DenseVector from a double array.
     * 
     * <p>Values are cast to float for internal storage.
     * 
     * @param data the vector data (must not be null)
     * @throws IllegalArgumentException if data is null or empty
     */
    public DenseVector(double[] data) {
        Objects.requireNonNull(data, "Vector data must not be null");
        if (data.length == 0) {
            throw new IllegalArgumentException("Vector data must not be empty");
        }
        this.data = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            this.data[i] = (float) data[i];
        }
    }

    /**
     * Create a zero vector with the specified dimensions.
     * 
     * @param dimensions the number of dimensions (must be positive)
     * @return a new zero vector
     * @throws IllegalArgumentException if dimensions is not positive
     */
    public static DenseVector zeros(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        return new DenseVector(new float[dimensions]);
    }

    /**
     * Create a ones vector with the specified dimensions.
     * 
     * @param dimensions the number of dimensions (must be positive)
     * @return a new ones vector
     * @throws IllegalArgumentException if dimensions is not positive
     */
    public static DenseVector ones(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        float[] data = new float[dimensions];
        Arrays.fill(data, 1.0f);
        return new DenseVector(data);
    }

    /**
     * Create a random vector with values between 0 and 1.
     * 
     * @param dimensions the number of dimensions (must be positive)
     * @return a new random vector
     * @throws IllegalArgumentException if dimensions is not positive
     */
    public static DenseVector random(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        float[] data = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            data[i] = (float) Math.random();
        }
        return new DenseVector(data);
    }

    @Override
    public int getDimensions() {
        return data.length;
    }

    @Override
    public float[] toArray() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Get the internal data array for efficient access.
     * 
     * <p><strong>Warning:</strong> This returns a reference to the internal array.
     * Modifying the returned array will break immutability. Use only for
     * read-only operations where performance is critical.
     * 
     * @return the internal data array
     */
    public float[] getData() {
        return data;
    }

    /**
     * Get the value at a specific index.
     * 
     * @param index the index (must be >= 0 and < dimensions)
     * @return the value at the given index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public float get(int index) {
        return data[index];
    }

    /**
     * Create a new DenseVector with a value set at a specific index.
     * 
     * @param index the index to set
     * @param value the new value
     * @return a new DenseVector with the updated value
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public DenseVector set(int index, float value) {
        float[] newData = Arrays.copyOf(data, data.length);
        newData[index] = value;
        return new DenseVector(newData);
    }

    /**
     * Add another vector to this vector element-wise.
     * 
     * @param other the vector to add (must have same dimensions)
     * @return a new DenseVector containing the sum
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public DenseVector add(DenseVector other) {
        if (this.getDimensions() != other.getDimensions()) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        float[] result = new float[data.length];
        float[] otherData = other.data;
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i] + otherData[i];
        }
        return new DenseVector(result);
    }

    /**
     * Subtract another vector from this vector element-wise.
     * 
     * @param other the vector to subtract (must have same dimensions)
     * @return a new DenseVector containing the difference
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public DenseVector subtract(DenseVector other) {
        if (this.getDimensions() != other.getDimensions()) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        float[] result = new float[data.length];
        float[] otherData = other.data;
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i] - otherData[i];
        }
        return new DenseVector(result);
    }

    /**
     * Multiply this vector by a scalar.
     * 
     * @param scalar the scalar value to multiply by
     * @return a new DenseVector containing the scaled values
     */
    public DenseVector multiply(float scalar) {
        float[] result = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i] * scalar;
        }
        return new DenseVector(result);
    }

    /**
     * Perform element-wise multiplication with another vector.
     * 
     * @param other the vector to multiply with (must have same dimensions)
     * @return a new DenseVector containing the element-wise product
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public DenseVector hadamard(DenseVector other) {
        if (this.getDimensions() != other.getDimensions()) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        float[] result = new float[data.length];
        float[] otherData = other.data;
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i] * otherData[i];
        }
        return new DenseVector(result);
    }

    /**
     * Normalize this vector to unit length (L2 norm = 1).
     * 
     * @return a new normalized DenseVector
     * @throws ArithmeticException if this vector has zero norm
     */
    public DenseVector normalize() {
        double norm = norm();
        if (norm == 0.0) {
            throw new ArithmeticException("Cannot normalize zero vector");
        }
        return multiply((float) (1.0 / norm));
    }

    /**
     * Optimized dot product calculation for DenseVector.
     * 
     * @param other the other DenseVector
     * @return the dot product
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public float dotProduct(DenseVector other) {
        if (this.getDimensions() != other.getDimensions()) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        float result = 0.0f;
        float[] otherData = other.data;
        for (int i = 0; i < data.length; i++) {
            result += data[i] * otherData[i];
        }
        return result;
    }

    @Override
    public double norm() {
        double sum = 0.0;
        for (float value : data) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DenseVector other = (DenseVector) obj;
        return Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        if (data.length <= 10) {
            return "DenseVector" + Arrays.toString(data);
        } else {
            return String.format("DenseVector[%d elements: [%.3f, %.3f, ..., %.3f, %.3f]]", 
                data.length, data[0], data[1], data[data.length-2], data[data.length-1]);
        }
    }
} 