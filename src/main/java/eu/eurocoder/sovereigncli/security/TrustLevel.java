package eu.eurocoder.sovereigncli.security;

/**
 * Configurable trust levels controlling when the agent must request
 * explicit user approval before performing operations.
 * <ul>
 *   <li>{@link #ASK_ALWAYS} — Prompt before every write and destructive operation</li>
 *   <li>{@link #ASK_DESTRUCTIVE} — Prompt only before destructive operations (default)</li>
 *   <li>{@link #TRUST_ALL} — Never prompt (suitable for automation/scripting)</li>
 * </ul>
 */
public enum TrustLevel {

    ASK_ALWAYS("ask-always", "Prompt before every write and destructive operation"),
    ASK_DESTRUCTIVE("ask-destructive", "Prompt only before destructive operations (default)"),
    TRUST_ALL("trust-all", "Never prompt — trust all agent operations");

    private final String id;
    private final String description;

    TrustLevel(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    /**
     * Resolves a trust level from its string id (case-insensitive, trimmed).
     * Returns {@link #ASK_DESTRUCTIVE} for {@code null}, blank, or unknown values.
     */
    public static TrustLevel fromId(String id) {
        if (id == null || id.isBlank()) {
            return ASK_DESTRUCTIVE;
        }
        String trimmed = id.trim().toLowerCase();
        for (TrustLevel level : values()) {
            if (level.id.equals(trimmed)) {
                return level;
            }
        }
        return ASK_DESTRUCTIVE;
    }
}
