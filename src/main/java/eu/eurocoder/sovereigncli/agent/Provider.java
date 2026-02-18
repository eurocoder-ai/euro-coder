package eu.eurocoder.sovereigncli.agent;

/**
 * Supported AI provider backends.
 * <ul>
 *   <li>{@link #MISTRAL} — European cloud API (requires API key)</li>
 *   <li>{@link #OLLAMA} — 100 % local / offline via Ollama</li>
 * </ul>
 */
public enum Provider {

    MISTRAL("mistral", "Mistral Cloud API"),
    OLLAMA("ollama", "Ollama (Local)");

    private final String id;
    private final String displayName;

    Provider(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    /**
     * Resolves a provider from its string id (case-insensitive, trimmed).
     * Returns {@link #MISTRAL} for {@code null}, blank, or unknown values.
     */
    public static Provider fromId(String id) {
        if (id == null) return MISTRAL;
        for (Provider p : values()) {
            if (p.id.equalsIgnoreCase(id.trim())) return p;
        }
        return MISTRAL;
    }
}
