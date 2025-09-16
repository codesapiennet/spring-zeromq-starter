package com.example.zeromq.autoconfig;

import com.example.zeromq.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * High-performance vector processing service for ZeroMQ applications.
 * 
 * <p>This service provides optimized operations for vector data types including:
 * <ul>
 * <li>Parallel batch processing and transformations</li>
 * <li>Vector similarity computations and clustering</li>
 * <li>Memory-efficient sparse vector operations</li>
 * <li>Feature extraction and dimensionality reduction</li>
 * <li>Statistical analysis and aggregations</li>
 * <li>ML-ready data preprocessing pipelines</li>
 * </ul>
 * 
 * <p>The service is designed for high-throughput scenarios where vector
 * operations need to be performed efficiently across large datasets.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public class VectorProcessingService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(VectorProcessingService.class);
    
    private final ZeroMqProperties.Vector vectorConfig;
    private final ExecutorService processingExecutor;
    private final ForkJoinPool parallelProcessor;
    
    // Processing statistics
    private final AtomicLong vectorsProcessed = new AtomicLong(0);
    private final AtomicLong batchesProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    
    // Vector pool for memory efficiency
    private final ConcurrentLinkedQueue<float[]> vectorPool = new ConcurrentLinkedQueue<>();
    private final int maxPoolSize;

    /**
     * Create a new vector processing service.
     * 
     * @param vectorConfig the vector processing configuration
     */
    public VectorProcessingService(ZeroMqProperties.Vector vectorConfig) {
        this.vectorConfig = Objects.requireNonNull(vectorConfig, "Vector config must not be null");
        this.maxPoolSize = vectorConfig.getMemory().getPoolSize();
        
        // Create processing executor
        int processorCount = Runtime.getRuntime().availableProcessors();
        this.processingExecutor = new ThreadPoolExecutor(
            processorCount,
            processorCount * 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "vector-processing");
                t.setDaemon(true);
                return t;
            }
        );
        
        // Create parallel processor for compute-intensive operations
        this.parallelProcessor = new ForkJoinPool(
            processorCount,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
        );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (vectorConfig.isEnabled()) {
            log.info("Vector processing service initialized: poolSize={}, maxDimensions={}", 
                    maxPoolSize, vectorConfig.getMemory().getMaxDimensions());
        } else {
            log.info("Vector processing service disabled");
        }
    }

    // ========== Batch Processing ==========

    /**
     * Process a batch of vectors in parallel.
     * 
     * @param batch the batch of vectors to process
     * @param processor the processing function to apply
     * @param <R> the result type
     * @return CompletableFuture containing the processed results
     */
    public <R> CompletableFuture<List<R>> processBatchAsync(BatchVector batch, 
                                                           Function<DenseVector, R> processor) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                List<R> results = batch.parallelStream()
                    .map(processor)
                    .collect(Collectors.toList());
                
                recordBatchProcessing(batch.getBatchSize(), startTime);
                
                log.debug("Processed batch of {} vectors in {}ms", 
                         batch.getBatchSize(), System.currentTimeMillis() - startTime);
                
                return results;
                
            } catch (Exception e) {
                log.error("Batch processing failed: {}", e.getMessage());
                throw new RuntimeException("Vector batch processing failed", e);
            }
        }, processingExecutor);
    }

    /**
     * Transform a batch of vectors using parallel processing.
     * 
     * @param batch the input batch
     * @param transformer the transformation function
     * @return a new transformed batch
     */
    public BatchVector transformBatch(BatchVector batch, Function<DenseVector, DenseVector> transformer) {
        long startTime = System.currentTimeMillis();
        
        try {
            DenseVector[] transformedVectors = batch.parallelStream()
                .map(transformer)
                .toArray(DenseVector[]::new);
            
            recordBatchProcessing(batch.getBatchSize(), startTime);
            
            return new BatchVector(transformedVectors);
            
        } catch (Exception e) {
            log.error("Batch transformation failed: {}", e.getMessage());
            throw new RuntimeException("Vector batch transformation failed", e);
        }
    }

    /**
     * Split a large batch into smaller chunks for processing.
     * 
     * @param batch the batch to split
     * @param chunkSize the size of each chunk
     * @return list of batch chunks
     */
    public List<BatchVector> splitBatch(BatchVector batch, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        
        return batch.split(chunkSize);
    }

    // ========== Similarity and Distance Computations ==========

    /**
     * Compute pairwise cosine similarities for a batch of vectors.
     * 
     * @param batch the batch of vectors
     * @return CompletableFuture containing the similarity matrix
     */
    public CompletableFuture<double[][]> computeCosineSimilarityMatrixAsync(BatchVector batch) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                double[][] similarities = batch.pairwiseCosineSimilarities();
                
                recordBatchProcessing(batch.getBatchSize(), startTime);
                
                log.debug("Computed cosine similarity matrix ({}x{}) in {}ms", 
                         similarities.length, similarities[0].length, 
                         System.currentTimeMillis() - startTime);
                
                return similarities;
                
            } catch (Exception e) {
                log.error("Cosine similarity computation failed: {}", e.getMessage());
                throw new RuntimeException("Cosine similarity computation failed", e);
            }
        }, parallelProcessor);
    }

    /**
     * Find the k most similar vectors to a query vector.
     * 
     * @param queryVector the query vector
     * @param candidates the candidate vectors to search
     * @param k the number of similar vectors to return
     * @return list of the k most similar vectors with their similarity scores
     */
    public List<SimilarityResult> findMostSimilar(DenseVector queryVector, 
                                                 BatchVector candidates, int k) {
        if (k <= 0 || k > candidates.getBatchSize()) {
            throw new IllegalArgumentException("Invalid k value: " + k);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Compute similarities in parallel
            List<SimilarityResult> results = IntStream.range(0, candidates.getBatchSize())
                .parallel()
                .mapToObj(i -> {
                    DenseVector candidate = candidates.getVector(i);
                    double similarity = queryVector.cosineSimilarity(candidate);
                    return new SimilarityResult(candidate, similarity, i);
                })
                .sorted((a, b) -> Double.compare(b.similarity, a.similarity)) // Descending order
                .limit(k)
                .collect(Collectors.toList());
            
            recordVectorProcessing(candidates.getBatchSize(), startTime);
            
            log.debug("Found {} most similar vectors from {} candidates in {}ms", 
                     k, candidates.getBatchSize(), System.currentTimeMillis() - startTime);
            
            return results;
            
        } catch (Exception e) {
            log.error("Similarity search failed: {}", e.getMessage());
            throw new RuntimeException("Vector similarity search failed", e);
        }
    }

    // ========== Statistical Operations ==========

    /**
     * Compute statistical summary for a batch of vectors.
     * 
     * @param batch the batch to analyze
     * @return statistical summary
     */
    public VectorStatistics computeStatistics(BatchVector batch) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Compute in parallel using ForkJoin
            CompletableFuture<DenseVector> meanFuture = CompletableFuture.supplyAsync(batch::mean, parallelProcessor);
            CompletableFuture<DenseVector> stdDevFuture = CompletableFuture.supplyAsync(batch::standardDeviation, parallelProcessor);
            CompletableFuture<Map<String, Object>> basicStatsFuture = CompletableFuture.supplyAsync(batch::getStatistics, parallelProcessor);
            
            // Wait for all computations
            DenseVector mean = meanFuture.join();
            DenseVector stdDev = stdDevFuture.join();
            Map<String, Object> basicStats = basicStatsFuture.join();
            
            recordBatchProcessing(batch.getBatchSize(), startTime);
            
            return new VectorStatistics(mean, stdDev, basicStats);
            
        } catch (Exception e) {
            log.error("Statistics computation failed: {}", e.getMessage());
            throw new RuntimeException("Vector statistics computation failed", e);
        }
    }

    /**
     * Normalize a batch of vectors using z-score normalization.
     * 
     * @param batch the batch to normalize
     * @return normalized batch
     */
    public BatchVector normalizeZScore(BatchVector batch) {
        long startTime = System.currentTimeMillis();
        
        try {
            DenseVector mean = batch.mean();
            DenseVector stdDev = batch.standardDeviation();
            
            BatchVector normalized = batch.map(vector -> {
                float[] data = vector.getData();
                float[] meanData = mean.getData();
                float[] stdData = stdDev.getData();
                
                float[] normalizedData = new float[data.length];
                for (int i = 0; i < data.length; i++) {
                    normalizedData[i] = stdData[i] > 0 ? (data[i] - meanData[i]) / stdData[i] : 0;
                }
                
                return new DenseVector(normalizedData);
            });
            
            recordBatchProcessing(batch.getBatchSize(), startTime);
            
            log.debug("Normalized batch of {} vectors using z-score in {}ms", 
                     batch.getBatchSize(), System.currentTimeMillis() - startTime);
            
            return normalized;
            
        } catch (Exception e) {
            log.error("Z-score normalization failed: {}", e.getMessage());
            throw new RuntimeException("Vector normalization failed", e);
        }
    }

    // ========== Feature Processing ==========

    /**
     * Extract top features from named vectors based on importance scores.
     * 
     * @param namedVector the named vector to analyze
     * @param topK the number of top features to extract
     * @return map of top features with their importance scores
     */
    public Map<String, Float> extractTopFeatures(NamedVector namedVector, int topK) {
        return namedVector.getTopFeatures(topK);
    }

    /**
     * Apply dimensionality reduction using feature selection.
     * 
     * @param namedVector the input named vector
     * @param selectedFeatures the features to keep
     * @return reduced dimensionality vector
     */
    public NamedVector selectFeatures(NamedVector namedVector, String... selectedFeatures) {
        return namedVector.selectFeatures(selectedFeatures);
    }

    /**
     * Convert sparse vectors to dense format for batch processing.
     * 
     * @param sparseVectors the sparse vectors to convert
     * @param dimensions the target dimensions
     * @return batch of dense vectors
     */
    public BatchVector sparseToDenseBatch(List<SparseVector> sparseVectors, int dimensions) {
        long startTime = System.currentTimeMillis();
        
        try {
            DenseVector[] denseVectors = sparseVectors.parallelStream()
                .map(sparse -> sparse.toDenseVector(dimensions))
                .toArray(DenseVector[]::new);
            
            recordVectorProcessing(sparseVectors.size(), startTime);
            
            return new BatchVector(denseVectors);
            
        } catch (Exception e) {
            log.error("Sparse to dense conversion failed: {}", e.getMessage());
            throw new RuntimeException("Vector conversion failed", e);
        }
    }

    // ========== Memory Management ==========

    /**
     * Get a pooled float array for vector operations.
     * 
     * @param size the required size
     * @return a float array (may be reused from pool)
     */
    public float[] getPooledArray(int size) {
        if (size <= 0 || size > vectorConfig.getMemory().getMaxDimensions()) {
            throw new IllegalArgumentException("Invalid array size: " + size);
        }
        
        float[] array = vectorPool.poll();
        if (array == null || array.length < size) {
            array = new float[size];
        } else if (array.length > size) {
            // Clear only the portion we'll use
            Arrays.fill(array, 0, size, 0.0f);
        }
        
        return array;
    }

    /**
     * Return a float array to the pool for reuse.
     * 
     * @param array the array to return
     */
    public void returnPooledArray(float[] array) {
        if (array != null && vectorPool.size() < maxPoolSize) {
            vectorPool.offer(array);
        }
    }

    // ========== Statistics and Monitoring ==========

    /**
     * Record vector processing metrics.
     */
    private void recordVectorProcessing(int count, long startTime) {
        vectorsProcessed.addAndGet(count);
        totalProcessingTimeMs.addAndGet(System.currentTimeMillis() - startTime);
    }

    /**
     * Record batch processing metrics.
     */
    private void recordBatchProcessing(int batchSize, long startTime) {
        batchesProcessed.incrementAndGet();
        recordVectorProcessing(batchSize, startTime);
    }

    /**
     * Get processing statistics.
     * 
     * @return processing statistics map
     */
    public Map<String, Object> getProcessingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalVectors = vectorsProcessed.get();
        long totalTime = totalProcessingTimeMs.get();
        
        stats.put("vectorsProcessed", totalVectors);
        stats.put("batchesProcessed", batchesProcessed.get());
        stats.put("totalProcessingTimeMs", totalTime);
        stats.put("averageVectorsPerSecond", totalTime > 0 ? (totalVectors * 1000.0) / totalTime : 0.0);
        stats.put("poolSize", vectorPool.size());
        stats.put("maxPoolSize", maxPoolSize);
        
        // Executor statistics
        if (processingExecutor instanceof ThreadPoolExecutor tpe) {
            stats.put("executor", Map.of(
                "activeThreads", tpe.getActiveCount(),
                "completedTasks", tpe.getCompletedTaskCount(),
                "queueSize", tpe.getQueue().size()
            ));
        }
        
        return stats;
    }

    /**
     * Shutdown the processing service.
     */
    public void shutdown() {
        log.info("Shutting down vector processing service");
        
        processingExecutor.shutdown();
        parallelProcessor.shutdown();
        
        try {
            if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
            if (!parallelProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                parallelProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            processingExecutor.shutdownNow();
            parallelProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        vectorPool.clear();
        log.info("Vector processing service shutdown completed");
    }

    // ========== Data Classes ==========

    /**
     * Result of a vector similarity computation.
     */
    public static class SimilarityResult {
        private final DenseVector vector;
        private final double similarity;
        private final int index;

        public SimilarityResult(DenseVector vector, double similarity, int index) {
            this.vector = vector;
            this.similarity = similarity;
            this.index = index;
        }

        public DenseVector getVector() { return vector; }
        public double getSimilarity() { return similarity; }
        public int getIndex() { return index; }

        @Override
        public String toString() {
            return String.format("SimilarityResult[index=%d, similarity=%.4f]", index, similarity);
        }
    }

    /**
     * Statistical summary of vector data.
     */
    public static class VectorStatistics {
        private final DenseVector mean;
        private final DenseVector standardDeviation;
        private final Map<String, Object> additionalStats;

        public VectorStatistics(DenseVector mean, DenseVector standardDeviation, 
                              Map<String, Object> additionalStats) {
            this.mean = mean;
            this.standardDeviation = standardDeviation;
            this.additionalStats = additionalStats;
        }

        public DenseVector getMean() { return mean; }
        public DenseVector getStandardDeviation() { return standardDeviation; }
        public Map<String, Object> getAdditionalStats() { return additionalStats; }

        @Override
        public String toString() {
            return String.format("VectorStatistics[dimensions=%d, meanNorm=%.4f, avgStdDev=%.4f]",
                    mean.getDimensions(), mean.norm(), 
                    Arrays.stream(standardDeviation.toArray()).average().orElse(0.0));
        }
    }
} 