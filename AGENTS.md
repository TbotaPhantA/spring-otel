# AGENTS.md

This is a Kotlin + Spring Boot project using Gradle as the build system.

## Project Overview

- **Language**: Kotlin 2.2.21
- **Framework**: Spring Boot 4.0.5
- **Build Tool**: Gradle 9.4.1 (via Gradle Wrapper)
- **Java Version**: 21
- **Package**: `com.example.otel`
- **Test Framework**: JUnit 5 (via kotlin-test-junit5)
- **Additional Tools**: Spring Boot Actuator, Micrometer, OpenTelemetry, SpringDoc

## Observability Stack

The project includes a full observability stack with Grafana, Loki (logs), Tempo (traces), Prometheus (metrics), Pyroscope (profiling), and MinIO (S3 storage).

### Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| **Grafana** | http://localhost:3000 | admin/admin |
| **Prometheus** | http://localhost:9090 | - |
| **Tempo** | http://localhost:3200 | - |
| **Pyroscope** | http://localhost:4040 | - |
| **MinIO Console** | http://localhost:9001 | admin/admin123 |
| **App Metrics** | http://localhost:8080/actuator/prometheus | - |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | - |

### Starting the Observability Stack

```bash
docker compose up -d
```

### Example Grafana Queries

- **Request Rate**: `rate(http_server_requests_seconds_count{uri="/api/products"}[5m])`
- **Latency (p95)**: `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/products"}[5m]))`
- **Logs**: `{app="otel"} |= "ERROR"`
- **Traces**: Use Tempo datasource with trace ID from logs
- **Profiles**: `pyroscope_cpu{app="otel"}`

## Build Commands

### Build & Test
```bash
./gradlew build          # Full build and test
./gradlew assemble       # Compile and package (no tests)
./gradlew classes        # Compile main sources only
./gradlew testClasses    # Compile test sources only
```

### Running the Application
```bash
./gradlew bootRun        # Run the application
./gradlew bootTestRun    # Run with test classpath
```

### Testing
```bash
./gradlew test                       # Run all tests
./gradlew test --rerun               # Force re-run all tests (ignore cache)

# Run single test class
./gradlew test --tests "com.example.otel.OtelApplicationTests"

# Run single test method
./gradlew test --tests "com.example.otel.OtelApplicationTests.contextLoads"

# Run tests with fail-fast (stop on first failure)
./gradlew test --fail-fast

# Debug tests (starts JVM suspended on port 5005)
./gradlew test --debug-jvm
```

### Clean & Other
```bash
./gradlew clean          # Delete build directory
./gradlew clean build    # Clean and rebuild
./gradlew dependencies   # Show dependency tree
./gradlew check          # Run all verification (tests + other checks)
```

## Code Style Guidelines

### General Principles
- Follow standard Kotlin conventions as per [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Follow Spring Boot best practices for application structure
- Use explicit types over `var`; prefer `val` over `var` when possible

### Package & Naming Conventions
- Package format: `com.example.<project-name>`
- Class naming: PascalCase (e.g., `OtelApplication`)
- Function/variable naming: camelCase (e.g., `contextLoads`, `runApplication`)
- Test classes: End with `Tests` suffix (e.g., `OtelApplicationTests`)
- Test methods: Use `camelCase` with verb prefix (e.g., `shouldReturnOk`, `contextLoads`)

### Import Organization
Imports should follow Kotlin's recommended order:
1. `kotlin.*` imports
2. `javax.*` / `java.*` imports (note: Spring Boot 4.x uses Jakarta EE instead)
3. Third-party imports (Spring, Jackson, etc.)
4. Internal project imports

Use import aliases sparingly and only when they clarify intent.

### Type System
- Use Kotlin's null safety features (`?`, `?:`, `?.`, `let`)
- Avoid force unwrapping (`!!`) except when absolutely certain
- Use sealed classes for representing restricted type hierarchies
- Use data classes for simple data containers
- Return `Unit` explicitly only when side effects are the primary purpose

### Error Handling
- Prefer `Result<T>` or sealed results over throwing exceptions for expected failures
- Use specific exception types for unexpected failures
- Log errors with appropriate context (use Spring's `@Slf4j` or `Logger`)
- Never swallow exceptions silently

### Functions
- Use expression body syntax for single-expression functions when readable
- Keep functions focused and small (prefer <20 lines)
- Use default parameter values instead of function overloads when appropriate
- Prefer named arguments at call sites when parameters aren't obvious

### Classes & Objects
- Use `class` for mutable state, `data class` for immutable data
- Use `object` for singletons, `companion object` for class-level members
- Avoid deep inheritance hierarchies; prefer composition
- Mark classes `final` by default; use `open` only when extension is intended

### Coroutines (if added later)
- Use structured concurrency
- Prefer `suspend` functions over blocking calls
- Use `Flow` for streams, `Channel` for hot streams
- Handle cancellation explicitly when needed

### Annotations
- Place annotations on separate lines before the declaration
- Use annotation arguments with named parameters for clarity
- Spring annotations: `@Service`, `@Repository`, `@Component`, `@Controller`, `@RestController`
- Use `@SpringBootApplication` only on the main application class

### Testing Conventions
- One test class per production class (naming: `<ClassName>Tests`)
- Use `@SpringBootTest` for integration tests
- Use `@Test` from `org.junit.jupiter.api.Test`
- Test names should describe the expected behavior
- Use assertion libraries for readable error messages
- Avoid `Thread.sleep`; use `await` conditions or test utilities instead

### Code Formatting
- Indent with 4 spaces (Kotlin default)
- No semicolons at end of statements
- Use trailing commas where appropriate (especially in multi-line constructs)
- Max line length: 120 characters (Kotlin default)
- Use blank lines to separate logical sections within functions

### Gradle (build.gradle.kts)
- Use type-safe accessors for project properties
- Use `implementation` over `api` for better compile times
- Group related dependencies in comments sections if >10 dependencies
- Use platform dependencies (`enforcedPlatform`) for BOM alignment when needed

### Spring Boot Conventions
- Use constructor injection over field injection (preferred)
- Place configuration in `application.yml` or `application.properties`
- Externalize secrets via environment variables or Spring profiles
- Use `@ConfigurationProperties` for type-safe configuration binding

## File Structure

```
src/
├── main/
│   ├── kotlin/com/example/otel/
│   │   ├── OtelApplication.kt         # Main application class
│   │   ├── config/
│   │   │   ├── OpenApiConfig.kt        # Swagger/OpenAPI configuration
│   │   │   └── OtelConfig.kt          # OpenTelemetry SDK + TracingFilter
│   │   ├── controller/
│   │   │   └── ProductController.kt    # REST API endpoints
│   │   ├── dto/
│   │   │   ├── ProductRequest.kt       # Request DTOs
│   │   │   └── ProductResponse.kt      # Response DTOs
│   │   ├── entity/
│   │   │   └── Product.kt             # JPA entity
│   │   ├── exception/
│   │   │   ├── EntityNotFoundException.kt
│   │   │   └── GlobalExceptionHandler.kt
│   │   ├── repository/
│   │   │   └── ProductRepository.kt    # Spring Data JPA repository
│   │   └── service/
│   │       └── ProductService.kt       # Business logic
│   └── resources/
│       ├── application.yml             # Default configuration
│       └── logback-spring.xml         # Logging configuration
└── test/
    └── kotlin/com/example/otel/
        └── OtelApplicationTests.kt      # Integration tests

config/
├── prometheus/
│   └── prometheus.yml                  # Prometheus scrape config
├── loki/
│   └── loki.yml                       # Loki config
├── tempo/
│   └── tempo.yml                      # Tempo config
└── grafana/
    └── provisioning/
        ├── datasources/
        │   └── datasources.yml        # Auto-provisioned datasources
        └── dashboards/
            ├── dashboards.yml
            └── otel-overview.json       # Custom dashboard
```

## IDE Configuration

The project includes IntelliJ IDEA configuration (`.idea/`). 
For VS Code, a `.vscode/` folder is gitignored if needed.

## Additional Notes

- The project uses Gradle Toolchains to auto-download Java 21 if not present
- Spring Boot 4.x uses Jakarta EE (`jakarta.*`) instead of `javax.*`
- Actuator is configured for health checks and monitoring endpoints
- Jackson Kotlin module is included for JSON serialization
- The compiler uses strict JSR-305 checks (`-Xjsr305=strict`)
- Default annotation targets are set to `param-property`
- OpenTelemetry tracing is configured with 100% sampling (100% trace collection)
- JSON structured logging is enabled for Loki integration
- All observability data (metrics, logs, traces) is collected via Micrometer + OpenTelemetry
- Tempo accepts OTLP traces via gRPC on port 43170 (HTTP on 4318)
- The app sends traces directly to Tempo using manual OpenTelemetry SDK configuration
