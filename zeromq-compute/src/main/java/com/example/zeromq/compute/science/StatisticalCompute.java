package com.example.zeromq.compute.science;

/**
 * Basic statistical routines used by scientific tasks. Implementations prioritize
 * numerical stability where practical and are suitable for small to medium-sized data.
 */
public final class StatisticalCompute {

    private StatisticalCompute() {}

    public static double mean(double[] data) {
        double sum = 0.0;
        for (double v : data) sum += v;
        return data.length == 0 ? 0.0 : sum / data.length;
    }

    public static double variance(double[] data) {
        if (data.length == 0) return 0.0;
        double mu = mean(data);
        double s = 0.0;
        for (double v : data) {
            double d = v - mu; s += d * d;
        }
        return s / data.length;
    }

    public static double covariance(double[] x, double[] y) {
        if (x.length != y.length) throw new IllegalArgumentException("Arrays must have same length");
        int n = x.length;
        if (n == 0) return 0.0;
        double mx = mean(x);
        double my = mean(y);
        double s = 0.0;
        for (int i = 0; i < n; i++) s += (x[i] - mx) * (y[i] - my);
        return s / n;
    }

    public static double pearsonCorrelation(double[] x, double[] y) {
        double cov = covariance(x, y);
        double stdx = Math.sqrt(variance(x));
        double stdy = Math.sqrt(variance(y));
        if (stdx == 0.0 || stdy == 0.0) return 0.0;
        return cov / (stdx * stdy);
    }
} 