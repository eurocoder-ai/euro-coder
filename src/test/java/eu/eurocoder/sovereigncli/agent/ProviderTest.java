package eu.eurocoder.sovereigncli.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

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
    void fromId_openai() {
        assertThat(Provider.fromId("openai")).isEqualTo(Provider.OPENAI);
    }

    @Test
    void fromId_anthropic() {
        assertThat(Provider.fromId("anthropic")).isEqualTo(Provider.ANTHROPIC);
    }

    @Test
    void fromId_google() {
        assertThat(Provider.fromId("google")).isEqualTo(Provider.GOOGLE_GEMINI);
    }

    @Test
    void fromId_xai() {
        assertThat(Provider.fromId("xai")).isEqualTo(Provider.XAI);
    }

    @Test
    void fromId_deepseek() {
        assertThat(Provider.fromId("deepseek")).isEqualTo(Provider.DEEPSEEK);
    }

    @Test
    void fromId_isCaseInsensitive() {
        assertThat(Provider.fromId("MISTRAL")).isEqualTo(Provider.MISTRAL);
        assertThat(Provider.fromId("Ollama")).isEqualTo(Provider.OLLAMA);
        assertThat(Provider.fromId("OPENAI")).isEqualTo(Provider.OPENAI);
        assertThat(Provider.fromId("Anthropic")).isEqualTo(Provider.ANTHROPIC);
        assertThat(Provider.fromId("GOOGLE")).isEqualTo(Provider.GOOGLE_GEMINI);
        assertThat(Provider.fromId("XAI")).isEqualTo(Provider.XAI);
        assertThat(Provider.fromId("DEEPSEEK")).isEqualTo(Provider.DEEPSEEK);
    }

    @Test
    void fromId_trimsWhitespace() {
        assertThat(Provider.fromId("  mistral  ")).isEqualTo(Provider.MISTRAL);
        assertThat(Provider.fromId("  openai  ")).isEqualTo(Provider.OPENAI);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "chatgpt", "  "})
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

        assertThat(Provider.OPENAI.id()).isEqualTo("openai");
        assertThat(Provider.OPENAI.displayName()).isEqualTo("OpenAI");

        assertThat(Provider.ANTHROPIC.id()).isEqualTo("anthropic");
        assertThat(Provider.ANTHROPIC.displayName()).isEqualTo("Anthropic");

        assertThat(Provider.GOOGLE_GEMINI.id()).isEqualTo("google");
        assertThat(Provider.GOOGLE_GEMINI.displayName()).isEqualTo("Google Gemini");

        assertThat(Provider.XAI.id()).isEqualTo("xai");
        assertThat(Provider.XAI.displayName()).isEqualTo("xAI (Grok)");

        assertThat(Provider.DEEPSEEK.id()).isEqualTo("deepseek");
        assertThat(Provider.DEEPSEEK.displayName()).isEqualTo("DeepSeek");
    }

    @Test
    void cloudProviders_requireApiKey() {
        assertThat(Provider.MISTRAL.requiresApiKey()).isTrue();
        assertThat(Provider.OPENAI.requiresApiKey()).isTrue();
        assertThat(Provider.ANTHROPIC.requiresApiKey()).isTrue();
        assertThat(Provider.GOOGLE_GEMINI.requiresApiKey()).isTrue();
        assertThat(Provider.XAI.requiresApiKey()).isTrue();
        assertThat(Provider.DEEPSEEK.requiresApiKey()).isTrue();
    }

    @Test
    void ollama_doesNotRequireApiKey() {
        assertThat(Provider.OLLAMA.requiresApiKey()).isFalse();
        assertThat(Provider.OLLAMA.envVarName()).isNull();
        assertThat(Provider.OLLAMA.configField()).isNull();
    }

    @Test
    void cloudProviders_haveEnvVarAndConfigField() {
        for (Provider p : Provider.values()) {
            if (p.requiresApiKey()) {
                assertThat(p.envVarName()).as(p.id() + " envVarName").isNotBlank();
                assertThat(p.configField()).as(p.id() + " configField").isNotBlank();
            }
        }
    }

    @Test
    void allProviders_haveDefaultBaseUrl() {
        for (Provider p : Provider.values()) {
            assertThat(p.defaultBaseUrl()).as(p.id() + " defaultBaseUrl").isNotBlank();
        }
    }

    @Test
    void openAiCompatible_flagIsCorrect() {
        assertThat(Provider.OPENAI.isOpenAiCompatible()).isTrue();
        assertThat(Provider.XAI.isOpenAiCompatible()).isTrue();
        assertThat(Provider.DEEPSEEK.isOpenAiCompatible()).isTrue();

        assertThat(Provider.MISTRAL.isOpenAiCompatible()).isFalse();
        assertThat(Provider.OLLAMA.isOpenAiCompatible()).isFalse();
        assertThat(Provider.ANTHROPIC.isOpenAiCompatible()).isFalse();
        assertThat(Provider.GOOGLE_GEMINI.isOpenAiCompatible()).isFalse();
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
    void openaiSuggestions_containExpectedModels() {
        var ids = ModelManager.OPENAI_SUGGESTIONS.stream()
                .map(ModelOption::id)
                .toList();

        assertThat(ids).contains("gpt-4o", "gpt-4o-mini");
    }

    @Test
    void anthropicSuggestions_containExpectedModels() {
        var ids = ModelManager.ANTHROPIC_SUGGESTIONS.stream()
                .map(ModelOption::id)
                .toList();

        assertThat(ids).contains("claude-sonnet-4-20250514");
    }

    @Test
    void allSuggestionLists_haveNonBlankFields() {
        var allSuggestions = java.util.List.of(
                ModelManager.MISTRAL_SUGGESTIONS,
                ModelManager.OLLAMA_SUGGESTIONS,
                ModelManager.OPENAI_SUGGESTIONS,
                ModelManager.ANTHROPIC_SUGGESTIONS,
                ModelManager.GOOGLE_GEMINI_SUGGESTIONS,
                ModelManager.XAI_SUGGESTIONS,
                ModelManager.DEEPSEEK_SUGGESTIONS
        );

        for (var suggestions : allSuggestions) {
            assertThat(suggestions).isNotEmpty();
            for (var model : suggestions) {
                assertThat(model.id()).isNotBlank();
                assertThat(model.displayName()).isNotBlank();
                assertThat(model.description()).isNotBlank();
            }
        }
    }
}
