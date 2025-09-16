package com.example.zeromq.annotation;

import java.lang.annotation.*;

/**
 * Annotation for general-purpose ZeroMQ message handling methods.
 * 
 * <p>Methods annotated with {@code @ZeroMQHandler} can handle messages from
 * multiple endpoints or topics using flexible routing criteria. This annotation
 * provides a more generic approach compared to the specialized subscriber patterns.
 * 
 * <p>Handler methods can be configured to:
 * <ul>
 * <li>Handle multiple message types with different processing logic</li>
 * <li>Route messages based on headers, topics, or content</li>
 * <li>Apply conditional processing with SpEL expressions</li>
 * <li>Transform messages before and after processing</li>
 * <li>Integrate with Spring's transaction management</li>
 * </ul>
 * 
 * <h3>Usage Examples:</h3>
 * <pre>
 * // Simple handler for any message type
 * {@literal @}ZeroMQHandler
 * public void handleAnyMessage(Object message) {
 *     // Process message
 * }
 * 
 * // Handler with topic filtering
 * {@literal @}ZeroMQHandler(topics = {"orders", "payments"})
 * public void handleBusinessEvents(String topic, BusinessEvent event) {
 *     // Process business events
 * }
 * 
 * // Conditional handler with transformation
 * {@literal @}ZeroMQHandler(
 *     condition = "#message instanceof com.example.HighPriorityEvent",
 *     transform = "#message.normalize()"
 * )
 * public void handleHighPriorityEvents(HighPriorityEvent event) {
 *     // Handle urgent events
 * }
 * </pre>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZeroMQHandler {

    /**
     * Unique identifier for this handler.
     * 
     * <p>If empty, a unique ID will be generated based on the method signature.
     * 
     * @return the handler ID
     */
    String id() default "";

    /**
     * Topics that this handler should process.
     * 
     * <p>If empty, the handler will process messages from all topics.
     * Can use SpEL expressions for dynamic topic resolution.
     * 
     * @return array of topics to handle
     */
    String[] topics() default {};

    /**
     * Message types that this handler can process.
     * 
     * <p>If empty, the handler will accept any message type that matches
     * the method parameter types.
     * 
     * @return array of message types
     */
    Class<?>[] messageTypes() default {};

    /**
     * Condition for when to invoke this handler.
     * 
     * <p>SpEL expression that must evaluate to true for the handler to be invoked.
     * The expression has access to:
     * <ul>
     * <li>{@code #message} - the received message</li>
     * <li>{@code #topic} - the message topic (if applicable)</li>
     * <li>{@code #headers} - message headers</li>
     * <li>{@code #endpoint} - the endpoint the message came from</li>
     * </ul>
     * 
     * @return the condition expression
     */
    String condition() default "";

    /**
     * SpEL expression to transform the message before processing.
     * 
     * <p>Same context as {@link #condition()} is available.
     * 
     * @return the transformation expression
     */
    String transform() default "";

    /**
     * Priority level for this handler.
     * 
     * <p>Higher priority handlers will be invoked before lower priority ones
     * when multiple handlers match the same message.
     * 
     * @return the priority level
     */
    int priority() default 0;

    /**
     * Whether this handler should run asynchronously.
     * 
     * <p>When true, the handler will be executed in a separate thread,
     * allowing the message processing pipeline to continue immediately.
     * 
     * @return true for asynchronous execution
     */
    boolean async() default false;

    /**
     * Maximum execution time for this handler in milliseconds.
     * 
     * <p>If the handler takes longer than this time, it will be interrupted
     * and treated as a timeout error.
     * 
     * @return the timeout in milliseconds
     */
    long timeoutMs() default 30000;

    /**
     * Error handling strategy for handler failures.
     * 
     * @return the error handling strategy
     */
    ErrorHandling errorHandling() default ErrorHandling.LOG_AND_CONTINUE;

    /**
     * Whether to enable transaction management for this handler.
     * 
     * <p>When true, the handler will be wrapped in a Spring transaction.
     * 
     * @return true to enable transactions
     */
    boolean transactional() default false;

    /**
     * Transaction propagation behavior when transactions are enabled.
     * 
     * @return the transaction propagation
     */
    TransactionPropagation transactionPropagation() default TransactionPropagation.REQUIRED;

    /**
     * Custom attributes for this handler.
     * 
     * <p>Array of "key=value" pairs that can be used by custom processing logic.
     * Values can use SpEL expressions.
     * 
     * @return array of custom attributes
     */
    String[] attributes() default {};

    /**
     * Error handling strategies for message processing failures.
     */
    enum ErrorHandling {
        /**
         * Log the error and continue processing other messages.
         */
        LOG_AND_CONTINUE,
        
        /**
         * Log the error and stop processing.
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
         * Use a custom error handler.
         */
        CUSTOM
    }

    /**
     * Transaction propagation behaviors.
     */
    enum TransactionPropagation {
        /**
         * Support a current transaction, create a new one if none exists.
         */
        REQUIRED,
        
        /**
         * Support a current transaction, execute non-transactionally if none exists.
         */
        SUPPORTS,
        
        /**
         * Support a current transaction, throw an exception if none exists.
         */
        MANDATORY,
        
        /**
         * Create a new transaction, suspend the current transaction if one exists.
         */
        REQUIRES_NEW,
        
        /**
         * Execute non-transactionally, suspend the current transaction if one exists.
         */
        NOT_SUPPORTED,
        
        /**
         * Execute non-transactionally, throw an exception if a transaction exists.
         */
        NEVER
    }
} 