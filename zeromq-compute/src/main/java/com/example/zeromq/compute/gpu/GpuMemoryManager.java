package com.example.zeromq.compute.gpu;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    // Track allocations by both the parent ByteBuffer and any view buffers (FloatBuffer)
    private final Map<Object, Cleaner.Cleanable> allocations = new ConcurrentHashMap<>();
    private final Map<Cleaner.Cleanable, Integer> allocationSizes = new ConcurrentHashMap<>();
    private final Cleaner cleaner = Cleaner.create();

    /**
     * Allocate a direct float buffer of the requested length (number of floats).
     */
    public FloatBuffer allocateFloatBuffer(int length) {
        if (length <= 0) throw new IllegalArgumentException("length must be > 0");
        int bytes = Math.multiplyExact(length, Float.BYTES);
        ByteBuffer parent = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        Cleaner.Cleanable cleanable = cleaner.register(parent, () -> log.debug("Cleaner executed for allocation of {} bytes", bytes));
        FloatBuffer view = parent.asFloatBuffer();

        // Track both parent and view so callers can free either reference.
        allocations.put(parent, cleanable);
        allocations.put(view, cleanable);
        allocationSizes.put(cleanable, bytes);

        log.info("Allocated float buffer: {} floats ({} bytes)", length, bytes);
        return view;
    }

    /**
     * Allocate a direct byte buffer of the requested size (in bytes).
     */
    public ByteBuffer allocateByteBuffer(int sizeBytes) {
        if (sizeBytes <= 0) throw new IllegalArgumentException("sizeBytes must be > 0");
        ByteBuffer parent = ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder());
        Cleaner.Cleanable cleanable = cleaner.register(parent, () -> log.debug("Cleaner executed for allocation of {} bytes", sizeBytes));

        allocations.put(parent, cleanable);
        allocationSizes.put(cleanable, sizeBytes);

        log.info("Allocated byte buffer: {} bytes", sizeBytes);
        return parent;
    }

    /**
     * Free a previously allocated float buffer. Best-effort; relies on Cleaner for actual memory reclamation.
     */
    public void freeBuffer(FloatBuffer buffer) {
        if (buffer == null) return;
        Cleaner.Cleanable cleanable = allocations.remove(buffer);
        if (cleanable == null) {
            log.warn("Attempted to free unknown FloatBuffer instance: capacity={}, remaining={}", buffer.capacity(), buffer.remaining());
            return;
        }
        // Remove any other references that point to the same cleanable (parent + other views)
        removeAllForCleanable(cleanable);
        try {
            cleanable.clean();
            log.info("Freed FloatBuffer ({} bytes)", allocationSizes.remove(cleanable));
        } catch (Throwable t) {
            log.warn("Failed to clean FloatBuffer: {}", t.getMessage());
        }
    }

    /**
     * Free a previously allocated byte buffer.
     */
    public void freeBuffer(ByteBuffer buffer) {
        if (buffer == null) return;
        Cleaner.Cleanable cleanable = allocations.remove(buffer);
        if (cleanable == null) {
            log.warn("Attempted to free unknown ByteBuffer instance: capacity={}, remaining={}", buffer.capacity(), buffer.remaining());
            return;
        }
        removeAllForCleanable(cleanable);
        try {
            cleanable.clean();
            log.info("Freed ByteBuffer ({} bytes)", allocationSizes.remove(cleanable));
        } catch (Throwable t) {
            log.warn("Failed to clean ByteBuffer: {}", t.getMessage());
        }
    }

    private void removeAllForCleanable(Cleaner.Cleanable target) {
        List<Object> toRemove = new ArrayList<>();
        for (Map.Entry<Object, Cleaner.Cleanable> e : allocations.entrySet()) {
            if (Objects.equals(e.getValue(), target)) toRemove.add(e.getKey());
        }
        for (Object k : toRemove) allocations.remove(k);
    }

    @PreDestroy
    public void shutdown() {
        log.info("GpuMemoryManager shutting down, cleaning {} allocations", allocations.size());
        // Clean each unique cleanable once
        Set<Cleaner.Cleanable> unique = new HashSet<>(allocations.values());
        for (Cleaner.Cleanable c : unique) {
            try {
                c.clean();
            } catch (Throwable t) {
                log.warn("Failed to clean buffer during shutdown: {}", t.getMessage());
            }
        }
        allocations.clear();
        allocationSizes.clear();
    }
} 