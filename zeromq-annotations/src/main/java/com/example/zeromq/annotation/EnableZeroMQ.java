package com.example.zeromq.annotation;

import com.example.zeromq.annotation.config.ZeroMQAnnotationConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enable ZeroMQ declarative messaging support.
 * 
 * <p>This annotation enables the ZeroMQ annotation processing infrastructure,
 * allowing the use of declarative annotations like {@code @ZeroMQPublisher}
 * and {@code @ZeroMQSubscriber} on methods and classes.
 * 
 * <p>When this annotation is present, the framework will:
 * <ul>
 * <li>Scan for ZeroMQ annotations on methods and classes</li>
 * <li>Create necessary message listener containers</li>
 * <li>Set up AOP aspects for message publishing</li>
 * <li>Configure security and connection management</li>
 * <li>Initialize metrics and monitoring</li>
 * </ul>
 * 
 * <h3>Basic Usage:</h3>
 * <pre>
 * {@literal @}Configuration
 * {@literal @}EnableZeroMQ
 * public class MyConfiguration {
 *     // Configuration beans
 * }
 * </pre>
 * 
 * <h3>With Custom Configuration:</h3>
 * <pre>
 * {@literal @}Configuration
 * {@literal @}EnableZeroMQ(
 *     basePackages = "com.example.messaging",
 *     containerFactory = "customContainerFactory",
 *     enableMetrics = true
 * )
 * public class MyConfiguration {
 *     // Configuration beans
 * }
 * </pre>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ZeroMQAnnotationConfiguration.class)
public @interface EnableZeroMQ {

    /**
     * Base packages to scan for ZeroMQ annotations.
     * 
     * <p>If empty, scanning will start from the package of the class
     * that declares this annotation.
     * 
     * @return array of base packages to scan
     */
    String[] basePackages() default {};

    /**
     * Base package classes for type-safe package specification.
     * 
     * <p>Alternative to {@link #basePackages()} for type-safe package names.
     * 
     * @return array of base package classes
     */
    Class<?>[] basePackageClasses() default {};

    /**
     * Default container factory to use for message listener containers.
     * 
     * <p>If empty, uses the default container factory provided by the framework.
     * 
     * @return the container factory bean name
     */
    String containerFactory() default "";

    /**
     * Whether to enable automatic metrics collection for ZeroMQ operations.
     * 
     * @return true to enable metrics collection
     */
    boolean enableMetrics() default true;

    /**
     * Whether to enable automatic health indicators.
     * 
     * @return true to enable health indicators
     */
    boolean enableHealthIndicators() default true;

    /**
     * Maximum number of concurrent message processing threads per subscriber.
     * 
     * <p>This sets the default concurrency level for all subscribers unless
     * overridden at the method level.
     * 
     * @return the default concurrency level
     */
    int defaultConcurrency() default 1;

    /**
     * Default error handling strategy for message processing failures.
     * 
     * @return the default error handling strategy
     */
    ErrorHandling defaultErrorHandling() default ErrorHandling.LOG_AND_CONTINUE;

    /**
     * Whether to enable automatic connection pooling.
     * 
     * @return true to enable connection pooling
     */
    boolean enableConnectionPooling() default true;

    /**
     * Whether to enable vector processing optimizations.
     * 
     * @return true to enable vector processing
     */
    boolean enableVectorProcessing() default true;

    /**
     * Profile-specific configuration mode.
     * 
     * <p>Controls various framework behaviors based on the deployment profile:
     * <ul>
     * <li>{@code DEVELOPMENT} - Relaxed security, detailed logging, auto key generation</li>
     * <li>{@code PRODUCTION} - Strict security, optimized performance, minimal logging</li>
     * <li>{@code AUTO} - Automatically detect based on active Spring profiles</li>
     * </ul>
     * 
     * @return the configuration mode
     */
    Mode mode() default Mode.AUTO;

    /**
     * Configuration modes for different deployment environments.
     */
    enum Mode {
        /**
         * Development mode with relaxed security and verbose logging.
         */
        DEVELOPMENT,
        
        /**
         * Production mode with strict security and optimized performance.
         */
        PRODUCTION,
        
        /**
         * Automatically detect mode based on active Spring profiles.
         */
        AUTO
    }

    /**
     * Default error handling strategies.
     */
    enum ErrorHandling {
        /**
         * Log errors and continue processing.
         */
        LOG_AND_CONTINUE,
        
        /**
         * Log errors and stop the subscriber.
         */
        LOG_AND_STOP,
        
        /**
         * Silently ignore errors.
         */
        IGNORE,
        
        /**
         * Retry processing with exponential backoff.
         */
        RETRY,
        
        /**
         * Send failed messages to dead letter queue.
         */
        DEAD_LETTER,
        
        /**
         * Use custom error handler.
         */
        CUSTOM
    }
} 