package eu.eurocoder.sovereigncli.agent;

/**
 * Supported AI provider backends. Each provider carries its own metadata
 * for API key resolution (environment variable + config field) and default base URL.
 */
public enum Provider {

    MISTRAL("mistral", "Mistral Cloud API",
            "MISTRAL_API_KEY", "mistral_api_key",
            "https://api.mistral.ai/v1", true),

    OLLAMA("ollama", "Ollama (Local)",
            null, null,
            "http://localhost:11434", false),

    OPENAI("openai", "OpenAI",
            "OPENAI_API_KEY", "openai_api_key",
            "https://api.openai.com/v1", true),

    ANTHROPIC("anthropic", "Anthropic",
            "ANTHROPIC_API_KEY", "anthropic_api_key",
            "https://api.anthropic.com", true),

    GOOGLE_GEMINI("google", "Google Gemini",
            "GOOGLE_API_KEY", "google_api_key",
            "https://generativelanguage.googleapis.com", true),

    XAI("xai", "xAI (Grok)",
            "XAI_API_KEY", "xai_api_key",
            "https://api.x.ai/v1", true),

    DEEPSEEK("deepseek", "DeepSeek",
            "DEEPSEEK_API_KEY", "deepseek_api_key",
            "https://api.deepseek.com", true);

    private final String id;
    private final String displayName;
    private final String envVarName;
    private final String configField;
    private final String defaultBaseUrl;
    private final boolean requiresApiKey;

    Provider(String id, String displayName, String envVarName, String configField,
             String defaultBaseUrl, boolean requiresApiKey) {
        this.id = id;
        this.displayName = displayName;
        this.envVarName = envVarName;
        this.configField = configField;
        this.defaultBaseUrl = defaultBaseUrl;
        this.requiresApiKey = requiresApiKey;
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    public String envVarName() { return envVarName; }

    public String configField() { return configField; }

    public String defaultBaseUrl() { return defaultBaseUrl; }

    public boolean requiresApiKey() { return requiresApiKey; }

    /**
     * Whether this provider uses an OpenAI-compatible API
     * (same /models endpoint format and chat completions protocol).
     */
    public boolean isOpenAiCompatible() {
        return this == OPENAI || this == XAI || this == DEEPSEEK;
    }

    public static Provider fromId(String id) {
        if (id == null) return MISTRAL;
        for (Provider p : values()) {
            if (p.id.equalsIgnoreCase(id.trim())) return p;
        }
        return MISTRAL;
    }
}
