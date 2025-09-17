package com.example.zeromq.compute.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compatibility helper that executes a list of callables on an executor and
 * cancels remaining tasks when one fails (semantics similar to
 * {@code StructuredTaskScope.ShutdownOnFailure}).
 *
 * Improvements over the original helper:
 * - Optional overall timeout.
 * - Per-task thread naming (prefix + index) to aid debugging and observability.
 * - Stronger cancellation semantics: remaining tasks are cancelled as soon as the
 *   first failure/timeout/interruption is observed.
 */
public final class ConcurrencyUtils {

    private ConcurrencyUtils() {}

    /**
     * Backwards-compatible API: submit tasks and cancel remaining tasks on first failure.
     */
    public static <T> List<T> invokeAllCancelOnFailure(ExecutorService executor, List<Callable<T>> tasks) throws Exception {
        return invokeAllCancelOnFailure(executor, tasks, 0L, null, "concurrent-task");
    }

    /**
     * Submit tasks to {@code executor}. If any task fails, remaining tasks are cancelled.
     * If {@code unit} is non-null the call will time out after {@code timeout}.
     *
     * @param executor executor to submit tasks to
     * @param tasks list of callables
     * @param timeout overall timeout value (ignored when {@code unit} is null)
     * @param unit time unit for {@code timeout}; when {@code null} no timeout is applied
     * @param threadNamePrefix optional thread name prefix for per-task naming (may be null)
     * @param <T> task result type
     * @return list of results in the same order as {@code tasks}
     * @throws Exception first task exception, {@link TimeoutException} on timeout, or {@link InterruptedException}
     */
    public static <T> List<T> invokeAllCancelOnFailure(ExecutorService executor,
                                                       List<Callable<T>> tasks,
                                                       long timeout,
                                                       TimeUnit unit,
                                                       String threadNamePrefix) throws Exception {

        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }

        final CompletionService<T> completionService = new ExecutorCompletionService<>(executor);
        final List<Future<T>> futures = new ArrayList<>(tasks.size());
        final Map<Future<T>, Integer> indexMap = new ConcurrentHashMap<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        // Submit wrapped tasks that set thread name for easy identification
        for (int i = 0; i < tasks.size(); i++) {
            final int idx = i;
            final Callable<T> original = tasks.get(i);
            Callable<T> wrapped = () -> {
                String originalThreadName = Thread.currentThread().getName();
                String prefix = threadNamePrefix == null ? "concurrent-task" : threadNamePrefix;
                try {
                    Thread.currentThread().setName(prefix + "-" + idx);
                    return original.call();
                } finally {
                    // best-effort restore
                    try { Thread.currentThread().setName(originalThreadName); } catch (Throwable ignored) {}
                }
            };

            Future<T> f = completionService.submit(wrapped);
            futures.add(f);
            indexMap.put(f, idx);
        }

        final Object[] results = new Object[tasks.size()];
        final long deadlineNanos = (unit != null) ? System.nanoTime() + unit.toNanos(timeout) : 0L;

        try {
            for (int completed = 0; completed < tasks.size(); completed++) {
                Future<T> finished;
                if (unit != null) {
                    long nanosLeft = deadlineNanos - System.nanoTime();
                    if (nanosLeft <= 0L) {
                        cancelAll(futures);
                        throw new TimeoutException("Timeout waiting for concurrent tasks");
                    }
                    finished = completionService.poll(nanosLeft, TimeUnit.NANOSECONDS);
                    if (finished == null) {
                        cancelAll(futures);
                        throw new TimeoutException("Timeout waiting for concurrent tasks");
                    }
                } else {
                    finished = completionService.take();
                }

                try {
                    T value = finished.get();
                    Integer idx = indexMap.get(finished);
                    if (idx == null) idx = 0; // defensive
                    results[idx] = value;
                } catch (ExecutionException ee) {
                    // cancel others and rethrow the underlying cause if possible
                    cancelAll(futures);
                    Throwable cause = ee.getCause();
                    if (cause instanceof Exception) throw (Exception) cause;
                    throw ee;
                }
            }

            // all succeeded; build ordered result list
            List<T> ordered = new ArrayList<>(tasks.size());
            for (Object o : results) ordered.add((T) o);
            return ordered;
        } catch (InterruptedException ie) {
            cancelAll(futures);
            Thread.currentThread().interrupt();
            throw ie;
        }
    }

    private static void cancelAll(List<? extends Future<?>> futures) {
        for (Future<?> other : futures) {
            try {
                other.cancel(true);
            } catch (Throwable ignored) {
                // best-effort cancellation
            }
        }
    }
} 