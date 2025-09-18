# Spring ZeroMQ Starter - Implementation TODO

This TODO replaces the previous, broad checklist with a focused set of actionable items discovered by a static scan that located incomplete/stubbed implementations, `return null` stubs, `UnsupportedOperationException` placeholders, and `TODO` comments.

Guiding principles for each task:
- Prefer safe, incremental changes. Make one logical batch of edits per pull request.
- Do not change public APIs or business logic without explicit approval. If a stub cannot be implemented safely, convert it to an explicit thrown exception with a clear message and add a Javadoc `@implNote` pointing to this TODO.
- Add JUnit 5 tests for every public class/method changed; target >= 80% coverage for modified classes.
- Add structured JSON logging for lifecycle/error events where missing.

## High-level tasks
- **Stop-silent-failures**: Replace all `return null;` stubs with either a proper implementation or an explicit `UnsupportedOperationException("Not implemented: <fully-qualified-method>")` to avoid NPEs at runtime.
- **Complete annotation processing**: Finish implementations in annotation processors and container factory so that published/subscribed handlers are discovered and wired correctly.
- **Implement compute engine paths**: Provide CPU/GPU implementations or clear fallbacks for kernels and ML inference shims; document native dependency requirements and provide feature flags/properties to disable native behavior.
- **Testing & CI**: Add unit and integration tests for every fixed class and wire the module into CI. Run `./mvnw -DskipTests=false test` and ensure Spotless/formatting passes.

## Detailed per-file TODOs (actionable)

- **zeromq-annotations**
  - `src/main/java/com/example/zeromq/annotation/processor/ZeroMQPublisherAspect.java`
    - Implement the methods currently returning `null` (found at scan locations). Ensure proper handling of return types, serialization via `MessageConverter`, and structured logging.
    - Implement custom error handler lookup (replace `// TODO: Implement custom error handler lookup`) and add unit tests verifying behavior when a custom handler is present and absent.
  - `src/main/java/com/example/zeromq/annotation/processor/ZeroMQSubscriberProcessor.java`
    - Replace `return null` stub with a robust subscriber registration implementation. Complete error handling strategy (see TODOs). Add unit tests covering subscription lifecycle and error scenarios.
  - `src/main/java/com/example/zeromq/annotation/config/ZeroMQMessageConverterRegistry.java`
    - Implement registry lookup methods that currently return `null`. Ensure converters are returned as `Optional<MessageConverter>` or throw a descriptive exception if missing. Add unit tests.
  - `src/main/java/com/example/zeromq/annotation/container/ZeroMQMessageListenerContainerFactory.java`
    - Implement container creation (lifecycle, start/stop, metrics tagging). Replace informational TODOs with code that binds `ZeroMqTemplate` and registers Micrometer metrics per rule #7. Add integration tests.
  - `src/main/java/com/example/zeromq/annotation/processor/ZeroMQAnnotationPostProcessor.java`
    - Process additional annotation types as noted by TODO; ensure processors produce Spring beans and validate configuration at startup. Add unit tests.
  - `src/main/java/com/example/zeromq/annotation/config/ZeroMQErrorHandlerRegistry.java`
    - Implement subscriber stop and message reprocessing scheduling per TODO comments. Ensure handlers are pluggable and non-blocking; add unit tests.

- **zeromq-autoconfigure**
  - `src/main/java/com/example/zeromq/autoconfig/ZeroMqAutoConfiguration.java`
    - Replace methods returning `null` with proper `@Bean` factory implementations. Ensure beans are conditional on properties and profiles (dev/prod) and that CURVE/secure configuration is enforced per `ZeroMqProperties`. Add configuration tests.

- **zeromq-compute (CPU/GPU/ML)**
  - `src/main/java/com/example/zeromq/compute/gpu/CudaComputeEngine.java`
    - Implement methods currently returning `null` or provide clear exceptions if native CUDA support is not present. Add Javadoc explaining native dependency requirements and feature flags to disable CUDA. Add unit and integration tests, and graceful fallback to CPU paths.
  - `src/main/java/com/example/zeromq/compute/cpu/MultiThreadedEngine.java`
    - Implement task submission/aggregation methods currently returning `null`. Ensure thread-safety and use `ConcurrencyUtils` and `WorkerManager` where applicable. Add tests for concurrency correctness and timeouts.
  - `src/main/java/com/example/zeromq/compute/cpu/OptimizedCpuComputeEngine.java`
    - Implement optimized code paths for methods returning `null`. Document algorithmic assumptions and add benchmark tests where feasible.
  - ML engine placeholders (`TensorFlowEngine.java`, `PyTorchEngine.java`, `ONNXEngine.java`)
    - For each `UnsupportedOperationException("Operation not implemented: " + op)` default case: either implement the operation or add a detailed Javadoc and runtime fallback that logs a WARN and returns the input/state safely. Add tests that assert fallback behavior.
  - `src/main/java/com/example/zeromq/compute/gpu/OpenCLComputeEngine.java`
    - Replace warning-only fallbacks with explicit implementations or documented fallbacks. Add tests for the fallback behavior.

- **General**
  - Audit and replace any other `return null;` found by the scan in the codebase with either real implementations, explicit exceptions, or validated `Optional` return types.
  - Convert intentionally uninstantiable utility-class constructors (like `ZAuthKeyGenerator` private constructor that throws) into documented patterns; ensure no other utility classes throw unexpected exceptions.

## Testing & Release tasks
- Add or update unit tests for every modified class under `src/test/java` mirroring the package structure; aim for ≥ 80% coverage for changed classes.
- Add integration tests that exercise annotation wiring (publish/subscribe) and compute engine fallbacks.
- Update CI to run tests and Spotless formatting: ensure `./mvnw spotless:apply` and `./mvnw -DskipTests=false test` pass.

## Acceptance criteria for each TODO
- No `return null;` stubs remain in committed code for public/non-internal methods.
- No unhandled `UnsupportedOperationException` placeholders in production paths without documented feature flags.
- All new/updated classes have unit tests demonstrating expected behavior and edge cases.
- Structured JSON logs are present for lifecycle/error events added during fixes.

---

This document should be kept current: after each PR closing a task, cross the item off with `- [x]` and reference the PR number. 