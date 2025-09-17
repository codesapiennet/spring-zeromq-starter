package com.example.zeromq.annotation;

import java.lang.annotation.*;

/**
 * Annotation for declarative ZeroMQ message publishing.
 * 
 * <p>Methods annotated with {@code @ZeroMQPublisher} will automatically publish
 * their return values to the specified ZeroMQ endpoint. The method can optionally
 * take a topic parameter to dynamically control message routing.
 * 
 * <p>This annotation supports both simple publishing and publish-subscribe patterns
 * with topic-based routing. Security can be configured through properties or
 * additional annotations.
 * 
 * <p>Usage Examples:
 * <pre>
 * // Simple publishing
 * {@literal @}ZeroMQPublisher(endpoint = "tcp://*:5555")
 * public String publishMessage() {
 *     return "Hello, World!";
 * }
 * 
 * // Topic-based publishing
 * {@literal @}ZeroMQPublisher(endpoint = "tcp://*:5555", topic = "news")
 * public NewsEvent publishNews(NewsEvent event) {
 *     return event;
 * }
 * 
 * // Dynamic topic from parameter
 * {@literal @}ZeroMQPublisher(endpoint = "tcp://*:5555", topicParameter = "category")
 * public Message publishToCategory(String category, Message msg) {
 *     return msg;
 * }
 * </pre>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZeroMQPublisher {

    /**
     * The ZeroMQ endpoint to bind to for publishing.
     * 
     * <p>Supports Spring expression language (SpEL) for dynamic endpoint resolution.
     * Examples:
     * <ul>
     * <li>{@code "tcp://*:5555"} - Static endpoint</li>
     * <li>{@code "${app.zeromq.publisher.endpoint}"} - Property reference</li>
     * <li>{@code "#{publisherConfig.getEndpoint()}"} - SpEL expression</li>
     * </ul>
     * 
     * @return the endpoint to bind to
     */
    String endpoint();

    /**
     * The topic to publish messages to (for PUB/SUB pattern).
     * 
     * <p>If empty, messages will be published without a topic prefix.
     * Can use SpEL expressions for dynamic topics.
     * 
     * @return the topic name
     */
    String topic() default "";

    /**
     * Name of method parameter to use as the topic.
     * 
     * <p>When specified, the value of the named parameter will be used as the
     * topic for each published message. This allows dynamic topic selection
     * at runtime. The parameter must be of type String.
     * 
     * @return the parameter name to use as topic
     */
    String topicParameter() default "";

    /**
     * The messaging pattern to use.
     * 
     * @return the messaging pattern
     */
    Pattern pattern() default Pattern.PUB;

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
     * Whether to publish asynchronously.
     * 
     * <p>When true, the method will return immediately and publishing
     * will happen in a background thread. This can improve performance
     * but messages may be lost if the application shuts down quickly.
     * 
     * @return true for asynchronous publishing
     */
    boolean async() default false;

    /**
     * Timeout for synchronous publishing operations.
     * 
     * <p>Specified in milliseconds. Only applies when {@link #async()} is false.
     * A value of 0 means no timeout (wait indefinitely).
     * 
     * @return the timeout in milliseconds
     */
    long timeoutMs() default 5000;

    /**
     * Whether to publish the method's return value.
     * 
     * <p>When false, only the side effects of the method call occur,
     * and no message is published. This is useful for conditional publishing
     * based on method logic.
     * 
     * @return true to publish return value
     */
    boolean publishReturnValue() default true;

    /**
     * Name of method parameter containing the message to publish.
     * 
     * <p>When specified, the value of the named parameter will be published
     * instead of the method's return value. Useful for methods that need to
     * publish input parameters or transformed versions of them.
     * 
     * @return the parameter name containing the message
     */
    String messageParameter() default "";

    /**
     * SpEL expression to transform the message before publishing.
     * 
     * <p>The expression has access to:
     * <ul>
     * <li>{@code #root} - the original message</li>
     * <li>{@code #method} - the method being intercepted</li>
     * <li>{@code #args} - array of method arguments</li>
     * <li>{@code #target} - the target object</li>
     * </ul>
     * 
     * @return the transformation expression
     */
    String transform() default "";

    /**
     * Condition for when to publish the message.
     * 
     * <p>SpEL expression that must evaluate to true for publishing to occur.
     * Same context as {@link #transform()} is available.
     * 
     * @return the condition expression
     */
    String condition() default "";

    /**
     * Additional headers to include with the message.
     * 
     * <p>Array of "key=value" pairs that will be added as message headers.
     * Values can use SpEL expressions.
     * 
     * @return array of header specifications
     */
    String[] headers() default {};

    /**
     * Priority level for this publisher.
     * 
     * <p>Higher priority publishers will be processed before lower priority ones
     * when multiple publishers are configured for the same method.
     * 
     * @return the priority level
     */
    int priority() default 0;

    /**
     * Error handling strategy for publishing failures.
     * 
     * @return the error handling strategy
     */
    ErrorHandling errorHandling() default ErrorHandling.LOG_AND_CONTINUE;

    /**
     * Messaging patterns supported by the publisher.
     */
    enum Pattern {
        /**
         * Publish-Subscribe pattern (one-to-many).
         */
        PUB,
        
        /**
         * Push-Pull pattern (load balanced distribution).
         */
        PUSH,
        
        /**
         * Request-Reply pattern (synchronous communication).
         * Note: When using REQ pattern, the method must have a return type
         * and the published message will be the request.
         */
        REQ
    }

    /**
     * Error handling strategies for publishing failures.
     */
    enum ErrorHandling {
        /**
         * Log the error and continue method execution.
         */
        LOG_AND_CONTINUE,
        
        /**
         * Log the error and rethrow it, interrupting method execution.
         */
        LOG_AND_RETHROW,
        
        /**
         * Silently ignore errors and continue.
         */
        IGNORE,
        
        /**
         * Rethrow errors without logging.
         */
        RETHROW,
        
        /**
         * Use a custom error handler bean (specified via configuration).
         */
        CUSTOM
    }
} 