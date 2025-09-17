package com.example.zeromq.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import org.springframework.stereotype.Component;

/**
 * Registers BufferPool metrics with Micrometer.
 */
@Component
public class BufferPoolMetricsBinder implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        BufferPool pool = BufferPool.INSTANCE;

        // Gauges
        Gauge.builder("zeromq.bufferpool.current_size", pool, BufferPool::getCurrentPoolSize)
                .description("Current number of pooled buffers")
                .tag("component", "bufferpool")
                .register(registry);

        Gauge.builder("zeromq.bufferpool.max_size", pool, BufferPool::getMaxPoolSize)
                .description("Configured max pool size")
                .tag("component", "bufferpool")
                .register(registry);

        // FunctionCounters for monotonically increasing counters
        FunctionCounter.builder("zeromq.bufferpool.hits", pool, p -> (double) p.getHitCount())
                .description("Number of buffer hits served from pool")
                .tag("component", "bufferpool")
                .register(registry);

        FunctionCounter.builder("zeromq.bufferpool.misses", pool, p -> (double) p.getMissCount())
                .description("Number of buffer misses requiring allocation")
                .tag("component", "bufferpool")
                .register(registry);

        FunctionCounter.builder("zeromq.bufferpool.acquisitions", pool, p -> (double) p.getAcquisitionCount())
                .description("Number of buffer allocations performed")
                .tag("component", "bufferpool")
                .register(registry);

        FunctionCounter.builder("zeromq.bufferpool.releases", pool, p -> (double) p.getReleaseCount())
                .description("Number of buffer releases back to pool")
                .tag("component", "bufferpool")
                .register(registry);
    }
} 