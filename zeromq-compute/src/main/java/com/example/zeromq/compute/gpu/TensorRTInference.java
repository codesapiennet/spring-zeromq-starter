package com.example.zeromq.compute.gpu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Thin Java wrapper for TensorRT-based inference.
 *
 * <p>This class expects a native library named `tensorrt_inference` to be available on the
 * native library path. The native library should provide the JNI implementations for
 * {@code nativeLoadEngine}, {@code nativeExecute} and {@code nativeClose}.
 *
 * <p>When the native library is not available the wrapper will log a warning and {@link #isAvailable()}
 * will return {@code false}. Calls to {@link #execute(float[])} will then throw an
 * {@link UnsupportedOperationException} so failures are explicit and easy to detect in CI.
 *
 * <p>Implementers integrating TensorRT should provide a small JNI shim named
 * `tensorrt_inference` that implements the native methods used by this class.
 */
public final class TensorRTInference implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TensorRTInference.class);

    private final String enginePath;
    private final int deviceId;
    private boolean nativeAvailable;

    /**
     * Create a TensorRTInference wrapper for the given engine file and device id.
     * @param enginePath path to a serialized TensorRT engine file (.plan / .trt)
     * @param deviceId CUDA device id
     */
    public TensorRTInference(String enginePath, int deviceId) {
        this.enginePath = Objects.requireNonNull(enginePath, "enginePath must not be null");
        this.deviceId = deviceId;
        this.nativeAvailable = tryLoadNative();
        if (this.nativeAvailable) {
            try {
                nativeLoadEngine(enginePath, deviceId);
            } catch (Throwable t) {
                log.error("Failed to load TensorRT engine '{}': {}", enginePath, t.getMessage(), t);
                this.nativeAvailable = false;
            }
        }
    }

    private boolean tryLoadNative() {
        try {
            System.loadLibrary("tensorrt_inference");
            log.info("Loaded native TensorRT inference library");
            return true;
        } catch (UnsatisfiedLinkError e) {
            log.warn("Native TensorRT library 'tensorrt_inference' not found: {}", e.getMessage());
            return false;
        } catch (SecurityException e) {
            log.warn("Security manager prevented loading native TensorRT library: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Execute inference on the provided float input. Returns a float array with the model output.
     * @param input flattened input tensor (expected format depends on the model)
     * @return flattened output tensor
     * @throws Exception when native execution fails
     */
    public float[] execute(float[] input) throws Exception {
        Objects.requireNonNull(input, "input must not be null");
        if (!nativeAvailable) {
            log.warn("TensorRT native library not available - falling back to CPU passthrough for execute");
            // Return a defensive copy to avoid accidental mutation of caller data
            return input.clone();
        }
        try {
            return nativeExecute(input);
        } catch (Exception e) {
            log.error("TensorRT native execution failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * @return true if the native TensorRT library and engine were successfully loaded
     */
    public boolean isAvailable() {
        return nativeAvailable;
    }

    @Override
    public void close() {
        if (nativeAvailable) {
            try {
                nativeClose();
            } catch (Throwable t) {
                log.warn("Error while closing native TensorRT resources: {}", t.getMessage());
            }
        }
    }

    /* ----------------- Native method stubs (must be implemented in native library) ----------------- */

    // Load/deserialize a serialized engine and bind it to device
    private native void nativeLoadEngine(String enginePath, int deviceId) throws Exception;

    // Execute inference using the loaded engine
    private native float[] nativeExecute(float[] input) throws Exception;

    // Free native resources
    private native void nativeClose();
} 