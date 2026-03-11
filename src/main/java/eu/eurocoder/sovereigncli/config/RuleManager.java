package eu.eurocoder.sovereigncli.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manages project-level rules stored in {@code .eurocoder/rules/} within the project directory.
 * Rules are plain {@code .md} files whose contents are injected into every agent prompt,
 * giving the AI persistent instructions — similar to Cursor rules.
 * <p>
 * Override {@link #getProjectDir()} in tests to redirect to a temp directory.
 */
@Component
public class RuleManager {

    private static final Logger log = LoggerFactory.getLogger(RuleManager.class);
    private static final String RULES_DIR = ".eurocoder/rules";
    private static final String RULE_EXTENSION = ".md";

    public Path getProjectDir() {
        return Paths.get(System.getProperty("user.dir"));
    }

    public Path getRulesDir() {
        return getProjectDir().resolve(RULES_DIR);
    }

    public record Rule(String name, String content) {}

    public List<Rule> loadRules() {
        Path rulesDir = getRulesDir();
        if (!Files.isDirectory(rulesDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> files = Files.list(rulesDir)) {
            return files
                    .filter(p -> p.toString().endsWith(RULE_EXTENSION))
                    .sorted()
                    .map(this::readRule)
                    .filter(r -> r != null && !r.content().isBlank())
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to load rules from {}: {}", rulesDir, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Rule getRule(String name) {
        Path file = resolveRuleFile(name);
        if (!Files.exists(file)) {
            return null;
        }
        return readRule(file);
    }

    public void saveRule(String name, String content) throws IOException {
        Path rulesDir = getRulesDir();
        Files.createDirectories(rulesDir);
        Path file = rulesDir.resolve(sanitizeName(name) + RULE_EXTENSION);
        Files.writeString(file, content);
        log.info("Rule '{}' saved to {}", name, file);
    }

    public boolean removeRule(String name) throws IOException {
        Path file = resolveRuleFile(name);
        if (Files.exists(file)) {
            Files.delete(file);
            log.info("Rule '{}' removed: {}", name, file);
            return true;
        }
        return false;
    }

    /**
     * Formats all loaded rules into a single block suitable for injection into agent prompts.
     * Returns empty string if no rules are defined.
     */
    public String formatRulesForPrompt() {
        List<Rule> rules = loadRules();
        if (rules.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("CUSTOM RULES (always follow these instructions):\n");
        rules.forEach(rule -> sb.append("\n--- ")
                .append(rule.name())
                .append(" ---\n")
                .append(rule.content().strip())
                .append("\n"));
        return sb.toString();
    }

    private Rule readRule(Path file) {
        try {
            String name = file.getFileName().toString()
                    .replace(RULE_EXTENSION, "");
            String content = Files.readString(file);
            return new Rule(name, content);
        } catch (IOException e) {
            log.warn("Failed to read rule {}: {}", file, e.getMessage());
            return null;
        }
    }

    private Path resolveRuleFile(String name) {
        return getRulesDir().resolve(sanitizeName(name) + RULE_EXTENSION);
    }

    private String sanitizeName(String name) {
        return name.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-");
    }
}
