package com.example.zeromq.annotation;

import java.lang.annotation.*;

/**
 * Annotation for specifying topic information in ZeroMQ messaging.
 * 
 * <p>This annotation can be used on method parameters to indicate that the
 * parameter should receive the topic information from the message, or on
 * methods to specify topic-based routing and filtering behavior.
 * 
 * <p>When used on parameters, the annotated parameter will be populated with:
 * <ul>
 * <li>The topic string for publish-subscribe messages</li>
 * <li>Routing key information for advanced routing</li>
 * <li>Message classification metadata</li>
 * </ul>
 * 
 * <p>When used on methods, it provides additional topic configuration for
 * publishers and subscribers beyond what's available in the main annotations.
 * 
 * <h3>Usage Examples:</h3>
 * <pre>
 * // Parameter annotation - receive topic information
 * {@literal @}ZeroMQSubscriber(endpoint = "tcp://localhost:5555")
 * public void handleMessage({@literal @}ZeroMQTopic String topic, Object message) {
 *     System.out.println("Received message on topic: " + topic);
 * }
 * 
 * // Method annotation - advanced topic configuration
 * {@literal @}ZeroMQTopic(
 *     pattern = "orders.*",
 *     priority = TopicPriority.HIGH,
 *     retentionTime = 3600000
 * )
 * {@literal @}ZeroMQSubscriber(endpoint = "tcp://localhost:5555")
 * public void handleOrderMessages(OrderEvent event) {
 *     // Handle order-related messages
 * }
 * 
 * // Dynamic topic routing
 * {@literal @}ZeroMQTopic(
 *     routingExpression = "#message.getCategory() + '.' + #message.getPriority()"
 * )
 * {@literal @}ZeroMQPublisher(endpoint = "tcp://*:5555")
 * public void publishCategorizedMessage(CategorizedMessage message) {
 *     // Topic will be determined dynamically
 * }
 * </pre>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZeroMQTopic {

    /**
     * Static topic name or pattern.
     * 
     * <p>When used on methods, this specifies the topic pattern for filtering.
     * Supports wildcards and regular expressions depending on the pattern type.
     * 
     * <p>When used on parameters, this is typically empty as the topic
     * comes from the actual message.
     * 
     * @return the topic pattern
     */
    String value() default "";

    /**
     * Alternative name for the topic pattern.
     * 
     * <p>Alias for {@link #value()} to improve readability.
     * 
     * @return the topic pattern
     */
    String pattern() default "";

    /**
     * Type of pattern matching to use.
     * 
     * @return the pattern type
     */
    PatternType patternType() default PatternType.WILDCARD;

    /**
     * SpEL expression for dynamic topic routing.
     * 
     * <p>When specified, this expression will be evaluated to determine
     * the topic at runtime. The expression has access to:
     * <ul>
     * <li>{@code #message} - the message being processed</li>
     * <li>{@code #headers} - message headers</li>
     * <li>{@code #method} - the current method</li>
     * <li>{@code #args} - method arguments</li>
     * </ul>
     * 
     * @return the routing expression
     */
    String routingExpression() default "";

    /**
     * Priority level for topic-based messages.
     * 
     * <p>Higher priority topics may receive preferential processing
     * or resource allocation.
     * 
     * @return the topic priority
     */
    TopicPriority priority() default TopicPriority.NORMAL;

    /**
     * Message retention time for this topic in milliseconds.
     * 
     * <p>Specifies how long messages on this topic should be retained
     * for late subscribers or replay scenarios. Use -1 for no retention.
     * 
     * @return the retention time in milliseconds
     */
    long retentionTime() default -1;

    /**
     * Maximum number of messages to retain for this topic.
     * 
     * <p>When the limit is reached, older messages will be discarded.
     * Use -1 for no limit.
     * 
     * @return the maximum retained messages
     */
    int maxRetainedMessages() default -1;

    /**
     * Whether messages on this topic should be persistent.
     * 
     * <p>Persistent messages survive server restarts and network failures.
     * 
     * @return true for persistent messages
     */
    boolean persistent() default false;

    /**
     * Compression type for messages on this topic.
     * 
     * @return the compression type
     */
    CompressionType compression() default CompressionType.NONE;

    /**
     * Serialization format for messages on this topic.
     * 
     * @return the serialization format
     */
    SerializationFormat serialization() default SerializationFormat.AUTO;

    /**
     * Content type for messages on this topic.
     * 
     * <p>Helps message converters choose appropriate serialization strategies.
     * 
     * @return the content type
     */
    String contentType() default "";

    /**
     * Tags for topic categorization and filtering.
     * 
     * <p>Array of tags that can be used for advanced filtering and routing.
     * 
     * @return array of topic tags
     */
    String[] tags() default {};

    /**
     * Custom metadata for this topic.
     * 
     * <p>Array of "key=value" pairs that provide additional topic information.
     * Values can use SpEL expressions.
     * 
     * @return array of metadata entries
     */
    String[] metadata() default {};

    /**
     * Whether to validate topic names against a schema.
     * 
     * @return true to enable validation
     */
    boolean validateName() default false;

    /**
     * Schema reference for topic name validation.
     * 
     * <p>References a validation schema bean or resource.
     * 
     * @return the schema reference
     */
    String validationSchema() default "";

    /**
     * Whether to enable topic-level metrics collection.
     * 
     * @return true to enable metrics
     */
    boolean enableMetrics() default true;

    /**
     * Types of pattern matching for topic filtering.
     */
    enum PatternType {
        /**
         * Exact string matching.
         */
        EXACT,
        
        /**
         * Wildcard matching with * and ? characters.
         */
        WILDCARD,
        
        /**
         * Regular expression matching.
         */
        REGEX,
        
        /**
         * Hierarchical topic matching (e.g., orders.*.urgent).
         */
        HIERARCHICAL,
        
        /**
         * Custom pattern matching using a pluggable matcher.
         */
        CUSTOM
    }

    /**
     * Priority levels for topic-based messages.
     */
    enum TopicPriority {
        /**
         * Low priority - processed when resources are available.
         */
        LOW,
        
        /**
         * Normal priority - standard processing order.
         */
        NORMAL,
        
        /**
         * High priority - processed before normal priority messages.
         */
        HIGH,
        
        /**
         * Critical priority - processed immediately.
         */
        CRITICAL
    }

    /**
     * Compression types for topic messages.
     */
    enum CompressionType {
        /**
         * No compression.
         */
        NONE,
        
        /**
         * GZIP compression.
         */
        GZIP,
        
        /**
         * LZ4 compression (fast).
         */
        LZ4,
        
        /**
         * Snappy compression (balanced).
         */
        SNAPPY,
        
        /**
         * DEFLATE compression.
         */
        DEFLATE
    }

    /**
     * Serialization formats for topic messages.
     */
    enum SerializationFormat {
        /**
         * Automatically detect format based on content type.
         */
        AUTO,
        
        /**
         * JSON serialization.
         */
        JSON,
        
        /**
         * Binary serialization.
         */
        BINARY,
        
        /**
         * Protocol Buffers.
         */
        PROTOBUF,
        
        /**
         * Apache Avro.
         */
        AVRO,
        
        /**
         * MessagePack.
         */
        MESSAGEPACK,
        
        /**
         * Plain text.
         */
        TEXT
    }
} 