package dev.aihelpcenter.sovereigncli.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Provider} enum and {@link ModelOption} record.
 */
class ProviderTest {

    // ── Provider.fromId ──────────────────────────────────────────────

    @Test
    void fromId_mistral() {
        assertThat(Provider.fromId("mistral")).isEqualTo(Provider.MISTRAL);
    }

    @Test
    void fromId_ollama() {
        assertThat(Provider.fromId("ollama")).isEqualTo(Provider.OLLAMA);
    }

    @Test
    void fromId_isCaseInsensitive() {
        assertThat(Provider.fromId("MISTRAL")).isEqualTo(Provider.MISTRAL);
        assertThat(Provider.fromId("Ollama")).isEqualTo(Provider.OLLAMA);
        assertThat(Provider.fromId("OLLAMA")).isEqualTo(Provider.OLLAMA);
    }

    @Test
    void fromId_trimsWhitespace() {
        assertThat(Provider.fromId("  mistral  ")).isEqualTo(Provider.MISTRAL);
        assertThat(Provider.fromId("  ollama  ")).isEqualTo(Provider.OLLAMA);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "openai", "anthropic", "  "})
    void fromId_unknownValues_defaultToMistral(String input) {
        assertThat(Provider.fromId(input)).isEqualTo(Provider.MISTRAL);
    }

    // ── Provider properties ──────────────────────────────────────────

    @Test
    void provider_idAndDisplayName() {
        assertThat(Provider.MISTRAL.id()).isEqualTo("mistral");
        assertThat(Provider.MISTRAL.displayName()).isEqualTo("Mistral Cloud API");

        assertThat(Provider.OLLAMA.id()).isEqualTo("ollama");
        assertThat(Provider.OLLAMA.displayName()).isEqualTo("Ollama (Local)");
    }

    // ── ModelOption record ───────────────────────────────────────────

    @Test
    void modelOption_recordAccessors() {
        var option = new ModelOption("model-id", "Model Name", "Description");

        assertThat(option.id()).isEqualTo("model-id");
        assertThat(option.displayName()).isEqualTo("Model Name");
        assertThat(option.description()).isEqualTo("Description");
    }

    @Test
    void modelOption_equality() {
        var a = new ModelOption("id", "name", "desc");
        var b = new ModelOption("id", "name", "desc");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ── Suggestion lists ─────────────────────────────────────────────

    @Test
    void mistralSuggestions_containExpectedModels() {
        var ids = ModelManager.MISTRAL_SUGGESTIONS.stream()
                .map(ModelOption::id)
                .toList();

        assertThat(ids).contains("mistral-large-latest", "codestral-latest");
    }

    @Test
    void ollamaSuggestions_containExpectedModels() {
        var ids = ModelManager.OLLAMA_SUGGESTIONS.stream()
                .map(ModelOption::id)
                .toList();

        assertThat(ids).contains("llama3.1", "qwen3:4b");
    }

    @Test
    void allSuggestions_haveNonBlankFields() {
        for (var model : ModelManager.MISTRAL_SUGGESTIONS) {
            assertThat(model.id()).isNotBlank();
            assertThat(model.displayName()).isNotBlank();
            assertThat(model.description()).isNotBlank();
        }
        for (var model : ModelManager.OLLAMA_SUGGESTIONS) {
            assertThat(model.id()).isNotBlank();
            assertThat(model.displayName()).isNotBlank();
            assertThat(model.description()).isNotBlank();
        }
    }
}
