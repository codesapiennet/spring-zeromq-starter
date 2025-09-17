package com.example.zeromq.annotation;

import java.lang.annotation.*;

/**
 * Annotation for declarative ZeroMQ security configuration.
 * 
 * <p>This annotation can be applied to classes, methods, or used in combination
 * with other ZeroMQ annotations to specify security requirements and settings.
 * It provides a declarative way to configure authentication, encryption, and
 * authorization for ZeroMQ messaging operations.
 * 
 * <p>The annotation supports various security mechanisms including:
 * <ul>
 * <li>CURVE encryption for end-to-end security</li>
 * <li>PLAIN authentication for simple username/password</li>
 * <li>Custom authentication mechanisms</li>
 * <li>IP-based access control</li>
 * <li>Role-based authorization</li>
 * </ul>
 * 
 * Usage Examples:
 * <pre>
 * // Class-level security - applies to all messaging methods
 * {@literal @}ZeroMQSecure(mechanism = SecurityMechanism.CURVE, profile = "production")
 * {@literal @}Service
 * public class SecureMessagingService {
 *     
 *     {@literal @}ZeroMQPublisher(endpoint = "tcp://*:5555")
 *     public void publishSecureMessage(String message) {
 *         // This will use CURVE encryption
 *     }
 * }
 * 
 * // Method-level security override
 * {@literal @}ZeroMQSecure(
 *     mechanism = SecurityMechanism.CURVE,
 *     serverKeyReference = "#{securityConfig.serverKey}",
 *     clientKeyReference = "#{securityConfig.clientKey}",
 *     requireMutualAuth = true
 * )
 * {@literal @}ZeroMQRequestHandler(endpoint = "tcp://*:5556")
 * public String handleSecureRequest(String request) {
 *     return "Processed: " + request;
 * }
 * 
 * // Role-based authorization
 * {@literal @}ZeroMQSecure(
 *     mechanism = SecurityMechanism.PLAIN,
 *     requiredRoles = {"ADMIN", "MANAGER"},
 *     allowedIpPatterns = {"192.168.1.*", "10.0.0.*"}
 * )
 * {@literal @}ZeroMQSubscriber(endpoint = "tcp://localhost:5557")
 * public void handleAdminMessage(AdminCommand command) {
 *     // Only users with ADMIN or MANAGER roles can send messages
 * }
 * </pre>
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZeroMQSecure {

    /**
     * Security mechanism to use.
     * 
     * @return the security mechanism
     */
    SecurityMechanism mechanism() default SecurityMechanism.AUTO;

    /**
     * Security profile to apply.
     * 
     * <p>References a named security profile configuration. Common profiles
     * include "development", "testing", "production".
     * 
     * @return the security profile name
     */
    String profile() default "";

    /**
     * Server public key reference for CURVE encryption.
     * 
     * <p>Can be:
     * <ul>
     * <li>A direct key value (Z85 encoded)</li>
     * <li>A property reference: {@code ${security.curve.server-public-key}}</li>
     * <li>A SpEL expression: {@code #{keyManager.getServerPublicKey()}}</li>
     * <li>A bean reference: {@code @securityKeyProvider}</li>
     * </ul>
     * 
     * @return the server public key reference
     */
    String serverKeyReference() default "";

    /**
     * Server secret key reference for CURVE encryption.
     * 
     * <p>Same format options as {@link #serverKeyReference()}.
     * 
     * @return the server secret key reference
     */
    String serverSecretReference() default "";

    /**
     * Client public key reference for CURVE encryption.
     * 
     * <p>Same format options as {@link #serverKeyReference()}.
     * 
     * @return the client public key reference
     */
    String clientKeyReference() default "";

    /**
     * Client secret key reference for CURVE encryption.
     * 
     * <p>Same format options as {@link #serverKeyReference()}.
     * 
     * @return the client secret key reference
     */
    String clientSecretReference() default "";

    /**
     * Username for PLAIN authentication.
     * 
     * <p>Can use property references and SpEL expressions like key references.
     * 
     * @return the username
     */
    String username() default "";

    /**
     * Password reference for PLAIN authentication.
     * 
     * <p>Can use property references and SpEL expressions like key references.
     * For security, avoid hardcoding passwords in annotations.
     * 
     * @return the password reference
     */
    String passwordReference() default "";

    /**
     * Whether to require mutual authentication for CURVE.
     * 
     * <p>When true, both client and server must authenticate each other.
     * 
     * @return true to require mutual authentication
     */
    boolean requireMutualAuth() default false;

    /**
     * Required roles for authorization.
     * 
     * <p>Array of role names that the user must have to access this
     * messaging endpoint. Empty array means no role-based restrictions.
     * 
     * @return array of required roles
     */
    String[] requiredRoles() default {};

    /**
     * Required permissions for authorization.
     * 
     * <p>Array of permission names that the user must have. Permissions
     * are more fine-grained than roles.
     * 
     * @return array of required permissions
     */
    String[] requiredPermissions() default {};

    /**
     * Allowed IP address patterns.
     * 
     * <p>Array of IP address patterns (supports wildcards and CIDR notation)
     * that are allowed to connect. Empty array means no IP restrictions.
     * 
     * Examples:
     * <ul>
     * <li>{@code "192.168.1.*"} - wildcard pattern</li>
     * <li>{@code "10.0.0.0/8"} - CIDR notation</li>
     * <li>{@code "127.0.0.1"} - exact IP</li>
     * </ul>
     * 
     * @return array of allowed IP patterns
     */
    String[] allowedIpPatterns() default {};

    /**
     * Denied IP address patterns.
     * 
     * <p>Array of IP address patterns that are explicitly denied access.
     * Denial rules take precedence over allow rules.
     * 
     * @return array of denied IP patterns
     */
    String[] deniedIpPatterns() default {};

    /**
     * Custom authentication provider reference.
     * 
     * <p>References a Spring bean that implements custom authentication logic.
     * 
     * @return the authentication provider bean name
     */
    String authenticationProvider() default "";

    /**
     * Custom authorization provider reference.
     * 
     * <p>References a Spring bean that implements custom authorization logic.
     * 
     * @return the authorization provider bean name
     */
    String authorizationProvider() default "";

    /**
     * Session timeout for authenticated connections in milliseconds.
     * 
     * <p>After this time, clients must re-authenticate. Use -1 for no timeout.
     * 
     * @return the session timeout in milliseconds
     */
    long sessionTimeout() default 3600000; // 1 hour

    /**
     * Whether to enable security audit logging.
     * 
     * <p>When true, all security-related events (authentication, authorization
     * failures, key exchanges) will be logged for compliance and monitoring.
     * 
     * @return true to enable audit logging
     */
    boolean auditLogging() default true;

    /**
     * Security context validation expression.
     * 
     * <p>SpEL expression that performs additional security validation.
     * The expression has access to:
     * <ul>
     * <li>{@code #securityContext} - the current security context</li>
     * <li>{@code #user} - the authenticated user (if any)</li>
     * <li>{@code #clientIp} - the client IP address</li>
     * <li>{@code #method} - the target method</li>
     * <li>{@code #message} - the message being processed</li>
     * </ul>
     * 
     * @return the validation expression
     */
    String securityValidation() default "";

    /**
     * Custom security attributes.
     * 
     * <p>Array of "key=value" pairs that provide additional security context.
     * Values can use SpEL expressions and property references.
     * 
     * @return array of security attributes
     */
    String[] securityAttributes() default {};

    /**
     * Whether to encrypt message payloads.
     * 
     * <p>When true, message contents will be encrypted in addition to
     * transport-level security. This provides defense in depth.
     * 
     * @return true to encrypt payloads
     */
    boolean encryptPayload() default false;

    /**
     * Payload encryption algorithm when payload encryption is enabled.
     * 
     * @return the encryption algorithm
     */
    EncryptionAlgorithm encryptionAlgorithm() default EncryptionAlgorithm.AES_GCM;

    /**
     * Whether to sign message payloads for integrity verification.
     * 
     * @return true to sign payloads
     */
    boolean signPayload() default false;

    /**
     * Digital signature algorithm when payload signing is enabled.
     * 
     * @return the signature algorithm
     */
    SignatureAlgorithm signatureAlgorithm() default SignatureAlgorithm.ECDSA_SHA256;

    /**
     * Security mechanisms supported by ZeroMQ.
     */
    enum SecurityMechanism {
        /**
         * Automatically detect the best security mechanism based on configuration.
         */
        AUTO,
        
        /**
         * No security (plain text communication).
         */
        NONE,
        
        /**
         * PLAIN authentication mechanism (username/password).
         */
        PLAIN,
        
        /**
         * CURVE encryption mechanism (public key cryptography).
         */
        CURVE,
        
        /**
         * Custom security mechanism.
         */
        CUSTOM
    }

    /**
     * Supported encryption algorithms for payload encryption.
     */
    enum EncryptionAlgorithm {
        /**
         * AES with Galois/Counter Mode (recommended).
         */
        AES_GCM,
        
        /**
         * AES with Cipher Block Chaining.
         */
        AES_CBC,
        
        /**
         * ChaCha20-Poly1305 (fast and secure).
         */
        CHACHA20_POLY1305,
        
        /**
         * Custom encryption algorithm.
         */
        CUSTOM
    }

    /**
     * Supported digital signature algorithms.
     */
    enum SignatureAlgorithm {
        /**
         * ECDSA with SHA-256 (recommended).
         */
        ECDSA_SHA256,
        
        /**
         * RSA with SHA-256.
         */
        RSA_SHA256,
        
        /**
         * EdDSA (Edwards-curve Digital Signature Algorithm).
         */
        EDDSA,
        
        /**
         * HMAC-SHA256 (symmetric signing).
         */
        HMAC_SHA256,
        
        /**
         * Custom signature algorithm.
         */
        CUSTOM
    }
} 