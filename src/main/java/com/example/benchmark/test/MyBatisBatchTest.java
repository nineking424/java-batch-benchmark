package com.example.benchmark.test;

import com.example.benchmark.config.DatabaseConfig;
import com.example.benchmark.domain.Item;
import com.example.benchmark.mapper.ItemMapper;
import com.example.benchmark.result.BenchmarkResult;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * MyBatis Batch and Single INSERT test implementation.
 * Tests both ExecutorType.BATCH and ExecutorType.SIMPLE modes.
 */
public class MyBatisBatchTest {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisBatchTest.class);

    private final DatabaseConfig config;
    private final SqlSessionFactory sqlSessionFactory;
    private final int batchSize;

    public MyBatisBatchTest(DatabaseConfig config) {
        this.config = config;
        this.sqlSessionFactory = config.getSqlSessionFactory();
        this.batchSize = config.getBatchSize();
    }

    /**
     * Run the MyBatis BATCH mode INSERT benchmark.
     *
     * @param items list of items to insert
     * @param warmupIterations number of warmup iterations
     * @param testIterations number of actual test iterations
     * @return benchmark result with statistics
     */
    public BenchmarkResult runBatchBenchmark(List<Item> items, int warmupIterations, int testIterations) {
        BenchmarkResult result = new BenchmarkResult("MyBatis BATCH INSERT", items.size(), batchSize);

        printHeader("Running MyBatis BATCH Test...");

        // Warmup phase
        System.out.println("  [MyBatis BATCH] Warming up...");
        for (int i = 0; i < warmupIterations; i++) {
            config.truncateTable();
            executeBatchInsert(items);
        }

        // Actual test phase
        System.out.println("  [MyBatis BATCH] Running benchmark...");
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
     * Run the MyBatis SIMPLE mode (single INSERT) benchmark.
     * Only runs when record count is <= 10,000 due to time constraints.
     *
     * @param items list of items to insert
     * @param warmupIterations number of warmup iterations
     * @param testIterations number of actual test iterations
     * @return benchmark result with statistics, or null if skipped
     */
    public BenchmarkResult runSingleBenchmark(List<Item> items, int warmupIterations, int testIterations) {
        // Skip if record count is too high
        if (items.size() > 10000) {
            System.out.println();
            System.out.println("═══════════════════════════════════════════════════════");
            System.out.println("Skipping MyBatis Single INSERT Test...");
            System.out.println("═══════════════════════════════════════════════════════");
            System.out.println("  [MyBatis Single] Skipped (record count > 10,000)");
            return null;
        }

        BenchmarkResult result = new BenchmarkResult("MyBatis Single INSERT", items.size(), 1);

        printHeader("Running MyBatis Single INSERT Test...");

        // Warmup phase
        System.out.println("  [MyBatis Single] Warming up...");
        for (int i = 0; i < warmupIterations; i++) {
            config.truncateTable();
            executeSingleInsert(items);
        }

        // Actual test phase
        System.out.println("  [MyBatis Single] Running benchmark...");
        for (int i = 0; i < testIterations; i++) {
            config.truncateTable();

            long startTime = System.currentTimeMillis();
            executeSingleInsert(items);
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
     * Execute batch INSERT using MyBatis ExecutorType.BATCH.
     *
     * @param items list of items to insert
     */
    private void executeBatchInsert(List<Item> items) {
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            ItemMapper mapper = session.getMapper(ItemMapper.class);

            int count = 0;
            for (Item item : items) {
                mapper.insert(item);
                count++;

                // Flush statements every batchSize records
                if (count % batchSize == 0) {
                    session.flushStatements();
                    session.clearCache();
                }
            }

            // Flush remaining statements
            if (count % batchSize != 0) {
                session.flushStatements();
                session.clearCache();
            }

            session.commit();
        }
    }

    /**
     * Execute single INSERT using MyBatis ExecutorType.SIMPLE.
     *
     * @param items list of items to insert
     */
    private void executeSingleInsert(List<Item> items) {
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.SIMPLE, false)) {
            ItemMapper mapper = session.getMapper(ItemMapper.class);

            for (Item item : items) {
                mapper.insert(item);
            }

            session.commit();
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
