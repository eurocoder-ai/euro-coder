package eu.eurocoder.sovereigncli.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates formatted console output for benchmark results and persists them as JSON
 * in {@code ~/.eurocoder/benchmark-results/}.
 */
@Component
public class BenchmarkReport {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkReport.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int SEPARATOR_WIDTH = 80;
    private static final int COL_TASK = 30;
    private static final int COL_STATUS = 8;
    private static final int COL_LATENCY = 12;
    private static final int COL_TOKENS = 14;
    private static final int COL_DETAILS_PADDING = 20;
    private static final int MAX_ERROR_DISPLAY_LENGTH = 40;
    private static final int COL_COMPARE_RUN = 25;
    private static final int COL_COMPARE_MODEL = 10;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Value("${eurocoder.benchmark.results-dir:${user.home}/.eurocoder/benchmark-results}")
    private String resultsDir;

    public String formatResults(List<BenchmarkResult> results) {
        if (results.isEmpty()) {
            return "No benchmark results to display.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        appendSeparator(sb);
        sb.append("  BENCHMARK RESULTS\n");
        appendSeparator(sb);

        Map<String, List<BenchmarkResult>> byCategory = results.stream()
                .collect(Collectors.groupingBy(BenchmarkResult::category,
                        LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<BenchmarkResult>> entry : byCategory.entrySet()) {
            sb.append("\n  ").append(entry.getKey().toUpperCase()).append("\n");
            appendTableHeader(sb);

            for (BenchmarkResult result : entry.getValue()) {
                appendResultRow(sb, result);
            }
        }

        appendSeparator(sb);
        appendSummary(sb, results);
        sb.append("\n");
        return sb.toString();
    }

    public String formatComparison(List<RunSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return "No benchmark runs to compare.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        appendSeparator(sb);
        sb.append("  BENCHMARK COMPARISON\n");
        appendSeparator(sb);

        sb.append(String.format("  %-" + COL_COMPARE_RUN + "s %-" + COL_COMPARE_MODEL
                        + "s %-8s %-8s %-12s%n",
                "Run", "Model", "Passed", "Failed", "Avg Latency"));
        appendSeparator(sb);

        for (RunSnapshot snap : snapshots) {
            List<BenchmarkResult> results = snap.results();
            long passed = results.stream().filter(BenchmarkResult::passed).count();
            long failed = results.size() - passed;
            double avgLatency = results.stream()
                    .mapToLong(BenchmarkResult::latencyMs).average().orElse(0);
            String model = results.isEmpty() ? "N/A" : results.getFirst().modelName();

            sb.append(String.format("  %-" + COL_COMPARE_RUN + "s %-" + COL_COMPARE_MODEL
                            + "s %-8d %-8d %8.0fms%n",
                    truncate(snap.filename(), COL_COMPARE_RUN),
                    truncate(model, COL_COMPARE_MODEL),
                    passed, failed, avgLatency));
        }

        appendSeparator(sb);
        return sb.toString();
    }

    public void saveResults(List<BenchmarkResult> results, String modelName, String provider) {
        try {
            Path dir = Paths.get(resultsDir);
            Files.createDirectories(dir);

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String sanitizedModel = modelName.replaceAll("[^a-zA-Z0-9.-]", "_");
            String filename = String.format("%s-%s-%s.json", timestamp, provider, sanitizedModel);
            Path outputFile = dir.resolve(filename);

            RunData data = new RunData(timestamp, provider, modelName, results.size(),
                    results.stream().filter(BenchmarkResult::passed).count(), results);

            objectMapper.writeValue(outputFile.toFile(), data);
            log.info("Benchmark results saved to: {}", outputFile);
        } catch (IOException e) {
            log.warn("Failed to save benchmark results: {}", e.getMessage());
        }
    }

    public List<RunSnapshot> loadPreviousRuns() {
        Path dir = Paths.get(resultsDir);
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(this::loadSnapshot)
                    .filter(s -> s != null)
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to load previous benchmark runs: {}", e.getMessage());
            return List.of();
        }
    }

    public RunSnapshot loadLatestRun() {
        List<RunSnapshot> runs = loadPreviousRuns();
        return runs.isEmpty() ? null : runs.getLast();
    }

    private RunSnapshot loadSnapshot(Path file) {
        try {
            RunData data = objectMapper.readValue(file.toFile(), RunData.class);
            return new RunSnapshot(file.getFileName().toString(), data.results());
        } catch (IOException e) {
            log.debug("Failed to load benchmark run {}: {}", file.getFileName(), e.getMessage());
            return null;
        }
    }

    private void appendTableHeader(StringBuilder sb) {
        sb.append(String.format("  %-" + COL_TASK + "s %-" + COL_STATUS + "s %-"
                        + COL_LATENCY + "s %-" + COL_TOKENS + "s %s%n",
                "Task", "Status", "Latency", "Tokens", "Details"));
        sb.append("  ").append("-".repeat(COL_TASK + COL_STATUS + COL_LATENCY + COL_TOKENS + COL_DETAILS_PADDING))
                .append("\n");
    }

    private void appendResultRow(StringBuilder sb, BenchmarkResult result) {
        String status = result.passed() ? "PASS" : "FAIL";
        String latency = result.latencyMs() + "ms";
        String tokens = result.inputTokens() + result.outputTokens() > 0
                ? result.inputTokens() + "/" + result.outputTokens()
                : "-";
        String details = result.error() != null
                ? truncate(result.error(), MAX_ERROR_DISPLAY_LENGTH)
                : formatAssertionSummary(result.assertionResults());

        sb.append(String.format("  %-" + COL_TASK + "s %-" + COL_STATUS + "s %-"
                        + COL_LATENCY + "s %-" + COL_TOKENS + "s %s%n",
                truncate(result.taskId(), COL_TASK),
                status, latency, tokens, details));
    }

    private String formatAssertionSummary(List<BenchmarkResult.AssertionOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) return "-";
        long passed = outcomes.stream().filter(BenchmarkResult.AssertionOutcome::passed).count();
        return passed + "/" + outcomes.size() + " assertions passed";
    }

    private void appendSummary(StringBuilder sb, List<BenchmarkResult> results) {
        long passed = results.stream().filter(BenchmarkResult::passed).count();
        long failed = results.size() - passed;
        double avgLatency = results.stream()
                .mapToLong(BenchmarkResult::latencyMs).average().orElse(0);
        long totalInput = results.stream().mapToLong(BenchmarkResult::inputTokens).sum();
        long totalOutput = results.stream().mapToLong(BenchmarkResult::outputTokens).sum();

        sb.append(String.format("  Total: %d | Passed: %d | Failed: %d | Avg latency: %.0fms",
                results.size(), passed, failed, avgLatency));
        if (totalInput + totalOutput > 0) {
            sb.append(String.format(" | Tokens: %d in / %d out", totalInput, totalOutput));
        }
        sb.append("\n");
    }

    private void appendSeparator(StringBuilder sb) {
        sb.append("  ").append("=".repeat(SEPARATOR_WIDTH)).append("\n");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    record RunData(String timestamp, String provider, String model,
                   long totalTasks, long passedTasks, List<BenchmarkResult> results) {}

    public record RunSnapshot(String filename, List<BenchmarkResult> results) {}
}
