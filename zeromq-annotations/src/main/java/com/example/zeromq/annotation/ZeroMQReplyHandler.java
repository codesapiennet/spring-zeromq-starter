package com.example.zeromq.annotation;

import java.lang.annotation.*;

/**
 * Annotation for ZeroMQ request-reply pattern reply handling methods.
 * 
 * <p>Methods annotated with {@code @ZeroMQReplyHandler} handle incoming replies
 * from servers in a request-reply messaging pattern. This annotation is used
 * on the client side to process responses to previously sent requests.
 * 
 * <p>This annotation is specifically designed for asynchronous request-reply
 * communication where:
 * <ul>
 * <li>Clients send requests and register reply handlers</li>
 * <li>Reply handlers are invoked when responses arrive</li>
 * <li>Correlation IDs are used to match replies to requests</li>
 * <li>Timeout handling for missing or delayed replies</li>
 * </ul>
 * 
 * Usage Examples:
 * <pre>
 * // Simple reply handler
 * {@literal @}ZeroMQReplyHandler
 * public void handleReply(String reply) {
 *     System.out.println("Received reply: " + reply);
 * }
 * 
 * // Reply handler with correlation ID
 * {@literal @}ZeroMQReplyHandler(correlationIdParameter = "correlationId")
 * public void handleBusinessReply(String correlationId, BusinessResponse reply) {
 *     // Process the business reply with correlation context
 *     businessService.processReply(correlationId, reply);
 * }
 * 
 * // Conditional reply handler
 * {@literal @}ZeroMQReplyHandler(
 *     condition = "#reply.status == 'SUCCESS'",
 *     timeout = 15000
 * )
 * public void handleSuccessReply(ProcessingResult reply) {
 *     // Handle successful processing results only
 *     successService.processSuccess(reply);
 * }
 * </pre>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZeroMQReplyHandler {

    /**
     * Unique identifier for this reply handler.
     * 
     * <p>If empty, a unique ID will be generated based on the method signature.
     * This ID is used to register and manage the reply handler lifecycle.
     * 
     * @return the handler ID
     */
    String id() default "";

    /**
     * Condition for when to invoke this reply handler.
     * 
     * <p>SpEL expression that must evaluate to true for the handler to be invoked.
     * The expression has access to:
     * <ul>
     * <li>{@code #reply} - the received reply message</li>
     * <li>{@code #correlationId} - the correlation ID</li>
     * <li>{@code #headers} - reply headers</li>
     * <li>{@code #requestTime} - timestamp when the original request was sent</li>
     * </ul>
     * 
     * @return the condition expression
     */
    String condition() default "";

    /**
     * Reply transformation expression.
     * 
     * <p>SpEL expression to transform the reply before processing.
     * Same context as {@link #condition()} is available.
     * 
     * @return the transformation expression
     */
    String transform() default "";

    /**
     * Name of method parameter that should receive the correlation ID.
     * 
     * <p>When specified, the correlation ID will be passed to the named
     * parameter, allowing the handler to access correlation context.
     * 
     * @return the correlation ID parameter name
     */
    String correlationIdParameter() default "";

    /**
     * Name of method parameter that should receive the request timestamp.
     * 
     * <p>When specified, the timestamp when the original request was sent
     * will be passed to the named parameter.
     * 
     * @return the request timestamp parameter name
     */
    String requestTimeParameter() default "";

    /**
     * Name of method parameter that should receive the reply headers.
     * 
     * <p>When specified, the reply message headers will be passed to
     * the named parameter as a Map.
     * 
     * @return the headers parameter name
     */
    String headersParameter() default "";

    /**
     * Expected reply types that this handler can process.
     * 
     * <p>If specified, the handler will only be invoked for replies
     * of the specified types.
     * 
     * @return array of expected reply types
     */
    Class<?>[] replyTypes() default {};

    /**
     * Timeout for waiting for replies in milliseconds.
     * 
     * <p>If no reply is received within this time, a timeout handler
     * will be invoked instead (if configured).
     * 
     * @return the timeout in milliseconds
     */
    long timeout() default 30000;

    /**
     * Whether to invoke this handler for timeout events.
     * 
     * <p>When true, this handler will be called with a null reply
     * when the timeout period expires.
     * 
     * @return true to handle timeouts
     */
    boolean handleTimeout() default false;

    /**
     * Whether this handler should run asynchronously.
     * 
     * <p>When true, the handler will be executed in a separate thread,
     * allowing the reply processing pipeline to continue immediately.
     * 
     * @return true for asynchronous execution
     */
    boolean async() default false;

    /**
     * Priority level for this reply handler.
     * 
     * <p>Higher priority handlers will be invoked before lower priority ones
     * when multiple handlers match the same reply.
     * 
     * @return the priority level
     */
    int priority() default 0;

    /**
     * Whether this is a one-time handler.
     * 
     * <p>When true, the handler will be automatically unregistered
     * after processing its first reply.
     * 
     * @return true for one-time handling
     */
    boolean oneTime() default true;

    /**
     * Maximum number of replies this handler should process.
     * 
     * <p>After processing this many replies, the handler will be
     * automatically unregistered. Use -1 for unlimited.
     * 
     * @return the maximum reply count
     */
    int maxReplies() default 1;

    /**
     * Error handling strategy for reply processing failures.
     * 
     * @return the error handling strategy
     */
    ErrorHandling errorHandling() default ErrorHandling.LOG_AND_CONTINUE;

    /**
     * Whether to enable metrics collection for this handler.
     * 
     * @return true to enable metrics
     */
    boolean enableMetrics() default true;

    /**
     * Custom attributes for this reply handler.
     * 
     * <p>Array of "key=value" pairs that can be used by custom processing logic.
     * Values can use SpEL expressions.
     * 
     * @return array of custom attributes
     */
    String[] attributes() default {};

    /**
     * Error handling strategies for reply processing failures.
     */
    enum ErrorHandling {
        /**
         * Log the error and continue processing other replies.
         */
        LOG_AND_CONTINUE,
        
        /**
         * Log the error and unregister this handler.
         */
        LOG_AND_UNREGISTER,
        
        /**
         * Silently ignore errors and continue.
         */
        IGNORE,
        
        /**
         * Retry processing the reply.
         */
        RETRY,
        
        /**
         * Use a custom error handler.
         */
        CUSTOM
    }
} 