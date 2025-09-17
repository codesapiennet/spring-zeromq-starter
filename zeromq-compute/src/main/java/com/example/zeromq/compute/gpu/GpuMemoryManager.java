package com.example.zeromq.compute.gpu;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight GPU memory manager that provides allocation helpers and tracks
 * allocations so they can be aggressively released on shutdown. This is a conservative
 * implementation that uses direct buffers; native drivers may provide more efficient
 * APIs and should be used in production implementations.
 */
@Component
public class GpuMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(GpuMemoryManager.class);

    private final Map<ByteBuffer, Cleaner.Cleanable> allocations = new ConcurrentHashMap<>();
    private final Cleaner cleaner = Cleaner.create();

    /**
     * Allocate a direct float buffer of the requested length (number of floats).
     */
    public FloatBuffer allocateFloatBuffer(int length) {
        ByteBuffer buf = ByteBuffer.allocateDirect(length * Float.BYTES).order(ByteOrder.nativeOrder());
        // Register a cleaner to null out the buffer when GC runs as a safety net
        Cleaner.Cleanable cleanable = cleaner.register(buf, () -> {
            // no-op placeholder - keeping for explicit cleaning semantics
        });
        allocations.put(buf, cleanable);
        return buf.asFloatBuffer();
    }

    /**
     * Free a previously allocated buffer. Best-effort; relies on Cleaner for actual memory reclamation.
     */
    public void freeBuffer(FloatBuffer buffer) {
        if (buffer == null) return;
        ByteBuffer parent = (ByteBuffer) buffer; // FloatBuffer.asFloatBuffer uses the same underlying object
        Cleaner.Cleanable c = allocations.remove(parent);
        if (c != null) {
            try {
                c.clean();
            } catch (Throwable t) {
                log.warn("Failed to clean buffer: {}", t.getMessage());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("GpuMemoryManager shutting down, cleaning {} allocations", allocations.size());
        for (Map.Entry<ByteBuffer, Cleaner.Cleanable> e : allocations.entrySet()) {
            try {
                e.getValue().clean();
            } catch (Throwable t) {
                log.warn("Failed to clean buffer during shutdown: {}", t.getMessage());
            }
        }
        allocations.clear();
    }
} 