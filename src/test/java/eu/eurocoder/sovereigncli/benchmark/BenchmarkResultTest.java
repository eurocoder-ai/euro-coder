package eu.eurocoder.sovereigncli.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkResultTest {

    @Test
    void success_allPassed_resultIsPassed() {
        List<BenchmarkResult.AssertionOutcome> outcomes = List.of(
                new BenchmarkResult.AssertionOutcome("response_contains", true, "found"),
                new BenchmarkResult.AssertionOutcome("file_exists", true, "exists")
        );

        BenchmarkResult result = BenchmarkResult.success(
                "task-1", "code-generation", "model", "provider",
                outcomes, 1500, 100, 200);

        assertThat(result.passed()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.assertionResults()).hasSize(2);
        assertThat(result.latencyMs()).isEqualTo(1500);
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(200);
    }

    @Test
    void success_oneFailed_resultIsFailed() {
        List<BenchmarkResult.AssertionOutcome> outcomes = List.of(
                new BenchmarkResult.AssertionOutcome("response_contains", true, "found"),
                new BenchmarkResult.AssertionOutcome("file_exists", false, "missing")
        );

        BenchmarkResult result = BenchmarkResult.success(
                "task-1", "debugging", "model", "provider",
                outcomes, 2000, 50, 100);

        assertThat(result.passed()).isFalse();
    }

    @Test
    void failure_hasErrorAndZeroTokens() {
        BenchmarkResult result = BenchmarkResult.failure(
                "task-1", "code-generation", "model", "provider",
                5000, "Timeout after 60s");

        assertThat(result.passed()).isFalse();
        assertThat(result.error()).isEqualTo("Timeout after 60s");
        assertThat(result.assertionResults()).isEmpty();
        assertThat(result.inputTokens()).isZero();
        assertThat(result.outputTokens()).isZero();
    }
}
