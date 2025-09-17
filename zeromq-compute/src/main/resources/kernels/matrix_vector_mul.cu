// matrix_vector_mul.cu - CUDA kernel for matrix-vector multiplication
// Placeholder kernel. For production use cuBLAS or highly-optimized bespoke kernels.

extern "C" __global__ void matVecMul(const float* matrix, const float* vector, float* result, int rows, int cols) {
    int r = blockIdx.x * blockDim.x + threadIdx.x;
    if (r >= rows) return;

    float sum = 0.0f;
    int base = r * cols;
    for (int c = 0; c < cols; ++c) {
        sum += matrix[base + c] * vector[c];
    }
    result[r] = sum;
} 