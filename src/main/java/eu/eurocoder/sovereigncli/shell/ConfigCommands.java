package eu.eurocoder.sovereigncli.shell;

import eu.eurocoder.sovereigncli.agent.HybridAgentRouter;
import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.agent.ModelOption;
import eu.eurocoder.sovereigncli.agent.Provider;
import eu.eurocoder.sovereigncli.config.ApiKeyManager;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

/**
 * Shell commands for configuration: provider, model, config-key, config-clear,
 * config-show, and status.
 */
@ShellComponent
public class ConfigCommands {

    private final HybridAgentRouter router;
    private final ModelManager modelManager;
    private final ApiKeyManager apiKeyManager;

    public ConfigCommands(HybridAgentRouter router, ModelManager modelManager, ApiKeyManager apiKeyManager) {
        this.router = router;
        this.modelManager = modelManager;
        this.apiKeyManager = apiKeyManager;
    }

    // ── Provider command ─────────────────────────────────────────────

    @ShellMethod(key = "provider", value = "Show or switch the AI provider (mistral / ollama)")
    public String provider(@ShellOption(defaultValue = "") String name) {
        if (name.isBlank()) {
            return showProvider();
        }
        return switchProvider(name);
    }

    private String showProvider() {
        Provider current = modelManager.getProvider();
        StringBuilder sb = new StringBuilder("\n");

        sb.append(colorize("  Current provider: " + current.displayName(), AttributedStyle.CYAN)).append("\n\n");
        sb.append(colorize("  Available providers:", AttributedStyle.WHITE)).append("\n");

        for (Provider p : Provider.values()) {
            String active = (p == current) ? " <-- active" : "";
            String desc = switch (p) {
                case MISTRAL -> "European cloud API (requires API key, best quality)";
                case OLLAMA -> "100% local / offline (no API key, total sovereignty)";
            };
            sb.append(String.format("    %-12s %s%s\n",
                    colorize(p.id(), AttributedStyle.GREEN),
                    desc,
                    colorize(active, AttributedStyle.YELLOW)));
        }

        sb.append("\n");
        sb.append(colorize("  Usage: provider <name>  (e.g. 'provider ollama')", AttributedStyle.WHITE));
        return sb.toString();
    }

    private String switchProvider(String name) {
        String trimmedName = name.trim();
        Provider newProvider = Provider.fromId(trimmedName);

        // fromId() defaults to MISTRAL for unknown values — detect that case
        if (!newProvider.id().equalsIgnoreCase(trimmedName)) {
            return colorize("Unknown provider: '" + name + "'. Use 'mistral' or 'ollama'.", AttributedStyle.RED);
        }

        // If switching to Mistral, check for API key
        if (newProvider == Provider.MISTRAL && !apiKeyManager.hasApiKey()) {
            return colorize("No Mistral API key configured. Set one first with 'config-key <key>'.", AttributedStyle.RED);
        }

        modelManager.switchProvider(newProvider);
        router.reinitialize();

        StringBuilder sb = new StringBuilder();
        sb.append(colorize("Switched to: " + newProvider.displayName(), AttributedStyle.GREEN));
        sb.append("\n  Mode reset to: auto");
        sb.append("\n  Planner: ").append(modelManager.getPlannerModelName());
        sb.append("\n  Coder:   ").append(modelManager.getCoderModelName());

        if (newProvider == Provider.OLLAMA) {
            sb.append("\n\n");
            sb.append(colorize("  Make sure Ollama is running and models are pulled:", AttributedStyle.YELLOW));
            sb.append("\n  ollama pull ").append(modelManager.getPlannerModelName());
            sb.append("\n  ollama pull ").append(modelManager.getCoderModelName());
        }

        return sb.toString();
    }

    // ── Model command ────────────────────────────────────────────────

    @ShellMethod(key = "model", value = "Show/switch models. Subcommands: list, planner <name>, coder <name>")
    public String model(
            @ShellOption(defaultValue = "") String action,
            @ShellOption(defaultValue = "") String arg) {

        String a = action.trim().toLowerCase();

        if (a.isEmpty()) {
            return showCurrentModel();
        }
        return switch (a) {
            case "list" -> listModels();
            case "planner" -> arg.isBlank()
                    ? colorize("Usage: model planner <model-name>", AttributedStyle.YELLOW)
                    : setPlanner(arg.trim());
            case "coder" -> arg.isBlank()
                    ? colorize("Usage: model coder <model-name>", AttributedStyle.YELLOW)
                    : setCoder(arg.trim());
            case "auto" -> switchModel("auto");
            default -> {
                if (arg.isBlank()) {
                    yield switchModel(a);
                }
                yield colorize("Unknown subcommand: '" + a + "'. Try: model list | model planner <name> | model coder <name> | model <name>", AttributedStyle.RED);
            }
        };
    }

    private String showCurrentModel() {
        StringBuilder sb = new StringBuilder("\n");

        sb.append(colorize("  Provider: " + modelManager.getProvider().displayName(), AttributedStyle.WHITE)).append("\n");

        if (modelManager.isAutoMode()) {
            sb.append(colorize("  Mode: auto", AttributedStyle.CYAN));
            if (modelManager.hasCustomModels()) {
                sb.append(colorize("  (custom pairing)", AttributedStyle.YELLOW));
            }
            sb.append("\n");
            sb.append(colorize("    Planner: " + modelManager.getPlannerModelName() + "  (reasoning & planning)", AttributedStyle.WHITE)).append("\n");
            sb.append(colorize("    Coder:   " + modelManager.getCoderModelName() + "  (code generation)", AttributedStyle.WHITE)).append("\n");
        } else {
            sb.append(colorize("  Model: " + modelManager.getCurrentMode(), AttributedStyle.CYAN)).append("\n");
            sb.append(colorize("    Used for all tasks (planning + coding)", AttributedStyle.WHITE)).append("\n");
        }

        sb.append("\n");
        sb.append(colorize("  Commands:", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model list              — List all available models", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model <name>            — Use one model for everything", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model auto              — Reset to default auto pairing", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model planner <name>    — Set a custom planner model", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model coder <name>      — Set a custom coder model", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    provider [name]         — Switch Mistral / Ollama", AttributedStyle.WHITE));
        return sb.toString();
    }

    private String listModels() {
        StringBuilder sb = new StringBuilder("\n");
        String providerLabel = modelManager.getProvider().displayName();

        if (modelManager.isOllama()) {
            List<ModelOption> installed = modelManager.getInstalledOllamaModels();
            if (!installed.isEmpty()) {
                sb.append(colorize("  Installed Ollama models (live):", AttributedStyle.CYAN)).append("\n\n");
                for (ModelOption m : installed) {
                    String active = isActive(m.id());
                    sb.append(String.format("    %-30s %s%s\n",
                            colorize(m.id(), AttributedStyle.GREEN),
                            m.description(),
                            colorize(active, AttributedStyle.YELLOW)));
                }
                sb.append("\n");
            } else {
                sb.append(colorize("  Could not reach Ollama. Is it running? (ollama serve)", AttributedStyle.YELLOW)).append("\n\n");
            }
        } else {
            List<ModelOption> remote = modelManager.getRemoteMistralModels();
            if (!remote.isEmpty()) {
                sb.append(colorize("  Available Mistral models (live from API):", AttributedStyle.CYAN)).append("\n\n");
                for (ModelOption m : remote) {
                    String active = isActive(m.id());
                    sb.append(String.format("    %-34s %s%s\n",
                            colorize(m.id(), AttributedStyle.GREEN),
                            m.description(),
                            colorize(active, AttributedStyle.YELLOW)));
                }
                sb.append("\n");
            }
        }

        List<ModelOption> suggested = modelManager.getSuggestedModels();
        sb.append(colorize("  Recommended for tool calling (" + providerLabel + "):", AttributedStyle.CYAN)).append("\n\n");
        for (ModelOption m : suggested) {
            String active = isActive(m.id());
            sb.append(String.format("    %-30s %s%s\n",
                    colorize(m.id(), AttributedStyle.GREEN),
                    m.description(),
                    colorize(active, AttributedStyle.YELLOW)));
        }

        sb.append("\n");
        sb.append(colorize("  You can use ANY model name — the lists above are just suggestions.", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("  Examples:", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model planner llama3.1       — Set planner independently", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model coder codestral        — Set coder independently", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model mistral-small-latest   — Use one model for everything", AttributedStyle.WHITE));
        return sb.toString();
    }

    private String isActive(String modelId) {
        boolean isPlanner = modelId.equals(modelManager.getPlannerModelName());
        boolean isCoder = modelId.equals(modelManager.getCoderModelName());
        if (isPlanner && isCoder) return " <-- planner + coder";
        if (isPlanner) return " <-- planner";
        if (isCoder) return " <-- coder";
        return "";
    }

    private String setPlanner(String name) {
        modelManager.setCustomPlannerModel(name);
        router.reinitialize();

        StringBuilder sb = new StringBuilder();
        sb.append(colorize("Planner set to: " + name, AttributedStyle.GREEN));
        sb.append("\n  Coder remains: ").append(modelManager.getCoderModelName());
        if (modelManager.isOllama()) {
            sb.append("\n  Make sure it's pulled: ollama pull ").append(name);
        }
        return sb.toString();
    }

    private String setCoder(String name) {
        modelManager.setCustomCoderModel(name);
        router.reinitialize();

        StringBuilder sb = new StringBuilder();
        sb.append(colorize("Coder set to: " + name, AttributedStyle.GREEN));
        sb.append("\n  Planner remains: ").append(modelManager.getPlannerModelName());
        if (modelManager.isOllama()) {
            sb.append("\n  Make sure it's pulled: ollama pull ").append(name);
        }
        return sb.toString();
    }

    private String switchModel(String name) {
        String normalizedName = name.trim();

        modelManager.switchMode(normalizedName);
        router.reinitialize();

        if ("auto".equalsIgnoreCase(normalizedName)) {
            StringBuilder sb = new StringBuilder();
            sb.append(colorize("Reset to auto mode (default pairing)", AttributedStyle.GREEN));
            sb.append("\n  Planner: ").append(modelManager.getPlannerModelName());
            sb.append("\n  Coder:   ").append(modelManager.getCoderModelName());
            if (modelManager.isOllama()) {
                sb.append("\n\n  Make sure these models are pulled in Ollama.");
            }
            return sb.toString();
        }

        String result = colorize("Switched to: " + normalizedName + " (used for all tasks)", AttributedStyle.GREEN);
        if (modelManager.isOllama()) {
            result += "\n  Make sure it's pulled: ollama pull " + normalizedName;
        }
        return result;
    }

    // ── Config commands ──────────────────────────────────────────────

    @ShellMethod(key = "config-key", value = "Update or replace your Mistral API key")
    public String configKey(@ShellOption(help = "Your Mistral API key") String key) {
        try {
            apiKeyManager.saveApiKey(key);
            return colorize("API key updated. Use 'ask' to start using the agent.", AttributedStyle.GREEN);
        } catch (Exception e) {
            return colorize("Error saving key: ", AttributedStyle.RED) + e.getMessage();
        }
    }

    @ShellMethod(key = "config-clear", value = "Remove your stored API key")
    public String configClear() {
        try {
            apiKeyManager.clearApiKey();
            return colorize("API key removed from " + apiKeyManager.getConfigFilePath(), AttributedStyle.GREEN);
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED) + e.getMessage();
        }
    }

    @ShellMethod(key = "config-show", value = "Show configuration info (key is masked)")
    public String configShow() {
        String key = apiKeyManager.getApiKey();
        String masked = (key != null && key.length() > 8)
                ? key.substring(0, 4) + "****" + key.substring(key.length() - 4)
                : "(not set)";

        String mode = modelManager.getCurrentMode();
        String providerName = modelManager.getProvider().displayName();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                
                  Configuration
                  ─────────────────────────────────
                  Provider:   %s
                  Model:      %s
                  Planner:    %s
                  Coder:      %s
                """,
                providerName,
                mode,
                modelManager.getPlannerModelName(),
                modelManager.getCoderModelName()));

        if (modelManager.isMistral()) {
            sb.append(String.format("  API Key:    %s\n", masked));
            sb.append(String.format("  Source:     %s\n",
                    System.getenv("MISTRAL_API_KEY") != null ? "Environment variable" : "~/.eurocoder/config.json"));
        } else {
            sb.append(String.format("  Ollama URL: %s\n", apiKeyManager.getOllamaBaseUrl()));
        }

        sb.append(String.format("  Config:     %s\n", apiKeyManager.getConfigFilePath()));
        return sb.toString();
    }

    @ShellMethod(key = "status", value = "Show the current status of the Sovereign Agent")
    public String status() {
        boolean ready = !modelManager.isMistral() || apiKeyManager.hasApiKey();
        String mode = modelManager.getCurrentMode();
        String providerName = modelManager.getProvider().displayName();

        return String.format("""
                
                  EuroCoder — Sovereign AI Agent
                  ═══════════════════════════════════════
                  Status:    %s
                  Provider:  %s
                  Mode:      %s
                  Planner:   %-24s (reasoning)
                  Coder:     %-24s (code generation)
                  Runtime:   Spring Shell + LangChain4j
                  ───────────────────────────────────────
                  Commands:
                    ask <prompt>      — Auto-route (smart mode)
                    plan <prompt>     — Force hybrid Planner -> Coder
                    code <desc>       — Direct code generation
                    model [name]      — Show/switch model
                    model list        — List available models
                    provider [name]   — Show/switch provider
                    ls [dir]          — List files
                    config-show       — Show config
                    security          — Show security config
                    trust [level]     — Set trust level
                    sandbox [action]  — Configure sandbox
                    audit [action]    — View audit log
                    help              — All commands
                """,
                ready ? "ONLINE" : "NO API KEY",
                providerName,
                mode,
                modelManager.getPlannerModelName(),
                modelManager.getCoderModelName()
        );
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
