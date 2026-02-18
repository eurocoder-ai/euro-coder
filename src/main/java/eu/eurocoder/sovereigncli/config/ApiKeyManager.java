package eu.eurocoder.sovereigncli.config;

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

/**
 * Manages persistent configuration stored in {@code ~/.eurocoder/config.json}.
 * Handles API keys, provider settings, model mode, and custom model overrides.
 * <p>
 * Override {@link #getConfigDir()} in tests to redirect storage to a temp directory.
 */
@Component
public class ApiKeyManager {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyManager.class);
    private static final Path DEFAULT_CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".eurocoder");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Path getConfigDir() {
        return DEFAULT_CONFIG_DIR;
    }

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
        log.info("API key saved to {}", getConfigFilePath());
    }

    public void clearApiKey() throws IOException {
        Path configFile = getConfigFilePath();
        if (Files.exists(configFile)) {
            Files.delete(configFile);
            log.info("API key cleared from {}", configFile);
        }
    }

    public String getModelMode() {
        return readConfigField("model_mode");
    }

    public void saveModelMode(String mode) throws IOException {
        writeConfigField("model_mode", mode);
        log.info("Model mode saved: {}", mode);
    }

    public String getProvider() {
        return readConfigField("provider");
    }

    public void saveProvider(String provider) throws IOException {
        writeConfigField("provider", provider);
        log.info("Provider saved: {}", provider);
    }

    public String getOllamaBaseUrl() {
        String url = readConfigField("ollama_base_url");
        return (url != null && !url.isBlank()) ? url : "http://localhost:11434";
    }

    public void saveOllamaBaseUrl(String url) throws IOException {
        writeConfigField("ollama_base_url", url);
        log.info("Ollama base URL saved: {}", url);
    }

    public String getTrustLevel() {
        return readConfigField("trust_level");
    }

    public void saveTrustLevel(String trustLevel) throws IOException {
        writeConfigField("trust_level", trustLevel);
        log.info("Trust level saved: {}", trustLevel);
    }

    public boolean getSandboxEnabled() {
        String value = readConfigField("sandbox_enabled");
        return value == null || Boolean.parseBoolean(value);
    }

    public void saveSandboxEnabled(boolean enabled) throws IOException {
        writeConfigField("sandbox_enabled", String.valueOf(enabled));
        log.info("Sandbox enabled saved: {}", enabled);
    }

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
        return getConfigDir().resolve("config.json");
    }

    private String readConfigField(String field) {
        Path configFile = getConfigFilePath();
        if (!Files.exists(configFile)) {
            return null;
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(configFile.toFile());
            if (root.has(field)) {
                String value = root.get(field).asText();
                return value.isBlank() ? null : value;
            }
        } catch (IOException e) {
            log.warn("Failed to read config field '{}' from {}: {}", field, configFile, e.getMessage());
        }
        return null;
    }

    private void writeConfigField(String field, String value) throws IOException {
        Path configDir = getConfigDir();
        Path configFile = getConfigFilePath();
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

    private void restrictPermissions() {
        try {
            Files.setPosixFilePermissions(getConfigFilePath(), Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ));
            Files.setPosixFilePermissions(getConfigDir(), Set.of(
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
