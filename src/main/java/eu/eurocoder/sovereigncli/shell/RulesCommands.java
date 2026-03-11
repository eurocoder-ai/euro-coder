package eu.eurocoder.sovereigncli.shell;

import eu.eurocoder.sovereigncli.config.RuleManager;
import eu.eurocoder.sovereigncli.config.RuleManager.Rule;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

@ShellComponent
public class RulesCommands {

    private static final int PREVIEW_MAX_LENGTH = 80;

    private final RuleManager ruleManager;

    public RulesCommands(RuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    @ShellMethod(key = "rules", value = "Manage agent rules (list, show, add, remove, path)")
    public String rules(
            @ShellOption(defaultValue = "list") String action,
            @ShellOption(defaultValue = "") String name) {

        String normalized = action.trim().toLowerCase();

        return switch (normalized) {
            case "list", "" -> listRules();
            case "show" -> showRule(name);
            case "add" -> addRule(name);
            case "remove", "rm" -> removeRule(name);
            case "path" -> colorize("  Rules directory: " + ruleManager.getRulesDir(), AttributedStyle.WHITE);
            default -> {
                if (name.isBlank()) {
                    yield showRule(normalized);
                }
                yield colorize("Unknown action: '" + action + "'. Use: rules [list|show|add|remove|path]",
                        AttributedStyle.RED);
            }
        };
    }

    private String listRules() {
        List<Rule> rules = ruleManager.loadRules();
        StringBuilder sb = new StringBuilder("\n");

        if (rules.isEmpty()) {
            sb.append(colorize("  No rules defined.", AttributedStyle.YELLOW)).append("\n\n");
            sb.append(colorize("  Rules are persistent instructions for the AI agent — like Cursor rules.", AttributedStyle.WHITE)).append("\n");
            sb.append(colorize("  They are injected into every agent prompt automatically.", AttributedStyle.WHITE)).append("\n\n");
            sb.append(colorize("  Create rules:", AttributedStyle.CYAN)).append("\n");
            sb.append(colorize("    rules add <name>         — Creates a template rule file to edit", AttributedStyle.WHITE)).append("\n");
            sb.append(colorize("    Or create .md files in:  " + ruleManager.getRulesDir(), AttributedStyle.WHITE)).append("\n");
            return sb.toString();
        }

        sb.append(colorize("  Active Rules (" + rules.size() + ")", AttributedStyle.CYAN)).append("\n");
        sb.append(colorize("  ─────────────────────────────────", AttributedStyle.WHITE)).append("\n");

        rules.forEach(rule -> {
            sb.append(colorize("  " + rule.name(), AttributedStyle.GREEN)).append("\n");
            String preview = rule.content().strip().lines().findFirst().orElse("");
            if (preview.length() > PREVIEW_MAX_LENGTH) {
                preview = preview.substring(0, PREVIEW_MAX_LENGTH - 3) + "...";
            }
            sb.append(colorize("    " + preview, AttributedStyle.WHITE)).append("\n");
        });

        sb.append("\n");
        sb.append(colorize("  Commands: rules show <name> | rules add <name> | rules remove <name>", AttributedStyle.WHITE));
        return sb.toString();
    }

    private String showRule(String name) {
        if (name.isBlank()) {
            return colorize("Usage: rules show <name>", AttributedStyle.YELLOW);
        }

        Rule rule = ruleManager.getRule(name.trim());
        if (rule == null) {
            return colorize("Rule '" + name + "' not found.", AttributedStyle.RED);
        }

        return "\n" + colorize("  Rule: " + rule.name(), AttributedStyle.CYAN)
                + "\n" + colorize("  ─────────────────────────────────", AttributedStyle.WHITE)
                + "\n" + rule.content().strip() + "\n";
    }

    private String addRule(String name) {
        if (name.isBlank()) {
            return colorize("Usage: rules add <name>", AttributedStyle.YELLOW);
        }

        String sanitized = name.trim();
        Rule existing = ruleManager.getRule(sanitized);
        if (existing != null) {
            return colorize("Rule '" + sanitized + "' already exists. Edit it at:",
                    AttributedStyle.YELLOW)
                    + "\n  " + ruleManager.getRulesDir().resolve(sanitized + ".md");
        }

        try {
            String template = """
                    # %s
                    
                    Describe your rule here. This content is injected into every agent prompt.
                    
                    Examples:
                    - Always use functional programming with streams instead of loops
                    - Follow Spring Boot conventions
                    - Write tests for every new feature
                    - Use Java records for data transfer objects
                    """.formatted(sanitized);

            ruleManager.saveRule(sanitized, template);

            return colorize("Rule '" + sanitized + "' created.", AttributedStyle.GREEN)
                    + "\n" + colorize("  Edit it at: " + ruleManager.getRulesDir().resolve(sanitized + ".md"),
                    AttributedStyle.WHITE)
                    + "\n" + colorize("  The rule is active immediately — the agent reads it on every request.",
                    AttributedStyle.WHITE);
        } catch (Exception e) {
            return colorize("Error creating rule: ", AttributedStyle.RED) + e.getMessage();
        }
    }

    private String removeRule(String name) {
        if (name.isBlank()) {
            return colorize("Usage: rules remove <name>", AttributedStyle.YELLOW);
        }

        try {
            boolean removed = ruleManager.removeRule(name.trim());
            if (removed) {
                return colorize("Rule '" + name.trim() + "' removed.", AttributedStyle.GREEN);
            }
            return colorize("Rule '" + name.trim() + "' not found.", AttributedStyle.RED);
        } catch (Exception e) {
            return colorize("Error removing rule: ", AttributedStyle.RED) + e.getMessage();
        }
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
