package com.example.zeromq.compute.cpu;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Helper utilities that expose common vectorized operations using the JDK Vector API.
 *
 * <p>This class provides small, well-tested primitives that higher-level engines can
 * call to keep numeric kernels concise and unit-testable. Methods validate inputs
 * and fallback to scalar loops when necessary.
 *
 * Note: Uses the incubating Vector API; requires a JVM that supports the API (Java 17+ preview or Java 21+).
 */
public final class VectorizedOperations {

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private VectorizedOperations() {}

    public static void add(float[] a, float[] b, float[] dest, int length) {
        validateArgs(a, b, dest, length);

        int i = 0;
        int upper = SPECIES.loopBound(length);
        for (; i < upper; i += SPECIES.length()) {
            var va = FloatVector.fromArray(SPECIES, a, i);
            var vb = FloatVector.fromArray(SPECIES, b, i);
            var vr = va.add(vb);
            vr.intoArray(dest, i);
        }
        for (; i < length; i++) dest[i] = a[i] + b[i];
    }

    public static void multiply(float[] a, float[] b, float[] dest, int length) {
        validateArgs(a, b, dest, length);

        int i = 0;
        int upper = SPECIES.loopBound(length);
        for (; i < upper; i += SPECIES.length()) {
            var va = FloatVector.fromArray(SPECIES, a, i);
            var vb = FloatVector.fromArray(SPECIES, b, i);
            var vr = va.mul(vb);
            vr.intoArray(dest, i);
        }
        for (; i < length; i++) dest[i] = a[i] * b[i];
    }

    public static float dot(float[] a, float[] b, int start, int end) {
        ObjectsRequireNonNullRanges(a, b, start, end);
        int length = end - start;
        if (length <= 0) return 0.0f;

        FloatVector vsum = FloatVector.zero(SPECIES);
        int i = start;
        int upper = SPECIES.loopBound(end) ;
        for (; i < upper; i += SPECIES.length()) {
            var va = FloatVector.fromArray(SPECIES, a, i);
            var vb = FloatVector.fromArray(SPECIES, b, i);
            vsum = va.fma(vb, vsum);
        }
        float sum = vsum.reduceLanes(VectorOperators.ADD);
        for (; i < end; i++) sum += a[i] * b[i];
        return sum;
    }

    public static void relu(float[] a, float[] dest, int length) {
        if (a == null || dest == null) throw new IllegalArgumentException("Arrays must not be null");
        if (length < 0 || a.length < length || dest.length < length) throw new IllegalArgumentException("Invalid length");

        int i = 0;
        int upper = SPECIES.loopBound(length);
        var zero = FloatVector.broadcast(SPECIES, 0.0f);
        for (; i < upper; i += SPECIES.length()) {
            var va = FloatVector.fromArray(SPECIES, a, i);
            va.max(zero).intoArray(dest, i);
        }
        for (; i < length; i++) dest[i] = Math.max(0.0f, a[i]);
    }

    public static float l2Norm(float[] a, int length) {
        if (a == null) throw new IllegalArgumentException("Array must not be null");
        if (length < 0 || a.length < length) throw new IllegalArgumentException("Invalid length");

        FloatVector vsum = FloatVector.zero(SPECIES);
        int i = 0;
        int upper = SPECIES.loopBound(length);
        for (; i < upper; i += SPECIES.length()) {
            var va = FloatVector.fromArray(SPECIES, a, i);
            vsum = va.fma(va, vsum);
        }
        double sum = vsum.reduceLanes(VectorOperators.ADD);
        for (; i < length; i++) sum += (double) a[i] * a[i];
        return (float) Math.sqrt(sum);
    }

    public static void sigmoid(float[] src, float[] dest, int length) {
        if (src == null || dest == null) throw new IllegalArgumentException("Arrays must not be null");
        if (length < 0 || src.length < length || dest.length < length) throw new IllegalArgumentException("Invalid length");
        for (int i = 0; i < length; i++) dest[i] = (float) (1.0 / (1.0 + Math.exp(-src[i])));
    }

    public static void tanh(float[] src, float[] dest, int length) {
        if (src == null || dest == null) throw new IllegalArgumentException("Arrays must not be null");
        if (length < 0 || src.length < length || dest.length < length) throw new IllegalArgumentException("Invalid length");
        for (int i = 0; i < length; i++) dest[i] = (float) Math.tanh(src[i]);
    }

    private static void validateArgs(float[] a, float[] b, float[] dest, int length) {
        if (a == null || b == null || dest == null) throw new IllegalArgumentException("Arrays must not be null");
        if (length < 0 || a.length < length || b.length < length || dest.length < length) throw new IllegalArgumentException("Invalid length");
    }

    private static void ObjectsRequireNonNullRanges(float[] a, float[] b, int start, int end) {
        if (a == null || b == null) throw new IllegalArgumentException("Arrays must not be null");
        if (start < 0 || end < start || end > a.length || end > b.length) throw new IllegalArgumentException("Invalid start/end range");
    }
} 