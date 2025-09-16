package com.example.zeromq.core;

import com.example.zeromq.core.exception.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Utility class for generating and managing ZeroMQ CURVE authentication keys.
 * 
 * <p>This class provides secure key generation capabilities for ZeroMQ's CURVE
 * security mechanism. It follows cryptographic best practices and provides
 * proper logging for security-sensitive operations while never exposing
 * private keys in logs.
 * 
 * <p>All key generation operations use cryptographically secure random sources
 * and the keys are properly encoded using Z85 format as required by ZeroMQ.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public final class ZAuthKeyGenerator {

    private static final Logger log = LoggerFactory.getLogger(ZAuthKeyGenerator.class);
    
    /**
     * Size of CURVE keys in bytes (32 bytes for Curve25519).
     */
    private static final int KEY_SIZE = 32;
    
    /**
     * Size of Z85 encoded keys (40 characters).
     */
    private static final int Z85_KEY_SIZE = 40;
    
    /**
     * Secure random instance for key generation.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Private constructor to prevent instantiation
    private ZAuthKeyGenerator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Represents a CURVE key pair with public and secret keys.
     */
    public static final class CurveKeyPair {
        private final String publicKey;
        private final String secretKey;
        private final String correlationId;
        private final long generatedTimestamp;

        private CurveKeyPair(String publicKey, String secretKey, String correlationId) {
            this.publicKey = publicKey;
            this.secretKey = secretKey;
            this.correlationId = correlationId;
            this.generatedTimestamp = System.currentTimeMillis();
        }

        /**
         * Get the Z85-encoded public key.
         * 
         * @return the public key (safe to log and share)
         */
        public String getPublicKey() {
            return publicKey;
        }

        /**
         * Get the Z85-encoded secret key.
         * 
         * <p><strong>SECURITY WARNING:</strong> This contains the private key material.
         * Handle with extreme care and never log or expose this value.
         * 
         * @return the secret key (NEVER log this value)
         */
        public String getSecretKey() {
            return secretKey;
        }

        /**
         * Get the correlation ID for this key generation operation.
         * 
         * @return the correlation ID for audit logging
         */
        public String getCorrelationId() {
            return correlationId;
        }

        /**
         * Get the timestamp when this key pair was generated.
         * 
         * @return the generation timestamp in milliseconds
         */
        public long getGeneratedTimestamp() {
            return generatedTimestamp;
        }

        /**
         * Check if this key pair is valid.
         * 
         * @return true if both keys are properly formatted
         */
        public boolean isValid() {
            return isValidZ85Key(publicKey) && isValidZ85Key(secretKey);
        }

        @Override
        public String toString() {
            return String.format("CurveKeyPair[publicKey=%s, correlationId=%s, generated=%d]", 
                    publicKey, correlationId, generatedTimestamp);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CurveKeyPair other = (CurveKeyPair) obj;
            return Objects.equals(publicKey, other.publicKey) && 
                   Objects.equals(secretKey, other.secretKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(publicKey, secretKey);
        }
    }

    /**
     * Generate a new CURVE key pair using cryptographically secure randomness.
     * 
     * <p>This method generates a fresh Curve25519 key pair suitable for use
     * with ZeroMQ's CURVE security mechanism. Each generation is logged with
     * a unique correlation ID for audit purposes.
     * 
     * @return a new CurveKeyPair with public and secret keys
     * @throws SecurityException if key generation fails
     */
    public static CurveKeyPair generateKeyPair() {
        return generateKeyPair(generateCorrelationId());
    }

    /**
     * Generate a new CURVE key pair with a specific correlation ID.
     * 
     * @param correlationId the correlation ID for audit logging
     * @return a new CurveKeyPair with public and secret keys
     * @throws SecurityException if key generation fails
     */
    public static CurveKeyPair generateKeyPair(String correlationId) {
        Objects.requireNonNull(correlationId, "Correlation ID must not be null");
        
        try {
            long startTime = System.nanoTime();
            
            // Generate CURVE key pair using JeroMQ API (already Z85 encoded)
            String[] keyPair = ZMQ.Curve.generateKeyPair();
            String publicKey = keyPair[0];
            String secretKey = keyPair[1];
            
            long duration = System.nanoTime() - startTime;
            
            // Validate generated keys
            if (!isValidZ85Key(publicKey) || !isValidZ85Key(secretKey)) {
                throw new SecurityException("Generated keys failed validation", "CURVE");
            }
            
            // Log key generation event (NEVER log the secret key)
            log.info("component=zeromq-security event=curve-key-generated " +
                    "correlationId={} publicKey={} durationMicros={} " +
                    "keySize={} algorithm=curve25519",
                    correlationId, publicKey, duration / 1000, KEY_SIZE);
            
            return new CurveKeyPair(publicKey, secretKey, correlationId);
            
        } catch (Exception e) {
            log.error("component=zeromq-security event=curve-key-generation-failed " +
                     "correlationId={} error={}", correlationId, e.getMessage());
            throw new SecurityException("Failed to generate CURVE key pair: " + e.getMessage(), e, "CURVE");
        }
    }

    /**
     * Generate a server key pair for CURVE authentication.
     * 
     * <p>This is a convenience method that generates a key pair and logs it
     * appropriately for server-side usage.
     * 
     * @return a new server key pair
     * @throws SecurityException if key generation fails
     */
    public static CurveKeyPair generateServerKeyPair() {
        String correlationId = "server-" + generateCorrelationId();
        CurveKeyPair keyPair = generateKeyPair(correlationId);
        
        log.info("component=zeromq-security event=server-keypair-generated " +
                "correlationId={} publicKey={}", 
                correlationId, keyPair.getPublicKey());
        
        return keyPair;
    }

    /**
     * Generate a client key pair for CURVE authentication.
     * 
     * <p>This is a convenience method that generates a key pair and logs it
     * appropriately for client-side usage.
     * 
     * @return a new client key pair
     * @throws SecurityException if key generation fails
     */
    public static CurveKeyPair generateClientKeyPair() {
        String correlationId = "client-" + generateCorrelationId();
        CurveKeyPair keyPair = generateKeyPair(correlationId);
        
        log.info("component=zeromq-security event=client-keypair-generated " +
                "correlationId={} publicKey={}", 
                correlationId, keyPair.getPublicKey());
        
        return keyPair;
    }

    /**
     * Validate that a string is a properly formatted Z85 key.
     * 
     * @param key the key to validate
     * @return true if the key is valid Z85 format
     */
    public static boolean isValidZ85Key(String key) {
        if (key == null || key.length() != Z85_KEY_SIZE) {
            return false;
        }
        
        try {
            // Attempt to decode the key to verify it's valid Z85
            byte[] decoded = ZMQ.Curve.z85Decode(key);
            return decoded != null && decoded.length == KEY_SIZE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Compute the public key from a secret key.
     * 
     * <p>This method derives the corresponding public key from a given secret key
     * using Curve25519 cryptography.
     * 
     * @param secretKey the Z85-encoded secret key
     * @return the corresponding Z85-encoded public key
     * @throws SecurityException if the secret key is invalid or computation fails
     */
    public static String computePublicKey(String secretKey) {
        Objects.requireNonNull(secretKey, "Secret key must not be null");
        
        if (!isValidZ85Key(secretKey)) {
            throw new SecurityException("Invalid secret key format", "CURVE");
        }
        
        try {
            // Use JeroMQ's curve key derivation
            String publicKey = ZMQ.Curve.publicKey(secretKey);
            
            log.debug("component=zeromq-security event=public-key-computed " +
                     "publicKey={}", publicKey);
            
            return publicKey;
            
        } catch (Exception e) {
            log.error("component=zeromq-security event=public-key-computation-failed " +
                     "error={}", e.getMessage());
            throw new SecurityException("Failed to compute public key: " + e.getMessage(), e, "CURVE");
        }
    }

    /**
     * Generate a cryptographically secure random correlation ID.
     * 
     * @return a unique correlation ID for audit logging
     */
    private static String generateCorrelationId() {
        byte[] randomBytes = new byte[8];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Utility method to generate and print key pairs for development/testing.
     * 
     * <p><strong>WARNING:</strong> This method prints secret keys to the console.
     * It should only be used for development and testing purposes, never in production.
     * 
     * @param count the number of key pairs to generate
     */
    public static void printKeyPairs(int count) {
        System.out.println("=== ZeroMQ CURVE Key Pairs (DEVELOPMENT ONLY) ===");
        System.out.println("WARNING: Secret keys are shown - do not use in production!");
        System.out.println();
        
        for (int i = 0; i < count; i++) {
            CurveKeyPair keyPair = generateKeyPair();
            System.out.printf("Key Pair %d:%n", i + 1);
            System.out.printf("  Public Key:  %s%n", keyPair.getPublicKey());
            System.out.printf("  Secret Key:  %s%n", keyPair.getSecretKey());
            System.out.printf("  Correlation: %s%n", keyPair.getCorrelationId());
            System.out.println();
        }
    }

    /**
     * Main method for generating key pairs from command line.
     * 
     * @param args command line arguments (optional: number of key pairs to generate)
     */
    public static void main(String[] args) {
        int count = 1;
        if (args.length > 0) {
            try {
                count = Integer.parseInt(args[0]);
                if (count <= 0 || count > 10) {
                    System.err.println("Invalid count. Must be between 1 and 10.");
                    System.exit(1);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid number format: " + args[0]);
                System.exit(1);
            }
        }
        
        printKeyPairs(count);
    }
} 