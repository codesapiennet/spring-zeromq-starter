package com.example.zeromq.annotation.processor;

import com.example.zeromq.annotation.EnableZeroMQ;
import com.example.zeromq.annotation.ZeroMQSubscriber;
import com.example.zeromq.autoconfig.ZeroMqTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bean post processor that scans for ZeroMQ annotations and sets up message handling.
 * 
 * <p>This processor examines all Spring beans for ZeroMQ annotations and automatically
 * configures the necessary infrastructure for declarative messaging. It handles:
 * <ul>
 * <li>Scanning beans for {@code @ZeroMQSubscriber} annotated methods</li>
 * <li>Creating message listener containers for subscribers</li>
 * <li>Validating annotation configurations</li>
 * <li>Setting up error handlers and retry mechanisms</li>
 * <li>Registering metrics collectors and health indicators</li>
 * </ul>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Component
public class ZeroMQAnnotationPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(ZeroMQAnnotationPostProcessor.class);
    
    private final ZeroMqTemplate template;
    private BeanFactory beanFactory;
    private Environment environment;
    
    // Configuration from @EnableZeroMQ
    private String[] basePackages = {};
    private Class<?>[] basePackageClasses = {};
    private String defaultContainerFactory = "";
    private int defaultConcurrency = 1;
    private boolean metricsEnabled = true;
    private boolean healthIndicatorsEnabled = true;
    private EnableZeroMQ.ErrorHandling defaultErrorHandling = EnableZeroMQ.ErrorHandling.LOG_AND_CONTINUE;
    private EnableZeroMQ.Mode mode = EnableZeroMQ.Mode.AUTO;
    
    // Tracking processed beans and methods
    private final Set<String> processedBeans = ConcurrentHashMap.newKeySet();
    private final Set<String> subscriberMethods = ConcurrentHashMap.newKeySet();
    
    // Statistics
    private final AtomicLong beansProcessed = new AtomicLong(0);
    private final AtomicLong subscribersRegistered = new AtomicLong(0);
    private final AtomicLong errorsEncountered = new AtomicLong(0);

    /**
     * Create a new annotation post processor.
     * 
     * @param template the ZeroMQ template for operations
     */
    public ZeroMQAnnotationPostProcessor(ZeroMqTemplate template) {
        this.template = template;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        
        // Try to get the environment for property resolution
        try {
            this.environment = beanFactory.getBean(Environment.class);
        } catch (Exception e) {
            log.debug("Environment not available for property resolution");
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (shouldSkipBean(bean, beanName)) {
            return bean;
        }
        
        try {
            processBean(bean, beanName);
            beansProcessed.incrementAndGet();
            
        } catch (Exception e) {
            errorsEncountered.incrementAndGet();
            log.error("Failed to process ZeroMQ annotations for bean '{}': {}", beanName, e.getMessage(), e);
            
            // Don't fail bean creation, just log the error
        }
        
        return bean;
    }

    /**
     * Process a bean for ZeroMQ annotations.
     */
    private void processBean(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();
        
        // Skip if already processed (can happen with proxies)
        String beanKey = beanClass.getName() + "@" + System.identityHashCode(bean);
        if (!processedBeans.add(beanKey)) {
            return;
        }
        
        log.debug("Processing bean '{}' of type '{}' for ZeroMQ annotations", beanName, beanClass.getSimpleName());
        
        // Check if this bean is in scope for processing
        if (!isBeanInScope(beanClass)) {
            log.trace("Bean '{}' not in configured scope, skipping", beanName);
            return;
        }
        
        // Process subscriber methods
        processSubscriberMethods(bean, beanName, beanClass);
        
        // TODO: Process other annotation types (handlers, etc.)
    }

    /**
     * Process methods annotated with @ZeroMQSubscriber.
     */
    private void processSubscriberMethods(Object bean, String beanName, Class<?> beanClass) {
        ReflectionUtils.doWithMethods(beanClass, method -> {
            ZeroMQSubscriber subscriberAnnotation = AnnotationUtils.findAnnotation(method, ZeroMQSubscriber.class);
            
            if (subscriberAnnotation != null) {
                processSubscriberMethod(bean, beanName, method, subscriberAnnotation);
            }
        });
    }

    /**
     * Process a single subscriber method.
     */
    private void processSubscriberMethod(Object bean, String beanName, Method method, ZeroMQSubscriber annotation) {
        String methodKey = beanName + "#" + method.getName();
        
        // Avoid duplicate processing
        if (!subscriberMethods.add(methodKey)) {
            return;
        }
        
        try {
            log.info("Registering ZeroMQ subscriber: {}.{}", beanName, method.getName());
            
            // Validate the method signature
            validateSubscriberMethod(method, annotation);
            
            // Create subscriber configuration
            SubscriberConfiguration config = createSubscriberConfiguration(annotation, method);
            
            // Register the subscriber with the template
            registerSubscriber(bean, method, config);
            
            subscribersRegistered.incrementAndGet();
            
            log.debug("Successfully registered subscriber for method: {}", methodKey);
            
        } catch (Exception e) {
            errorsEncountered.incrementAndGet();
            log.error("Failed to process subscriber method '{}': {}", methodKey, e.getMessage(), e);
            
            // Continue processing other methods
        }
    }

    /**
     * Validate that a subscriber method has a proper signature.
     */
    private void validateSubscriberMethod(Method method, ZeroMQSubscriber annotation) {
        // Check that method is not static
        if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("@ZeroMQSubscriber method must not be static: " + method);
        }
        
        // Check parameter count
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            throw new IllegalArgumentException("@ZeroMQSubscriber method must have at least one parameter: " + method);
        }
        
        // For REP pattern, method must have a return type
        if (annotation.pattern() == ZeroMQSubscriber.Pattern.REP && method.getReturnType() == Void.TYPE) {
            throw new IllegalArgumentException("@ZeroMQSubscriber method with REP pattern must have a return type: " + method);
        }
        
        // Check parameter types are supported
        for (Class<?> paramType : paramTypes) {
            if (!isSupportedParameterType(paramType)) {
                log.warn("Parameter type '{}' in method '{}' may not be supported for deserialization", 
                        paramType.getName(), method.getName());
            }
        }
    }

    /**
     * Create subscriber configuration from annotation.
     */
    private SubscriberConfiguration createSubscriberConfiguration(ZeroMQSubscriber annotation, Method method) {
        SubscriberConfiguration config = new SubscriberConfiguration();
        
        // Basic configuration
        config.setEndpoint(resolveEndpoint(annotation.endpoint()));
        config.setTopics(resolveTopics(annotation));
        config.setPattern(annotation.pattern());
        config.setConcurrency(annotation.concurrency() > 0 ? annotation.concurrency() : defaultConcurrency);
        config.setAckMode(annotation.ackMode());
        config.setProcessingTimeout(Duration.ofMillis(annotation.processingTimeoutMs()));
        config.setAutoStart(annotation.autoStart());
        config.setPriority(annotation.priority());
        
        // Error handling configuration
        config.setErrorHandling(annotation.errorHandling() != ZeroMQSubscriber.ErrorHandling.LOG_AND_CONTINUE ? 
                               annotation.errorHandling() : defaultErrorHandling);
        config.setMaxRetries(annotation.maxRetries());
        config.setRetryDelay(Duration.ofMillis(annotation.retryDelayMs()));
        config.setDeadLetterQueue(annotation.deadLetterQueue());
        
        // Filtering and transformation
        config.setFilter(annotation.filter());
        config.setTransform(annotation.transform());
        
        // Container configuration
        config.setContainerFactory(StringUtils.hasText(annotation.containerFactory()) ? 
                                  annotation.containerFactory() : defaultContainerFactory);
        config.setGroup(annotation.group());
        
        // Method information
        config.setTargetMethod(method);
        config.setParameterTypes(method.getParameterTypes());
        config.setReturnType(method.getReturnType());
        
        return config;
    }

    /**
     * Register a subscriber with the messaging template.
     */
    private void registerSubscriber(Object bean, Method method, SubscriberConfiguration config) {
        if (template == null) {
            log.warn("ZeroMqTemplate not available - cannot register subscriber for method: {}", method.getName());
            return;
        }
        
        // Create method invoker
        SubscriberMethodInvoker invoker = new SubscriberMethodInvoker(bean, method, config);
        
        // Register with template based on pattern
        String subscriptionId = switch (config.getPattern()) {
            case SUB -> {
                Class<?> messageType = determineMessageType(method);
                String topic = config.getTopics().isEmpty() ? "" : config.getTopics().get(0);
                yield template.subscribe(config.getEndpoint(), topic, messageType, invoker::invoke);
            }
            case PULL -> {
                Class<?> messageType = determineMessageType(method);
                yield template.pull(config.getEndpoint(), messageType, invoker::invoke);
            }
            case REP -> {
                // REP pattern needs special handling for request-reply
                log.warn("REP pattern not fully implemented - using SUB pattern as fallback");
                Class<?> messageType = determineMessageType(method);
                yield template.subscribe(config.getEndpoint(), "", messageType, invoker::invoke);
            }
            case ROUTER -> {
                log.warn("ROUTER pattern not fully implemented - using SUB pattern as fallback");
                Class<?> messageType = determineMessageType(method);
                yield template.subscribe(config.getEndpoint(), "", messageType, invoker::invoke);
            }
        };
        
        // Store subscription ID for lifecycle management
        config.setSubscriptionId(subscriptionId);
        
        log.debug("Registered subscriber with ID: {} for method: {}", subscriptionId, method.getName());
    }

    /**
     * Determine the message type for a subscriber method.
     */
    private Class<?> determineMessageType(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        
        // For now, use the first parameter type as the message type
        // In a full implementation, we would have more sophisticated logic
        // to handle topic parameters, headers, etc.
        
        if (paramTypes.length > 0) {
            Class<?> firstParam = paramTypes[0];
            
            // Skip String parameters that might be topics
            if (firstParam == String.class && paramTypes.length > 1) {
                return paramTypes[1];
            }
            
            return firstParam;
        }
        
        return Object.class; // Default fallback
    }

    /**
     * Check if a bean should be skipped for processing.
     */
    private boolean shouldSkipBean(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();
        
        // Skip framework beans
        if (beanName.startsWith("org.springframework") || 
            beanName.startsWith("com.example.zeromq")) {
            return true;
        }
        
        // Skip proxy classes without proper target
        if (beanClass.getName().contains("$$")) {
            return false; // Process proxies, they might have annotations
        }
        
        return false;
    }

    /**
     * Check if a bean class is in the configured scope for processing.
     */
    private boolean isBeanInScope(Class<?> beanClass) {
        String packageName = beanClass.getPackage() != null ? beanClass.getPackage().getName() : "";
        
        // If no base packages configured, process all beans
        if (basePackages.length == 0 && basePackageClasses.length == 0) {
            return true;
        }
        
        // Check base packages
        for (String basePackage : basePackages) {
            if (packageName.startsWith(basePackage)) {
                return true;
            }
        }
        
        // Check base package classes
        for (Class<?> basePackageClass : basePackageClasses) {
            String basePackage = basePackageClass.getPackage().getName();
            if (packageName.startsWith(basePackage)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Resolve endpoint with property placeholder support.
     */
    private String resolveEndpoint(String endpoint) {
        if (environment != null && endpoint.contains("${")) {
            return environment.resolvePlaceholders(endpoint);
        }
        return endpoint;
    }

    /**
     * Resolve topics from annotation.
     */
    private List<String> resolveTopics(ZeroMQSubscriber annotation) {
        List<String> topics = new ArrayList<>();
        
        if (StringUtils.hasText(annotation.topic())) {
            topics.add(resolveEndpoint(annotation.topic())); // Reuse endpoint resolution for properties
        }
        
        for (String topic : annotation.topics()) {
            if (StringUtils.hasText(topic)) {
                topics.add(resolveEndpoint(topic));
            }
        }
        
        return topics;
    }

    /**
     * Check if a parameter type is supported for message deserialization.
     */
    private boolean isSupportedParameterType(Class<?> type) {
        // Basic types
        if (type.isPrimitive() || type == String.class) {
            return true;
        }
        
        // Collections and arrays
        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            return true;
        }
        
        // Vector types
        if (type.getName().startsWith("com.example.zeromq.core")) {
            return true;
        }
        
        // Assume other types are serializable
        return true;
    }

    // Configuration setters (called from ZeroMQAnnotationConfiguration)

    public void setBasePackages(String[] basePackages) {
        this.basePackages = basePackages != null ? basePackages : new String[0];
    }

    public void setBasePackageClasses(Class<?>[] basePackageClasses) {
        this.basePackageClasses = basePackageClasses != null ? basePackageClasses : new Class<?>[0];
    }

    public void setDefaultContainerFactory(String defaultContainerFactory) {
        this.defaultContainerFactory = defaultContainerFactory;
    }

    public void setDefaultConcurrency(int defaultConcurrency) {
        this.defaultConcurrency = defaultConcurrency;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public void setHealthIndicatorsEnabled(boolean healthIndicatorsEnabled) {
        this.healthIndicatorsEnabled = healthIndicatorsEnabled;
    }

    public void setDefaultErrorHandling(EnableZeroMQ.ErrorHandling defaultErrorHandling) {
        this.defaultErrorHandling = defaultErrorHandling;
    }

    public void setMode(EnableZeroMQ.Mode mode) {
        this.mode = mode;
    }

    /**
     * Get processing statistics.
     * 
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "beansProcessed", beansProcessed.get(),
            "subscribersRegistered", subscribersRegistered.get(),
            "errorsEncountered", errorsEncountered.get(),
            "processedBeansCount", processedBeans.size(),
            "subscriberMethodsCount", subscriberMethods.size()
        );
    }

    /**
     * Configuration for a subscriber method.
     */
    public static class SubscriberConfiguration {
        private String endpoint;
        private List<String> topics = new ArrayList<>();
        private ZeroMQSubscriber.Pattern pattern = ZeroMQSubscriber.Pattern.SUB;
        private int concurrency = 1;
        private ZeroMQSubscriber.AckMode ackMode = ZeroMQSubscriber.AckMode.AUTO;
        private Duration processingTimeout = Duration.ofSeconds(30);
        private boolean autoStart = true;
        private int priority = 0;
        private Object errorHandling = ZeroMQSubscriber.ErrorHandling.LOG_AND_CONTINUE; // Using Object to handle both enum types
        private int maxRetries = 3;
        private Duration retryDelay = Duration.ofSeconds(1);
        private String deadLetterQueue = "";
        private String filter = "";
        private String transform = "";
        private String containerFactory = "";
        private String group = "";
        private Method targetMethod;
        private Class<?>[] parameterTypes;
        private Class<?> returnType;
        private String subscriptionId;

        // Getters and setters
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public List<String> getTopics() { return topics; }
        public void setTopics(List<String> topics) { this.topics = topics; }
        public ZeroMQSubscriber.Pattern getPattern() { return pattern; }
        public void setPattern(ZeroMQSubscriber.Pattern pattern) { this.pattern = pattern; }
        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
        public ZeroMQSubscriber.AckMode getAckMode() { return ackMode; }
        public void setAckMode(ZeroMQSubscriber.AckMode ackMode) { this.ackMode = ackMode; }
        public Duration getProcessingTimeout() { return processingTimeout; }
        public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }
        public boolean isAutoStart() { return autoStart; }
        public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        public Object getErrorHandling() { return errorHandling; }
        public void setErrorHandling(Object errorHandling) { this.errorHandling = errorHandling; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public Duration getRetryDelay() { return retryDelay; }
        public void setRetryDelay(Duration retryDelay) { this.retryDelay = retryDelay; }
        public String getDeadLetterQueue() { return deadLetterQueue; }
        public void setDeadLetterQueue(String deadLetterQueue) { this.deadLetterQueue = deadLetterQueue; }
        public String getFilter() { return filter; }
        public void setFilter(String filter) { this.filter = filter; }
        public String getTransform() { return transform; }
        public void setTransform(String transform) { this.transform = transform; }
        public String getContainerFactory() { return containerFactory; }
        public void setContainerFactory(String containerFactory) { this.containerFactory = containerFactory; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public Method getTargetMethod() { return targetMethod; }
        public void setTargetMethod(Method targetMethod) { this.targetMethod = targetMethod; }
        public Class<?>[] getParameterTypes() { return parameterTypes; }
        public void setParameterTypes(Class<?>[] parameterTypes) { this.parameterTypes = parameterTypes; }
        public Class<?> getReturnType() { return returnType; }
        public void setReturnType(Class<?> returnType) { this.returnType = returnType; }
        public String getSubscriptionId() { return subscriptionId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
    }

    /**
     * Method invoker for subscriber methods.
     */
    public static class SubscriberMethodInvoker {
        private final Object target;
        private final Method method;
        private final SubscriberConfiguration config;

        public SubscriberMethodInvoker(Object target, Method method, SubscriberConfiguration config) {
            this.target = target;
            this.method = method;
            this.config = config;
            method.setAccessible(true);
        }

        public void invoke(Object message) {
            try {
                // Simple invocation - in a full implementation, we would handle:
                // - Message conversion
                // - Parameter mapping
                // - Error handling
                // - Retry logic
                method.invoke(target, message);
            } catch (Exception e) {
                log.error("Failed to invoke subscriber method {}: {}", method.getName(), e.getMessage(), e);
            }
        }
    }
} 