package com.example.zeromq.annotation.processor;

import com.example.zeromq.annotation.ZeroMQPublisher;
import com.example.zeromq.autoconfig.ZeroMqTemplate;
import com.example.zeromq.core.ZmqSecurityConfig;
import com.example.zeromq.core.exception.ZeroMQException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOP aspect for handling {@code @ZeroMQPublisher} annotations.
 * 
 * <p>This aspect intercepts method calls annotated with {@code @ZeroMQPublisher}
 * and automatically publishes the return value or specified parameter to the
 * configured ZeroMQ endpoint. It supports:
 * <ul>
 * <li>Dynamic endpoint resolution using SpEL expressions</li>
 * <li>Topic-based routing with parameter extraction</li>
 * <li>Conditional publishing based on method results</li>
 * <li>Message transformation using SpEL expressions</li>
 * <li>Asynchronous and synchronous publishing modes</li>
 * <li>Comprehensive error handling strategies</li>
 * </ul>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Aspect
@Component
public class ZeroMQPublisherAspect {

    private static final Logger log = LoggerFactory.getLogger(ZeroMQPublisherAspect.class);
    
    private final ZeroMqTemplate template;
    private final Environment environment;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    
    // Performance optimization: cache compiled expressions
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong publicationsAttempted = new AtomicLong(0);
    private final AtomicLong publicationsSuccessful = new AtomicLong(0);
    private final AtomicLong publicationsFailed = new AtomicLong(0);
    private final AtomicLong asyncPublications = new AtomicLong(0);

    /**
     * Create a new publisher aspect.
     * 
     * @param template the ZeroMQ template for publishing
     */
    public ZeroMQPublisherAspect(ZeroMqTemplate template) {
        this.template = template;
        this.environment = null; // Will be injected if available
    }

    /**
     * Create a new publisher aspect with environment support.
     * 
     * @param template the ZeroMQ template for publishing
     * @param environment the Spring environment for property resolution
     */
    public ZeroMQPublisherAspect(ZeroMqTemplate template, Environment environment) {
        this.template = template;
        this.environment = environment;
    }

    /**
     * Intercept methods annotated with @ZeroMQPublisher.
     * 
     * @param joinPoint the method join point
     * @param publisherAnnotation the publisher annotation
     * @return the original method result
     * @throws Throwable if method execution or publishing fails
     */
    @Around("@annotation(publisherAnnotation)")
    public Object handlePublisher(ProceedingJoinPoint joinPoint, ZeroMQPublisher publisherAnnotation) throws Throwable {
        if (template == null) {
            log.warn("ZeroMqTemplate not available - skipping publication for method: {}", 
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }
        
        long startTime = System.nanoTime();
        String correlationId = generateCorrelationId();
        publicationsAttempted.incrementAndGet();
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        
        log.debug("Processing @ZeroMQPublisher on method: {} with correlationId: {}", 
                 method.getName(), correlationId);
        
        try {
            // Execute the original method
            Object result = joinPoint.proceed();
            
            // Process the publishing
            processPublishing(publisherAnnotation, method, args, result, correlationId, startTime);
            
            return result;
            
        } catch (Throwable e) {
            handlePublishingError(publisherAnnotation, method, e, correlationId);
            throw e; // Re-throw the original exception
        }
    }

    /**
     * Process the message publishing based on annotation configuration.
     */
    private void processPublishing(ZeroMQPublisher annotation, Method method, Object[] args, 
                                 Object result, String correlationId, long startTime) {
        
        try {
            // Check publishing condition
            if (!evaluateCondition(annotation, method, args, result)) {
                log.trace("Publishing condition not met for method: {}", method.getName());
                return;
            }
            
            // Determine the message to publish
            Object messageToPublish = determineMessage(annotation, method, args, result);
            
            if (messageToPublish == null && annotation.publishReturnValue()) {
                log.trace("No message to publish and publishReturnValue=false for method: {}", method.getName());
                return;
            }
            
            // Apply message transformation if specified
            if (StringUtils.hasText(annotation.transform())) {
                messageToPublish = transformMessage(annotation, method, args, result, messageToPublish);
            }
            
            // Resolve endpoint and topic
            String endpoint = resolveEndpoint(annotation, method, args, result);
            String topic = resolveTopic(annotation, method, args, result);
            
            // Add headers if specified
            Map<String, Object> headers = resolveHeaders(annotation, method, args, result);
            
            // Perform the publishing
            if (annotation.async()) {
                publishAsync(endpoint, topic, messageToPublish, annotation, correlationId, headers);
                asyncPublications.incrementAndGet();
            } else {
                publishSync(endpoint, topic, messageToPublish, annotation, correlationId, headers);
            }
            
            publicationsSuccessful.incrementAndGet();
            
            long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            log.debug("Successfully published message for method: {} in {}ms", method.getName(), duration);
            
        } catch (Exception e) {
            publicationsFailed.incrementAndGet();
            handlePublishingError(annotation, method, e, correlationId);
        }
    }

    /**
     * Evaluate the publishing condition using SpEL.
     */
    private boolean evaluateCondition(ZeroMQPublisher annotation, Method method, Object[] args, Object result) {
        if (!StringUtils.hasText(annotation.condition())) {
            return true; // No condition means always publish
        }
        
        try {
            Expression expression = getOrCompileExpression(annotation.condition());
            EvaluationContext context = createEvaluationContext(method, args, result);
            
            Boolean conditionResult = expression.getValue(context, Boolean.class);
            return conditionResult != null && conditionResult;
            
        } catch (Exception e) {
            log.warn("Failed to evaluate condition '{}' for method {}: {}", 
                    annotation.condition(), method.getName(), e.getMessage());
            return false; // Fail safe - don't publish if condition evaluation fails
        }
    }

    /**
     * Determine what message to publish based on annotation configuration.
     */
    private Object determineMessage(ZeroMQPublisher annotation, Method method, Object[] args, Object result) {
        // Check if a specific parameter should be published
        if (StringUtils.hasText(annotation.messageParameter())) {
            return getParameterValue(annotation.messageParameter(), method, args);
        }
        
        // Default to publishing the return value
        if (annotation.publishReturnValue()) {
            return result;
        }
        
        return null;
    }

    /**
     * Transform the message using SpEL expression.
     */
    private Object transformMessage(ZeroMQPublisher annotation, Method method, Object[] args, 
                                  Object result, Object originalMessage) {
        try {
            Expression expression = getOrCompileExpression(annotation.transform());
            EvaluationContext context = createEvaluationContext(method, args, result, originalMessage);
            
            return expression.getValue(context);
            
        } catch (Exception e) {
            log.warn("Failed to transform message using expression '{}' for method {}: {}", 
                    annotation.transform(), method.getName(), e.getMessage());
            return originalMessage; // Return original message if transformation fails
        }
    }

    /**
     * Resolve the endpoint using SpEL or property placeholders.
     */
    private String resolveEndpoint(ZeroMQPublisher annotation, Method method, Object[] args, Object result) {
        String endpoint = annotation.endpoint();
        
        // Handle property placeholders
        if (environment != null && endpoint.contains("${")) {
            endpoint = environment.resolvePlaceholders(endpoint);
        }
        
        // Handle SpEL expressions
        if (endpoint.contains("#{")) {
            try {
                Expression expression = getOrCompileExpression(endpoint);
                EvaluationContext context = createEvaluationContext(method, args, result);
                Object resolved = expression.getValue(context);
                endpoint = resolved != null ? resolved.toString() : endpoint;
            } catch (Exception e) {
                log.warn("Failed to resolve endpoint expression '{}' for method {}: {}", 
                        endpoint, method.getName(), e.getMessage());
            }
        }
        
        return endpoint;
    }

    /**
     * Resolve the topic for publishing.
     */
    private String resolveTopic(ZeroMQPublisher annotation, Method method, Object[] args, Object result) {
        // Check if topic should come from a parameter
        if (StringUtils.hasText(annotation.topicParameter())) {
            Object topicValue = getParameterValue(annotation.topicParameter(), method, args);
            return topicValue != null ? topicValue.toString() : "";
        }
        
        String topic = annotation.topic();
        
        // Handle property placeholders
        if (environment != null && topic.contains("${")) {
            topic = environment.resolvePlaceholders(topic);
        }
        
        // Handle SpEL expressions
        if (topic.contains("#{")) {
            try {
                Expression expression = getOrCompileExpression(topic);
                EvaluationContext context = createEvaluationContext(method, args, result);
                Object resolved = expression.getValue(context);
                topic = resolved != null ? resolved.toString() : topic;
            } catch (Exception e) {
                log.warn("Failed to resolve topic expression '{}' for method {}: {}", 
                        topic, method.getName(), e.getMessage());
            }
        }
        
        return topic;
    }

    /**
     * Resolve headers from annotation configuration.
     */
    private Map<String, Object> resolveHeaders(ZeroMQPublisher annotation, Method method, 
                                             Object[] args, Object result) {
        if (annotation.headers().length == 0) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> headers = new HashMap<>();
        EvaluationContext context = createEvaluationContext(method, args, result);
        
        for (String headerSpec : annotation.headers()) {
            String[] parts = headerSpec.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String valueExpr = parts[1].trim();
                
                try {
                    Object value;
                    if (valueExpr.contains("#{") || valueExpr.contains("${")) {
                        Expression expression = getOrCompileExpression(valueExpr);
                        value = expression.getValue(context);
                    } else {
                        value = valueExpr;
                    }
                    
                    headers.put(key, value);
                    
                } catch (Exception e) {
                    log.warn("Failed to resolve header '{}' for method {}: {}", 
                            headerSpec, method.getName(), e.getMessage());
                }
            }
        }
        
        return headers;
    }

    /**
     * Publish message asynchronously.
     */
    private void publishAsync(String endpoint, String topic, Object message, 
                             ZeroMQPublisher annotation, String correlationId, 
                             Map<String, Object> headers) {
        
        CompletableFuture.runAsync(() -> {
            try {
                doPublish(endpoint, topic, message, annotation, correlationId, headers);
            } catch (Exception e) {
                log.error("Async publishing failed for endpoint {} with correlationId {}: {}", 
                         endpoint, correlationId, e.getMessage());
            }
        });
    }

    /**
     * Publish message synchronously.
     */
    private void publishSync(String endpoint, String topic, Object message, 
                            ZeroMQPublisher annotation, String correlationId, 
                            Map<String, Object> headers) {
        
        doPublish(endpoint, topic, message, annotation, correlationId, headers);
    }

    /**
     * Perform the actual message publishing.
     */
    private void doPublish(String endpoint, String topic, Object message, 
                          ZeroMQPublisher annotation, String correlationId, 
                          Map<String, Object> headers) {
        
        // TODO: In a full implementation, we would:
        // 1. Resolve security configuration
        // 2. Add headers to message
        // 3. Handle different patterns (PUB/SUB, PUSH/PULL, REQ/REP)
        
        switch (annotation.pattern()) {
            case PUB -> {
                if (StringUtils.hasText(topic)) {
                    template.publish(endpoint, topic, message);
                } else {
                    template.publish(endpoint, "", message);
                }
            }
            case PUSH -> template.push(endpoint, message);
            case REQ -> {
                // For REQ pattern, we would need to handle the response
                log.warn("REQ pattern not fully implemented in aspect - use template directly");
                template.publish(endpoint, topic, message);
            }
        }
    }

    /**
     * Handle publishing errors based on error handling strategy.
     */
    private void handlePublishingError(ZeroMQPublisher annotation, Method method, 
                                     Throwable error, String correlationId) {
        
        String errorMessage = String.format("Publishing failed for method %s with correlationId %s: %s", 
                                           method.getName(), correlationId, error.getMessage());
        
        switch (annotation.errorHandling()) {
            case LOG_AND_CONTINUE -> log.error(errorMessage, error);
            case LOG_AND_RETHROW -> {
                log.error(errorMessage, error);
                throw new ZeroMQException("Publishing failed", error);
            }
            case IGNORE -> { /* Do nothing */ }
            case RETHROW -> throw new ZeroMQException("Publishing failed", error);
            case CUSTOM -> {
                // TODO: Implement custom error handler lookup
                log.error(errorMessage + " (custom error handler not implemented)", error);
            }
        }
    }

    /**
     * Get a parameter value by name.
     */
    private Object getParameterValue(String parameterName, Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            if (parameterName.equals(parameters[i].getName())) {
                return i < args.length ? args[i] : null;
            }
        }
        
        log.warn("Parameter '{}' not found in method {}", parameterName, method.getName());
        return null;
    }

    /**
     * Create SpEL evaluation context with method information.
     */
    private EvaluationContext createEvaluationContext(Method method, Object[] args, Object result) {
        return createEvaluationContext(method, args, result, null);
    }

    /**
     * Create SpEL evaluation context with method information and root object.
     */
    private EvaluationContext createEvaluationContext(Method method, Object[] args, Object result, Object root) {
        StandardEvaluationContext context = new StandardEvaluationContext(root);
        
        context.setVariable("method", method);
        context.setVariable("args", args);
        context.setVariable("result", result);
        context.setVariable("root", root);
        
        // Add named parameters
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String paramName = parameters[i].getName();
            context.setVariable(paramName, args[i]);
        }
        
        return context;
    }

    /**
     * Get or compile a SpEL expression (with caching).
     */
    private Expression getOrCompileExpression(String expressionString) {
        return expressionCache.computeIfAbsent(expressionString, expressionParser::parseExpression);
    }

    /**
     * Generate a correlation ID for tracing.
     */
    private String generateCorrelationId() {
        return "pub-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get publishing statistics.
     * 
     * @return statistics map
     */
    public Map<String, Long> getStatistics() {
        return Map.of(
            "publicationsAttempted", publicationsAttempted.get(),
            "publicationsSuccessful", publicationsSuccessful.get(),
            "publicationsFailed", publicationsFailed.get(),
            "asyncPublications", asyncPublications.get(),
            "expressionsCached", (long) expressionCache.size()
        );
    }

    /**
     * Reset statistics.
     */
    public void resetStatistics() {
        publicationsAttempted.set(0);
        publicationsSuccessful.set(0);
        publicationsFailed.set(0);
        asyncPublications.set(0);
    }
} 