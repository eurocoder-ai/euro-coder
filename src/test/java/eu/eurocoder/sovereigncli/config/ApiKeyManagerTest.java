package eu.eurocoder.sovereigncli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.eurocoder.sovereigncli.agent.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ApiKeyManager} — config file persistence, per-provider API key
 * management, provider/model settings.
 */
class ApiKeyManagerTest {

    @TempDir
    Path tempDir;

    private ApiKeyManager apiKeyManager;
    private Path configFile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        apiKeyManager = new ApiKeyManager() {
            @Override
            public Path getConfigDir() {
                return tempDir;
            }

            @Override
            public String getApiKey() {
                return hasConfigField("mistral_api_key") ? readFromConfig("mistral_api_key") : null;
            }

            @Override
            public String getApiKeyForProvider(Provider provider) {
                if (!provider.requiresApiKey()) return null;
                String field = provider.configField();
                return hasConfigField(field) ? readFromConfig(field) : null;
            }

            private boolean hasConfigField(String field) {
                Path config = getConfigFilePath();
                if (!Files.exists(config)) return false;
                try {
                    var root = new ObjectMapper().readTree(config.toFile());
                    return root.has(field) && !root.get(field).asText().isBlank();
                } catch (IOException e) {
                    return false;
                }
            }

            private String readFromConfig(String field) {
                Path config = getConfigFilePath();
                try {
                    var root = new ObjectMapper().readTree(config.toFile());
                    if (root.has(field)) {
                        String val = root.get(field).asText();
                        return val.isBlank() ? null : val;
                    }
                } catch (IOException e) { /* fall through */ }
                return null;
            }
        };
        configFile = tempDir.resolve("config.json");
    }

    // ── Legacy API Key (Mistral) ────────────────────────────────────

    @Test
    void hasApiKey_falseWhenNoConfig() {
        assertThat(apiKeyManager.hasApiKey()).isFalse();
    }

    @Test
    void saveAndGetApiKey() throws IOException {
        apiKeyManager.saveApiKey("sk-test-key-12345678");

        assertThat(apiKeyManager.getApiKey()).isEqualTo("sk-test-key-12345678");
        assertThat(apiKeyManager.hasApiKey()).isTrue();
        assertThat(configFile).exists();
    }

    @Test
    void saveApiKey_trimsWhitespace() throws IOException {
        apiKeyManager.saveApiKey("  sk-trimmed-key  ");

        assertThat(apiKeyManager.getApiKey()).isEqualTo("sk-trimmed-key");
    }

    @Test
    void clearApiKey_deletesConfigFile() throws IOException {
        apiKeyManager.saveApiKey("sk-to-delete");
        assertThat(configFile).exists();

        apiKeyManager.clearApiKey();

        assertThat(configFile).doesNotExist();
    }

    @Test
    void clearApiKey_noErrorWhenFileDoesNotExist() throws IOException {
        apiKeyManager.clearApiKey();
    }

    // ── Per-provider API key management ─────────────────────────────

    @Test
    void saveAndGetApiKeyForProvider_openai() throws IOException {
        apiKeyManager.saveApiKeyForProvider(Provider.OPENAI, "sk-openai-test-key");

        assertThat(apiKeyManager.getApiKeyForProvider(Provider.OPENAI)).isEqualTo("sk-openai-test-key");
        assertThat(apiKeyManager.hasApiKeyForProvider(Provider.OPENAI)).isTrue();
    }

    @Test
    void saveAndGetApiKeyForProvider_anthropic() throws IOException {
        apiKeyManager.saveApiKeyForProvider(Provider.ANTHROPIC, "sk-ant-test-key");

        assertThat(apiKeyManager.getApiKeyForProvider(Provider.ANTHROPIC)).isEqualTo("sk-ant-test-key");
        assertThat(apiKeyManager.hasApiKeyForProvider(Provider.ANTHROPIC)).isTrue();
    }

    @Test
    void saveAndGetApiKeyForProvider_google() throws IOException {
        apiKeyManager.saveApiKeyForProvider(Provider.GOOGLE_GEMINI, "AIzaSy-test-key");

        assertThat(apiKeyManager.getApiKeyForProvider(Provider.GOOGLE_GEMINI)).isEqualTo("AIzaSy-test-key");
    }

    @Test
    void saveAndGetApiKeyForProvider_xai() throws IOException {
        apiKeyManager.saveApiKeyForProvider(Provider.XAI, "xai-test-key");

        assertThat(apiKeyManager.getApiKeyForProvider(Provider.XAI)).isEqualTo("xai-test-key");
    }

    @Test
    void saveAndGetApiKeyForProvider_deepseek() throws IOException {
        apiKeyManager.saveApiKeyForProvider(Provider.DEEPSEEK, "sk-deepseek-test");

        assertThat(apiKeyManager.getApiKeyForProvider(Provider.DEEPSEEK)).isEqualTo("sk-deepseek-test");
    }

    @Test
    void hasApiKeyForProvider_falseWhenNoKey() {
        assertThat(apiKeyManager.hasApiKeyForProvider(Provider.OPENAI)).isFalse();
    }

    @Test
    void hasApiKeyForProvider_ollama_alwaysTrue() {
        assertThat(apiKeyManager.hasApiKeyForProvider(Provider.OLLAMA)).isTrue();
    }

    @Test
    void getApiKeyForProvider_ollama_returnsNull() {
        assertThat(apiKeyManager.getApiKeyForProvider(Provider.OLLAMA)).isNull();
    }

    @Test
    void saveApiKeyForProvider_ollama_throwsException() {
        assertThatThrownBy(() -> apiKeyManager.saveApiKeyForProvider(Provider.OLLAMA, "key"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multipleProviderKeys_coexist() throws IOException {
        apiKeyManager.saveApiKeyForProvider(Provider.MISTRAL, "sk-mistral");
        apiKeyManager.saveApiKeyForProvider(Provider.OPENAI, "sk-openai");
        apiKeyManager.saveApiKeyForProvider(Provider.ANTHROPIC, "sk-anthropic");

        assertThat(apiKeyManager.getApiKeyForProvider(Provider.MISTRAL)).isEqualTo("sk-mistral");
        assertThat(apiKeyManager.getApiKeyForProvider(Provider.OPENAI)).isEqualTo("sk-openai");
        assertThat(apiKeyManager.getApiKeyForProvider(Provider.ANTHROPIC)).isEqualTo("sk-anthropic");
    }

    @Test
    void saveApiKeyForProvider_trimsWhitespace() throws IOException {
        apiKeyManager.saveApiKeyForProvider(Provider.OPENAI, "  sk-trimmed  ");

        assertThat(apiKeyManager.getApiKeyForProvider(Provider.OPENAI)).isEqualTo("sk-trimmed");
    }

    // ── Model Mode ───────────────────────────────────────────────────

    @Test
    void getModelMode_nullWhenNoConfig() {
        assertThat(apiKeyManager.getModelMode()).isNull();
    }

    @Test
    void saveAndGetModelMode() throws IOException {
        apiKeyManager.saveModelMode("codestral-latest");

        assertThat(apiKeyManager.getModelMode()).isEqualTo("codestral-latest");
    }

    // ── Provider ─────────────────────────────────────────────────────

    @Test
    void getProvider_nullWhenNoConfig() {
        assertThat(apiKeyManager.getProvider()).isNull();
    }

    @Test
    void saveAndGetProvider() throws IOException {
        apiKeyManager.saveProvider("ollama");

        assertThat(apiKeyManager.getProvider()).isEqualTo("ollama");
    }

    @Test
    void saveAndGetProvider_openai() throws IOException {
        apiKeyManager.saveProvider("openai");

        assertThat(apiKeyManager.getProvider()).isEqualTo("openai");
    }

    // ── Ollama URL ───────────────────────────────────────────────────

    @Test
    void getOllamaBaseUrl_defaultWhenNoConfig() {
        assertThat(apiKeyManager.getOllamaBaseUrl()).isEqualTo("http://localhost:11434");
    }

    @Test
    void saveAndGetOllamaBaseUrl() throws IOException {
        apiKeyManager.saveOllamaBaseUrl("http://192.168.1.100:11434");

        assertThat(apiKeyManager.getOllamaBaseUrl()).isEqualTo("http://192.168.1.100:11434");
    }

    // ── Custom Planner/Coder ─────────────────────────────────────────

    @Test
    void getCustomPlannerModel_nullWhenNoConfig() {
        assertThat(apiKeyManager.getCustomPlannerModel()).isNull();
    }

    @Test
    void saveAndGetCustomPlannerModel() throws IOException {
        apiKeyManager.saveCustomPlannerModel("mistral-nemo");

        assertThat(apiKeyManager.getCustomPlannerModel()).isEqualTo("mistral-nemo");
    }

    @Test
    void saveAndGetCustomCoderModel() throws IOException {
        apiKeyManager.saveCustomCoderModel("devstral-small");

        assertThat(apiKeyManager.getCustomCoderModel()).isEqualTo("devstral-small");
    }

    @Test
    void clearCustomModels_setsBothToBlank() throws IOException {
        apiKeyManager.saveCustomPlannerModel("planner");
        apiKeyManager.saveCustomCoderModel("coder");

        apiKeyManager.clearCustomModels();

        assertThat(apiKeyManager.getCustomPlannerModel()).isNull();
        assertThat(apiKeyManager.getCustomCoderModel()).isNull();
    }

    // ── Multiple fields coexist ──────────────────────────────────────

    @Test
    void multipleFields_persistIndependently() throws IOException {
        apiKeyManager.saveApiKey("sk-key-123");
        apiKeyManager.saveProvider("ollama");
        apiKeyManager.saveModelMode("auto");
        apiKeyManager.saveOllamaBaseUrl("http://myhost:11434");

        assertThat(apiKeyManager.getApiKey()).isEqualTo("sk-key-123");
        assertThat(apiKeyManager.getProvider()).isEqualTo("ollama");
        assertThat(apiKeyManager.getModelMode()).isEqualTo("auto");
        assertThat(apiKeyManager.getOllamaBaseUrl()).isEqualTo("http://myhost:11434");
    }

    @Test
    void configFile_isValidJson() throws IOException {
        apiKeyManager.saveApiKey("sk-test");
        apiKeyManager.saveProvider("mistral");

        String json = Files.readString(configFile);
        ObjectNode root = (ObjectNode) objectMapper.readTree(json);

        assertThat(root.has("mistral_api_key")).isTrue();
        assertThat(root.has("provider")).isTrue();
        assertThat(root.get("mistral_api_key").asText()).isEqualTo("sk-test");
    }

    @Test
    void configFile_storesMultipleProviderKeys() throws IOException {
        apiKeyManager.saveApiKeyForProvider(Provider.MISTRAL, "sk-mistral");
        apiKeyManager.saveApiKeyForProvider(Provider.OPENAI, "sk-openai");

        String json = Files.readString(configFile);
        ObjectNode root = (ObjectNode) objectMapper.readTree(json);

        assertThat(root.has("mistral_api_key")).isTrue();
        assertThat(root.has("openai_api_key")).isTrue();
        assertThat(root.get("mistral_api_key").asText()).isEqualTo("sk-mistral");
        assertThat(root.get("openai_api_key").asText()).isEqualTo("sk-openai");
    }

    // ── Beta feature flag ────────────────────────────────────────────

    @Test
    void isBetaEnabled_falseByDefault() {
        assertThat(apiKeyManager.isBetaEnabled()).isFalse();
    }

    @Test
    void saveBetaEnabled_true() throws IOException {
        apiKeyManager.saveBetaEnabled(true);

        assertThat(apiKeyManager.isBetaEnabled()).isTrue();
    }

    @Test
    void saveBetaEnabled_false() throws IOException {
        apiKeyManager.saveBetaEnabled(true);
        apiKeyManager.saveBetaEnabled(false);

        assertThat(apiKeyManager.isBetaEnabled()).isFalse();
    }

    @Test
    void betaFlag_persistsWithOtherConfig() throws IOException {
        apiKeyManager.saveProvider("openai");
        apiKeyManager.saveBetaEnabled(true);

        assertThat(apiKeyManager.getProvider()).isEqualTo("openai");
        assertThat(apiKeyManager.isBetaEnabled()).isTrue();
    }

    // ── Config file path ─────────────────────────────────────────────

    @Test
    void getConfigFilePath_returnsPath() {
        assertThat(apiKeyManager.getConfigFilePath()).isEqualTo(configFile);
    }
}
