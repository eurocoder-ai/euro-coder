package eu.eurocoder.sovereigncli.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Evaluates benchmark assertions against the model response and the task sandbox filesystem.
 */
@Component
public class BenchmarkEvaluator {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkEvaluator.class);
    private static final int COMPILE_TIMEOUT_SECONDS = 15;
    private static final int MAX_COMPILE_ERROR_LENGTH = 200;

    public List<BenchmarkResult.AssertionOutcome> evaluate(
            List<BenchmarkAssertion> assertions, String modelResponse, Path sandboxDir) {

        List<BenchmarkResult.AssertionOutcome> outcomes = new ArrayList<>();
        for (BenchmarkAssertion assertion : assertions) {
            outcomes.add(evaluateOne(assertion, modelResponse, sandboxDir));
        }
        return outcomes;
    }

    private BenchmarkResult.AssertionOutcome evaluateOne(
            BenchmarkAssertion assertion, String response, Path sandboxDir) {

        return switch (assertion.type()) {
            case "response_contains" -> checkResponseContains(response, assertion.value());
            case "file_exists" -> checkFileExists(sandboxDir, assertion.path());
            case "file_contains" -> checkFileContains(sandboxDir, assertion.path(), assertion.value());
            case "file_not_contains" -> checkFileNotContains(sandboxDir, assertion.path(), assertion.value());
            case "compiles" -> checkCompiles(sandboxDir, assertion.path());
            default -> new BenchmarkResult.AssertionOutcome(
                    assertion.type(), false, "Unknown assertion type: " + assertion.type());
        };
    }

    private BenchmarkResult.AssertionOutcome checkResponseContains(String response, String expected) {
        if (response == null) {
            return new BenchmarkResult.AssertionOutcome("response_contains", false, "Response was null");
        }
        boolean found = response.toLowerCase().contains(expected.toLowerCase());
        String detail = found
                ? "Found '" + expected + "' in response"
                : "'" + expected + "' not found in response";
        return new BenchmarkResult.AssertionOutcome("response_contains", found, detail);
    }

    private BenchmarkResult.AssertionOutcome checkFileExists(Path sandboxDir, String path) {
        Path target = sandboxDir.resolve(path);
        boolean exists = Files.exists(target);
        String detail = exists
                ? "File exists: " + path
                : "File not found: " + path;
        return new BenchmarkResult.AssertionOutcome("file_exists", exists, detail);
    }

    private BenchmarkResult.AssertionOutcome checkFileContains(Path sandboxDir, String path, String expected) {
        Path target = sandboxDir.resolve(path);
        if (!Files.exists(target)) {
            return new BenchmarkResult.AssertionOutcome(
                    "file_contains", false, "File not found: " + path);
        }
        try {
            String content = Files.readString(target);
            boolean found = content.contains(expected);
            String detail = found
                    ? "File '" + path + "' contains '" + expected + "'"
                    : "'" + expected + "' not found in " + path;
            return new BenchmarkResult.AssertionOutcome("file_contains", found, detail);
        } catch (Exception e) {
            return new BenchmarkResult.AssertionOutcome(
                    "file_contains", false, "Error reading " + path + ": " + e.getMessage());
        }
    }

    private BenchmarkResult.AssertionOutcome checkFileNotContains(Path sandboxDir, String path, String expected) {
        Path target = sandboxDir.resolve(path);
        if (!Files.exists(target)) {
            return new BenchmarkResult.AssertionOutcome(
                    "file_not_contains", false, "File not found: " + path);
        }
        try {
            String content = Files.readString(target);
            boolean absent = !content.contains(expected);
            String detail = absent
                    ? "File '" + path + "' does not contain '" + expected + "'"
                    : "'" + expected + "' still present in " + path;
            return new BenchmarkResult.AssertionOutcome("file_not_contains", absent, detail);
        } catch (Exception e) {
            return new BenchmarkResult.AssertionOutcome(
                    "file_not_contains", false, "Error reading " + path + ": " + e.getMessage());
        }
    }

    private BenchmarkResult.AssertionOutcome checkCompiles(Path sandboxDir, String path) {
        Path target = sandboxDir.resolve(path);
        if (!Files.exists(target)) {
            return new BenchmarkResult.AssertionOutcome(
                    "compiles", false, "File not found: " + path);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("javac", target.toString());
            pb.directory(sandboxDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new BenchmarkResult.AssertionOutcome("compiles", false, "Compilation timed out");
            }

            boolean success = process.exitValue() == 0;
            String detail = success
                    ? path + " compiles successfully"
                    : "Compilation failed: " + truncate(output, MAX_COMPILE_ERROR_LENGTH);
            return new BenchmarkResult.AssertionOutcome("compiles", success, detail);
        } catch (Exception e) {
            return new BenchmarkResult.AssertionOutcome(
                    "compiles", false, "Compilation error: " + e.getMessage());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
