package eu.eurocoder.sovereigncli.shell;

import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.agent.ModelOption;
import eu.eurocoder.sovereigncli.agent.Provider;
import eu.eurocoder.sovereigncli.config.ApiKeyManager;
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
import java.util.Map;

@Component
@Order(1)
public class FirstRunSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstRunSetup.class);
    private static final int MAX_API_KEY_ATTEMPTS = 5;
    private static final int MIN_API_KEY_LENGTH = 10;

    private static final Map<Provider, String> PROVIDER_LABELS = Map.of(
            Provider.MISTRAL, "Mistral Cloud API (European AI, recommended)",
            Provider.OLLAMA, "Ollama (100% local / offline)",
            Provider.OPENAI, "OpenAI (GPT-4o, o3-mini)",
            Provider.ANTHROPIC, "Anthropic (Claude Sonnet 4)",
            Provider.GOOGLE_GEMINI, "Google Gemini (Gemini 2.0)",
            Provider.XAI, "xAI (Grok 3)",
            Provider.DEEPSEEK, "DeepSeek (V3 / R1)"
    );

    private static final Provider[] PROVIDER_ORDER = {
            Provider.MISTRAL, Provider.OLLAMA, Provider.OPENAI,
            Provider.ANTHROPIC, Provider.GOOGLE_GEMINI, Provider.XAI, Provider.DEEPSEEK
    };

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

        if (isAlreadyConfigured()) {
            printWelcomeBack();
            return;
        }

        Console console = System.console();
        if (console == null) {
            log.info("No interactive console — skipping first-run setup");
            System.out.println();
            System.out.println("  No provider configured.");
            System.out.println("  Run interactively to complete setup, or set an API key env var.");
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

        printColored("  STEP 1 — Choose your AI provider", AttributedStyle.YELLOW);
        System.out.println();
        for (int i = 0; i < PROVIDER_ORDER.length; i++) {
            Provider p = PROVIDER_ORDER[i];
            String label = PROVIDER_LABELS.getOrDefault(p, p.displayName());
            String extra = (i == 0) ? " (recommended)" : "";
            printColored(String.format("    [%d] %s%s", i + 1, label, extra), AttributedStyle.WHITE);
        }
        System.out.println();

        String providerChoice = console.readLine("  Choice [1]: ");
        int pChoice;
        try {
            pChoice = (providerChoice == null || providerChoice.isBlank())
                    ? 1 : Integer.parseInt(providerChoice.trim());
        } catch (NumberFormatException e) {
            pChoice = 1;
        }
        if (pChoice < 1 || pChoice > PROVIDER_ORDER.length) {
            pChoice = 1;
        }

        Provider selectedProvider = PROVIDER_ORDER[pChoice - 1];
        modelManager.switchProvider(selectedProvider);

        System.out.println();
        printColored("  Provider set to: " + selectedProvider.displayName(), AttributedStyle.GREEN);
        System.out.println();

        int step = 2;
        if (selectedProvider == Provider.OLLAMA) {
            setupOllama(console);
        } else if (selectedProvider.requiresApiKey()) {
            setupApiKey(console, selectedProvider, step);
            step++;
        }

        System.out.println();
        printColored("  STEP " + step + " — Choose your model", AttributedStyle.YELLOW);
        System.out.println();

        List<ModelOption> models = modelManager.getSuggestedModels();
        for (int i = 0; i < models.size(); i++) {
            ModelOption m = models.get(i);
            String marker = (i == 0) ? " (recommended)" : "";
            printColored(String.format("    [%d] %-26s %s%s",
                    i + 1, m.displayName(), m.description(), marker), AttributedStyle.WHITE);
        }
        System.out.println();

        if (selectedProvider == Provider.OLLAMA) {
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
                if (selectedProvider == Provider.OLLAMA) {
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

        printColored("  You're all set! Type 'ask <prompt>' to start.", AttributedStyle.CYAN);
        printColored("  Type 'model' to switch models later.", AttributedStyle.CYAN);
        printColored("  Type 'provider' to switch between providers.", AttributedStyle.CYAN);
        printColored("  Type 'help' for all available commands.", AttributedStyle.CYAN);
        System.out.println();
    }

    private void setupApiKey(Console console, Provider provider, int step) {
        printColored("  STEP " + step + " — " + provider.displayName() + " API Key", AttributedStyle.YELLOW);
        if (provider.envVarName() != null) {
            printColored("  (You can also set " + provider.envVarName() + " env var instead)", AttributedStyle.WHITE);
        }
        System.out.println();

        String apiKey = promptApiKey(console);
        if (apiKey == null) {
            return;
        }

        try {
            apiKeyManager.saveApiKeyForProvider(provider, apiKey);
        } catch (Exception e) {
            printColored("  Error saving key: " + e.getMessage(), AttributedStyle.RED);
            return;
        }

        System.out.println();
        printColored("  API key saved to " + apiKeyManager.getConfigFilePath(), AttributedStyle.GREEN);
        printColored("  File permissions restricted to owner-only.", AttributedStyle.GREEN);
    }

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
        printColored("  Pull models with: 'ollama pull llama3.1'", AttributedStyle.WHITE);
    }

    private boolean isAlreadyConfigured() {
        String savedProvider = apiKeyManager.getProvider();
        if (savedProvider == null || savedProvider.isBlank()) {
            return apiKeyManager.hasApiKey();
        }

        Provider p = Provider.fromId(savedProvider);
        return apiKeyManager.hasApiKeyForProvider(p);
    }

    private String promptApiKey(Console console) {
        for (int attempt = 0; attempt < MAX_API_KEY_ATTEMPTS; attempt++) {
            try {
                char[] chars = console.readPassword("  Key (hidden): ");
                if (chars == null) {
                    printColored("  Set the env var or use 'config-key <key>' after startup.", AttributedStyle.YELLOW);
                    return null;
                }

                String key = new String(chars).trim();
                if (key.isEmpty()) {
                    printColored("  API key cannot be empty. Please try again.", AttributedStyle.RED);
                    continue;
                }
                if (key.length() < MIN_API_KEY_LENGTH) {
                    printColored("  That doesn't look like a valid API key. Please try again.", AttributedStyle.RED);
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

        Provider current = modelManager.getProvider();
        printColored("  Provider: " + current.displayName(), AttributedStyle.WHITE);

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
