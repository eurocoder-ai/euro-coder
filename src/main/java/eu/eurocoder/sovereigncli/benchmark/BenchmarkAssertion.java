package eu.eurocoder.sovereigncli.benchmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single assertion to verify against a benchmark task result.
 *
 * @param type  assertion type: response_contains, file_exists, file_contains, file_not_contains, compiles
 * @param value expected string (for contains-type assertions) or language (for compiles)
 * @param path  target file path relative to the task sandbox (for file-based assertions)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BenchmarkAssertion(String type, String value, String path) {}
