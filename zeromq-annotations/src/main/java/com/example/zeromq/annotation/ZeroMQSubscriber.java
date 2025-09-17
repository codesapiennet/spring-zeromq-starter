package com.example.zeromq.annotation;

import java.lang.annotation.*;

/**
 * Annotation for declarative ZeroMQ message subscription.
 * 
 * <p>Methods annotated with {@code @ZeroMQSubscriber} will automatically
 * subscribe to messages from the specified ZeroMQ endpoint and invoke
 * the annotated method when messages arrive. The method parameter types
 * determine how messages are deserialized.
 * 
 * <p>This annotation supports various messaging patterns including
 * publish-subscribe, pull (load-balanced), and request-reply patterns.
 * 
 * Usage Examples:
 * <pre>
 * // Simple subscription
 * {@literal @}ZeroMQSubscriber(endpoint = "tcp://localhost:5555")
 * public void handleMessage(String message) {
 *     // Process message
 * }
 * 
 * // Topic-based subscription
 * {@literal @}ZeroMQSubscriber(endpoint = "tcp://localhost:5555", topic = "news")
 * public void handleNews(NewsEvent event) {
 *     // Process news event
 * }
 * 
 * // Multiple topics
 * {@literal @}ZeroMQSubscriber(endpoint = "tcp://localhost:5555", topics = {"news", "sports"})
 * public void handleNewsAndSports(String topic, Object message) {
 *     // First parameter is topic, second is message
 * }
 * 
 * // Load-balanced pull pattern
 * {@literal @}ZeroMQSubscriber(endpoint = "tcp://localhost:5556", pattern = Pattern.PULL)
 * public void handleWork(WorkItem item) {
 *     // Process work item
 * }
 * </pre>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZeroMQSubscriber {

    /**
     * The ZeroMQ endpoint to connect to for subscription.
     * 
     * <p>Supports Spring expression language (SpEL) for dynamic endpoint resolution.
     * Examples:
     * <ul>
     * <li>{@code "tcp://localhost:5555"} - Static endpoint</li>
     * <li>{@code "${app.zeromq.subscriber.endpoint}"} - Property reference</li>
     * <li>{@code "#{subscriberConfig.getEndpoint()}"} - SpEL expression</li>
     * </ul>
     * 
     * @return the endpoint to connect to
     */
    String endpoint();

    /**
     * Single topic to subscribe to (for PUB/SUB pattern).
     * 
     * <p>If empty and {@link #topics()} is also empty, subscribes to all messages.
     * Can use SpEL expressions for dynamic topics.
     * 
     * @return the topic name
     */
    String topic() default "";

    /**
     * Multiple topics to subscribe to (for PUB/SUB pattern).
     * 
     * <p>When multiple topics are specified, the method will receive messages
     * from any of the topics. If the method has a String parameter named "topic"
     * or annotated with {@code @Topic}, it will receive the actual topic name.
     * 
     * @return array of topic names
     */
    String[] topics() default {};

    /**
     * The messaging pattern to use.
     * 
     * @return the messaging pattern
     */
    Pattern pattern() default Pattern.SUB;

    /**
     * Security configuration reference.
     * 
     * <p>References a security configuration bean by name, or uses SpEL
     * expressions to dynamically resolve security settings.
     * 
     * @return the security configuration reference
     */
    String security() default "";

    /**
     * Maximum number of concurrent message processing threads.
     * 
     * <p>Controls the parallelism of message processing for this subscriber.
     * A value of 1 ensures sequential processing, while higher values
     * allow concurrent processing of multiple messages.
     * 
     * @return the maximum concurrency level
     */
    int concurrency() default 1;

    /**
     * Message acknowledgment mode.
     * 
     * <p>Determines when and how messages are acknowledged. This affects
     * reliability and message delivery guarantees.
     * 
     * @return the acknowledgment mode
     */
    AckMode ackMode() default AckMode.AUTO;

    /**
     * Timeout for message processing in milliseconds.
     * 
     * <p>If message processing takes longer than this timeout, the
     * message will be considered failed and handled according to
     * the error handling strategy.
     * 
     * @return the processing timeout in milliseconds
     */
    long processingTimeoutMs() default 30000;

    /**
     * SpEL expression to filter which messages to process.
     * 
     * <p>The expression has access to:
     * <ul>
     * <li>{@code #message} - the received message</li>
     * <li>{@code #topic} - the topic (if applicable)</li>
     * <li>{@code #headers} - message headers</li>
     * </ul>
     * 
     * @return the filter expression
     */
    String filter() default "";

    /**
     * SpEL expression to transform the message before processing.
     * 
     * <p>Same context as {@link #filter()} is available.
     * 
     * @return the transformation expression
     */
    String transform() default "";

    /**
     * Error handling strategy for processing failures.
     * 
     * @return the error handling strategy
     */
    ErrorHandling errorHandling() default ErrorHandling.LOG_AND_CONTINUE;

    /**
     * Dead letter queue endpoint for failed messages.
     * 
     * <p>When specified, messages that fail processing after retries
     * will be forwarded to this endpoint for manual inspection or
     * alternative processing.
     * 
     * @return the dead letter queue endpoint
     */
    String deadLetterQueue() default "";

    /**
     * Maximum number of retry attempts for failed messages.
     * 
     * <p>When message processing fails, it will be retried this many
     * times before being sent to the dead letter queue or discarded.
     * 
     * @return the maximum retry attempts
     */
    int maxRetries() default 3;

    /**
     * Delay between retry attempts in milliseconds.
     * 
     * <p>Uses exponential backoff: delay = base * (2 ^ attempt).
     * 
     * @return the base retry delay in milliseconds
     */
    long retryDelayMs() default 1000;

    /**
     * Priority level for this subscriber.
     * 
     * <p>Higher priority subscribers will be started before lower priority ones
     * during application startup.
     * 
     * @return the priority level
     */
    int priority() default 0;

    /**
     * Auto-start behavior for this subscriber.
     * 
     * <p>When false, the subscriber must be started manually via the
     * ZeroMQ management interfaces.
     * 
     * @return true to start automatically
     */
    boolean autoStart() default true;

    /**
     * Container factory to use for creating the message listener container.
     * 
     * <p>References a bean name that implements the appropriate container factory
     * interface. If empty, uses the default container factory.
     * 
     * @return the container factory bean name
     */
    String containerFactory() default "";

    /**
     * Group ID for grouped consumers (similar to Kafka consumer groups).
     * 
     * <p>When specified, multiple instances of the same group will load-balance
     * message consumption. This is useful for horizontal scaling.
     * 
     * @return the consumer group ID
     */
    String group() default "";

    /**
     * Messaging patterns supported by the subscriber.
     */
    enum Pattern {
        /**
         * Subscribe pattern (one-to-many from publisher).
         */
        SUB,
        
        /**
         * Pull pattern (load balanced, many-to-one from pushers).
         */
        PULL,
        
        /**
         * Reply pattern (respond to requests).
         * Note: When using REP pattern, the method must have a return value
         * that will be sent as the reply.
         */
        REP,
        
        /**
         * Router pattern (advanced routing, handles multiple client connections).
         */
        ROUTER
    }

    /**
     * Message acknowledgment modes.
     */
    enum AckMode {
        /**
         * Automatically acknowledge messages after successful processing.
         */
        AUTO,
        
        /**
         * Manually acknowledge messages via provided acknowledgment context.
         */
        MANUAL,
        
        /**
         * No acknowledgment (fire and forget).
         */
        NONE
    }

    /**
     * Error handling strategies for processing failures.
     */
    enum ErrorHandling {
        /**
         * Log the error and continue processing other messages.
         */
        LOG_AND_CONTINUE,
        
        /**
         * Log the error, stop this subscriber, but continue application.
         */
        LOG_AND_STOP,
        
        /**
         * Silently ignore errors and continue.
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
         * Use a custom error handler bean (specified via configuration).
         */
        CUSTOM
    }
} 