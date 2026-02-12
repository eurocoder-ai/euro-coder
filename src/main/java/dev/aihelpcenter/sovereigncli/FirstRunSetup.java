package dev.aihelpcenter.sovereigncli;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.Console;
import java.util.List;

@Component
@Order(1)
public class FirstRunSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstRunSetup.class);

    private final ApiKeyManager apiKeyManager;
    private final ModelManager modelManager;

    @Value("${eurocoder.first-run.enabled:true}")
    private boolean enabled;

    public FirstRunSetup(ApiKeyManager apiKeyManager, ModelManager modelManager) {
        this.apiKeyManager = apiKeyManager;
        this.modelManager = modelManager;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.debug("First-run setup disabled via configuration");
            return;
        }

        // If already configured, just show welcome back
        if (isAlreadyConfigured()) {
            printWelcomeBack();
            return;
        }

        Console console = System.console();
        if (console == null) {
            log.info("No interactive console — skipping first-run setup");
            System.out.println();
            System.out.println("  No provider configured.");
            System.out.println("  Run interactively to complete setup, or set MISTRAL_API_KEY env var.");
            System.out.println();
            return;
        }

        System.out.println();
        printColored("╔══════════════════════════════════════════════════════════════╗", AttributedStyle.CYAN);
        printColored("║           Welcome to EuroCoder (Sovereign Edition)           ║", AttributedStyle.CYAN);
        printColored("╚══════════════════════════════════════════════════════════════╝", AttributedStyle.CYAN);
        System.out.println();
        printColored("  This agent runs 100% on sovereign infrastructure.", AttributedStyle.WHITE);
        printColored("  Your data stays on YOUR machine.", AttributedStyle.WHITE);
        System.out.println();

        // ── Step 1: Choose provider ──────────────────────────────────

        printColored("  STEP 1 — Choose your AI provider", AttributedStyle.YELLOW);
        System.out.println();
        printColored("    [1] Mistral Cloud API (recommended)", AttributedStyle.WHITE);
        printColored("        European AI provider. Requires API key.", AttributedStyle.WHITE);
        printColored("        Best quality: Mistral Large + Codestral.", AttributedStyle.WHITE);
        System.out.println();
        printColored("    [2] Ollama (100% local / offline)", AttributedStyle.WHITE);
        printColored("        Runs models entirely on your machine. No API key.", AttributedStyle.WHITE);
        printColored("        Requires Ollama installed: https://ollama.com", AttributedStyle.WHITE);
        System.out.println();

        String providerChoice = console.readLine("  Choice [1]: ");
        int pChoice;
        try {
            pChoice = (providerChoice == null || providerChoice.isBlank()) ? 1 : Integer.parseInt(providerChoice.trim());
        } catch (NumberFormatException e) {
            pChoice = 1;
        }

        ModelManager.Provider selectedProvider = (pChoice == 2)
                ? ModelManager.Provider.OLLAMA
                : ModelManager.Provider.MISTRAL;

        modelManager.switchProvider(selectedProvider);

        System.out.println();
        printColored("  Provider set to: " + selectedProvider.displayName(), AttributedStyle.GREEN);
        System.out.println();

        // ── Step 2: Provider-specific setup ──────────────────────────

        if (selectedProvider == ModelManager.Provider.MISTRAL) {
            setupMistral(console);
        } else {
            setupOllama(console);
        }

        // ── Step 3: Choose model ─────────────────────────────────────

        System.out.println();
        printColored("  STEP " + (selectedProvider == ModelManager.Provider.MISTRAL ? "3" : "2")
                + " — Choose your model", AttributedStyle.YELLOW);
        System.out.println();

        List<ModelManager.ModelOption> models = modelManager.getSuggestedModels();
        for (int i = 0; i < models.size(); i++) {
            ModelManager.ModelOption m = models.get(i);
            String marker = (i == 0) ? " (recommended)" : "";
            printColored(String.format("    [%d] %-26s %s%s",
                    i + 1, m.displayName(), m.description(), marker), AttributedStyle.WHITE);
        }
        System.out.println();

        if (selectedProvider == ModelManager.Provider.OLLAMA) {
            printColored("    You can also type a custom model name (e.g. 'phi3:medium').", AttributedStyle.WHITE);
            System.out.println();
        }

        String choiceStr = console.readLine("  Choice [1]: ");
        String selectedMode;

        if (choiceStr == null || choiceStr.isBlank()) {
            selectedMode = models.get(0).id();
        } else {
            try {
                int choice = Integer.parseInt(choiceStr.trim());
                if (choice >= 1 && choice <= models.size()) {
                    selectedMode = models.get(choice - 1).id();
                } else {
                    selectedMode = models.get(0).id();
                }
            } catch (NumberFormatException e) {
                // Treat as custom model name for Ollama
                if (selectedProvider == ModelManager.Provider.OLLAMA) {
                    selectedMode = choiceStr.trim();
                } else {
                    selectedMode = models.get(0).id();
                }
            }
        }

        modelManager.switchMode(selectedMode);

        System.out.println();
        printColored("  Model set to: " + selectedMode, AttributedStyle.GREEN);
        System.out.println();

        // ── Done ─────────────────────────────────────────────────────

        printColored("  You're all set! Type 'ask <prompt>' to start.", AttributedStyle.CYAN);
        printColored("  Type 'model' to switch models later.", AttributedStyle.CYAN);
        printColored("  Type 'provider' to switch between Mistral / Ollama.", AttributedStyle.CYAN);
        printColored("  Type 'help' for all available commands.", AttributedStyle.CYAN);
        System.out.println();
    }

    // ── Mistral setup ────────────────────────────────────────────────

    private void setupMistral(Console console) {
        printColored("  STEP 2 — Mistral API Key", AttributedStyle.YELLOW);
        printColored("  Get one at: https://console.mistral.ai/api-keys", AttributedStyle.YELLOW);
        System.out.println();

        String apiKey = promptApiKey(console);
        if (apiKey == null) {
            return;
        }

        try {
            apiKeyManager.saveApiKey(apiKey);
        } catch (Exception e) {
            printColored("  Error saving key: " + e.getMessage(), AttributedStyle.RED);
            return;
        }

        System.out.println();
        printColored("  API key saved to " + apiKeyManager.getConfigFilePath(), AttributedStyle.GREEN);
        printColored("  File permissions restricted to owner-only.", AttributedStyle.GREEN);
    }

    // ── Ollama setup ─────────────────────────────────────────────────

    private void setupOllama(Console console) {
        printColored("  Ollama base URL (press Enter for default):", AttributedStyle.YELLOW);
        String url = console.readLine("  URL [http://localhost:11434]: ");

        if (url != null && !url.isBlank()) {
            try {
                apiKeyManager.saveOllamaBaseUrl(url.trim());
                printColored("  Ollama URL set to: " + url.trim(), AttributedStyle.GREEN);
            } catch (Exception e) {
                printColored("  Error saving URL: " + e.getMessage(), AttributedStyle.RED);
            }
        } else {
            printColored("  Using default: http://localhost:11434", AttributedStyle.GREEN);
        }

        System.out.println();
        printColored("  Make sure Ollama is running: 'ollama serve'", AttributedStyle.WHITE);
        printColored("  Pull models with: 'ollama pull mistral' or 'ollama pull codestral'", AttributedStyle.WHITE);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private boolean isAlreadyConfigured() {
        // Mistral: needs an API key
        if (apiKeyManager.hasApiKey()) {
            return true;
        }
        // Ollama: no key needed, just check if provider was set
        String savedProvider = apiKeyManager.getProvider();
        return "ollama".equalsIgnoreCase(savedProvider);
    }

    private String promptApiKey(Console console) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                char[] chars = console.readPassword("  Key (hidden): ");
                if (chars == null) {
                    printColored("  Set MISTRAL_API_KEY env var or use 'config-key <key>' after startup.", AttributedStyle.YELLOW);
                    return null;
                }

                String key = new String(chars).trim();
                if (key.isEmpty()) {
                    printColored("  API key cannot be empty. Please try again.", AttributedStyle.RED);
                    continue;
                }
                if (key.length() < 10) {
                    printColored("  That doesn't look like a valid Mistral API key. Please try again.", AttributedStyle.RED);
                    continue;
                }

                return key;
            } catch (Exception e) {
                printColored("  Input error: " + e.getMessage(), AttributedStyle.RED);
            }
        }

        printColored("  Too many attempts. Use 'config-key <key>' after startup.", AttributedStyle.RED);
        return null;
    }

    private void printWelcomeBack() {
        System.out.println();
        printColored("  EuroCoder (Sovereign Edition) — Ready.", AttributedStyle.CYAN);

        String providerLabel = modelManager.isMistral() ? "Mistral Cloud" : "Ollama (Local)";
        printColored("  Provider: " + providerLabel, AttributedStyle.WHITE);

        String mode = modelManager.getCurrentMode();
        if ("auto".equals(mode)) {
            printColored("  Model: auto (" + modelManager.getPlannerModelName()
                    + " + " + modelManager.getCoderModelName() + ")", AttributedStyle.WHITE);
        } else {
            printColored("  Model: " + mode, AttributedStyle.WHITE);
        }

        printColored("  Type 'ask <prompt>' to start. 'help' for commands.", AttributedStyle.WHITE);
        System.out.println();
    }

    private void printColored(String text, int color) {
        System.out.println(new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi());
    }
}
