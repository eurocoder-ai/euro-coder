package dev.aihelpcenter.sovereigncli.agent;

/**
 * A selectable AI model entry used in suggestion lists and dynamic model queries.
 *
 * @param id          the model identifier sent to the API (e.g. {@code "mistral-large-latest"})
 * @param displayName a human-readable label for the model
 * @param description a short one-liner describing capabilities or size
 */
public record ModelOption(String id, String displayName, String description) {}
