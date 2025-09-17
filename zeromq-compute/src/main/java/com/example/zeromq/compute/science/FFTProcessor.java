package com.example.zeromq.compute.science;

/**
 * Simple iterative Cooley-Tukey FFT implementation for radix-2 lengths.
 * This class provides utilities for in-place complex transforms using
 * separate real/imag arrays.
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

        // Cooley-Tukey
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? -1 : 1);
            double wlenR = Math.cos(ang);
            double wlenI = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double uR = 1.0; double uI = 0.0;
                for (int j = 0; j < (len >> 1); j++) {
                    int a = i + j;
                    int b = i + j + (len >> 1);
                    double vR = real[b] * uR - imag[b] * uI;
                    double vI = real[b] * uI + imag[b] * uR;

                    real[b] = real[a] - vR;
                    imag[b] = imag[a] - vI;
                    real[a] += vR;
                    imag[a] += vI;

                    double nextUR = uR * wlenR - uI * wlenI;
                    double nextUI = uR * wlenI + uI * wlenR;
                    uR = nextUR; uI = nextUI;
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