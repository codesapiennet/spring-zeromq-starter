package com.example.zeromq.compute.worker;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight manager for compute workers. Allows registration of worker lifecycle
 * callbacks and provides thread-safe operations to start/stop/scale workers and
 * query their status. This class intentionally keeps orchestration logic simple
 * so it can be composed into larger scheduling components later.
 */
@Component
public class WorkerManager {

    private static final Logger log = LoggerFactory.getLogger(WorkerManager.class);

    private final ConcurrentHashMap<String, WorkerHandle> workers = new ConcurrentHashMap<>();

    /**
     * Register a worker with lifecycle callbacks.
     *
     * @param workerId unique worker identifier (non-null)
     * @param startCallback callback that will start the worker when invoked
     * @param stopCallback callback that will stop the worker when invoked
     * @param initialCapacity initial processing capacity (>= 0)
     * @throws IllegalArgumentException when parameters are invalid or workerId already exists
     */
    public void registerWorker(String workerId, Runnable startCallback, Runnable stopCallback, int initialCapacity) {
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(startCallback, "startCallback must not be null");
        Objects.requireNonNull(stopCallback, "stopCallback must not be null");
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity must be >= 0");

        WorkerHandle handle = new WorkerHandle(workerId, startCallback, stopCallback, initialCapacity);
        WorkerHandle previous = workers.putIfAbsent(workerId, handle);
        if (previous != null) {
            throw new IllegalArgumentException("Worker already registered with id: " + workerId);
        }

        log.info("Registered worker {} capacity={}", workerId, initialCapacity);
    }

    /**
     * Start a registered worker.
     *
     * @param workerId worker id
     * @return true if the worker was started or already running
     */
    public boolean startWorker(String workerId) {
        WorkerHandle handle = workers.get(workerId);
        if (handle == null) {
            log.warn("startWorker: unknown worker {}", workerId);
            return false;
        }
        return handle.start();
    }

    /**
     * Stop a registered worker.
     *
     * @param workerId worker id
     * @return true if the worker was stopped or already stopped
     */
    public boolean stopWorker(String workerId) {
        WorkerHandle handle = workers.get(workerId);
        if (handle == null) {
            log.warn("stopWorker: unknown worker {}", workerId);
            return false;
        }
        return handle.stop();
    }

    /**
     * Unregister a worker and ensure it is stopped.
     *
     * @param workerId worker id
     * @return true if removed
     */
    public boolean unregisterWorker(String workerId) {
        WorkerHandle handle = workers.remove(workerId);
        if (handle == null) return false;
        try {
            handle.stop();
        } catch (Exception e) {
            log.warn("Error while stopping worker {} during unregister: {}", workerId, e.getMessage());
        }
        log.info("Unregistered worker {}", workerId);
        return true;
    }

    /**
     * Scale worker capacity (logical units). This is a soft directive; actual
     * worker implementation decides how to apply capacity changes.
     *
     * @param workerId worker id
     * @param newCapacity non-negative capacity
     * @return true when the capacity was updated
     */
    public boolean scaleWorker(String workerId, int newCapacity) {
        WorkerHandle handle = workers.get(workerId);
        if (handle == null) {
            log.warn("scaleWorker: unknown worker {}", workerId);
            return false;
        }
        if (newCapacity < 0) throw new IllegalArgumentException("newCapacity must be >= 0");
        handle.setCapacity(newCapacity);
        log.info("Scaled worker {} to capacity={}", workerId, newCapacity);
        return true;
    }

    /**
     * Get a snapshot of current worker statuses keyed by workerId.
     */
    public Map<String, WorkerInfo> getWorkerStatuses() {
        Map<String, WorkerInfo> snapshot = new ConcurrentHashMap<>();
        workers.forEach((id, handle) -> snapshot.put(id, handle.getInfo()));
        return Collections.unmodifiableMap(snapshot);
    }

    @PreDestroy
    public void shutdown() {
        log.info("WorkerManager shutting down {} workers", workers.size());
        workers.forEach((id, handle) -> {
            try {
                handle.stop();
            } catch (Exception e) {
                log.warn("Error stopping worker {}: {}", id, e.getMessage());
            }
        });
        workers.clear();
    }

    // ----- Helper classes -----

    private static final class WorkerHandle {
        private final String id;
        private final Runnable startCallback;
        private final Runnable stopCallback;
        private final AtomicInteger capacity;

        private volatile boolean running = false;
        private volatile Instant lastStarted = null;

        WorkerHandle(String id, Runnable startCallback, Runnable stopCallback, int initialCapacity) {
            this.id = id;
            this.startCallback = startCallback;
            this.stopCallback = stopCallback;
            this.capacity = new AtomicInteger(initialCapacity);
        }

        synchronized boolean start() {
            if (running) return true;
            try {
                startCallback.run();
                running = true;
                lastStarted = Instant.now();
                log.info("Started worker {}", id);
                return true;
            } catch (Exception e) {
                log.error("Failed to start worker {}: {}", id, e.getMessage(), e);
                return false;
            }
        }

        synchronized boolean stop() {
            if (!running) return true;
            try {
                stopCallback.run();
                running = false;
                log.info("Stopped worker {}", id);
                return true;
            } catch (Exception e) {
                log.error("Failed to stop worker {}: {}", id, e.getMessage(), e);
                return false;
            }
        }

        void setCapacity(int newCapacity) { this.capacity.set(newCapacity); }

        WorkerInfo getInfo() {
            return new WorkerInfo(id, running, capacity.get(), lastStarted);
        }
    }

    /**
     * Immutable snapshot of worker status.
     */
    public static final class WorkerInfo {
        private final String workerId;
        private final boolean running;
        private final int capacity;
        private final Instant lastStarted;

        public WorkerInfo(String workerId, boolean running, int capacity, Instant lastStarted) {
            this.workerId = workerId;
            this.running = running;
            this.capacity = capacity;
            this.lastStarted = lastStarted;
        }

        public String getWorkerId() { return workerId; }
        public boolean isRunning() { return running; }
        public int getCapacity() { return capacity; }
        public Instant getLastStarted() { return lastStarted; }

        @Override
        public String toString() {
            return String.format("WorkerInfo[id=%s,running=%s,capacity=%d,lastStarted=%s]",
                    workerId, running, capacity, lastStarted);
        }
    }
} 