package com.example.zeromq.compute.science;

import com.example.zeromq.core.DenseVector;

import java.util.Arrays;

/**
 * Basic linear algebra utilities used by scientific tasks. Implementations are
 * intentionally simple and robust; optimized libraries should be used in production.
 */
public final class LinearAlgebra {

    private LinearAlgebra() {}

    public static DenseVector multiplyMatrixVector(float[][] matrix, DenseVector vector) {
        if (matrix == null || vector == null) throw new IllegalArgumentException("matrix and vector must not be null");
        int rows = matrix.length;
        int cols = matrix[0].length;
        if (cols != vector.getDimensions()) throw new IllegalArgumentException("Matrix columns must match vector dimensions");
        float[] out = new float[rows];
        for (int i = 0; i < rows; i++) {
            float[] row = matrix[i];
            float sum = 0.0f;
            for (int j = 0; j < cols; j++) sum += row[j] * vector.getData()[j];
            out[i] = sum;
        }
        return new DenseVector(out);
    }

    /**
     * Solve linear system Ax = b using Gaussian elimination with partial pivoting.
     * A and b are double precision for numerical stability; returns solution x.
     */
    public static double[] solveLinearSystem(double[][] A, double[] b) {
        int n = A.length;
        if (n == 0) return new double[0];
        double[][] mat = new double[n][n+1];
        for (int i = 0; i < n; i++) {
            if (A[i].length != n) throw new IllegalArgumentException("Matrix A must be square");
            System.arraycopy(A[i], 0, mat[i], 0, n);
            mat[i][n] = b[i];
        }

        for (int p = 0; p < n; p++) {
            // Partial pivot
            int max = p;
            for (int i = p+1; i < n; i++) if (Math.abs(mat[i][p]) > Math.abs(mat[max][p])) max = i;
            double[] tmp = mat[p]; mat[p] = mat[max]; mat[max] = tmp;
            if (Math.abs(mat[p][p]) < 1e-12) throw new IllegalArgumentException("Matrix is singular or nearly singular");

            // Normalize pivot row
            double pivot = mat[p][p];
            for (int j = p; j <= n; j++) mat[p][j] /= pivot;

            // Eliminate
            for (int i = 0; i < n; i++) {
                if (i == p) continue;
                double factor = mat[i][p];
                for (int j = p; j <= n; j++) mat[i][j] -= factor * mat[p][j];
            }
        }

        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = mat[i][n];
        return x;
    }
} 