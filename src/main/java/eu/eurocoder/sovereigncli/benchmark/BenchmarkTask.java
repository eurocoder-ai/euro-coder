package eu.eurocoder.sovereigncli.benchmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Definition of a single benchmark task, loaded from JSON.
 *
 * @param id             unique task identifier (e.g. "gen-fibonacci")
 * @param category       task category: code-generation, refactoring, debugging, tool-calling
 * @param description    human-readable description of the task
 * @param type           execution mode: "raw" (prompt-to-response) or "agent" (with tool calling)
 * @param prompt         the prompt sent to the model
 * @param setup          files to create in the sandbox before execution (path -> content)
 * @param assertions     list of assertions to verify the result
 * @param timeoutSeconds max execution time before the task is considered failed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BenchmarkTask(
        String id,
        String category,
        String description,
        String type,
        String prompt,
        Map<String, String> setup,
        List<BenchmarkAssertion> assertions,
        int timeoutSeconds
) {
    public boolean isRaw() {
        return "raw".equalsIgnoreCase(type);
    }

    public boolean isAgent() {
        return "agent".equalsIgnoreCase(type);
    }
}
