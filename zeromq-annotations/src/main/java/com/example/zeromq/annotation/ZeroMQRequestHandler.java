package com.example.zeromq.annotation;

import java.lang.annotation.*;

/**
 * Annotation for ZeroMQ request-reply pattern request handling methods.
 * 
 * <p>Methods annotated with {@code @ZeroMQRequestHandler} automatically handle
 * incoming requests in a request-reply messaging pattern. The method's return
 * value is automatically sent back as the reply to the requesting client.
 * 
 * <p>This annotation is specifically designed for synchronous request-reply
 * communication patterns where:
 * <ul>
 * <li>Clients send requests and wait for replies</li>
 * <li>Servers process requests and return responses</li>
 * <li>Each request expects exactly one reply</li>
 * <li>Correlation IDs are handled automatically</li>
 * </ul>
 * 
 * Usage Examples:
 * <pre>
 * // Simple request handler
 * {@literal @}ZeroMQRequestHandler(endpoint = "tcp://*:5555")
 * public String processRequest(String request) {
 *     return "Processed: " + request;
 * }
 * 
 * // Complex request handler with validation
 * {@literal @}ZeroMQRequestHandler(
 *     endpoint = "tcp://*:5556",
 *     validate = "{@code #request != null && #request.isValid()}",
 *     timeout = 10000
 * )
 * public ProcessingResult handleBusinessRequest(BusinessRequest request) {
 *     // Process the business request
 *     return new ProcessingResult(request.getId(), "SUCCESS");
 * }
 * 
 * // Handler with custom error responses
 * {@literal @}ZeroMQRequestHandler(
 *     endpoint = "tcp://*:5557",
 *     errorResponse = "#{new ErrorResponse(#error.message)}"
 * )
 * public DataResponse processDataRequest(DataRequest request) {
 *     // Process data request
 *     return new DataResponse(processData(request.getData()));
 * }
 * </pre>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZeroMQRequestHandler {

    /**
     * The ZeroMQ endpoint to bind to for handling requests.
     * 
     * <p>Supports Spring expression language (SpEL) for dynamic endpoint resolution.
     * Examples:
     * <ul>
     * <li>{@code "tcp://*:5555"} - Static endpoint</li>
     * <li>{@code "${app.zeromq.request.endpoint}"} - Property reference</li>
     * <li>{@code "#{requestConfig.getEndpoint()}"} - SpEL expression</li>
     * </ul>
     * 
     * @return the endpoint to bind to
     */
    String endpoint();

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
     * Request validation expression.
     * 
     * <p>SpEL expression that must evaluate to true for the request to be processed.
     * The expression has access to:
     * <ul>
     * <li>{@code #request} - the received request</li>
     * <li>{@code #headers} - request headers</li>
     * <li>{@code #correlationId} - the request correlation ID</li>
     * </ul>
     * 
     * @return the validation expression
     */
    String validate() default "";

    /**
     * Request transformation expression.
     * 
     * <p>SpEL expression to transform the request before processing.
     * Same context as {@link #validate()} is available.
     * 
     * @return the transformation expression
     */
    String transform() default "";

    /**
     * Response transformation expression.
     * 
     * <p>SpEL expression to transform the response before sending.
     * The expression has access to:
     * <ul>
     * <li>{@code #response} - the method return value</li>
     * <li>{@code #request} - the original request</li>
     * <li>{@code #headers} - request headers</li>
     * <li>{@code #correlationId} - the request correlation ID</li>
     * </ul>
     * 
     * @return the response transformation expression
     */
    String transformResponse() default "";

    /**
     * Custom error response expression.
     * 
     * <p>SpEL expression to generate custom error responses when processing fails.
     * The expression has access to:
     * <ul>
     * <li>{@code #error} - the exception that occurred</li>
     * <li>{@code #request} - the original request</li>
     * <li>{@code #correlationId} - the request correlation ID</li>
     * </ul>
     * 
     * @return the error response expression
     */
    String errorResponse() default "";

    /**
     * Maximum processing time for requests in milliseconds.
     * 
     * <p>If request processing takes longer than this time, a timeout
     * error will be returned to the client.
     * 
     * @return the timeout in milliseconds
     */
    long timeout() default 30000;

    /**
     * Maximum number of concurrent requests to process.
     * 
     * <p>Controls the parallelism of request processing for this handler.
     * 
     * @return the maximum concurrency level
     */
    int concurrency() default 1;

    /**
     * Priority level for this request handler.
     * 
     * <p>Higher priority handlers will be started before lower priority ones
     * during application startup.
     * 
     * @return the priority level
     */
    int priority() default 0;

    /**
     * Request queue size for buffering incoming requests.
     * 
     * <p>When the handler is busy, incoming requests will be buffered
     * up to this limit. Additional requests will be rejected.
     * 
     * @return the request queue size
     */
    int queueSize() default 1000;

    /**
     * Whether to enable automatic correlation ID handling.
     * 
     * <p>When true, correlation IDs will be extracted from requests
     * and included in responses automatically.
     * 
     * @return true to enable correlation ID handling
     */
    boolean correlationIdEnabled() default true;

    /**
     * Header name for correlation ID.
     * 
     * <p>The name of the message header that contains the correlation ID.
     * 
     * @return the correlation ID header name
     */
    String correlationIdHeader() default "correlationId";

    /**
     * Additional headers to include in responses.
     * 
     * <p>Array of "key=value" pairs that will be added as response headers.
     * Values can use SpEL expressions.
     * 
     * @return array of header specifications
     */
    String[] responseHeaders() default {};

    /**
     * Error handling strategy for processing failures.
     * 
     * @return the error handling strategy
     */
    ErrorHandling errorHandling() default ErrorHandling.SEND_ERROR_RESPONSE;

    /**
     * Whether to enable metrics collection for this handler.
     * 
     * @return true to enable metrics
     */
    boolean enableMetrics() default true;

    /**
     * Auto-start behavior for this request handler.
     * 
     * <p>When false, the handler must be started manually.
     * 
     * @return true to start automatically
     */
    boolean autoStart() default true;

    /**
     * Error handling strategies for request processing failures.
     */
    enum ErrorHandling {
        /**
         * Send a standard error response to the client.
         */
        SEND_ERROR_RESPONSE,
        
        /**
         * Send a custom error response (using errorResponse expression).
         */
        SEND_CUSTOM_ERROR,
        
        /**
         * Log the error and send no response (client will timeout).
         */
        LOG_AND_NO_RESPONSE,
        
        /**
         * Retry processing with exponential backoff.
         */
        RETRY,
        
        /**
         * Use a custom error handler.
         */
        CUSTOM
    }
} 