package com.example.zeromq.compute.science;

/**
 * Simple iterative Cooley-Tukey FFT implementation for radix-2 lengths.
 * This class provides utilities for in-place complex transforms using
 * separate real/imag arrays. Optimized to precompute twiddle factors per stage
 * to avoid repeated Math.cos/Math.sin calls and reduce floating work inside inner loops.
 */
public final class FFTProcessor {

    private FFTProcessor() {}

    /**
     * Perform in-place FFT (or inverse FFT) on the provided real and imag arrays.
     * Arrays must have length n = 2^k.
     * @param real real parts (in/out)
     * @param imag imag parts (in/out)
     * @param inverse true for inverse FFT
     */
    public static void transform(double[] real, double[] imag, boolean inverse) {
        int n = real.length;
        if (n != imag.length) throw new IllegalArgumentException("real and imag length must match");
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("length must be a power of two");

        // Bit-reverse permutation
        int shift = 1 + Integer.numberOfLeadingZeros(n);
        for (int i = 1; i < n; i++) {
            int j = Integer.reverse(i) >>> shift;
            if (j > i) {
                double tmpR = real[i]; real[i] = real[j]; real[j] = tmpR;
                double tmpI = imag[i]; imag[i] = imag[j]; imag[j] = tmpI;
            }
        }

        // Cooley-Tukey with precomputed twiddles per stage
        for (int len = 2; len <= n; len <<= 1) {
            int half = len >> 1;
            double ang = 2 * Math.PI / len * (inverse ? -1 : 1);

            // Precompute twiddle factors for this stage (length = len)
            double[] wR = new double[half];
            double[] wI = new double[half];
            double wlenR = Math.cos(ang);
            double wlenI = Math.sin(ang);
            // Start with w=1
            wR[0] = 1.0;
            wI[0] = 0.0;
            for (int k = 1; k < half; k++) {
                double prevR = wR[k-1];
                double prevI = wI[k-1];
                // complex multiply prev * wlen
                wR[k] = prevR * wlenR - prevI * wlenI;
                wI[k] = prevR * wlenI + prevI * wlenR;
            }

            for (int i = 0; i < n; i += len) {
                for (int j = 0; j < half; j++) {
                    int a = i + j;
                    int b = i + j + half;

                    double uR = real[a];
                    double uI = imag[a];

                    double vR = real[b] * wR[j] - imag[b] * wI[j];
                    double vI = real[b] * wI[j] + imag[b] * wR[j];

                    real[b] = uR - vR;
                    imag[b] = uI - vI;
                    real[a] = uR + vR;
                    imag[a] = uI + vI;
                }
            }
        }

        // If inverse, divide by n
        if (inverse) {
            for (int i = 0; i < n; i++) {
                real[i] /= n;
                imag[i] /= n;
            }
        }
    }
} 