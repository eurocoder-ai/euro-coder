package eu.eurocoder.sovereigncli.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkReportTest {

    @TempDir
    Path tempDir;

    private BenchmarkReport report;

    @BeforeEach
    void setUp() {
        report = new BenchmarkReport();
        ReflectionTestUtils.setField(report, "resultsDir", tempDir.toString());
    }

    @Test
    void formatResults_emptyList_returnsNoResults() {
        String output = report.formatResults(List.of());

        assertThat(output).contains("No benchmark results");
    }

    @Test
    void formatResults_withResults_containsSummary() {
        List<BenchmarkResult> results = List.of(
                BenchmarkResult.success("gen-fibonacci", "code-generation",
                        "codestral-latest", "mistral",
                        List.of(new BenchmarkResult.AssertionOutcome("response_contains", true, "found")),
                        1500, 100, 200),
                BenchmarkResult.failure("debug-fix-npe", "debugging",
                        "codestral-latest", "mistral",
                        2000, "Timeout")
        );

        String output = report.formatResults(results);

        assertThat(output).contains("BENCHMARK RESULTS");
        assertThat(output).contains("CODE-GENERATION");
        assertThat(output).contains("DEBUGGING");
        assertThat(output).contains("gen-fibonacci");
        assertThat(output).contains("debug-fix-npe");
        assertThat(output).contains("PASS");
        assertThat(output).contains("FAIL");
        assertThat(output).contains("Total: 2");
        assertThat(output).contains("Passed: 1");
        assertThat(output).contains("Failed: 1");
    }

    @Test
    void formatResults_showsTokensWhenAvailable() {
        List<BenchmarkResult> results = List.of(
                BenchmarkResult.success("gen-fibonacci", "code-generation",
                        "model", "mistral",
                        List.of(new BenchmarkResult.AssertionOutcome("response_contains", true, "ok")),
                        1000, 50, 150)
        );

        String output = report.formatResults(results);

        assertThat(output).contains("50/150");
    }

    @Test
    void saveResults_createsJsonFile() {
        List<BenchmarkResult> results = List.of(
                BenchmarkResult.success("gen-fibonacci", "code-generation",
                        "codestral-latest", "mistral",
                        List.of(new BenchmarkResult.AssertionOutcome("response_contains", true, "ok")),
                        1000, 50, 100)
        );

        report.saveResults(results, "codestral-latest", "mistral");

        List<BenchmarkReport.RunSnapshot> runs = report.loadPreviousRuns();
        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst().results()).hasSize(1);
        assertThat(runs.getFirst().results().getFirst().taskId()).isEqualTo("gen-fibonacci");
    }

    @Test
    void loadLatestRun_noRuns_returnsNull() {
        assertThat(report.loadLatestRun()).isNull();
    }

    @Test
    void loadLatestRun_afterSave_returnsLatest() {
        report.saveResults(
                List.of(BenchmarkResult.success("task1", "cat", "model", "prov",
                        List.of(), 100, 0, 0)),
                "model", "prov");

        BenchmarkReport.RunSnapshot latest = report.loadLatestRun();

        assertThat(latest).isNotNull();
        assertThat(latest.results()).hasSize(1);
    }

    @Test
    void formatComparison_emptyList_returnsNoRuns() {
        String output = report.formatComparison(List.of());

        assertThat(output).contains("No benchmark runs");
    }

    @Test
    void formatComparison_withSnapshots_showsComparisonTable() {
        List<BenchmarkReport.RunSnapshot> snapshots = List.of(
                new BenchmarkReport.RunSnapshot("run1.json", List.of(
                        BenchmarkResult.success("t1", "cat", "model-a", "prov",
                                List.of(), 1000, 0, 0))),
                new BenchmarkReport.RunSnapshot("run2.json", List.of(
                        BenchmarkResult.failure("t1", "cat", "model-b", "prov", 2000, "err")))
        );

        String output = report.formatComparison(snapshots);

        assertThat(output).contains("BENCHMARK COMPARISON");
        assertThat(output).contains("run1.json");
        assertThat(output).contains("run2.json");
    }
}
