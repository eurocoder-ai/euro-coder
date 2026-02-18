package eu.eurocoder.sovereigncli.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.eurocoder.sovereigncli.config.ApiKeyManager;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
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

/**
 * Manages AI model lifecycle with support for two providers:
 * <ul>
 *   <li><b>mistral</b> — Mistral Cloud API (requires API key, European AI provider)</li>
 *   <li><b>ollama</b>  — Local models via Ollama (no API key, 100% offline/sovereign)</li>
 * </ul>
 * <p>
 * Models are created <b>lazily</b> — only when first used (after FirstRunSetup has
 * had a chance to collect configuration). This avoids startup crashes.
 * <p>
 * Modes:
 * <ul>
 *   <li><b>auto</b> — Hybrid: a planner model + a coder model (configurable independently).</li>
 *   <li><b>&lt;model-name&gt;</b> — Uses a single specific model for all tasks.</li>
 * </ul>
 * <p>
 * The planner and coder models can be overridden independently via
 * {@link #setCustomPlannerModel} / {@link #setCustomCoderModel}, allowing
 * any combination of models regardless of the curated suggestion lists.
 */
@Service
public class ModelManager {

    private static final Logger log = LoggerFactory.getLogger(ModelManager.class);

    // ── Suggested models per provider (curated, not restrictive) ─────

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

    // Curated Ollama models known to support tool calling.
    // See: https://ollama.com/search?c=tools
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

    // ── Default auto-mode pairings ──────────────────────────────────

    private static final String MISTRAL_DEFAULT_PLANNER = "mistral-large-latest";
    private static final String MISTRAL_DEFAULT_CODER = "codestral-latest";
    private static final String OLLAMA_DEFAULT_PLANNER = "llama3.1";
    private static final String OLLAMA_DEFAULT_CODER = "qwen3:4b";

    // ── Dependencies ────────────────────────────────────────────────

    private final ApiKeyManager apiKeyManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ── Configuration (from application.properties) ─────────────────

    @Value("${eurocoder.planner.temperature:0.3}")
    private double plannerTemperature;

    @Value("${eurocoder.coder.temperature:0.2}")
    private double coderTemperature;

    @Value("${eurocoder.timeout:120}")
    private int timeoutSeconds;

    // ── State ───────────────────────────────────────────────────────

    private Provider provider;
    private String currentMode;
    private String customPlannerModel;  // null = use default for mode
    private String customCoderModel;    // null = use default for mode
    private ChatModel plannerModel;
    private ChatModel coderModel;

    // ── Constructor ─────────────────────────────────────────────────

    public ModelManager(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;

        // Load persisted provider
        String savedProvider = apiKeyManager.getProvider();
        this.provider = Provider.fromId(savedProvider);

        // Load persisted mode
        String savedMode = apiKeyManager.getModelMode();
        this.currentMode = (savedMode != null && !savedMode.isBlank()) ? savedMode : "auto";

        // Load custom planner/coder overrides
        this.customPlannerModel = nullIfBlank(apiKeyManager.getCustomPlannerModel());
        this.customCoderModel = nullIfBlank(apiKeyManager.getCustomCoderModel());

        log.info("ModelManager initialized — provider: {}, mode: {}, planner: {}, coder: {}",
                provider.id(), currentMode, getPlannerModelName(), getCoderModelName());
    }

    // ── Lazy model accessors ────────────────────────────────────────

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

    // ── Model switching ─────────────────────────────────────────────

    /**
     * Switches to a new model mode. If not "auto", both planner and coder
     * use this single model. Custom overrides are cleared.
     */
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

    /**
     * Sets a custom planner model independently. Switches to "auto" mode
     * if not already, since custom planner/coder only makes sense in auto.
     */
    public synchronized void setCustomPlannerModel(String modelName) {
        this.currentMode = "auto";
        this.customPlannerModel = modelName;
        this.plannerModel = null;

        try {
            apiKeyManager.saveModelMode("auto");
            apiKeyManager.saveCustomPlannerModel(modelName);
        } catch (Exception e) {
            log.warn("Failed to persist custom planner: {}", e.getMessage());
        }
        log.info("Custom planner set to: {}", modelName);
    }

    /**
     * Sets a custom coder model independently. Switches to "auto" mode
     * if not already, since custom planner/coder only makes sense in auto.
     */
    public synchronized void setCustomCoderModel(String modelName) {
        this.currentMode = "auto";
        this.customCoderModel = modelName;
        this.coderModel = null;

        try {
            apiKeyManager.saveModelMode("auto");
            apiKeyManager.saveCustomCoderModel(modelName);
        } catch (Exception e) {
            log.warn("Failed to persist custom coder: {}", e.getMessage());
        }
        log.info("Custom coder set to: {}", modelName);
    }

    /**
     * Switches the provider (mistral/ollama) and resets to defaults.
     */
    public synchronized void switchProvider(Provider newProvider) {
        this.provider = newProvider;
        this.currentMode = "auto";
        this.customPlannerModel = null;
        this.customCoderModel = null;
        this.plannerModel = null;
        this.coderModel = null;

        try {
            apiKeyManager.saveProvider(newProvider.id());
            apiKeyManager.saveModelMode("auto");
            apiKeyManager.clearCustomModels();
        } catch (Exception e) {
            log.warn("Failed to persist provider: {}", e.getMessage());
        }
        log.info("Switched provider to: {}, mode reset to auto", newProvider.id());
    }

    // ── Queries ─────────────────────────────────────────────────────

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
        return "auto".equalsIgnoreCase(currentMode);
    }

    public String getPlannerModelName() {
        // 1. Custom override takes priority
        if (customPlannerModel != null) {
            return customPlannerModel;
        }
        // 2. Auto mode uses defaults
        if (isAutoMode()) {
            return isMistral() ? MISTRAL_DEFAULT_PLANNER : OLLAMA_DEFAULT_PLANNER;
        }
        // 3. Single-model mode uses the mode name for both
        return currentMode;
    }

    public String getCoderModelName() {
        if (customCoderModel != null) {
            return customCoderModel;
        }
        if (isAutoMode()) {
            return isMistral() ? MISTRAL_DEFAULT_CODER : OLLAMA_DEFAULT_CODER;
        }
        return currentMode;
    }

    public boolean hasCustomModels() {
        return customPlannerModel != null || customCoderModel != null;
    }

    public List<ModelOption> getSuggestedModels() {
        return isMistral() ? MISTRAL_SUGGESTIONS : OLLAMA_SUGGESTIONS;
    }

    /**
     * For Ollama: dynamically queries the local Ollama server for installed models.
     * Returns an empty list if Ollama is not running or on error.
     */
    public List<ModelOption> getInstalledOllamaModels() {
        if (!isOllama()) {
            return Collections.emptyList();
        }

        String baseUrl = apiKeyManager.getOllamaBaseUrl();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
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
                    size = String.format("%.1fGB", bytes / 1_000_000_000.0);
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

    /**
     * For Mistral: dynamically queries the Mistral API for available models.
     * Returns an empty list if no API key or on error.
     */
    public List<ModelOption> getRemoteMistralModels() {
        if (!isMistral()) {
            return Collections.emptyList();
        }

        String apiKey = apiKeyManager.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return Collections.emptyList();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mistral.ai/v1/models"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Mistral API returned status {}", response.statusCode());
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
                // Truncate long descriptions
                if (description.length() > 60) {
                    description = description.substring(0, 57) + "...";
                }
                result.add(new ModelOption(id, id, description));
            }

            // Sort: latest/recommended first
            result.sort((a, b) -> {
                boolean aLatest = a.id().contains("latest");
                boolean bLatest = b.id().contains("latest");
                if (aLatest != bLatest) return aLatest ? -1 : 1;
                return a.id().compareTo(b.id());
            });

            return result;

        } catch (Exception e) {
            log.debug("Failed to query Mistral models: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean isValidModel(String modelId) {
        // Accept any model name — the curated lists are suggestions, not restrictions.
        // If the model doesn't exist, the API call will fail with a clear error.
        return modelId != null && !modelId.isBlank();
    }

    // ── Factory ─────────────────────────────────────────────────────

    private ChatModel buildModel(String modelName, double temperature) {
        if (isOllama()) {
            return buildOllamaModel(modelName, temperature);
        }
        return buildMistralModel(modelName, temperature);
    }

    private ChatModel buildMistralModel(String modelName, double temperature) {
        String apiKey = apiKeyManager.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "No Mistral API key configured. Use 'config-key <key>' to set one.");
        }
        return MistralAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
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

    // ── Helpers ──────────────────────────────────────────────────────

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
