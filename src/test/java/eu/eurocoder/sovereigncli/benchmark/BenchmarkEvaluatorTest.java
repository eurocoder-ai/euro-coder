package eu.eurocoder.sovereigncli.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkEvaluatorTest {

    @TempDir
    Path sandboxDir;

    private BenchmarkEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BenchmarkEvaluator();
    }

    // ── response_contains ───────────────────────────────────────────

    @Test
    void responseContains_matchFound_passes() {
        BenchmarkAssertion assertion = new BenchmarkAssertion("response_contains", "fibonacci", null);

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "Here is the fibonacci method...", sandboxDir);

        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.getFirst().passed()).isTrue();
    }

    @Test
    void responseContains_caseInsensitive_passes() {
        BenchmarkAssertion assertion = new BenchmarkAssertion("response_contains", "Fibonacci", null);

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "FIBONACCI sequence", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isTrue();
    }

    @Test
    void responseContains_notFound_fails() {
        BenchmarkAssertion assertion = new BenchmarkAssertion("response_contains", "fibonacci", null);

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "Here is a sorting algorithm", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
    }

    @Test
    void responseContains_nullResponse_fails() {
        BenchmarkAssertion assertion = new BenchmarkAssertion("response_contains", "test", null);

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), null, sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
        assertThat(outcomes.getFirst().detail()).contains("null");
    }

    // ── file_exists ─────────────────────────────────────────────────

    @Test
    void fileExists_filePresent_passes() throws IOException {
        Files.writeString(sandboxDir.resolve("Calculator.java"), "class Calculator {}");
        BenchmarkAssertion assertion = new BenchmarkAssertion("file_exists", null, "Calculator.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isTrue();
    }

    @Test
    void fileExists_fileMissing_fails() {
        BenchmarkAssertion assertion = new BenchmarkAssertion("file_exists", null, "Missing.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
    }

    // ── file_contains ───────────────────────────────────────────────

    @Test
    void fileContains_matchFound_passes() throws IOException {
        Files.writeString(sandboxDir.resolve("Test.java"), "public class Test { int value = 42; }");
        BenchmarkAssertion assertion = new BenchmarkAssertion("file_contains", "value = 42", "Test.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isTrue();
    }

    @Test
    void fileContains_notFound_fails() throws IOException {
        Files.writeString(sandboxDir.resolve("Test.java"), "public class Test {}");
        BenchmarkAssertion assertion = new BenchmarkAssertion("file_contains", "missing_string", "Test.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
    }

    @Test
    void fileContains_fileMissing_fails() {
        BenchmarkAssertion assertion = new BenchmarkAssertion("file_contains", "anything", "NoFile.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
        assertThat(outcomes.getFirst().detail()).contains("not found");
    }

    // ── file_not_contains ───────────────────────────────────────────

    @Test
    void fileNotContains_stringAbsent_passes() throws IOException {
        Files.writeString(sandboxDir.resolve("Fixed.java"), "if (name != null) return name.length();");
        BenchmarkAssertion assertion = new BenchmarkAssertion(
                "file_not_contains", "name.length()", "Fixed.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        // The string IS present, so this should fail
        assertThat(outcomes.getFirst().passed()).isFalse();
    }

    @Test
    void fileNotContains_stringTrulyAbsent_passes() throws IOException {
        Files.writeString(sandboxDir.resolve("Clean.java"), "public class Clean {}");
        BenchmarkAssertion assertion = new BenchmarkAssertion(
                "file_not_contains", "System.exit", "Clean.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isTrue();
    }

    @Test
    void fileNotContains_fileMissing_fails() {
        BenchmarkAssertion assertion = new BenchmarkAssertion(
                "file_not_contains", "anything", "NoFile.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
    }

    // ── compiles ────────────────────────────────────────────────────

    @Test
    void compiles_validJava_passes() throws IOException {
        Files.writeString(sandboxDir.resolve("Hello.java"),
                "public class Hello { public static void main(String[] args) {} }");
        BenchmarkAssertion assertion = new BenchmarkAssertion("compiles", null, "Hello.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isTrue();
    }

    @Test
    void compiles_invalidJava_fails() throws IOException {
        Files.writeString(sandboxDir.resolve("Bad.java"), "public class Bad { broken syntax");
        BenchmarkAssertion assertion = new BenchmarkAssertion("compiles", null, "Bad.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
        assertThat(outcomes.getFirst().detail()).containsIgnoringCase("compil");
    }

    @Test
    void compiles_fileMissing_fails() {
        BenchmarkAssertion assertion = new BenchmarkAssertion("compiles", null, "NoFile.java");

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
    }

    // ── Unknown assertion type ──────────────────────────────────────

    @Test
    void unknownAssertionType_fails() {
        BenchmarkAssertion assertion = new BenchmarkAssertion("unknown_type", "test", null);

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(List.of(assertion), "response", sandboxDir);

        assertThat(outcomes.getFirst().passed()).isFalse();
        assertThat(outcomes.getFirst().detail()).contains("Unknown");
    }

    // ── Multiple assertions ─────────────────────────────────────────

    @Test
    void multipleAssertions_evaluatesAll() throws IOException {
        Files.writeString(sandboxDir.resolve("Code.java"),
                "public class Code { public int fibonacci(int n) { return n; } }");

        List<BenchmarkAssertion> assertions = List.of(
                new BenchmarkAssertion("response_contains", "fibonacci", null),
                new BenchmarkAssertion("file_exists", null, "Code.java"),
                new BenchmarkAssertion("file_contains", "fibonacci", "Code.java")
        );

        List<BenchmarkResult.AssertionOutcome> outcomes =
                evaluator.evaluate(assertions, "Here is the fibonacci method", sandboxDir);

        assertThat(outcomes).hasSize(3);
        assertThat(outcomes).allMatch(BenchmarkResult.AssertionOutcome::passed);
    }
}
