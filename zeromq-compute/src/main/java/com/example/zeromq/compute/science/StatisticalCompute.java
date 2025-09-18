package com.example.zeromq.compute.science;

/**
 * Basic statistical routines used by scientific tasks. Implementations prioritize
 * numerical stability where practical and are suitable for small to medium-sized data.
 */
public final class StatisticalCompute {

    private StatisticalCompute() {}

    public static double mean(double[] data) {
        if (data == null || data.length == 0) return 0.0;
        // Kahan summation for numerical stability
        double sum = 0.0;
        double c = 0.0;
        for (double v : data) {
            double y = v - c;
            double t = sum + y;
            c = (t - sum) - y;
            sum = t;
        }
        return sum / data.length;
    }

    public static double variance(double[] data) {
        if (data == null || data.length == 0) return 0.0;
        // Welford's online algorithm for stable one-pass variance
        double mean = 0.0;
        double m2 = 0.0;
        int n = 0;
        for (double x : data) {
            n++;
            double delta = x - mean;
            mean += delta / n;
            double delta2 = x - mean;
            m2 += delta * delta2;
        }
        return m2 / n; // population variance
    }

    public static double covariance(double[] x, double[] y) {
        if (x == null || y == null) throw new IllegalArgumentException("Arrays must not be null");
        if (x.length != y.length) throw new IllegalArgumentException("Arrays must have same length");
        int n = x.length;
        if (n == 0) return 0.0;
        double meanX = mean(x);
        double meanY = mean(y);
        double cov = 0.0;
        for (int i = 0; i < n; i++) cov += (x[i] - meanX) * (y[i] - meanY);
        return cov / n;
    }

    public static double pearsonCorrelation(double[] x, double[] y) {
        double cov = covariance(x, y);
        double stdx = Math.sqrt(variance(x));
        double stdy = Math.sqrt(variance(y));
        if (stdx == 0.0 || stdy == 0.0) return 0.0;
        return cov / (stdx * stdy);
    }
} 