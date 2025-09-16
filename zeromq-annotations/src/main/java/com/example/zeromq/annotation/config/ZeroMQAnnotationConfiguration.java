package com.example.zeromq.annotation.config;

import com.example.zeromq.annotation.EnableZeroMQ;
import com.example.zeromq.annotation.processor.ZeroMQAnnotationPostProcessor;
import com.example.zeromq.annotation.processor.ZeroMQPublisherAspect;
import com.example.zeromq.annotation.processor.ZeroMQSubscriberProcessor;
import com.example.zeromq.annotation.container.ZeroMQMessageListenerContainerFactory;
import com.example.zeromq.autoconfig.ZeroMqTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Configuration class for ZeroMQ annotation processing infrastructure.
 * 
 * <p>This configuration is automatically imported when {@code @EnableZeroMQ}
 * is used and sets up all necessary components for declarative ZeroMQ messaging:
 * <ul>
 * <li>Annotation post processors for scanning and registration</li>
 * <li>AOP aspects for method interception</li>
 * <li>Message listener containers for subscribers</li>
 * <li>Error handling and retry mechanisms</li>
 * </ul>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Configuration
@EnableAspectJAutoProxy
public class ZeroMQAnnotationConfiguration implements ImportAware {

    private static final Logger log = LoggerFactory.getLogger(ZeroMQAnnotationConfiguration.class);
    
    private AnnotationAttributes enableZeroMQAttributes;

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.enableZeroMQAttributes = AnnotationAttributes.fromMap(
            importMetadata.getAnnotationAttributes(EnableZeroMQ.class.getName(), false));
        
        if (this.enableZeroMQAttributes == null) {
            throw new IllegalArgumentException(
                "@EnableZeroMQ is not present on importing class " + importMetadata.getClassName());
        }
        
        log.info("ZeroMQ annotation processing enabled for class: {}", importMetadata.getClassName());
    }

    /**
     * Create the annotation post processor for scanning ZeroMQ annotations.
     * 
     * @param template the ZeroMQ template for messaging operations
     * @return annotation post processor bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZeroMQAnnotationPostProcessor zeroMQAnnotationPostProcessor(
            @Autowired(required = false) ZeroMqTemplate template) {
        
        ZeroMQAnnotationPostProcessor processor = new ZeroMQAnnotationPostProcessor(template);
        
        // Configure from @EnableZeroMQ attributes
        if (enableZeroMQAttributes != null) {
            processor.setBasePackages(enableZeroMQAttributes.getStringArray("basePackages"));
            processor.setBasePackageClasses(enableZeroMQAttributes.getClassArray("basePackageClasses"));
            processor.setDefaultContainerFactory(enableZeroMQAttributes.getString("containerFactory"));
            processor.setDefaultConcurrency(enableZeroMQAttributes.getNumber("defaultConcurrency"));
            processor.setMetricsEnabled(enableZeroMQAttributes.getBoolean("enableMetrics"));
            processor.setHealthIndicatorsEnabled(enableZeroMQAttributes.getBoolean("enableHealthIndicators"));
            
            EnableZeroMQ.ErrorHandling defaultErrorHandling = 
                enableZeroMQAttributes.getEnum("defaultErrorHandling");
            processor.setDefaultErrorHandling(defaultErrorHandling);
            
            EnableZeroMQ.Mode mode = enableZeroMQAttributes.getEnum("mode");
            processor.setMode(mode);
        }
        
        log.debug("Created ZeroMQ annotation post processor with configuration: {}", 
                 enableZeroMQAttributes != null ? enableZeroMQAttributes : "default");
        
        return processor;
    }

    /**
     * Create the AOP aspect for handling @ZeroMQPublisher annotations.
     * 
     * @param template the ZeroMQ template for publishing messages
     * @return publisher aspect bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZeroMQPublisherAspect zeroMQPublisherAspect(
            @Autowired(required = false) ZeroMqTemplate template) {
        
        if (template == null) {
            log.warn("ZeroMqTemplate not available - @ZeroMQPublisher functionality will be limited");
        }
        
        ZeroMQPublisherAspect aspect = new ZeroMQPublisherAspect(template);
        
        log.debug("Created ZeroMQ publisher aspect");
        
        return aspect;
    }

    /**
     * Create the subscriber processor for handling @ZeroMQSubscriber annotations.
     * 
     * @param template the ZeroMQ template for subscription operations
     * @param containerFactory the container factory for creating listener containers
     * @return subscriber processor bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZeroMQSubscriberProcessor zeroMQSubscriberProcessor(
            @Autowired(required = false) ZeroMqTemplate template,
            @Autowired(required = false) ZeroMQMessageListenerContainerFactory containerFactory) {
        
        if (template == null) {
            log.warn("ZeroMqTemplate not available - @ZeroMQSubscriber functionality will be limited");
        }
        
        ZeroMQSubscriberProcessor processor = new ZeroMQSubscriberProcessor(template, containerFactory);
        
        // Configure from @EnableZeroMQ attributes
        if (enableZeroMQAttributes != null) {
            processor.setDefaultConcurrency(enableZeroMQAttributes.getNumber("defaultConcurrency"));
            
            EnableZeroMQ.ErrorHandling defaultErrorHandling = 
                enableZeroMQAttributes.getEnum("defaultErrorHandling");
            processor.setDefaultErrorHandling(defaultErrorHandling);
            
            processor.setMetricsEnabled(enableZeroMQAttributes.getBoolean("enableMetrics"));
        }
        
        log.debug("Created ZeroMQ subscriber processor");
        
        return processor;
    }

    /**
     * Create the default message listener container factory.
     * 
     * @param template the ZeroMQ template for container operations
     * @return container factory bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZeroMQMessageListenerContainerFactory zeroMQMessageListenerContainerFactory(
            @Autowired(required = false) ZeroMqTemplate template) {
        
        ZeroMQMessageListenerContainerFactory factory = new ZeroMQMessageListenerContainerFactory(template);
        
        // Configure from @EnableZeroMQ attributes
        if (enableZeroMQAttributes != null) {
            factory.setDefaultConcurrency(enableZeroMQAttributes.getNumber("defaultConcurrency"));
            factory.setConnectionPoolingEnabled(enableZeroMQAttributes.getBoolean("enableConnectionPooling"));
            factory.setVectorProcessingEnabled(enableZeroMQAttributes.getBoolean("enableVectorProcessing"));
            
            EnableZeroMQ.ErrorHandling defaultErrorHandling = 
                enableZeroMQAttributes.getEnum("defaultErrorHandling");
            factory.setDefaultErrorHandling(defaultErrorHandling);
            
            EnableZeroMQ.Mode mode = enableZeroMQAttributes.getEnum("mode");
            factory.setMode(mode);
        }
        
        log.debug("Created ZeroMQ message listener container factory");
        
        return factory;
    }

    /**
     * Create an error handler registry for custom error handling strategies.
     * 
     * @return error handler registry bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZeroMQErrorHandlerRegistry zeroMQErrorHandlerRegistry() {
        ZeroMQErrorHandlerRegistry registry = new ZeroMQErrorHandlerRegistry();
        
        log.debug("Created ZeroMQ error handler registry");
        
        return registry;
    }

    /**
     * Create a message converter registry for handling different message types.
     * 
     * @return message converter registry bean
     */
    @Bean
    @ConditionalOnMissingBean  
    public ZeroMQMessageConverterRegistry zeroMQMessageConverterRegistry() {
        ZeroMQMessageConverterRegistry registry = new ZeroMQMessageConverterRegistry();
        
        log.debug("Created ZeroMQ message converter registry");
        
        return registry;
    }

    /**
     * Create a metrics collector for annotation-based operations.
     * 
     * @return annotation metrics collector bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ZeroMQAnnotationMetricsCollector zeroMQAnnotationMetricsCollector() {
        boolean metricsEnabled = enableZeroMQAttributes != null && 
                               enableZeroMQAttributes.getBoolean("enableMetrics");
        
        ZeroMQAnnotationMetricsCollector collector = new ZeroMQAnnotationMetricsCollector(metricsEnabled);
        
        log.debug("Created ZeroMQ annotation metrics collector (enabled: {})", metricsEnabled);
        
        return collector;
    }

    /**
     * Get the @EnableZeroMQ configuration attributes.
     * 
     * @return the annotation attributes
     */
    public AnnotationAttributes getEnableZeroMQAttributes() {
        return enableZeroMQAttributes;
    }

    /**
     * Check if metrics collection is enabled.
     * 
     * @return true if metrics are enabled
     */
    public boolean isMetricsEnabled() {
        return enableZeroMQAttributes != null && enableZeroMQAttributes.getBoolean("enableMetrics");
    }

    /**
     * Check if health indicators are enabled.
     * 
     * @return true if health indicators are enabled
     */
    public boolean isHealthIndicatorsEnabled() {
        return enableZeroMQAttributes != null && enableZeroMQAttributes.getBoolean("enableHealthIndicators");
    }

    /**
     * Check if connection pooling is enabled.
     * 
     * @return true if connection pooling is enabled
     */
    public boolean isConnectionPoolingEnabled() {
        return enableZeroMQAttributes != null && enableZeroMQAttributes.getBoolean("enableConnectionPooling");
    }

    /**
     * Check if vector processing is enabled.
     * 
     * @return true if vector processing is enabled
     */
    public boolean isVectorProcessingEnabled() {
        return enableZeroMQAttributes != null && enableZeroMQAttributes.getBoolean("enableVectorProcessing");
    }

    /**
     * Get the configuration mode.
     * 
     * @return the configuration mode
     */
    public EnableZeroMQ.Mode getMode() {
        return enableZeroMQAttributes != null ? 
               enableZeroMQAttributes.getEnum("mode") : EnableZeroMQ.Mode.AUTO;
    }

    /**
     * Get the default error handling strategy.
     * 
     * @return the default error handling strategy
     */
    public EnableZeroMQ.ErrorHandling getDefaultErrorHandling() {
        return enableZeroMQAttributes != null ?
               enableZeroMQAttributes.getEnum("defaultErrorHandling") : 
               EnableZeroMQ.ErrorHandling.LOG_AND_CONTINUE;
    }
} 