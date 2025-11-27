package com.example.benchmark.test;

import com.example.benchmark.config.DatabaseConfig;
import com.example.benchmark.domain.Item;
import com.example.benchmark.result.BenchmarkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC Batch INSERT test implementation.
 * Uses PreparedStatement.addBatch() and executeBatch() for batch processing.
 */
public class JdbcBatchTest {

    private static final Logger logger = LoggerFactory.getLogger(JdbcBatchTest.class);

    private static final String INSERT_SQL =
            "INSERT INTO benchmark_items (id, name, description, quantity, price) " +
                    "VALUES (benchmark_items_seq.NEXTVAL, ?, ?, ?, ?)";

    private final DatabaseConfig config;
    private final int batchSize;

    public JdbcBatchTest(DatabaseConfig config) {
        this.config = config;
        this.batchSize = config.getBatchSize();
    }

    /**
     * Run the JDBC Batch INSERT benchmark.
     *
     * @param items list of items to insert
     * @param warmupIterations number of warmup iterations
     * @param testIterations number of actual test iterations
     * @return benchmark result with statistics
     */
    public BenchmarkResult runBenchmark(List<Item> items, int warmupIterations, int testIterations) {
        BenchmarkResult result = new BenchmarkResult("JDBC Batch INSERT", items.size(), batchSize);

        printHeader("Running JDBC Batch Test...");

        // Warmup phase
        System.out.println("  [JDBC] Warming up...");
        for (int i = 0; i < warmupIterations; i++) {
            config.truncateTable();
            executeBatchInsert(items);
        }

        // Actual test phase
        System.out.println("  [JDBC] Running benchmark...");
        for (int i = 0; i < testIterations; i++) {
            config.truncateTable();

            long startTime = System.currentTimeMillis();
            executeBatchInsert(items);
            long endTime = System.currentTimeMillis();

            long elapsed = endTime - startTime;
            long count = config.getRecordCount();

            result.addExecutionTime(elapsed);
            System.out.printf("    Iteration %d: %,d ms (Count: %,d)%n", i + 1, elapsed, count);

            // Verify record count
            if (count != items.size()) {
                logger.warn("Record count mismatch! Expected: {}, Actual: {}", items.size(), count);
            }
        }

        return result;
    }

    /**
     * Execute batch INSERT using JDBC.
     *
     * @param items list of items to insert
     */
    private void executeBatchInsert(List<Item> items) {
        try (Connection conn = config.getDataSource().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT_SQL)) {

            conn.setAutoCommit(false);

            int count = 0;
            for (Item item : items) {
                pstmt.setString(1, item.getName());
                pstmt.setString(2, item.getDescription());
                pstmt.setInt(3, item.getQuantity());
                pstmt.setDouble(4, item.getPrice());
                pstmt.addBatch();

                count++;

                // Execute batch every batchSize records
                if (count % batchSize == 0) {
                    pstmt.executeBatch();
                    pstmt.clearBatch();
                }
            }

            // Execute remaining batch
            if (count % batchSize != 0) {
                pstmt.executeBatch();
                pstmt.clearBatch();
            }

            conn.commit();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute JDBC batch insert", e);
        }
    }

    /**
     * Print section header.
     */
    private void printHeader(String title) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println(title);
        System.out.println("═══════════════════════════════════════════════════════");
    }
}
