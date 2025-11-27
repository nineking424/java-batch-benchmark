package com.example.benchmark.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores and calculates benchmark statistics for a test run.
 * Provides average, median, min, max, standard deviation, and throughput.
 */
public class BenchmarkResult {

    private final String testName;
    private final int recordCount;
    private final int batchSize;
    private final List<Long> executionTimes;

    public BenchmarkResult(String testName, int recordCount, int batchSize) {
        this.testName = testName;
        this.recordCount = recordCount;
        this.batchSize = batchSize;
        this.executionTimes = new ArrayList<>();
    }

    /**
     * Add an execution time from a single iteration.
     *
     * @param timeMs execution time in milliseconds
     */
    public void addExecutionTime(long timeMs) {
        executionTimes.add(timeMs);
    }

    /**
     * Get the test name.
     */
    public String getTestName() {
        return testName;
    }

    /**
     * Get the number of records per iteration.
     */
    public int getRecordCount() {
        return recordCount;
    }

    /**
     * Get the batch size used.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Get the number of iterations completed.
     */
    public int getIterationCount() {
        return executionTimes.size();
    }

    /**
     * Get all execution times.
     */
    public List<Long> getExecutionTimes() {
        return Collections.unmodifiableList(executionTimes);
    }

    /**
     * Calculate the average execution time.
     *
     * @return average time in milliseconds
     */
    public double getAverageTime() {
        if (executionTimes.isEmpty()) {
            return 0;
        }
        return executionTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
    }

    /**
     * Calculate the median execution time.
     *
     * @return median time in milliseconds
     */
    public double getMedianTime() {
        if (executionTimes.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(executionTimes);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    /**
     * Get the minimum execution time.
     *
     * @return minimum time in milliseconds
     */
    public long getMinTime() {
        return executionTimes.stream()
                .mapToLong(Long::longValue)
                .min()
                .orElse(0);
    }

    /**
     * Get the maximum execution time.
     *
     * @return maximum time in milliseconds
     */
    public long getMaxTime() {
        return executionTimes.stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
    }

    /**
     * Calculate the standard deviation of execution times.
     *
     * @return standard deviation in milliseconds
     */
    public double getStandardDeviation() {
        if (executionTimes.size() < 2) {
            return 0;
        }
        double avg = getAverageTime();
        double sumSquaredDiff = executionTimes.stream()
                .mapToDouble(t -> Math.pow(t - avg, 2))
                .sum();
        return Math.sqrt(sumSquaredDiff / (executionTimes.size() - 1));
    }

    /**
     * Calculate the throughput (records per second) based on average time.
     *
     * @return throughput in records/sec
     */
    public double getThroughput() {
        double avgTime = getAverageTime();
        if (avgTime == 0) {
            return 0;
        }
        return (recordCount / avgTime) * 1000;
    }

    /**
     * Print the result in a formatted box.
     */
    public void printResult() {
        String border = "══════════════════════════════════════════════════════════════";

        System.out.println();
        System.out.println("╔" + border + "╗");
        System.out.printf("║ %-62s ║%n", testName);
        System.out.println("╠" + border + "╣");
        System.out.printf("║ Records: %,-10d Batch Size: %,-10d Iterations: %-5d ║%n",
                recordCount, batchSize, getIterationCount());
        System.out.println("╠" + border + "╣");
        System.out.printf("║ Average Time    : %,18.2f ms                         ║%n", getAverageTime());
        System.out.printf("║ Median Time     : %,18.2f ms                         ║%n", getMedianTime());
        System.out.printf("║ Min Time        : %,18d ms                         ║%n", getMinTime());
        System.out.printf("║ Max Time        : %,18d ms                         ║%n", getMaxTime());
        System.out.printf("║ Std Deviation   : %,18.2f ms                         ║%n", getStandardDeviation());
        System.out.printf("║ Throughput      : %,18.2f records/sec                ║%n", getThroughput());
        System.out.println("╚" + border + "╝");
    }

    /**
     * Print comparison between two benchmark results.
     *
     * @param result1 first result (typically JDBC)
     * @param result2 second result (typically MyBatis)
     */
    public static void printComparison(BenchmarkResult result1, BenchmarkResult result2) {
        double avg1 = result1.getAverageTime();
        double avg2 = result2.getAverageTime();
        double diff = Math.abs(avg1 - avg2);
        double percentDiff = (diff / Math.max(avg1, avg2)) * 100;
        String winner = avg1 < avg2 ? result1.getTestName() : result2.getTestName();

        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.printf("│ %-62s │%n", result1.getTestName() + " vs " + result2.getTestName());
        System.out.println("├──────────────────────────────────────────────────────────────┤");
        System.out.printf("│ ├── %-20s: %,15.2f ms                    │%n",
                truncate(result1.getTestName(), 20), avg1);
        System.out.printf("│ ├── %-20s: %,15.2f ms                    │%n",
                truncate(result2.getTestName(), 20), avg2);
        System.out.printf("│ ├── Difference        : %,15.2f ms (%.2f%%)            │%n", diff, percentDiff);
        System.out.printf("│ └── Winner            : %-36s │%n", winner);
        System.out.println("├──────────────────────────────────────────────────────────────┤");
        System.out.printf("│ Throughput Comparison:                                       │%n");
        System.out.printf("│ ├── %-20s: %,15.0f records/sec           │%n",
                truncate(result1.getTestName(), 20), result1.getThroughput());
        System.out.printf("│ └── %-20s: %,15.0f records/sec           │%n",
                truncate(result2.getTestName(), 20), result2.getThroughput());
        System.out.println("└──────────────────────────────────────────────────────────────┘");
    }

    /**
     * Print batch vs single comparison showing performance improvement.
     *
     * @param batchResult batch processing result
     * @param singleResult single insert result
     */
    public static void printBatchVsSingleComparison(BenchmarkResult batchResult, BenchmarkResult singleResult) {
        double batchAvg = batchResult.getAverageTime();
        double singleAvg = singleResult.getAverageTime();
        double speedup = singleAvg / batchAvg;

        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────────────────┐");
        System.out.printf("│ Batch vs Single INSERT Performance                           │%n");
        System.out.println("├──────────────────────────────────────────────────────────────┤");
        System.out.printf("│ ├── %-20s: %,15.2f ms                    │%n",
                truncate(batchResult.getTestName(), 20), batchAvg);
        System.out.printf("│ ├── %-20s: %,15.2f ms                    │%n",
                truncate(singleResult.getTestName(), 20), singleAvg);
        System.out.printf("│ └── Batch Speedup     : %,15.2fx faster                 │%n", speedup);
        System.out.println("└──────────────────────────────────────────────────────────────┘");
    }

    private static String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}
