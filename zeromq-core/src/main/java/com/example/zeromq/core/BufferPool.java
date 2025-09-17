package com.example.zeromq.core;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight thread-safe pool for temporary float[] buffers.
 * Meant for short-lived use where the borrower copies data out (e.g. into immutable objects)
 * before releasing the buffer. Keeps a small bounded pool to reduce GC churn.
 */
public final class BufferPool {

    private static final Logger log = LoggerFactory.getLogger(BufferPool.class);
    private static final int DEFAULT_MAX_POOL_SIZE = 32;

    private final Deque<float[]> pool = new ConcurrentLinkedDeque<>();
    private final int maxPoolSize;

    // Metrics counters
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong acquisitions = new AtomicLong(0);
    private final AtomicLong releases = new AtomicLong(0);

    // Singleton instance reads optional system property to tune max pool size
    private static final int initialMax = readMaxFromSystem();
    public static final BufferPool INSTANCE = new BufferPool(initialMax);

    private static int readMaxFromSystem() {
        try {
            String v = System.getProperty("zeromq.bufferpool.maxSize");
            if (v == null || v.isBlank()) return DEFAULT_MAX_POOL_SIZE;
            int parsed = Integer.parseInt(v);
            return Math.max(1, parsed);
        } catch (Exception e) {
            return DEFAULT_MAX_POOL_SIZE;
        }
    }

    public BufferPool(int maxPoolSize) {
        this.maxPoolSize = Math.max(1, maxPoolSize);
        if (log.isInfoEnabled()) {
            log.info("Initialized BufferPool[maxPoolSize={}]", this.maxPoolSize);
        }
    }

    private BufferPool() {
        this(DEFAULT_MAX_POOL_SIZE);
    }

    /**
     * Acquire a float[] with length >= minSize. May return a larger buffer from the pool.
     * If none is available, a new array is allocated.
     */
    public float[] acquire(int minSize) {
        if (minSize <= 0) return new float[0];

        float[] buf;
        while ((buf = pool.pollFirst()) != null) {
            if (buf.length >= minSize) {
                hits.incrementAndGet();
                if (log.isDebugEnabled()) log.debug("BufferPool hit: returning buffer length={}", buf.length);
                return buf;
            } else {
                // put smaller buffers aside for reuse
                pool.addLast(buf);
                buf = null;
            }
        }
        misses.incrementAndGet();
        acquisitions.incrementAndGet();
        if (log.isDebugEnabled()) log.debug("BufferPool miss: allocating new buffer size={}", minSize);
        return new float[minSize];
    }

    /**
     * Release a buffer back to the pool for reuse. Buffers larger than needed are accepted.
     */
    public void release(float[] buffer) {
        if (buffer == null) return;
        releases.incrementAndGet();
        if (pool.size() >= maxPoolSize) {
            if (log.isDebugEnabled()) log.debug("BufferPool full: dropping buffer length={}", buffer.length);
            return;
        }
        pool.addFirst(buffer);
        if (log.isDebugEnabled()) log.debug("BufferPool release: buffer length={}, poolSize={}", buffer.length, pool.size());
    }

    /** Metrics getters for external exposition */
    public long getHitCount() { return hits.get(); }
    public long getMissCount() { return misses.get(); }
    public long getAcquisitionCount() { return acquisitions.get(); }
    public long getReleaseCount() { return releases.get(); }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getCurrentPoolSize() { return pool.size(); }
} 