package eu.eurocoder.sovereigncli.benchmark;

import java.util.List;

/**
 * Result of running a single benchmark task against a model.
 *
 * @param taskId           the task that was run
 * @param category         task category (denormalized for easier reporting)
 * @param modelName        the model used
 * @param provider         the provider used (mistral, ollama)
 * @param passed           true if all assertions passed
 * @param assertionResults per-assertion pass/fail with details
 * @param latencyMs        wall-clock execution time in milliseconds
 * @param inputTokens      tokens sent to the model (0 if unavailable)
 * @param outputTokens     tokens received from the model (0 if unavailable)
 * @param error            error message if the task failed with an exception, null otherwise
 */
public record BenchmarkResult(
        String taskId,
        String category,
        String modelName,
        String provider,
        boolean passed,
        List<AssertionOutcome> assertionResults,
        long latencyMs,
        long inputTokens,
        long outputTokens,
        String error
) {
    public record AssertionOutcome(String type, boolean passed, String detail) {}

    public static BenchmarkResult success(String taskId, String category, String modelName,
                                          String provider, List<AssertionOutcome> outcomes,
                                          long latencyMs, long inputTokens, long outputTokens) {
        boolean allPassed = outcomes.stream().allMatch(AssertionOutcome::passed);
        return new BenchmarkResult(taskId, category, modelName, provider,
                allPassed, outcomes, latencyMs, inputTokens, outputTokens, null);
    }

    public static BenchmarkResult failure(String taskId, String category, String modelName,
                                          String provider, long latencyMs, String error) {
        return new BenchmarkResult(taskId, category, modelName, provider,
                false, List.of(), latencyMs, 0, 0, error);
    }

}
