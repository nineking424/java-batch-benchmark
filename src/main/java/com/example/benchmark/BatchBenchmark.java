package com.example.benchmark;

import com.example.benchmark.config.DatabaseConfig;
import com.example.benchmark.domain.Item;
import com.example.benchmark.result.BenchmarkResult;
import com.example.benchmark.test.JdbcBatchTest;
import com.example.benchmark.test.MyBatisBatchTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main class for Oracle JDBC Batch vs MyBatis Batch performance benchmark.
 *
 * <p>This benchmark compares three INSERT strategies:</p>
 * <ul>
 *   <li>JDBC Batch - Pure JDBC with PreparedStatement.addBatch()</li>
 *   <li>MyBatis BATCH - MyBatis with ExecutorType.BATCH</li>
 *   <li>MyBatis Single - MyBatis with ExecutorType.SIMPLE (comparison baseline)</li>
 * </ul>
 */
public class BatchBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(BatchBenchmark.class);

    private final DatabaseConfig config;
    private final int recordCount;
    private final int batchSize;
    private final int warmupIterations;
    private final int testIterations;

    public BatchBenchmark() {
        this.config = new DatabaseConfig();
        this.recordCount = config.getRecordCount_Setting();
        this.batchSize = config.getBatchSize();
        this.warmupIterations = config.getWarmupIterations();
        this.testIterations = config.getTestIterations();
    }

    /**
     * Main entry point for the benchmark.
     */
    public static void main(String[] args) {
        BatchBenchmark benchmark = new BatchBenchmark();
        benchmark.run();
    }

    /**
     * Run the complete benchmark suite.
     */
    public void run() {
        printBanner();
        printConfiguration();

        try {
            // Initialize database
            config.initDataSource();
            config.initSqlSessionFactory();
            config.initializeSchema();

            // Prepare test data
            List<Item> items = prepareTestData();

            // Run benchmarks
            JdbcBatchTest jdbcTest = new JdbcBatchTest(config);
            MyBatisBatchTest myBatisTest = new MyBatisBatchTest(config);

            BenchmarkResult jdbcResult = jdbcTest.runBenchmark(items, warmupIterations, testIterations);
            BenchmarkResult myBatisBatchResult = myBatisTest.runBatchBenchmark(items, warmupIterations, testIterations);
            BenchmarkResult myBatisSingleResult = myBatisTest.runSingleBenchmark(items, warmupIterations, testIterations);

            // Print results
            printResultsSummary(jdbcResult, myBatisBatchResult, myBatisSingleResult);

        } catch (Exception e) {
            logger.error("Benchmark failed", e);
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            config.shutdown();
        }
    }

    /**
     * Prepare test data with random values.
     */
    private List<Item> prepareTestData() {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("Preparing test data...");
        System.out.println("═══════════════════════════════════════════════════════");

        List<Item> items = new ArrayList<>(recordCount);
        Random random = new Random(42); // Fixed seed for reproducibility

        for (int i = 1; i <= recordCount; i++) {
            String name = String.format("Item-%08d", i);
            String description = "Test item description for benchmark testing - index " + i;
            int quantity = random.nextInt(1000); // 0-999
            double price = Math.round(random.nextDouble() * 1000000) / 100.0; // 0-10000.00

            items.add(new Item(name, description, quantity, price));
        }

        System.out.printf("  Prepared %,d items for testing%n", items.size());
        return items;
    }

    /**
     * Print benchmark results summary.
     */
    private void printResultsSummary(BenchmarkResult jdbcResult,
                                     BenchmarkResult myBatisBatchResult,
                                     BenchmarkResult myBatisSingleResult) {
        System.out.println();
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    BENCHMARK RESULTS                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        // Print individual results
        jdbcResult.printResult();
        myBatisBatchResult.printResult();

        if (myBatisSingleResult != null) {
            myBatisSingleResult.printResult();
        }

        // Print comparisons
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                   COMPARISON ANALYSIS                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        BenchmarkResult.printComparison(jdbcResult, myBatisBatchResult);

        if (myBatisSingleResult != null) {
            BenchmarkResult.printBatchVsSingleComparison(jdbcResult, myBatisSingleResult);
            BenchmarkResult.printBatchVsSingleComparison(myBatisBatchResult, myBatisSingleResult);
        }

        // Print final summary
        printFinalSummary(jdbcResult, myBatisBatchResult, myBatisSingleResult);
    }

    /**
     * Print final summary and recommendations.
     */
    private void printFinalSummary(BenchmarkResult jdbcResult,
                                   BenchmarkResult myBatisBatchResult,
                                   BenchmarkResult myBatisSingleResult) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                        SUMMARY                               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Determine winner
        double jdbcAvg = jdbcResult.getAverageTime();
        double myBatisAvg = myBatisBatchResult.getAverageTime();

        String winner;
        double improvement;
        if (jdbcAvg < myBatisAvg) {
            winner = "JDBC Batch";
            improvement = ((myBatisAvg - jdbcAvg) / myBatisAvg) * 100;
        } else {
            winner = "MyBatis BATCH";
            improvement = ((jdbcAvg - myBatisAvg) / jdbcAvg) * 100;
        }

        System.out.println("  Test Configuration:");
        System.out.printf("    - Records per iteration: %,d%n", recordCount);
        System.out.printf("    - Batch size: %,d%n", batchSize);
        System.out.printf("    - Test iterations: %d%n", testIterations);
        System.out.println();
        System.out.println("  Performance Winner: " + winner);
        System.out.printf("    - Improvement: %.2f%% faster%n", improvement);
        System.out.println();
        System.out.println("  Throughput Summary:");
        System.out.printf("    - JDBC Batch:     %,.0f records/sec%n", jdbcResult.getThroughput());
        System.out.printf("    - MyBatis BATCH:  %,.0f records/sec%n", myBatisBatchResult.getThroughput());

        if (myBatisSingleResult != null) {
            System.out.printf("    - MyBatis Single: %,.0f records/sec%n", myBatisSingleResult.getThroughput());

            double batchSpeedup = myBatisSingleResult.getAverageTime() / Math.min(jdbcAvg, myBatisAvg);
            System.out.println();
            System.out.printf("  Batch processing is %.1fx faster than single INSERT%n", batchSpeedup);
        }

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("Benchmark completed successfully!");
        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    /**
     * Print startup banner.
     */
    private void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                              ║");
        System.out.println("║     Oracle JDBC Batch vs MyBatis Batch Benchmark             ║");
        System.out.println("║                                                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    /**
     * Print benchmark configuration.
     */
    private void printConfiguration() {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("Configuration:");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.printf("  Record Count      : %,d%n", recordCount);
        System.out.printf("  Batch Size        : %,d%n", batchSize);
        System.out.printf("  Warmup Iterations : %d%n", warmupIterations);
        System.out.printf("  Test Iterations   : %d%n", testIterations);
    }
}
