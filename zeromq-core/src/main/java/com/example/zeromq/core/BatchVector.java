package com.example.zeromq.core;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Batch vector implementation for processing multiple vectors efficiently.
 * 
 * <p>BatchVector enables efficient batch processing of vectors, which is essential
 * for machine learning inference, distributed computing, and high-throughput
 * data processing scenarios. It provides optimized operations for working with
 * collections of vectors while maintaining memory efficiency.
 * 
 * <p>This implementation is particularly useful for:
 * <ul>
 * <li>ML model batch inference</li>
 * <li>Parallel vector operations</li>
 * <li>Distributed computing workloads</li>
 * <li>High-throughput data pipelines</li>
 * </ul>
 * 
 * <p>This class is immutable and thread-safe.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public final class BatchVector implements Iterable<DenseVector> {

    private final DenseVector[] vectors;
    private final int batchSize;
    private final int vectorDimensions;

    /**
     * Create a new BatchVector from an array of vectors.
     * 
     * @param vectors array of vectors (must not be null or empty)
     * @throws IllegalArgumentException if vectors array is invalid or vectors have different dimensions
     */
    public BatchVector(DenseVector[] vectors) {
        Objects.requireNonNull(vectors, "Vectors array must not be null");
        if (vectors.length == 0) {
            throw new IllegalArgumentException("Vectors array must not be empty");
        }
        
        // Validate that all vectors have the same dimensions
        int firstDim = vectors[0].getDimensions();
        for (int i = 1; i < vectors.length; i++) {
            if (vectors[i].getDimensions() != firstDim) {
                throw new IllegalArgumentException(
                    String.format("All vectors must have the same dimensions. Vector 0 has %d, vector %d has %d",
                        firstDim, i, vectors[i].getDimensions()));
            }
        }
        
        this.vectors = Arrays.copyOf(vectors, vectors.length);
        this.batchSize = vectors.length;
        this.vectorDimensions = firstDim;
    }

    /**
     * Create a BatchVector from a collection of vectors.
     * 
     * @param vectors collection of vectors
     * @throws IllegalArgumentException if collection is null or empty
     */
    public BatchVector(Collection<DenseVector> vectors) {
        this(vectors.toArray(new DenseVector[0]));
    }

    /**
     * Create a BatchVector from a list of vectors.
     * 
     * @param vectors list of vectors
     * @return a new BatchVector
     */
    public static BatchVector of(DenseVector... vectors) {
        return new BatchVector(vectors);
    }

    /**
     * Create a BatchVector from a list of arrays.
     * 
     * @param vectorArrays arrays to convert to vectors
     * @return a new BatchVector
     */
    public static BatchVector fromArrays(float[]... vectorArrays) {
        DenseVector[] vectors = Arrays.stream(vectorArrays)
            .map(DenseVector::new)
            .toArray(DenseVector[]::new);
        return new BatchVector(vectors);
    }

    /**
     * Create a BatchVector with random vectors of specified dimensions.
     * 
     * @param batchSize number of vectors to generate
     * @param dimensions dimensions of each vector
     * @return a new BatchVector with random data
     */
    public static BatchVector random(int batchSize, int dimensions) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        
        DenseVector[] vectors = new DenseVector[batchSize];
        for (int i = 0; i < batchSize; i++) {
            vectors[i] = DenseVector.random(dimensions);
        }
        return new BatchVector(vectors);
    }

    /**
     * Create a BatchVector with zero vectors of specified dimensions.
     * 
     * @param batchSize number of vectors to generate
     * @param dimensions dimensions of each vector
     * @return a new BatchVector with zero data
     */
    public static BatchVector zeros(int batchSize, int dimensions) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        
        DenseVector[] vectors = new DenseVector[batchSize];
        for (int i = 0; i < batchSize; i++) {
            vectors[i] = DenseVector.zeros(dimensions);
        }
        return new BatchVector(vectors);
    }

    /**
     * Get the batch size (number of vectors).
     * 
     * @return the number of vectors in this batch
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Get the dimensions of each vector in the batch.
     * 
     * @return the vector dimensions
     */
    public int getVectorDimensions() {
        return vectorDimensions;
    }

    /**
     * Get all vectors in the batch.
     * 
     * @return a copy of the vectors array
     */
    public DenseVector[] getVectors() {
        return Arrays.copyOf(vectors, vectors.length);
    }

    /**
     * Get a specific vector from the batch.
     * 
     * @param index the index of the vector (0-based)
     * @return the vector at the specified index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public DenseVector getVector(int index) {
        if (index < 0 || index >= batchSize) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for batch size " + batchSize);
        }
        return vectors[index];
    }

    /**
     * Get a subset of vectors from the batch.
     * 
     * @param startIndex the start index (inclusive)
     * @param endIndex the end index (exclusive)
     * @return a new BatchVector with the subset of vectors
     * @throws IndexOutOfBoundsException if indices are invalid
     */
    public BatchVector getSubBatch(int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex > batchSize || startIndex >= endIndex) {
            throw new IndexOutOfBoundsException(
                String.format("Invalid range [%d, %d) for batch size %d", startIndex, endIndex, batchSize));
        }
        
        DenseVector[] subVectors = Arrays.copyOfRange(vectors, startIndex, endIndex);
        return new BatchVector(subVectors);
    }

    /**
     * Apply a function to each vector in the batch.
     * 
     * @param function the function to apply to each vector
     * @return a new BatchVector with transformed vectors
     */
    public BatchVector map(Function<DenseVector, DenseVector> function) {
        Objects.requireNonNull(function, "Function must not be null");
        
        DenseVector[] mappedVectors = Arrays.stream(vectors)
            .map(function)
            .toArray(DenseVector[]::new);
        
        return new BatchVector(mappedVectors);
    }

    /**
     * Apply a function to each vector with its index in the batch.
     * 
     * @param function the function to apply (index, vector) -> transformed vector
     * @return a new BatchVector with transformed vectors
     */
    public BatchVector mapWithIndex(java.util.function.BiFunction<Integer, DenseVector, DenseVector> function) {
        Objects.requireNonNull(function, "Function must not be null");
        
        DenseVector[] mappedVectors = IntStream.range(0, batchSize)
            .mapToObj(i -> function.apply(i, vectors[i]))
            .toArray(DenseVector[]::new);
        
        return new BatchVector(mappedVectors);
    }

    /**
     * Filter vectors in the batch based on a predicate.
     * 
     * @param predicate the predicate to test each vector
     * @return a new BatchVector with vectors that match the predicate
     */
    public BatchVector filter(java.util.function.Predicate<DenseVector> predicate) {
        Objects.requireNonNull(predicate, "Predicate must not be null");
        
        DenseVector[] filteredVectors = Arrays.stream(vectors)
            .filter(predicate)
            .toArray(DenseVector[]::new);
        
        if (filteredVectors.length == 0) {
            throw new IllegalStateException("Filter resulted in empty batch");
        }
        
        return new BatchVector(filteredVectors);
    }

    /**
     * Compute element-wise addition across all vectors in the batch.
     * 
     * @return a DenseVector containing the sum of all vectors
     */
    public DenseVector sum() {
        float[] result = new float[vectorDimensions];
        
        for (DenseVector vector : vectors) {
            float[] data = vector.getData();
            for (int i = 0; i < vectorDimensions; i++) {
                result[i] += data[i];
            }
        }
        
        return new DenseVector(result);
    }

    /**
     * Compute element-wise mean across all vectors in the batch.
     * 
     * @return a DenseVector containing the mean of all vectors
     */
    public DenseVector mean() {
        DenseVector sumVector = sum();
        return sumVector.multiply(1.0f / batchSize);
    }

    /**
     * Compute element-wise standard deviation across all vectors in the batch.
     * 
     * @return a DenseVector containing the standard deviation of all vectors
     */
    public DenseVector standardDeviation() {
        DenseVector meanVector = mean();
        float[] meanData = meanVector.getData();
        float[] varianceSum = new float[vectorDimensions];
        
        // Compute sum of squared differences
        for (DenseVector vector : vectors) {
            float[] data = vector.getData();
            for (int i = 0; i < vectorDimensions; i++) {
                float diff = data[i] - meanData[i];
                varianceSum[i] += diff * diff;
            }
        }
        
        // Compute standard deviation
        float[] stdDev = new float[vectorDimensions];
        for (int i = 0; i < vectorDimensions; i++) {
            stdDev[i] = (float) Math.sqrt(varianceSum[i] / batchSize);
        }
        
        return new DenseVector(stdDev);
    }

    /**
     * Normalize all vectors in the batch to have the same scale.
     * 
     * <p>Each vector is normalized by its L2 norm.
     * 
     * @return a new BatchVector with normalized vectors
     */
    public BatchVector normalize() {
        return map(vector -> {
            double norm = vector.norm();
            return norm > 0 ? vector.multiply((float) (1.0 / norm)) : vector;
        });
    }

    /**
     * Compute pairwise dot products between all vectors in the batch.
     * 
     * @return a 2D array where result[i][j] is the dot product of vector i and vector j
     */
    public float[][] pairwiseDotProducts() {
        float[][] results = new float[batchSize][batchSize];
        
        for (int i = 0; i < batchSize; i++) {
            for (int j = 0; j < batchSize; j++) {
                results[i][j] = vectors[i].dotProduct(vectors[j]);
            }
        }
        
        return results;
    }

    /**
     * Compute cosine similarities between all pairs of vectors.
     * 
     * @return a 2D array where result[i][j] is the cosine similarity of vector i and vector j
     */
    public double[][] pairwiseCosineSimilarities() {
        double[][] results = new double[batchSize][batchSize];
        
        for (int i = 0; i < batchSize; i++) {
            for (int j = 0; j < batchSize; j++) {
                if (i == j) {
                    results[i][j] = 1.0; // Self-similarity
                } else {
                    results[i][j] = vectors[i].cosineSimilarity(vectors[j]);
                }
            }
        }
        
        return results;
    }

    /**
     * Split the batch into smaller batches of specified size.
     * 
     * @param subBatchSize the size of each sub-batch
     * @return a list of BatchVector instances
     */
    public List<BatchVector> split(int subBatchSize) {
        if (subBatchSize <= 0) {
            throw new IllegalArgumentException("Sub-batch size must be positive");
        }
        
        List<BatchVector> subBatches = new ArrayList<>();
        for (int i = 0; i < batchSize; i += subBatchSize) {
            int endIndex = Math.min(i + subBatchSize, batchSize);
            subBatches.add(getSubBatch(i, endIndex));
        }
        
        return subBatches;
    }

    /**
     * Combine this batch with another batch.
     * 
     * @param other the other batch to combine with
     * @return a new BatchVector containing vectors from both batches
     * @throws IllegalArgumentException if vector dimensions don't match
     */
    public BatchVector concat(BatchVector other) {
        Objects.requireNonNull(other, "Other batch must not be null");
        
        if (this.vectorDimensions != other.vectorDimensions) {
            throw new IllegalArgumentException(
                String.format("Vector dimensions must match: %d vs %d", 
                    this.vectorDimensions, other.vectorDimensions));
        }
        
        DenseVector[] combinedVectors = new DenseVector[this.batchSize + other.batchSize];
        System.arraycopy(this.vectors, 0, combinedVectors, 0, this.batchSize);
        System.arraycopy(other.vectors, 0, combinedVectors, this.batchSize, other.batchSize);
        return new BatchVector(combinedVectors);
    }

    /**
     * Shuffle the vectors in the batch randomly.
     * 
     * @return a new BatchVector with shuffled vectors
     */
    public BatchVector shuffle() {
        return shuffle(new Random());
    }

    /**
     * Shuffle the vectors in the batch using the specified random generator.
     * 
     * @param random the random generator to use
     * @return a new BatchVector with shuffled vectors
     */
    public BatchVector shuffle(Random random) {
        Objects.requireNonNull(random, "Random generator must not be null");
        
        List<DenseVector> vectorList = new ArrayList<>(Arrays.asList(vectors));
        Collections.shuffle(vectorList, random);
        
        return new BatchVector(vectorList.toArray(new DenseVector[0]));
    }

    /**
     * Convert the batch to a 2D array representation.
     * 
     * @return a 2D array where each row is a vector
     */
    public float[][] toArray() {
        float[][] result = new float[batchSize][];
        for (int i = 0; i < batchSize; i++) {
            result[i] = vectors[i].toArray();
        }
        return result;
    }

    /**
     * Get statistics about the batch.
     * 
     * @return a map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("batchSize", batchSize);
        stats.put("vectorDimensions", vectorDimensions);
        stats.put("totalElements", (long) batchSize * vectorDimensions);
        
        // Compute norms for each vector
        double[] norms = Arrays.stream(vectors)
            .mapToDouble(DenseVector::norm)
            .toArray();
        
        stats.put("meanNorm", Arrays.stream(norms).average().orElse(0.0));
        stats.put("minNorm", Arrays.stream(norms).min().orElse(0.0));
        stats.put("maxNorm", Arrays.stream(norms).max().orElse(0.0));
        
        return stats;
    }

    /**
     * Check if the batch is empty.
     * 
     * @return true if batch size is 0, false otherwise
     */
    public boolean isEmpty() {
        return batchSize == 0;
    }

    /**
     * Get a stream of vectors in the batch.
     * 
     * @return a stream of vectors
     */
    public Stream<DenseVector> stream() {
        return Arrays.stream(vectors);
    }

    /**
     * Get a parallel stream of vectors in the batch.
     * 
     * @return a parallel stream of vectors
     */
    public Stream<DenseVector> parallelStream() {
        return Arrays.stream(vectors).parallel();
    }

    @Override
    public Iterator<DenseVector> iterator() {
        return Arrays.asList(vectors).iterator();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BatchVector other = (BatchVector) obj;
        return Arrays.equals(vectors, other.vectors);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vectors);
    }

    @Override
    public String toString() {
        if (batchSize <= 3) {
            return String.format("BatchVector[size=%d, dims=%d, vectors=%s]", 
                batchSize, vectorDimensions, Arrays.toString(vectors));
        } else {
            return String.format("BatchVector[size=%d, dims=%d, samples=[%s, %s, ..., %s]]", 
                batchSize, vectorDimensions, vectors[0], vectors[1], vectors[batchSize - 1]);
        }
    }

    /**
     * Get a human-readable description of this batch.
     * 
     * @return a string description with key metrics
     */
    public String getDescription() {
        Map<String, Object> stats = getStatistics();
        return String.format("BatchVector[size=%d, dims=%d, meanNorm=%.4f, totalElements=%d]", 
            batchSize, vectorDimensions, stats.get("meanNorm"), stats.get("totalElements"));
    }
} 