package eu.eurocoder.sovereigncli.config;

import eu.eurocoder.sovereigncli.config.RuleManager.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleManagerTest {

    @TempDir
    Path tempDir;

    private RuleManager ruleManager;
    private Path rulesDir;

    @BeforeEach
    void setUp() {
        ruleManager = new RuleManager() {
            @Override
            public Path getProjectDir() {
                return tempDir;
            }
        };
        rulesDir = tempDir.resolve(".eurocoder/rules");
    }

    // ── loadRules ────────────────────────────────────────────────────

    @Test
    void loadRules_emptyWhenNoDirectory() {
        assertThat(ruleManager.loadRules()).isEmpty();
    }

    @Test
    void loadRules_emptyWhenDirectoryExists_butNoFiles() throws IOException {
        Files.createDirectories(rulesDir);
        assertThat(ruleManager.loadRules()).isEmpty();
    }

    @Test
    void loadRules_readsMarkdownFiles() throws IOException {
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("coding-style.md"), "Use streams over loops");
        Files.writeString(rulesDir.resolve("naming.md"), "Use descriptive names");

        List<Rule> rules = ruleManager.loadRules();

        assertThat(rules).hasSize(2);
        assertThat(rules).extracting(Rule::name).containsExactly("coding-style", "naming");
    }

    @Test
    void loadRules_ignoresNonMarkdownFiles() throws IOException {
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("valid.md"), "real rule");
        Files.writeString(rulesDir.resolve("notes.txt"), "not a rule");

        assertThat(ruleManager.loadRules()).hasSize(1);
        assertThat(ruleManager.loadRules().getFirst().name()).isEqualTo("valid");
    }

    @Test
    void loadRules_skipsBlankFiles() throws IOException {
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("empty.md"), "   ");
        Files.writeString(rulesDir.resolve("real.md"), "Always test");

        assertThat(ruleManager.loadRules()).hasSize(1);
    }

    // ── saveRule / getRule ────────────────────────────────────────────

    @Test
    void saveRule_createsDirectoryAndFile() throws IOException {
        ruleManager.saveRule("my-rule", "Follow TDD");

        assertThat(Files.isDirectory(rulesDir)).isTrue();
        assertThat(Files.readString(rulesDir.resolve("my-rule.md"))).isEqualTo("Follow TDD");
    }

    @Test
    void getRule_returnsNullWhenNotFound() {
        assertThat(ruleManager.getRule("nonexistent")).isNull();
    }

    @Test
    void getRule_returnsContent() throws IOException {
        ruleManager.saveRule("style", "Use functional style");

        Rule rule = ruleManager.getRule("style");

        assertThat(rule).isNotNull();
        assertThat(rule.name()).isEqualTo("style");
        assertThat(rule.content()).isEqualTo("Use functional style");
    }

    // ── removeRule ───────────────────────────────────────────────────

    @Test
    void removeRule_deletesFile() throws IOException {
        ruleManager.saveRule("temp", "temporary rule");

        boolean removed = ruleManager.removeRule("temp");

        assertThat(removed).isTrue();
        assertThat(ruleManager.getRule("temp")).isNull();
    }

    @Test
    void removeRule_returnsFalseWhenNotFound() throws IOException {
        assertThat(ruleManager.removeRule("ghost")).isFalse();
    }

    // ── formatRulesForPrompt ─────────────────────────────────────────

    @Test
    void formatRulesForPrompt_emptyWhenNoRules() {
        assertThat(ruleManager.formatRulesForPrompt()).isEmpty();
    }

    @Test
    void formatRulesForPrompt_includesAllRules() throws IOException {
        ruleManager.saveRule("style", "Use streams");
        ruleManager.saveRule("testing", "Always write tests");

        String prompt = ruleManager.formatRulesForPrompt();

        assertThat(prompt).contains("CUSTOM RULES");
        assertThat(prompt).contains("--- style ---");
        assertThat(prompt).contains("Use streams");
        assertThat(prompt).contains("--- testing ---");
        assertThat(prompt).contains("Always write tests");
    }

    // ── Name sanitization ────────────────────────────────────────────

    @Test
    void saveRule_sanitizesName() throws IOException {
        ruleManager.saveRule("My Rule!!", "content");

        assertThat(Files.exists(rulesDir.resolve("my-rule-.md"))).isTrue();
    }

    // ── getRulesDir ──────────────────────────────────────────────────

    @Test
    void getRulesDir_returnsCorrectPath() {
        assertThat(ruleManager.getRulesDir()).isEqualTo(rulesDir);
    }
}
