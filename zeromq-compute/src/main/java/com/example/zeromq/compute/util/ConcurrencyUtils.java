package com.example.zeromq.compute.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Small compatibility helper that executes a list of callables on an executor and
 * cancels remaining tasks if any task fails (behavior similar to ShutdownOnFailure).
 */
public final class ConcurrencyUtils {

    private ConcurrencyUtils() {}

    public static <T> List<T> invokeAllCancelOnFailure(ExecutorService executor, List<Callable<T>> tasks) throws Exception {
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> c : tasks) {
                futures.add(executor.submit(c));
            }

            List<T> results = new ArrayList<>(tasks.size());
            for (Future<T> f : futures) {
                try {
                    results.add(f.get());
                } catch (ExecutionException ee) {
                    // cancel remaining
                    for (Future<?> other : futures) {
                        try { other.cancel(true); } catch (Throwable ignored) {}
                    }
                    throw ee.getCause() instanceof Exception ? (Exception) ee.getCause() : ee;
                }
            }
            return results;
        } catch (InterruptedException ie) {
            // propagate interruption after attempting cancellation
            for (Future<?> other : futures) {
                try { other.cancel(true); } catch (Throwable ignored) {}
            }
            Thread.currentThread().interrupt();
            throw ie;
        }
    }
} 