package com.example.zeromq.core;

import com.example.zeromq.core.exception.SerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance binary message converter for vector data types.
 * 
 * <p>This converter provides efficient binary serialization for all vector types
 * (Dense, Sparse, Named, Batch) with optimized encoding formats. It uses custom
 * binary protocols for maximum performance while maintaining compatibility across
 * different platforms and languages.
 * 
 * <p>The converter supports versioned binary formats to enable backward compatibility
 * and includes compression for large vectors to minimize network overhead.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class VectorMessageConverter implements MessageConverter {

    private static final Logger log = LoggerFactory.getLogger(VectorMessageConverter.class);
    
    // Binary format constants
    private static final byte[] VECTOR_MAGIC = {'Z', 'V', 'E', 'C'}; // "ZVEC"
    private static final byte FORMAT_VERSION = 0x01;
    
    // Vector type identifiers
    private static final byte TYPE_DENSE_VECTOR = 0x01;
    private static final byte TYPE_SPARSE_VECTOR = 0x02;
    private static final byte TYPE_NAMED_VECTOR = 0x03;
    private static final byte TYPE_BATCH_VECTOR = 0x04;
    
    // Compression flags
    private static final byte FLAG_UNCOMPRESSED = 0x00;
    private static final byte FLAG_COMPRESSED = 0x01;
    
    // Compression threshold (compress vectors larger than 1KB)
    private static final int COMPRESSION_THRESHOLD = 1024;
    
    // Performance metrics
    private final AtomicLong serializationCounter = new AtomicLong(0);
    private final AtomicLong deserializationCounter = new AtomicLong(0);
    private final AtomicLong compressionSavings = new AtomicLong(0);
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(Class<?> type) {
        return Vector.class.isAssignableFrom(type) ||
               DenseVector.class.equals(type) ||
               SparseVector.class.equals(type) ||
               NamedVector.class.equals(type) ||
               BatchVector.class.equals(type);
    }

    @Override
    public byte[] toBytes(Object obj) throws SerializationException {
        if (obj == null) {
            throw new SerializationException("Cannot serialize null vector");
        }
        
        long startTime = System.nanoTime();
        long operationId = serializationCounter.incrementAndGet();
        
        try {
            byte[] result;
            
            if (obj instanceof DenseVector dense) {
                result = encodeDenseVector(dense);
            } else if (obj instanceof SparseVector sparse) {
                result = encodeSparseVector(sparse);
            } else if (obj instanceof NamedVector named) {
                result = encodeNamedVector(named);
            } else if (obj instanceof BatchVector batch) {
                result = encodeBatchVector(batch);
            } else {
                throw new SerializationException("Unsupported vector type: " + obj.getClass().getName(), obj.getClass());
            }
            
            long duration = System.nanoTime() - startTime;
            
            log.debug("component=vector-converter event=serialization-completed " +
                     "operationId={} type={} sizeBytes={} durationMicros={}", 
                     operationId, obj.getClass().getSimpleName(), result.length, duration / 1000);
            
            return result;
            
        } catch (Exception e) {
            log.error("component=vector-converter event=serialization-failed " +
                     "operationId={} type={} error={}", 
                     operationId, obj.getClass().getSimpleName(), e.getMessage());
            throw new SerializationException("Vector serialization failed: " + e.getMessage(), e, obj.getClass());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromBytes(byte[] data, Class<T> targetType) throws SerializationException {
        if (data == null || data.length == 0) {
            throw new SerializationException("Cannot deserialize null or empty data", targetType);
        }
        
        long startTime = System.nanoTime();
        long operationId = deserializationCounter.incrementAndGet();
        
        try {
            // Validate magic header
            if (data.length < VECTOR_MAGIC.length + 2) {
                throw new SerializationException("Invalid vector data: too short", targetType);
            }
            
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            
            // Check magic bytes
            byte[] magic = new byte[VECTOR_MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, VECTOR_MAGIC)) {
                throw new SerializationException("Invalid vector data: bad magic bytes", targetType);
            }
            
            // Check version
            byte version = buffer.get();
            if (version != FORMAT_VERSION) {
                throw new SerializationException("Unsupported vector format version: " + version, targetType);
            }
            
            // Get type and flags
            byte typeAndFlags = buffer.get();
            byte vectorType = (byte) (typeAndFlags & 0x0F);
            byte compressionFlag = (byte) ((typeAndFlags & 0xF0) >> 4);
            
            // Decompress if needed
            byte[] vectorData;
            if (compressionFlag == FLAG_COMPRESSED) {
                vectorData = decompress(buffer);
            } else {
                vectorData = new byte[buffer.remaining()];
                buffer.get(vectorData);
            }
            
            // Deserialize based on type
            Object result = switch (vectorType) {
                case TYPE_DENSE_VECTOR -> decodeDenseVector(vectorData);
                case TYPE_SPARSE_VECTOR -> decodeSparseVector(vectorData);
                case TYPE_NAMED_VECTOR -> decodeNamedVector(vectorData);
                case TYPE_BATCH_VECTOR -> decodeBatchVector(vectorData);
                default -> throw new SerializationException("Unknown vector type: " + vectorType, targetType);
            };
            
            // Validate result type
            if (!targetType.isInstance(result)) {
                throw new SerializationException(
                    String.format("Type mismatch: expected %s, got %s", 
                        targetType.getName(), result.getClass().getName()), targetType);
            }
            
            long duration = System.nanoTime() - startTime;
            
            log.debug("component=vector-converter event=deserialization-completed " +
                     "operationId={} type={} sizeBytes={} durationMicros={}", 
                     operationId, targetType.getSimpleName(), data.length, duration / 1000);
            
            return (T) result;
            
        } catch (Exception e) {
            log.error("component=vector-converter event=deserialization-failed " +
                     "operationId={} type={} error={}", 
                     operationId, targetType.getSimpleName(), e.getMessage());
            throw new SerializationException("Vector deserialization failed: " + e.getMessage(), e, targetType);
        }
    }

    /**
     * Encode a DenseVector to binary format.
     * Format: [dimensions:int][data:float[]]
     */
    private byte[] encodeDenseVector(DenseVector vector) {
        int dimensions = vector.getDimensions();
        float[] data = vector.getData();
        
        ByteBuffer buffer = ByteBuffer.allocate(4 + dimensions * 4).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(dimensions);
        
        for (float value : data) {
            buffer.putFloat(value);
        }
        
        return createVectorMessage(TYPE_DENSE_VECTOR, buffer.array());
    }

    /**
     * Decode a DenseVector from binary format.
     */
    private DenseVector decodeDenseVector(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int dimensions = buffer.getInt();
        
        if (buffer.remaining() != dimensions * 4) {
            throw new SerializationException("Invalid DenseVector data: size mismatch");
        }
        
        float[] vectorData = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            vectorData[i] = buffer.getFloat();
        }
        
        return new DenseVector(vectorData);
    }

    /**
     * Encode a SparseVector to binary format.
     * Format: [dimensions:int][nnz:int][indices:int[]][values:float[]]
     */
    private byte[] encodeSparseVector(SparseVector vector) {
        int dimensions = vector.getDimensions();
        Map<Integer, Float> indices = vector.getIndices();
        int nnz = indices.size();
        
        ByteBuffer buffer = ByteBuffer.allocate(8 + nnz * 8).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(dimensions);
        buffer.putInt(nnz);
        
        // Sort indices for deterministic encoding
        indices.entrySet().stream()
               .sorted(Map.Entry.comparingByKey())
               .forEach(entry -> {
                   buffer.putInt(entry.getKey());
                   buffer.putFloat(entry.getValue());
               });
        
        return createVectorMessage(TYPE_SPARSE_VECTOR, buffer.array());
    }

    /**
     * Decode a SparseVector from binary format.
     */
    private SparseVector decodeSparseVector(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int dimensions = buffer.getInt();
        int nnz = buffer.getInt();
        
        if (buffer.remaining() != nnz * 8) {
            throw new SerializationException("Invalid SparseVector data: size mismatch");
        }
        
        Map<Integer, Float> indices = new HashMap<>();
        for (int i = 0; i < nnz; i++) {
            int index = buffer.getInt();
            float value = buffer.getFloat();
            indices.put(index, value);
        }
        
        return new SparseVector(dimensions, indices);
    }

    /**
     * Encode a NamedVector to binary format.
     * Format: [metadataLength:int][metadata:json][vector:dense_vector_data]
     */
    private byte[] encodeNamedVector(NamedVector vector) throws JsonProcessingException {
        // Encode metadata as JSON
        String[] featureNames = vector.getFeatureNames();
        byte[] metadata = objectMapper.writeValueAsBytes(featureNames);
        
        // Encode the underlying dense vector
        byte[] denseData = encodeDenseVectorData(vector.getVector());
        
        ByteBuffer buffer = ByteBuffer.allocate(4 + metadata.length + denseData.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(metadata.length);
        buffer.put(metadata);
        buffer.put(denseData);
        
        return createVectorMessage(TYPE_NAMED_VECTOR, buffer.array());
    }

    /**
     * Decode a NamedVector from binary format.
     */
    private NamedVector decodeNamedVector(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int metadataLength = buffer.getInt();
        
        if (buffer.remaining() < metadataLength) {
            throw new SerializationException("Invalid NamedVector data: insufficient metadata");
        }
        
        // Read metadata
        byte[] metadata = new byte[metadataLength];
        buffer.get(metadata);
        String[] featureNames = objectMapper.readValue(metadata, String[].class);
        
        // Read dense vector data
        byte[] denseData = new byte[buffer.remaining()];
        buffer.get(denseData);
        DenseVector denseVector = decodeDenseVectorData(denseData);
        
        return new NamedVector(featureNames, denseVector);
    }

    /**
     * Encode a BatchVector to binary format.
     * Format: [batchSize:int][vectorDims:int][vectors:dense_vector_data[]]
     */
    private byte[] encodeBatchVector(BatchVector batch) {
        int batchSize = batch.getBatchSize();
        int vectorDims = batch.getVectorDimensions();
        DenseVector[] vectors = batch.getVectors();
        
        // Calculate total size
        int totalSize = 8; // batchSize + vectorDims
        for (DenseVector vector : vectors) {
            totalSize += encodeDenseVectorData(vector).length;
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(batchSize);
        buffer.putInt(vectorDims);
        
        for (DenseVector vector : vectors) {
            buffer.put(encodeDenseVectorData(vector));
        }
        
        return createVectorMessage(TYPE_BATCH_VECTOR, buffer.array());
    }

    /**
     * Decode a BatchVector from binary format.
     */
    private BatchVector decodeBatchVector(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int batchSize = buffer.getInt();
        int vectorDims = buffer.getInt();
        
        DenseVector[] vectors = new DenseVector[batchSize];
        int expectedVectorSize = vectorDims * 4; // 4 bytes per float
        
        for (int i = 0; i < batchSize; i++) {
            if (buffer.remaining() < expectedVectorSize) {
                throw new SerializationException("Invalid BatchVector data: insufficient vector data");
            }
            
            byte[] vectorData = new byte[expectedVectorSize];
            buffer.get(vectorData);
            vectors[i] = decodeDenseVectorData(vectorData);
        }
        
        return new BatchVector(vectors);
    }

    /**
     * Encode just the dense vector data without the full message wrapper.
     */
    private byte[] encodeDenseVectorData(DenseVector vector) {
        float[] data = vector.getData();
        ByteBuffer buffer = ByteBuffer.allocate(data.length * 4).order(ByteOrder.BIG_ENDIAN);
        
        for (float value : data) {
            buffer.putFloat(value);
        }
        
        return buffer.array();
    }

    /**
     * Decode dense vector data without the message wrapper.
     */
    private DenseVector decodeDenseVectorData(byte[] data) {
        if (data.length % 4 != 0) {
            throw new SerializationException("Invalid dense vector data: size not multiple of 4");
        }
        
        int dimensions = data.length / 4;
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        float[] vectorData = new float[dimensions];
        
        for (int i = 0; i < dimensions; i++) {
            vectorData[i] = buffer.getFloat();
        }
        
        return new DenseVector(vectorData);
    }

    /**
     * Create a complete vector message with header and optional compression.
     */
    private byte[] createVectorMessage(byte vectorType, byte[] vectorData) {
        boolean shouldCompress = vectorData.length > COMPRESSION_THRESHOLD;
        byte[] finalData = vectorData;
        byte flags = FLAG_UNCOMPRESSED;
        
        if (shouldCompress) {
            byte[] compressed = compress(vectorData);
            if (compressed.length < vectorData.length) {
                finalData = compressed;
                flags = FLAG_COMPRESSED;
                
                long savings = vectorData.length - compressed.length;
                compressionSavings.addAndGet(savings);
                
                log.debug("component=vector-converter event=compression-applied " +
                         "originalSize={} compressedSize={} savingsBytes={} compressionRatio={:.2f}",
                         vectorData.length, compressed.length, savings, 
                         (double) compressed.length / vectorData.length);
            }
        }
        
        // Combine type and flags
        byte typeAndFlags = (byte) ((flags << 4) | vectorType);
        
        // Create final message: [magic][version][type+flags][data]
        ByteBuffer message = ByteBuffer.allocate(VECTOR_MAGIC.length + 2 + finalData.length);
        message.put(VECTOR_MAGIC);
        message.put(FORMAT_VERSION);
        message.put(typeAndFlags);
        message.put(finalData);
        
        return message.array();
    }

    /**
     * Simple compression using GZIP.
     */
    private byte[] compress(byte[] data) {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(baos)) {
            
            gzos.write(data);
            gzos.finish();
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.warn("Compression failed, using uncompressed data: {}", e.getMessage());
            return data; // Fall back to uncompressed
        }
    }

    /**
     * Decompress GZIP data.
     */
    private byte[] decompress(ByteBuffer buffer) throws IOException {
        byte[] compressedData = new byte[buffer.remaining()];
        buffer.get(compressedData);
        
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(compressedData);
             java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(bais);
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            
            byte[] buffer2 = new byte[1024];
            int len;
            while ((len = gzis.read(buffer2)) != -1) {
                baos.write(buffer2, 0, len);
            }
            
            return baos.toByteArray();
        }
    }

    @Override
    public int getPriority() {
        return 100; // Higher priority than Jackson for vector types
    }

    @Override
    public String getDescription() {
        return "VectorMessageConverter[binary format, compression enabled]";
    }

    /**
     * Get performance statistics for monitoring.
     * 
     * @return a map of performance metrics
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("serializations", serializationCounter.get());
        stats.put("deserializations", deserializationCounter.get());
        stats.put("compressionSavingsBytes", compressionSavings.get());
        stats.put("compressionThreshold", COMPRESSION_THRESHOLD);
        return stats;
    }

    /**
     * Reset performance statistics (useful for testing).
     */
    public void resetStats() {
        serializationCounter.set(0);
        deserializationCounter.set(0);
        compressionSavings.set(0);
    }
} 