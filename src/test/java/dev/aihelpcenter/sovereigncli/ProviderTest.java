package dev.aihelpcenter.sovereigncli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ModelManager.Provider} enum and {@link ModelManager.ModelOption} record.
 */
class ProviderTest {

    // ── Provider.fromId ──────────────────────────────────────────────

    @Test
    void fromId_mistral() {
        assertThat(ModelManager.Provider.fromId("mistral")).isEqualTo(ModelManager.Provider.MISTRAL);
    }

    @Test
    void fromId_ollama() {
        assertThat(ModelManager.Provider.fromId("ollama")).isEqualTo(ModelManager.Provider.OLLAMA);
    }

    @Test
    void fromId_isCaseInsensitive() {
        assertThat(ModelManager.Provider.fromId("MISTRAL")).isEqualTo(ModelManager.Provider.MISTRAL);
        assertThat(ModelManager.Provider.fromId("Ollama")).isEqualTo(ModelManager.Provider.OLLAMA);
        assertThat(ModelManager.Provider.fromId("OLLAMA")).isEqualTo(ModelManager.Provider.OLLAMA);
    }

    @Test
    void fromId_trimsWhitespace() {
        assertThat(ModelManager.Provider.fromId("  mistral  ")).isEqualTo(ModelManager.Provider.MISTRAL);
        assertThat(ModelManager.Provider.fromId("  ollama  ")).isEqualTo(ModelManager.Provider.OLLAMA);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "openai", "anthropic", "  "})
    void fromId_unknownValues_defaultToMistral(String input) {
        assertThat(ModelManager.Provider.fromId(input)).isEqualTo(ModelManager.Provider.MISTRAL);
    }

    // ── Provider properties ──────────────────────────────────────────

    @Test
    void provider_idAndDisplayName() {
        assertThat(ModelManager.Provider.MISTRAL.id()).isEqualTo("mistral");
        assertThat(ModelManager.Provider.MISTRAL.displayName()).isEqualTo("Mistral Cloud API");

        assertThat(ModelManager.Provider.OLLAMA.id()).isEqualTo("ollama");
        assertThat(ModelManager.Provider.OLLAMA.displayName()).isEqualTo("Ollama (Local)");
    }

    // ── ModelOption record ───────────────────────────────────────────

    @Test
    void modelOption_recordAccessors() {
        var option = new ModelManager.ModelOption("model-id", "Model Name", "Description");

        assertThat(option.id()).isEqualTo("model-id");
        assertThat(option.displayName()).isEqualTo("Model Name");
        assertThat(option.description()).isEqualTo("Description");
    }

    @Test
    void modelOption_equality() {
        var a = new ModelManager.ModelOption("id", "name", "desc");
        var b = new ModelManager.ModelOption("id", "name", "desc");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ── Suggestion lists ─────────────────────────────────────────────

    @Test
    void mistralSuggestions_containExpectedModels() {
        var ids = ModelManager.MISTRAL_SUGGESTIONS.stream()
                .map(ModelManager.ModelOption::id)
                .toList();

        assertThat(ids).contains("mistral-large-latest", "codestral-latest");
    }

    @Test
    void ollamaSuggestions_containExpectedModels() {
        var ids = ModelManager.OLLAMA_SUGGESTIONS.stream()
                .map(ModelManager.ModelOption::id)
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
