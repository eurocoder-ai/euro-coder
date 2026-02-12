package dev.aihelpcenter.sovereigncli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiKeyManager} — config file persistence, API key management,
 * provider/model settings.
 * <p>
 * Uses a {@link TestableApiKeyManager} subclass that overrides the config paths
 * to a temp directory, ensuring tests don't touch the real ~/.eurocoder/config.json.
 */
class ApiKeyManagerTest {

    @TempDir
    Path tempDir;

    private TestableApiKeyManager apiKeyManager;
    private Path configFile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * A test-friendly ApiKeyManager that writes to a custom config directory
     * instead of ~/.eurocoder/.
     */
    static class TestableApiKeyManager extends ApiKeyManager {
        private final Path configDir;
        private final Path configFile;
        private final ObjectMapper objectMapper = new ObjectMapper();

        TestableApiKeyManager(Path configDir) {
            this.configDir = configDir;
            this.configFile = configDir.resolve("config.json");
        }

        @Override
        public Path getConfigFilePath() {
            return configFile;
        }

        @Override
        public String getApiKey() {
            // Skip env var check in tests — only read from file
            return readField("mistral_api_key");
        }

        @Override
        public boolean hasApiKey() {
            String key = getApiKey();
            return key != null && !key.isBlank();
        }

        @Override
        public void saveApiKey(String apiKey) throws IOException {
            writeField("mistral_api_key", apiKey.trim());
        }

        @Override
        public void clearApiKey() throws IOException {
            if (Files.exists(configFile)) {
                Files.delete(configFile);
            }
        }

        @Override
        public String getModelMode() {
            return readField("model_mode");
        }

        @Override
        public void saveModelMode(String mode) throws IOException {
            writeField("model_mode", mode);
        }

        @Override
        public String getProvider() {
            return readField("provider");
        }

        @Override
        public void saveProvider(String provider) throws IOException {
            writeField("provider", provider);
        }

        @Override
        public String getOllamaBaseUrl() {
            String url = readField("ollama_base_url");
            return (url != null && !url.isBlank()) ? url : "http://localhost:11434";
        }

        @Override
        public void saveOllamaBaseUrl(String url) throws IOException {
            writeField("ollama_base_url", url);
        }

        @Override
        public String getCustomPlannerModel() {
            return readField("custom_planner_model");
        }

        @Override
        public void saveCustomPlannerModel(String model) throws IOException {
            writeField("custom_planner_model", model);
        }

        @Override
        public String getCustomCoderModel() {
            return readField("custom_coder_model");
        }

        @Override
        public void saveCustomCoderModel(String model) throws IOException {
            writeField("custom_coder_model", model);
        }

        @Override
        public void clearCustomModels() throws IOException {
            writeField("custom_planner_model", "");
            writeField("custom_coder_model", "");
        }

        private String readField(String field) {
            if (!Files.exists(configFile)) return null;
            try {
                ObjectNode root = (ObjectNode) objectMapper.readTree(configFile.toFile());
                if (root.has(field)) {
                    String value = root.get(field).asText();
                    return value.isBlank() ? null : value;
                }
            } catch (IOException e) {
                // ignore in tests
            }
            return null;
        }

        private void writeField(String field, String value) throws IOException {
            Files.createDirectories(configDir);
            ObjectNode root;
            if (Files.exists(configFile)) {
                root = (ObjectNode) objectMapper.readTree(configFile.toFile());
            } else {
                root = objectMapper.createObjectNode();
            }
            root.put(field, value);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
        }
    }

    @BeforeEach
    void setUp() {
        apiKeyManager = new TestableApiKeyManager(tempDir);
        configFile = tempDir.resolve("config.json");
    }

    // ── API Key ──────────────────────────────────────────────────────

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

    // ── Config file path ─────────────────────────────────────────────

    @Test
    void getConfigFilePath_returnsPath() {
        assertThat(apiKeyManager.getConfigFilePath()).isEqualTo(configFile);
    }
}
