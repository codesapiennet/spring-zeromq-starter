// matrix_vector_mul.cl - OpenCL kernel for matrix-vector multiplication
// Placeholder kernel provided for integration and testing. Replace with
// optimized kernels for production (tiling, local memory usage, etc.).

#pragma OPENCL EXTENSION cl_khr_fp32 : enable

__kernel void mat_vec_mul(__global const float* matrix,
                          __global const float* vector,
                          __global float* result,
                          const int rows,
                          const int cols) {
    int r = get_global_id(0);
    if (r >= rows) return;

    float acc = 0.0f;
    int base = r * cols;
    for (int c = 0; c < cols; ++c) {
        acc += matrix[base + c] * vector[c];
    }
    result[r] = acc;
} 