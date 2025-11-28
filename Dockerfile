# Multi-stage build for Java Batch Benchmark Application
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="benchmark-team"
LABEL description="Java Batch Benchmark - JDBC vs MyBatis performance comparison"
LABEL version="1.0.0"

WORKDIR /app

# Create non-root user for security
RUN addgroup -g 1000 benchmark && \
    adduser -u 1000 -G benchmark -s /bin/sh -D benchmark

# Copy the uber-jar from builder stage
COPY --from=builder /app/target/batch-benchmark-1.0.0.jar ./benchmark.jar

# Copy configuration files (can be overridden via ConfigMap)
COPY --from=builder /app/src/main/resources/application.properties ./config/
COPY --from=builder /app/src/main/resources/logback.xml ./config/

# Set ownership
RUN chown -R benchmark:benchmark /app

USER benchmark

# Environment variables for configuration override
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
ENV DB_URL="jdbc:oracle:thin:@//localhost:1521/XEPDB1"
ENV DB_USERNAME="benchmark_user"
ENV DB_PASSWORD="benchmark_password"
ENV BENCHMARK_RECORD_COUNT="10000"
ENV BENCHMARK_BATCH_SIZE="1000"
ENV BENCHMARK_WARMUP_ITERATIONS="2"
ENV BENCHMARK_TEST_ITERATIONS="5"

# Health check (simple JVM check)
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD pgrep -f "benchmark.jar" || exit 1

# Run the benchmark
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS \
    -Ddb.url=$DB_URL \
    -Ddb.username=$DB_USERNAME \
    -Ddb.password=$DB_PASSWORD \
    -Dbenchmark.recordCount=$BENCHMARK_RECORD_COUNT \
    -Dbenchmark.batchSize=$BENCHMARK_BATCH_SIZE \
    -Dbenchmark.warmupIterations=$BENCHMARK_WARMUP_ITERATIONS \
    -Dbenchmark.testIterations=$BENCHMARK_TEST_ITERATIONS \
    -jar benchmark.jar"]
