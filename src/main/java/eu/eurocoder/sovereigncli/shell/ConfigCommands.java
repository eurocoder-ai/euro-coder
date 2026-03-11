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
import java.util.Map;

@ShellComponent
public class ConfigCommands {

    private static final int API_KEY_MASK_MIN_LENGTH = 8;
    private static final int API_KEY_MASK_VISIBLE_CHARS = 4;

    private static final Map<Provider, String> PROVIDER_DESCRIPTIONS = Map.of(
            Provider.MISTRAL, "European cloud API (requires API key, best quality)",
            Provider.OLLAMA, "100% local / offline (no API key, total sovereignty)",
            Provider.OPENAI, "OpenAI cloud API (GPT-4o, o3-mini, requires API key)",
            Provider.ANTHROPIC, "Anthropic cloud API (Claude Sonnet, requires API key)",
            Provider.GOOGLE_GEMINI, "Google AI cloud API (Gemini 2.0, requires API key)",
            Provider.XAI, "xAI cloud API (Grok 3, requires API key)",
            Provider.DEEPSEEK, "DeepSeek cloud API (V3/R1, requires API key)"
    );

    private final HybridAgentRouter router;
    private final ModelManager modelManager;
    private final ApiKeyManager apiKeyManager;

    public ConfigCommands(HybridAgentRouter router, ModelManager modelManager, ApiKeyManager apiKeyManager) {
        this.router = router;
        this.modelManager = modelManager;
        this.apiKeyManager = apiKeyManager;
    }

    @ShellMethod(key = "provider", value = "Show or switch the AI provider")
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
            String desc = PROVIDER_DESCRIPTIONS.getOrDefault(p, "");
            sb.append(String.format("    %-12s %s%s\n",
                    colorize(p.id(), AttributedStyle.GREEN),
                    desc,
                    colorize(active, AttributedStyle.YELLOW)));
        }

        sb.append("\n");
        sb.append(colorize("  Usage: provider <name>  (e.g. 'provider openai')", AttributedStyle.WHITE));
        return sb.toString();
    }

    private String switchProvider(String name) {
        String trimmedName = name.trim();
        Provider newProvider = Provider.fromId(trimmedName);

        if (!newProvider.id().equalsIgnoreCase(trimmedName)) {
            String validNames = String.join(", ",
                    java.util.Arrays.stream(Provider.values())
                            .map(Provider::id)
                            .toArray(String[]::new));
            return colorize("Unknown provider: '" + name + "'. Use one of: " + validNames, AttributedStyle.RED);
        }

        if (newProvider.requiresApiKey() && !apiKeyManager.hasApiKeyForProvider(newProvider)) {
            return colorize("No " + newProvider.displayName() + " API key configured. " +
                    "Set one first with 'config-key <key>' after switching, or set " +
                    newProvider.envVarName() + " env var.", AttributedStyle.YELLOW)
                    + "\n" + doSwitchProvider(newProvider);
        }

        return doSwitchProvider(newProvider);
    }

    private String doSwitchProvider(Provider newProvider) {
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

    @ShellMethod(key = "model", value = "Show/switch models. Subcommands: list, planner <name>, coder <name>")
    public String model(
            @ShellOption(defaultValue = "") String action,
            @ShellOption(defaultValue = "") String arg) {

        String normalizedAction = action.trim().toLowerCase();

        if (normalizedAction.isEmpty()) {
            return showCurrentModel();
        }
        return switch (normalizedAction) {
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
                    yield switchModel(normalizedAction);
                }
                yield colorize("Unknown subcommand: '" + normalizedAction + "'. Try: model list | model planner <name> | model coder <name> | model <name>", AttributedStyle.RED);
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
        sb.append(colorize("    provider [name]         — Switch provider", AttributedStyle.WHITE));
        return sb.toString();
    }

    private String listModels() {
        StringBuilder sb = new StringBuilder("\n");
        Provider current = modelManager.getProvider();
        String providerLabel = current.displayName();

        if (modelManager.isOllama()) {
            List<ModelOption> installed = modelManager.getInstalledOllamaModels();
            if (!installed.isEmpty()) {
                sb.append(colorize("  Installed Ollama models (live):", AttributedStyle.CYAN)).append("\n\n");
                appendModelList(sb, installed, 30);
            } else {
                sb.append(colorize("  Could not reach Ollama. Is it running? (ollama serve)", AttributedStyle.YELLOW)).append("\n\n");
            }
        } else if (current != Provider.ANTHROPIC) {
            List<ModelOption> remote = modelManager.getAvailableModels();
            if (!remote.isEmpty()) {
                sb.append(colorize("  Available " + providerLabel + " models (live from API):", AttributedStyle.CYAN)).append("\n\n");
                appendModelList(sb, remote, 34);
            }
        }

        List<ModelOption> suggested = modelManager.getSuggestedModels();
        sb.append(colorize("  Recommended for tool calling (" + providerLabel + "):", AttributedStyle.CYAN)).append("\n\n");
        appendModelList(sb, suggested, 30);

        sb.append("\n");
        sb.append(colorize("  You can use ANY model name — the lists above are just suggestions.", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("  Examples:", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model planner <name>     — Set planner independently", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model coder <name>       — Set coder independently", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    model <name>             — Use one model for everything", AttributedStyle.WHITE));
        return sb.toString();
    }

    private void appendModelList(StringBuilder sb, List<ModelOption> models, int nameWidth) {
        for (ModelOption m : models) {
            String active = isActive(m.id());
            sb.append(String.format("    %-" + nameWidth + "s %s%s\n",
                    colorize(m.id(), AttributedStyle.GREEN),
                    m.description(),
                    colorize(active, AttributedStyle.YELLOW)));
        }
        sb.append("\n");
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

    @ShellMethod(key = "beta", value = "Show or toggle beta features (on / off)")
    public String beta(@ShellOption(defaultValue = "") String action) {
        String normalized = action.trim().toLowerCase();

        if (normalized.isEmpty()) {
            return showBetaStatus();
        }

        return switch (normalized) {
            case "on" -> setBeta(true);
            case "off" -> setBeta(false);
            default -> colorize("Usage: beta | beta on | beta off", AttributedStyle.YELLOW);
        };
    }

    private String showBetaStatus() {
        boolean enabled = apiKeyManager.isBetaEnabled();
        StringBuilder sb = new StringBuilder("\n");

        sb.append(colorize("  Beta Features: " + (enabled ? "ENABLED" : "DISABLED"),
                enabled ? AttributedStyle.GREEN : AttributedStyle.WHITE)).append("\n\n");

        if (enabled) {
            sb.append(colorize("  Beta features are live and usable.", AttributedStyle.WHITE)).append("\n");
            sb.append(colorize("  They are under development and not fully secured.", AttributedStyle.YELLOW)).append("\n\n");
        }

        sb.append(colorize("  Features behind beta flag:", AttributedStyle.CYAN)).append("\n");
        sb.append(colorize("    - Streaming Responses  (real-time token streaming, progress indicators)", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    - RAG Semantic Search  (auto-indexes project, injects relevant context)", AttributedStyle.WHITE)).append("\n");
        sb.append("\n");
        sb.append(colorize("  Usage:", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    beta on    — Enable beta features (they become live immediately)", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    beta off   — Disable beta features", AttributedStyle.WHITE));
        return sb.toString();
    }

    private String setBeta(boolean enabled) {
        try {
            apiKeyManager.saveBetaEnabled(enabled);
            if (enabled) {
                return colorize("Beta features ENABLED", AttributedStyle.GREEN)
                        + "\n\n" + colorize("  WARNING: Beta features are under active development.", AttributedStyle.YELLOW)
                        + "\n" + colorize("  They may be incomplete, unstable, or not fully secured.", AttributedStyle.YELLOW)
                        + "\n" + colorize("  By continuing you accept these risks.", AttributedStyle.YELLOW)
                        + "\n\n" + colorize("  All beta features are now live and usable.", AttributedStyle.WHITE);
            }
            return colorize("Beta features DISABLED", AttributedStyle.YELLOW)
                    + "\n" + colorize("  Experimental features are no longer accessible.", AttributedStyle.WHITE);
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED) + e.getMessage();
        }
    }

    @ShellMethod(key = "config-key", value = "Set API key for the current provider")
    public String configKey(@ShellOption(help = "Your API key") String key) {
        Provider current = modelManager.getProvider();
        if (!current.requiresApiKey()) {
            return colorize(current.displayName() + " does not require an API key.", AttributedStyle.YELLOW);
        }

        try {
            apiKeyManager.saveApiKeyForProvider(current, key);
            return colorize("API key for " + current.displayName() + " saved. Use 'ask' to start.", AttributedStyle.GREEN);
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
        Provider current = modelManager.getProvider();
        String mode = modelManager.getCurrentMode();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                
                  Configuration
                  ─────────────────────────────────
                  Provider:   %s
                  Model:      %s
                  Planner:    %s
                  Coder:      %s
                """,
                current.displayName(),
                mode,
                modelManager.getPlannerModelName(),
                modelManager.getCoderModelName()));

        if (current == Provider.OLLAMA) {
            sb.append(String.format("  Ollama URL: %s\n", apiKeyManager.getOllamaBaseUrl()));
        } else if (current.requiresApiKey()) {
            String key = apiKeyManager.getApiKeyForProvider(current);
            String masked = maskApiKey(key);
            sb.append(String.format("  API Key:    %s\n", masked));
            String envVar = current.envVarName();
            sb.append(String.format("  Source:     %s\n",
                    envVar != null && System.getenv(envVar) != null
                            ? "Environment variable (" + envVar + ")"
                            : "~/.eurocoder/config.json"));
        }

        sb.append(String.format("  Beta:       %s\n", apiKeyManager.isBetaEnabled() ? "enabled" : "disabled"));
        sb.append(String.format("  Config:     %s\n", apiKeyManager.getConfigFilePath()));
        return sb.toString();
    }

    @ShellMethod(key = "status", value = "Show the current status of the Sovereign Agent")
    public String status() {
        Provider current = modelManager.getProvider();
        boolean ready = !current.requiresApiKey() || apiKeyManager.hasApiKeyForProvider(current);
        String mode = modelManager.getCurrentMode();
        String betaLabel = apiKeyManager.isBetaEnabled() ? "ON" : "off";

        return String.format("""
                
                  EuroCoder — Sovereign AI Agent
                  ═══════════════════════════════════════
                  Status:    %s
                  Provider:  %s
                  Mode:      %s
                  Planner:   %-24s (reasoning)
                  Coder:     %-24s (code generation)
                  Beta:      %s
                  Runtime:   Spring Shell + LangChain4j
                  ───────────────────────────────────────
                  Commands:
                    ask <prompt>      — Auto-route (smart mode)
                    plan <prompt>     — Force hybrid Planner -> Coder
                    code <desc>       — Direct code generation
                    model [name]      — Show/switch model
                    model list        — List available models
                    provider [name]   — Show/switch provider
                    beta [on|off]     — Toggle beta features
                    rag [action]      — Semantic code search (beta)
                    rules [action]    — Manage agent rules
                    ls [dir]          — List files
                    config-show       — Show config
                    security          — Show security config
                    trust [level]     — Set trust level
                    sandbox [action]  — Configure sandbox
                    audit [action]    — View audit log
                    help              — All commands
                """,
                ready ? "ONLINE" : "NO API KEY",
                current.displayName(),
                mode,
                modelManager.getPlannerModelName(),
                modelManager.getCoderModelName(),
                betaLabel
        );
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() <= API_KEY_MASK_MIN_LENGTH) {
            return "(not set)";
        }
        return key.substring(0, API_KEY_MASK_VISIBLE_CHARS)
                + "****"
                + key.substring(key.length() - API_KEY_MASK_VISIBLE_CHARS);
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
