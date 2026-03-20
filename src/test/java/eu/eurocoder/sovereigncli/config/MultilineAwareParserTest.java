package eu.eurocoder.sovereigncli.config;

import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MultilineAwareParserTest {

    private MultilineAwareParser parser;

    @BeforeEach
    void setUp() {
        parser = new MultilineAwareParser();
    }

    // ── Single-line input (no newlines) delegates to default parser ──

    @Test
    void singleLine_ask_delegatesToDefault() {
        ParsedLine result = parser.parse("ask hello world", 15, Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.words()).contains("ask");
        assertThat(result.words().stream().reduce("", (a, b) -> a + " " + b).trim())
                .contains("hello");
    }

    @Test
    void singleLine_nonAgentCommand_delegatesToDefault() {
        ParsedLine result = parser.parse("status", 6, Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.words()).containsExactly("status");
    }

    // ── Multiline input (newlines) for agent commands ────────────────

    @ParameterizedTest
    @ValueSource(strings = {"ask", "plan", "code"})
    void multiline_agentCommand_preservesNewlines(String command) {
        String input = command + " line one\nline two\nline three";
        ParsedLine result = parser.parse(input, input.length(), Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.words()).hasSize(2);
        assertThat(result.words().get(0)).isEqualTo(command);
        assertThat(result.words().get(1)).isEqualTo("line one\nline two\nline three");
    }

    @Test
    void multiline_ask_newlineDirectlyAfterCommand() {
        String input = "ask\nline one\nline two";
        ParsedLine result = parser.parse(input, input.length(), Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.words()).hasSize(2);
        assertThat(result.words().get(0)).isEqualTo("ask");
        assertThat(result.words().get(1)).isEqualTo("line one\nline two");
    }

    @Test
    void multiline_preservesInternalNewlines() {
        String input = "ask Process this:\n- item 1\n- item 2\n- item 3";
        ParsedLine result = parser.parse(input, input.length(), Parser.ParseContext.ACCEPT_LINE);

        String prompt = result.words().get(1);
        assertThat(prompt).contains("\n- item 1");
        assertThat(prompt).contains("\n- item 2");
        assertThat(prompt).contains("\n- item 3");
    }

    @Test
    void multiline_originalLinePreserved() {
        String input = "ask hello\nworld";
        ParsedLine result = parser.parse(input, input.length(), Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.line()).isEqualTo(input);
    }

    // ── Multiline input for non-agent commands delegates to default ──

    @Test
    void multiline_nonAgentCommand_delegatesToDefault() {
        String input = "status\nfoo";
        ParsedLine result = parser.parse(input, input.length(), Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.words()).contains("status", "foo");
    }

    // ── Non-ACCEPT_LINE contexts always delegate ─────────────────────

    @Test
    void completeContext_delegatesToDefault() {
        String input = "ask hello\nworld";
        ParsedLine result = parser.parse(input, 3, Parser.ParseContext.COMPLETE);

        assertThat(result).isNotNull();
    }

    // ── Edge cases ───────────────────────────────────────────────────

    @Test
    void multiline_commandOnly_noArgument() {
        String input = "ask\n";
        ParsedLine result = parser.parse(input, input.length(), Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.words()).hasSize(1);
        assertThat(result.words().get(0)).isEqualTo("ask");
    }

    @Test
    void multiline_trailingNewline_trimmed() {
        String input = "ask hello world\n";
        ParsedLine result = parser.parse(input, input.length(), Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.words()).hasSize(2);
        assertThat(result.words().get(0)).isEqualTo("ask");
    }

    @Test
    void multiline_leadingWhitespace_handled() {
        String input = "  ask line one\nline two";
        ParsedLine result = parser.parse(input, input.length(), Parser.ParseContext.ACCEPT_LINE);

        assertThat(result.words().get(0)).isEqualTo("ask");
        assertThat(result.words().get(1)).isEqualTo("line one\nline two");
    }
}
