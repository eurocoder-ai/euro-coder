package eu.eurocoder.sovereigncli.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.eurocoder.sovereigncli.config.ApiKeyManager;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Manages AI model lifecycle with support for multiple providers.
 * <p>
 * Models are created <b>lazily</b> — only when first used (after FirstRunSetup has
 * had a chance to collect configuration). This avoids startup crashes.
 * <p>
 * Modes:
 * <ul>
 *   <li><b>auto</b> — Hybrid: a planner model + a coder model (configurable independently).</li>
 *   <li><b>&lt;model-name&gt;</b> — Uses a single specific model for all tasks.</li>
 * </ul>
 */
@Service
public class ModelManager {

    private static final Logger log = LoggerFactory.getLogger(ModelManager.class);

    private static final int HTTP_CONNECT_TIMEOUT_SECONDS = 5;
    private static final int REMOTE_API_TIMEOUT_SECONDS = 10;
    private static final int MAX_MODEL_DESCRIPTION_LENGTH = 60;
    private static final double BYTES_PER_GIGABYTE = 1_000_000_000.0;
    private static final int ANTHROPIC_MAX_TOKENS = 4096;

    private static final String OLLAMA_TAGS_PATH = "/api/tags";
    private static final String OPENAI_MODELS_PATH = "/models";
    private static final String GEMINI_MODELS_PATH = "/v1beta/models";
    private static final String AUTO_MODE = "auto";

    // ── Default models per provider ─────────────────────────────────────

    private static final Map<Provider, String> DEFAULT_PLANNER = Map.of(
            Provider.MISTRAL, "mistral-large-latest",
            Provider.OLLAMA, "llama3.1",
            Provider.OPENAI, "gpt-4o",
            Provider.ANTHROPIC, "claude-sonnet-4-20250514",
            Provider.GOOGLE_GEMINI, "gemini-2.0-flash",
            Provider.XAI, "grok-3",
            Provider.DEEPSEEK, "deepseek-chat"
    );

    private static final Map<Provider, String> DEFAULT_CODER = Map.of(
            Provider.MISTRAL, "codestral-latest",
            Provider.OLLAMA, "qwen3:4b",
            Provider.OPENAI, "gpt-4o",
            Provider.ANTHROPIC, "claude-sonnet-4-20250514",
            Provider.GOOGLE_GEMINI, "gemini-2.0-flash",
            Provider.XAI, "grok-3",
            Provider.DEEPSEEK, "deepseek-chat"
    );

    // ── Curated suggestion lists per provider ───────────────────────────

    public static final List<ModelOption> MISTRAL_SUGGESTIONS = List.of(
            new ModelOption("mistral-large-latest",
                    "Mistral Large",
                    "Best reasoning and analysis"),
            new ModelOption("codestral-latest",
                    "Codestral",
                    "Optimized for code generation"),
            new ModelOption("mistral-small-latest",
                    "Mistral Small",
                    "Fast and cost-efficient"),
            new ModelOption("open-mistral-nemo",
                    "Mistral Nemo",
                    "Open-weight, good general purpose")
    );

    /** Curated Ollama models known to support tool calling. See: https://ollama.com/search?c=tools */
    public static final List<ModelOption> OLLAMA_SUGGESTIONS = List.of(
            new ModelOption("llama3.1",
                    "Llama 3.1 8B",
                    "Best local tool-calling model (4.7GB). Meta, open-weight."),
            new ModelOption("qwen3:4b",
                    "Qwen3 4B",
                    "Fast coder with tool calling (2.5GB). Great for <=16GB RAM."),
            new ModelOption("qwen3",
                    "Qwen3 8B",
                    "Stronger reasoning + tool calling (5.2GB). Needs 16GB RAM."),
            new ModelOption("qwen3-coder",
                    "Qwen3-Coder 30B",
                    "Top-tier coding agent (19GB). Needs 32GB RAM."),
            new ModelOption("devstral-small",
                    "Devstral Small 24B",
                    "Mistral's local coding agent (14GB). Needs 16GB+ RAM."),
            new ModelOption("mistral-nemo",
                    "Mistral Nemo 12B",
                    "European model with tool calling (7GB). Needs 12GB RAM."),
            new ModelOption("llama3.1:70b",
                    "Llama 3.1 70B",
                    "Most capable local model (40GB). Needs 48GB+ RAM.")
    );

    public static final List<ModelOption> OPENAI_SUGGESTIONS = List.of(
            new ModelOption("gpt-4o",
                    "GPT-4o",
                    "Best overall quality and tool calling"),
            new ModelOption("gpt-4o-mini",
                    "GPT-4o Mini",
                    "Fast, cheap, strong tool calling"),
            new ModelOption("o3-mini",
                    "o3-mini",
                    "Reasoning model, good for complex tasks"),
            new ModelOption("gpt-4-turbo",
                    "GPT-4 Turbo",
                    "Previous flagship, reliable tool calling")
    );

    public static final List<ModelOption> ANTHROPIC_SUGGESTIONS = List.of(
            new ModelOption("claude-sonnet-4-20250514",
                    "Claude Sonnet 4",
                    "Best coding model, excellent tool calling"),
            new ModelOption("claude-3-5-haiku-20241022",
                    "Claude 3.5 Haiku",
                    "Fast and cost-efficient, good tool calling")
    );

    public static final List<ModelOption> GOOGLE_GEMINI_SUGGESTIONS = List.of(
            new ModelOption("gemini-2.0-flash",
                    "Gemini 2.0 Flash",
                    "Fast and capable, good tool calling"),
            new ModelOption("gemini-2.5-pro-preview-06-05",
                    "Gemini 2.5 Pro",
                    "Most capable Gemini model"),
            new ModelOption("gemini-2.5-flash-preview-05-20",
                    "Gemini 2.5 Flash",
                    "Balanced speed and capability")
    );

    public static final List<ModelOption> XAI_SUGGESTIONS = List.of(
            new ModelOption("grok-3",
                    "Grok 3",
                    "Most capable xAI model"),
            new ModelOption("grok-3-mini",
                    "Grok 3 Mini",
                    "Fast reasoning model")
    );

    public static final List<ModelOption> DEEPSEEK_SUGGESTIONS = List.of(
            new ModelOption("deepseek-chat",
                    "DeepSeek Chat (V3)",
                    "Strong general-purpose and coding model"),
            new ModelOption("deepseek-reasoner",
                    "DeepSeek Reasoner (R1)",
                    "Advanced reasoning capabilities")
    );

    private static final Map<Provider, List<ModelOption>> SUGGESTIONS = Map.of(
            Provider.MISTRAL, MISTRAL_SUGGESTIONS,
            Provider.OLLAMA, OLLAMA_SUGGESTIONS,
            Provider.OPENAI, OPENAI_SUGGESTIONS,
            Provider.ANTHROPIC, ANTHROPIC_SUGGESTIONS,
            Provider.GOOGLE_GEMINI, GOOGLE_GEMINI_SUGGESTIONS,
            Provider.XAI, XAI_SUGGESTIONS,
            Provider.DEEPSEEK, DEEPSEEK_SUGGESTIONS
    );

    private final ApiKeyManager apiKeyManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
            .build();

    @Value("${eurocoder.planner.temperature:0.3}")
    private double plannerTemperature;

    @Value("${eurocoder.coder.temperature:0.2}")
    private double coderTemperature;

    @Value("${eurocoder.timeout:180}")
    private int timeoutSeconds;

    @Value("${eurocoder.max-retries:1}")
    private int maxRetries;

    private Provider provider;
    private String currentMode;
    private String customPlannerModel;
    private String customCoderModel;
    private ChatModel plannerModel;
    private ChatModel coderModel;

    public ModelManager(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;

        this.provider = Provider.fromId(apiKeyManager.getProvider());
        String savedMode = apiKeyManager.getModelMode();
        this.currentMode = (savedMode != null && !savedMode.isBlank()) ? savedMode : AUTO_MODE;
        this.customPlannerModel = nullIfBlank(apiKeyManager.getCustomPlannerModel());
        this.customCoderModel = nullIfBlank(apiKeyManager.getCustomCoderModel());

        log.info("ModelManager initialized — provider: {}, mode: {}, planner: {}, coder: {}",
                provider.id(), currentMode, getPlannerModelName(), getCoderModelName());
    }

    public synchronized ChatModel getPlannerModel() {
        if (plannerModel == null) {
            String name = getPlannerModelName();
            log.info("Creating planner model: {} (provider={}, temperature={})",
                    name, provider.id(), plannerTemperature);
            plannerModel = buildModel(name, plannerTemperature);
        }
        return plannerModel;
    }

    public synchronized ChatModel getCoderModel() {
        if (coderModel == null) {
            String name = getCoderModelName();
            log.info("Creating coder model: {} (provider={}, temperature={})",
                    name, provider.id(), coderTemperature);
            coderModel = buildModel(name, coderTemperature);
        }
        return coderModel;
    }

    public synchronized void switchMode(String newMode) {
        this.currentMode = newMode;
        this.customPlannerModel = null;
        this.customCoderModel = null;
        this.plannerModel = null;
        this.coderModel = null;

        try {
            apiKeyManager.saveModelMode(newMode);
            apiKeyManager.clearCustomModels();
        } catch (Exception e) {
            log.warn("Failed to persist model mode: {}", e.getMessage());
        }
        log.info("Switched model mode to: {}", newMode);
    }

    public synchronized void setCustomPlannerModel(String modelName) {
        this.currentMode = AUTO_MODE;
        this.customPlannerModel = modelName;
        this.plannerModel = null;

        try {
            apiKeyManager.saveModelMode(AUTO_MODE);
            apiKeyManager.saveCustomPlannerModel(modelName);
        } catch (Exception e) {
            log.warn("Failed to persist custom planner: {}", e.getMessage());
        }
        log.info("Custom planner set to: {}", modelName);
    }

    public synchronized void setCustomCoderModel(String modelName) {
        this.currentMode = AUTO_MODE;
        this.customCoderModel = modelName;
        this.coderModel = null;

        try {
            apiKeyManager.saveModelMode(AUTO_MODE);
            apiKeyManager.saveCustomCoderModel(modelName);
        } catch (Exception e) {
            log.warn("Failed to persist custom coder: {}", e.getMessage());
        }
        log.info("Custom coder set to: {}", modelName);
    }

    public synchronized void switchProvider(Provider newProvider) {
        this.provider = newProvider;
        this.currentMode = AUTO_MODE;
        this.customPlannerModel = null;
        this.customCoderModel = null;
        this.plannerModel = null;
        this.coderModel = null;

        try {
            apiKeyManager.saveProvider(newProvider.id());
            apiKeyManager.saveModelMode(AUTO_MODE);
            apiKeyManager.clearCustomModels();
        } catch (Exception e) {
            log.warn("Failed to persist provider: {}", e.getMessage());
        }
        log.info("Switched provider to: {}, mode reset to auto", newProvider.id());
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public Provider getProvider() {
        return provider;
    }

    public boolean isMistral() {
        return provider == Provider.MISTRAL;
    }

    public boolean isOllama() {
        return provider == Provider.OLLAMA;
    }

    public String getCurrentMode() {
        return currentMode;
    }

    public boolean isAutoMode() {
        return AUTO_MODE.equalsIgnoreCase(currentMode);
    }

    public String getPlannerModelName() {
        if (customPlannerModel != null) {
            return customPlannerModel;
        }
        if (isAutoMode()) {
            return DEFAULT_PLANNER.getOrDefault(provider, "gpt-4o");
        }
        return currentMode;
    }

    public String getCoderModelName() {
        if (customCoderModel != null) {
            return customCoderModel;
        }
        if (isAutoMode()) {
            return DEFAULT_CODER.getOrDefault(provider, "gpt-4o");
        }
        return currentMode;
    }

    public boolean hasCustomModels() {
        return customPlannerModel != null || customCoderModel != null;
    }

    public List<ModelOption> getSuggestedModels() {
        return SUGGESTIONS.getOrDefault(provider, Collections.emptyList());
    }

    public boolean isValidModel(String modelId) {
        return modelId != null && !modelId.isBlank();
    }

    /**
     * Builds a standalone ChatModel for benchmarking without affecting the active planner/coder.
     * Uses a fixed temperature of 0.0 for reproducibility.
     */
    public ChatModel buildBenchmarkModel(String modelName) {
        return buildModel(modelName, 0.0);
    }

    // ── Remote model listing ────────────────────────────────────────────

    /**
     * Fetches available models from the current provider's API.
     * Returns an empty list if the provider is unreachable or has no listing API.
     */
    public List<ModelOption> getAvailableModels() {
        return switch (provider) {
            case OLLAMA -> getInstalledOllamaModels();
            case MISTRAL -> getOpenAiCompatibleModels(provider.defaultBaseUrl() + OPENAI_MODELS_PATH);
            case OPENAI, XAI, DEEPSEEK -> getOpenAiCompatibleModels(provider.defaultBaseUrl() + OPENAI_MODELS_PATH);
            case GOOGLE_GEMINI -> getGoogleGeminiModels();
            case ANTHROPIC -> Collections.emptyList();
        };
    }

    public List<ModelOption> getInstalledOllamaModels() {
        if (!isOllama()) {
            return Collections.emptyList();
        }

        String baseUrl = apiKeyManager.getOllamaBaseUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + OLLAMA_TAGS_PATH))
                    .timeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Ollama API returned status {}", response.statusCode());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.get("models");
            if (models == null || !models.isArray()) {
                return Collections.emptyList();
            }

            List<ModelOption> result = new ArrayList<>();
            for (JsonNode model : models) {
                String name = model.has("name") ? model.get("name").asText() : "unknown";
                String size = "";
                if (model.has("size")) {
                    long bytes = model.get("size").asLong();
                    size = String.format("%.1fGB", bytes / BYTES_PER_GIGABYTE);
                }
                String family = "";
                if (model.has("details")) {
                    JsonNode details = model.get("details");
                    if (details.has("family")) {
                        family = details.get("family").asText();
                    }
                    if (details.has("parameter_size")) {
                        size = details.get("parameter_size").asText() + " / " + size;
                    }
                }
                result.add(new ModelOption(name,
                        name,
                        family.isEmpty() ? size : family + " — " + size));
            }
            return result;

        } catch (Exception e) {
            log.debug("Failed to query Ollama models: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** @deprecated Use {@link #getAvailableModels()} instead. */
    @Deprecated
    public List<ModelOption> getRemoteMistralModels() {
        if (!isMistral()) {
            return Collections.emptyList();
        }
        return getOpenAiCompatibleModels(Provider.MISTRAL.defaultBaseUrl() + OPENAI_MODELS_PATH);
    }

    // ── Model building ──────────────────────────────────────────────────

    private ChatModel buildModel(String modelName, double temperature) {
        return switch (provider) {
            case OLLAMA -> buildOllamaModel(modelName, temperature);
            case MISTRAL -> buildMistralModel(modelName, temperature);
            case OPENAI -> buildOpenAiModel(modelName, temperature, provider.defaultBaseUrl());
            case ANTHROPIC -> buildAnthropicModel(modelName, temperature);
            case GOOGLE_GEMINI -> buildGoogleGeminiModel(modelName, temperature);
            case XAI -> buildOpenAiModel(modelName, temperature, provider.defaultBaseUrl());
            case DEEPSEEK -> buildOpenAiModel(modelName, temperature, provider.defaultBaseUrl());
        };
    }

    private ChatModel buildMistralModel(String modelName, double temperature) {
        String apiKey = requireApiKey("Mistral");
        return MistralAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private ChatModel buildOllamaModel(String modelName, double temperature) {
        String baseUrl = apiKeyManager.getOllamaBaseUrl();
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private ChatModel buildOpenAiModel(String modelName, double temperature, String baseUrl) {
        String apiKey = requireApiKey(provider.displayName());
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private ChatModel buildAnthropicModel(String modelName, double temperature) {
        String apiKey = requireApiKey("Anthropic");
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(ANTHROPIC_MAX_TOKENS)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private ChatModel buildGoogleGeminiModel(String modelName, double temperature) {
        String apiKey = requireApiKey("Google Gemini");
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(maxRetries)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private String requireApiKey(String providerName) {
        String apiKey = apiKeyManager.getApiKeyForProvider(provider);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "No " + providerName + " API key configured. " +
                    "Use 'config-key <key>' or set " + provider.envVarName() + " env var.");
        }
        return apiKey;
    }

    // ── Remote model listing helpers ────────────────────────────────────

    /**
     * Fetches models from any OpenAI-compatible API (Mistral, OpenAI, xAI, DeepSeek).
     * All use the same response format: {@code { "data": [{ "id": "...", ... }] }}.
     */
    private List<ModelOption> getOpenAiCompatibleModels(String modelsUrl) {
        String apiKey = apiKeyManager.getApiKeyForProvider(provider);
        if (apiKey == null || apiKey.isBlank()) {
            return Collections.emptyList();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelsUrl))
                    .timeout(Duration.ofSeconds(REMOTE_API_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("{} API returned status {}", provider.displayName(), response.statusCode());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return Collections.emptyList();
            }

            List<ModelOption> result = new ArrayList<>();
            for (JsonNode model : data) {
                String id = model.has("id") ? model.get("id").asText() : "unknown";
                String ownedBy = model.has("owned_by") ? model.get("owned_by").asText() : "";
                String description = model.has("description")
                        ? model.get("description").asText()
                        : ownedBy;
                if (description.length() > MAX_MODEL_DESCRIPTION_LENGTH) {
                    description = description.substring(0, MAX_MODEL_DESCRIPTION_LENGTH - 3) + "...";
                }
                result.add(new ModelOption(id, id, description));
            }

            result.sort((a, b) -> {
                boolean aLatest = a.id().contains("latest");
                boolean bLatest = b.id().contains("latest");
                if (aLatest != bLatest) return aLatest ? -1 : 1;
                return a.id().compareTo(b.id());
            });

            return result;

        } catch (Exception e) {
            log.debug("Failed to query {} models: {}", provider.displayName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ModelOption> getGoogleGeminiModels() {
        String apiKey = apiKeyManager.getApiKeyForProvider(Provider.GOOGLE_GEMINI);
        if (apiKey == null || apiKey.isBlank()) {
            return Collections.emptyList();
        }

        try {
            String url = Provider.GOOGLE_GEMINI.defaultBaseUrl() + GEMINI_MODELS_PATH + "?key=" + apiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REMOTE_API_TIMEOUT_SECONDS))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Google Gemini API returned status {}", response.statusCode());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root.get("models");
            if (models == null || !models.isArray()) {
                return Collections.emptyList();
            }

            List<ModelOption> result = new ArrayList<>();
            for (JsonNode model : models) {
                String name = model.has("name") ? model.get("name").asText() : "unknown";
                String id = name.startsWith("models/") ? name.substring("models/".length()) : name;
                String displayName = model.has("displayName") ? model.get("displayName").asText() : id;
                String description = model.has("description")
                        ? model.get("description").asText()
                        : "";
                if (description.length() > MAX_MODEL_DESCRIPTION_LENGTH) {
                    description = description.substring(0, MAX_MODEL_DESCRIPTION_LENGTH - 3) + "...";
                }
                result.add(new ModelOption(id, displayName, description));
            }

            return result;

        } catch (Exception e) {
            log.debug("Failed to query Google Gemini models: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
