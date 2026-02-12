package dev.aihelpcenter.sovereigncli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

@Component
public class ApiKeyManager {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyManager.class);
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".eurocoder");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getApiKey() {
        String envKey = System.getenv("MISTRAL_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }

        return readConfigField("mistral_api_key");
    }

    public boolean hasApiKey() {
        String key = getApiKey();
        return key != null && !key.isBlank();
    }

    public void saveApiKey(String apiKey) throws IOException {
        writeConfigField("mistral_api_key", apiKey.trim());
        restrictPermissions();
        log.info("API key saved to {}", CONFIG_FILE);
    }

    public void clearApiKey() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            Files.delete(CONFIG_FILE);
            log.info("API key cleared from {}", CONFIG_FILE);
        }
    }

    public String getModelMode() {
        return readConfigField("model_mode");
    }

    public void saveModelMode(String mode) throws IOException {
        writeConfigField("model_mode", mode);
        log.info("Model mode saved: {}", mode);
    }

    // ── Provider (mistral / ollama) ──────────────────────────────────

    public String getProvider() {
        return readConfigField("provider");
    }

    public void saveProvider(String provider) throws IOException {
        writeConfigField("provider", provider);
        log.info("Provider saved: {}", provider);
    }

    // ── Ollama settings ──────────────────────────────────────────────

    public String getOllamaBaseUrl() {
        String url = readConfigField("ollama_base_url");
        return (url != null && !url.isBlank()) ? url : "http://localhost:11434";
    }

    public void saveOllamaBaseUrl(String url) throws IOException {
        writeConfigField("ollama_base_url", url);
        log.info("Ollama base URL saved: {}", url);
    }

    // ── Custom planner/coder model overrides ────────────────────────

    public String getCustomPlannerModel() {
        return readConfigField("custom_planner_model");
    }

    public void saveCustomPlannerModel(String model) throws IOException {
        writeConfigField("custom_planner_model", model);
        log.info("Custom planner model saved: {}", model);
    }

    public String getCustomCoderModel() {
        return readConfigField("custom_coder_model");
    }

    public void saveCustomCoderModel(String model) throws IOException {
        writeConfigField("custom_coder_model", model);
        log.info("Custom coder model saved: {}", model);
    }

    public void clearCustomModels() throws IOException {
        writeConfigField("custom_planner_model", "");
        writeConfigField("custom_coder_model", "");
        log.info("Custom model overrides cleared");
    }

    public Path getConfigFilePath() {
        return CONFIG_FILE;
    }

    private String readConfigField(String field) {
        if (!Files.exists(CONFIG_FILE)) {
            return null;
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(CONFIG_FILE.toFile());
            if (root.has(field)) {
                String value = root.get(field).asText();
                return value.isBlank() ? null : value;
            }
        } catch (IOException e) {
            log.warn("Failed to read config field '{}' from {}: {}", field, CONFIG_FILE, e.getMessage());
        }
        return null;
    }

    private void writeConfigField(String field, String value) throws IOException {
        Files.createDirectories(CONFIG_DIR);

        ObjectNode root;
        if (Files.exists(CONFIG_FILE)) {
            root = (ObjectNode) objectMapper.readTree(CONFIG_FILE.toFile());
        } else {
            root = objectMapper.createObjectNode();
        }

        root.put(field, value);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE.toFile(), root);
    }

    private void restrictPermissions() {
        try {
            Files.setPosixFilePermissions(CONFIG_FILE, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ));
            Files.setPosixFilePermissions(CONFIG_DIR, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ));
        } catch (UnsupportedOperationException e) {
            log.debug("POSIX permissions not supported on this OS");
        } catch (IOException e) {
            log.warn("Failed to set file permissions: {}", e.getMessage());
        }
    }
}
