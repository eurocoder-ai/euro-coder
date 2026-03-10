package eu.eurocoder.sovereigncli.agent;

import eu.eurocoder.sovereigncli.config.ApiKeyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ModelManager} — provider switching, model mode logic,
 * custom planner/coder overrides, and model name resolution for all providers.
 */
@ExtendWith(MockitoExtension.class)
class ModelManagerTest {

    @Mock
    private ApiKeyManager apiKeyManager;

    private ModelManager modelManager;

    @BeforeEach
    void setUp() {
        when(apiKeyManager.getProvider()).thenReturn(null);
        when(apiKeyManager.getModelMode()).thenReturn(null);
        when(apiKeyManager.getCustomPlannerModel()).thenReturn(null);
        when(apiKeyManager.getCustomCoderModel()).thenReturn(null);

        modelManager = new ModelManager(apiKeyManager);
    }

    // ── Default state ────────────────────────────────────────────────

    @Test
    void defaultProvider_isMistral() {
        assertThat(modelManager.getProvider()).isEqualTo(Provider.MISTRAL);
        assertThat(modelManager.isMistral()).isTrue();
        assertThat(modelManager.isOllama()).isFalse();
    }

    @Test
    void defaultMode_isAuto() {
        assertThat(modelManager.getCurrentMode()).isEqualTo("auto");
        assertThat(modelManager.isAutoMode()).isTrue();
    }

    @Test
    void defaultPlannerModel_mistral_isMistralLarge() {
        assertThat(modelManager.getPlannerModelName()).isEqualTo("mistral-large-latest");
    }

    @Test
    void defaultCoderModel_mistral_isCodestral() {
        assertThat(modelManager.getCoderModelName()).isEqualTo("codestral-latest");
    }

    @Test
    void defaultHasNoCustomModels() {
        assertThat(modelManager.hasCustomModels()).isFalse();
    }

    // ── Constructor with saved state ─────────────────────────────────

    @Test
    void constructor_loadsPersistedProvider() {
        when(apiKeyManager.getProvider()).thenReturn("ollama");
        ModelManager mgr = new ModelManager(apiKeyManager);

        assertThat(mgr.getProvider()).isEqualTo(Provider.OLLAMA);
        assertThat(mgr.isOllama()).isTrue();
    }

    @Test
    void constructor_loadsPersistedProvider_openai() {
        when(apiKeyManager.getProvider()).thenReturn("openai");
        ModelManager mgr = new ModelManager(apiKeyManager);

        assertThat(mgr.getProvider()).isEqualTo(Provider.OPENAI);
    }

    @Test
    void constructor_loadsPersistedProvider_anthropic() {
        when(apiKeyManager.getProvider()).thenReturn("anthropic");
        ModelManager mgr = new ModelManager(apiKeyManager);

        assertThat(mgr.getProvider()).isEqualTo(Provider.ANTHROPIC);
    }

    @Test
    void constructor_loadsPersistedMode() {
        when(apiKeyManager.getModelMode()).thenReturn("codestral-latest");
        ModelManager mgr = new ModelManager(apiKeyManager);

        assertThat(mgr.getCurrentMode()).isEqualTo("codestral-latest");
        assertThat(mgr.isAutoMode()).isFalse();
    }

    @Test
    void constructor_loadsCustomPlannerAndCoder() {
        when(apiKeyManager.getCustomPlannerModel()).thenReturn("my-planner");
        when(apiKeyManager.getCustomCoderModel()).thenReturn("my-coder");
        ModelManager mgr = new ModelManager(apiKeyManager);

        assertThat(mgr.getPlannerModelName()).isEqualTo("my-planner");
        assertThat(mgr.getCoderModelName()).isEqualTo("my-coder");
        assertThat(mgr.hasCustomModels()).isTrue();
    }

    @Test
    void constructor_blankPersistedValues_treatAsDefaults() {
        when(apiKeyManager.getProvider()).thenReturn("   ");
        when(apiKeyManager.getModelMode()).thenReturn("  ");
        when(apiKeyManager.getCustomPlannerModel()).thenReturn("");
        when(apiKeyManager.getCustomCoderModel()).thenReturn("  ");
        ModelManager mgr = new ModelManager(apiKeyManager);

        assertThat(mgr.getProvider()).isEqualTo(Provider.MISTRAL);
        assertThat(mgr.getCurrentMode()).isEqualTo("auto");
        assertThat(mgr.hasCustomModels()).isFalse();
    }

    // ── switchMode ───────────────────────────────────────────────────

    @Test
    void switchMode_changesCurrentMode() throws Exception {
        modelManager.switchMode("codestral-latest");

        assertThat(modelManager.getCurrentMode()).isEqualTo("codestral-latest");
        assertThat(modelManager.isAutoMode()).isFalse();
        verify(apiKeyManager).saveModelMode("codestral-latest");
    }

    @Test
    void switchMode_clearsCustomModels() throws Exception {
        modelManager.setCustomPlannerModel("custom-planner");
        modelManager.switchMode("mistral-small-latest");

        assertThat(modelManager.hasCustomModels()).isFalse();
        verify(apiKeyManager, atLeastOnce()).clearCustomModels();
    }

    @Test
    void switchMode_singleModel_bothNamesAreSame() {
        modelManager.switchMode("mistral-small-latest");

        assertThat(modelManager.getPlannerModelName()).isEqualTo("mistral-small-latest");
        assertThat(modelManager.getCoderModelName()).isEqualTo("mistral-small-latest");
    }

    @Test
    void switchMode_toAuto_restoresDefaults() {
        modelManager.switchMode("mistral-small-latest");
        modelManager.switchMode("auto");

        assertThat(modelManager.isAutoMode()).isTrue();
        assertThat(modelManager.getPlannerModelName()).isEqualTo("mistral-large-latest");
        assertThat(modelManager.getCoderModelName()).isEqualTo("codestral-latest");
    }

    // ── switchProvider ───────────────────────────────────────────────

    @Test
    void switchProvider_toOllama_changesDefaults() throws Exception {
        modelManager.switchProvider(Provider.OLLAMA);

        assertThat(modelManager.isOllama()).isTrue();
        assertThat(modelManager.isAutoMode()).isTrue();
        assertThat(modelManager.getPlannerModelName()).isEqualTo("llama3.1");
        assertThat(modelManager.getCoderModelName()).isEqualTo("qwen3:4b");

        verify(apiKeyManager).saveProvider("ollama");
        verify(apiKeyManager).saveModelMode("auto");
    }

    @Test
    void switchProvider_toOpenAi_changesDefaults() throws Exception {
        modelManager.switchProvider(Provider.OPENAI);

        assertThat(modelManager.getProvider()).isEqualTo(Provider.OPENAI);
        assertThat(modelManager.isAutoMode()).isTrue();
        assertThat(modelManager.getPlannerModelName()).isEqualTo("gpt-4o");
        assertThat(modelManager.getCoderModelName()).isEqualTo("gpt-4o");

        verify(apiKeyManager).saveProvider("openai");
    }

    @Test
    void switchProvider_toAnthropic_changesDefaults() throws Exception {
        modelManager.switchProvider(Provider.ANTHROPIC);

        assertThat(modelManager.getProvider()).isEqualTo(Provider.ANTHROPIC);
        assertThat(modelManager.getPlannerModelName()).isEqualTo("claude-sonnet-4-20250514");
        assertThat(modelManager.getCoderModelName()).isEqualTo("claude-sonnet-4-20250514");

        verify(apiKeyManager).saveProvider("anthropic");
    }

    @Test
    void switchProvider_toGoogleGemini_changesDefaults() throws Exception {
        modelManager.switchProvider(Provider.GOOGLE_GEMINI);

        assertThat(modelManager.getProvider()).isEqualTo(Provider.GOOGLE_GEMINI);
        assertThat(modelManager.getPlannerModelName()).isEqualTo("gemini-2.0-flash");
        assertThat(modelManager.getCoderModelName()).isEqualTo("gemini-2.0-flash");

        verify(apiKeyManager).saveProvider("google");
    }

    @Test
    void switchProvider_toXai_changesDefaults() throws Exception {
        modelManager.switchProvider(Provider.XAI);

        assertThat(modelManager.getProvider()).isEqualTo(Provider.XAI);
        assertThat(modelManager.getPlannerModelName()).isEqualTo("grok-3");
        assertThat(modelManager.getCoderModelName()).isEqualTo("grok-3");

        verify(apiKeyManager).saveProvider("xai");
    }

    @Test
    void switchProvider_toDeepSeek_changesDefaults() throws Exception {
        modelManager.switchProvider(Provider.DEEPSEEK);

        assertThat(modelManager.getProvider()).isEqualTo(Provider.DEEPSEEK);
        assertThat(modelManager.getPlannerModelName()).isEqualTo("deepseek-chat");
        assertThat(modelManager.getCoderModelName()).isEqualTo("deepseek-chat");

        verify(apiKeyManager).saveProvider("deepseek");
    }

    @Test
    void switchProvider_resetsCustomModels() throws Exception {
        modelManager.setCustomPlannerModel("custom");
        modelManager.switchProvider(Provider.OLLAMA);

        assertThat(modelManager.hasCustomModels()).isFalse();
        verify(apiKeyManager, atLeastOnce()).clearCustomModels();
    }

    @Test
    void switchProvider_backToMistral() throws Exception {
        modelManager.switchProvider(Provider.OLLAMA);
        modelManager.switchProvider(Provider.MISTRAL);

        assertThat(modelManager.isMistral()).isTrue();
        assertThat(modelManager.getPlannerModelName()).isEqualTo("mistral-large-latest");
        assertThat(modelManager.getCoderModelName()).isEqualTo("codestral-latest");
    }

    // ── Custom planner/coder ─────────────────────────────────────────

    @Test
    void setCustomPlannerModel_overridesDefault() throws Exception {
        modelManager.setCustomPlannerModel("mistral-nemo");

        assertThat(modelManager.getPlannerModelName()).isEqualTo("mistral-nemo");
        assertThat(modelManager.getCoderModelName()).isEqualTo("codestral-latest");
        assertThat(modelManager.hasCustomModels()).isTrue();
        assertThat(modelManager.isAutoMode()).isTrue();

        verify(apiKeyManager).saveCustomPlannerModel("mistral-nemo");
    }

    @Test
    void setCustomCoderModel_overridesDefault() throws Exception {
        modelManager.setCustomCoderModel("mistral-small-latest");

        assertThat(modelManager.getCoderModelName()).isEqualTo("mistral-small-latest");
        assertThat(modelManager.getPlannerModelName()).isEqualTo("mistral-large-latest");
        assertThat(modelManager.hasCustomModels()).isTrue();

        verify(apiKeyManager).saveCustomCoderModel("mistral-small-latest");
    }

    @Test
    void setCustomPlannerModel_forcesAutoMode() {
        modelManager.switchMode("mistral-small-latest");
        assertThat(modelManager.isAutoMode()).isFalse();

        modelManager.setCustomPlannerModel("llama3.1");
        assertThat(modelManager.isAutoMode()).isTrue();
    }

    @Test
    void setCustomCoderModel_forcesAutoMode() {
        modelManager.switchMode("mistral-small-latest");
        modelManager.setCustomCoderModel("codestral");
        assertThat(modelManager.isAutoMode()).isTrue();
    }

    // ── Ollama model names ───────────────────────────────────────────

    @Test
    void ollamaAutoMode_usesOllamaDefaults() {
        modelManager.switchProvider(Provider.OLLAMA);

        assertThat(modelManager.getPlannerModelName()).isEqualTo("llama3.1");
        assertThat(modelManager.getCoderModelName()).isEqualTo("qwen3:4b");
    }

    @Test
    void ollamaSingleMode_usesSameModel() {
        modelManager.switchProvider(Provider.OLLAMA);
        modelManager.switchMode("mistral-nemo");

        assertThat(modelManager.getPlannerModelName()).isEqualTo("mistral-nemo");
        assertThat(modelManager.getCoderModelName()).isEqualTo("mistral-nemo");
    }

    @Test
    void ollamaCustomPairing() {
        modelManager.switchProvider(Provider.OLLAMA);
        modelManager.setCustomPlannerModel("llama3.1:70b");
        modelManager.setCustomCoderModel("qwen3-coder");

        assertThat(modelManager.getPlannerModelName()).isEqualTo("llama3.1:70b");
        assertThat(modelManager.getCoderModelName()).isEqualTo("qwen3-coder");
    }

    // ── Cross-provider custom pairing ────────────────────────────────

    @Test
    void openAiCustomPairing() {
        modelManager.switchProvider(Provider.OPENAI);
        modelManager.setCustomPlannerModel("o3-mini");
        modelManager.setCustomCoderModel("gpt-4o-mini");

        assertThat(modelManager.getPlannerModelName()).isEqualTo("o3-mini");
        assertThat(modelManager.getCoderModelName()).isEqualTo("gpt-4o-mini");
    }

    // ── isValidModel ─────────────────────────────────────────────────

    @Test
    void isValidModel_acceptsAnyNonBlankString() {
        assertThat(modelManager.isValidModel("anything")).isTrue();
        assertThat(modelManager.isValidModel("custom-model:latest")).isTrue();
        assertThat(modelManager.isValidModel("mistral-large-latest")).isTrue();
    }

    @Test
    void isValidModel_rejectsNullAndBlank() {
        assertThat(modelManager.isValidModel(null)).isFalse();
        assertThat(modelManager.isValidModel("")).isFalse();
        assertThat(modelManager.isValidModel("   ")).isFalse();
    }

    // ── getSuggestedModels ───────────────────────────────────────────

    @Test
    void suggestedModels_mistral_returnsMistralList() {
        assertThat(modelManager.getSuggestedModels()).isEqualTo(ModelManager.MISTRAL_SUGGESTIONS);
    }

    @Test
    void suggestedModels_ollama_returnsOllamaList() {
        modelManager.switchProvider(Provider.OLLAMA);
        assertThat(modelManager.getSuggestedModels()).isEqualTo(ModelManager.OLLAMA_SUGGESTIONS);
    }

    @Test
    void suggestedModels_openai_returnsOpenAiList() {
        modelManager.switchProvider(Provider.OPENAI);
        assertThat(modelManager.getSuggestedModels()).isEqualTo(ModelManager.OPENAI_SUGGESTIONS);
    }

    @Test
    void suggestedModels_anthropic_returnsAnthropicList() {
        modelManager.switchProvider(Provider.ANTHROPIC);
        assertThat(modelManager.getSuggestedModels()).isEqualTo(ModelManager.ANTHROPIC_SUGGESTIONS);
    }

    @Test
    void suggestedModels_google_returnsGoogleList() {
        modelManager.switchProvider(Provider.GOOGLE_GEMINI);
        assertThat(modelManager.getSuggestedModels()).isEqualTo(ModelManager.GOOGLE_GEMINI_SUGGESTIONS);
    }

    @Test
    void suggestedModels_xai_returnsXaiList() {
        modelManager.switchProvider(Provider.XAI);
        assertThat(modelManager.getSuggestedModels()).isEqualTo(ModelManager.XAI_SUGGESTIONS);
    }

    @Test
    void suggestedModels_deepseek_returnsDeepSeekList() {
        modelManager.switchProvider(Provider.DEEPSEEK);
        assertThat(modelManager.getSuggestedModels()).isEqualTo(ModelManager.DEEPSEEK_SUGGESTIONS);
    }

    @Test
    void allSuggestionLists_areNotEmpty() {
        for (Provider p : Provider.values()) {
            modelManager.switchProvider(p);
            assertThat(modelManager.getSuggestedModels())
                    .as("suggestions for " + p.id())
                    .isNotEmpty();
        }
    }

    // ── Dynamic model listing (without real API) ─────────────────────

    @Test
    void getInstalledOllamaModels_whenNotOllamaProvider_returnsEmpty() {
        assertThat(modelManager.getInstalledOllamaModels()).isEmpty();
    }

    @Test
    void getInstalledOllamaModels_whenOllamaNotRunning_returnsEmpty() {
        modelManager.switchProvider(Provider.OLLAMA);
        when(apiKeyManager.getOllamaBaseUrl()).thenReturn("http://localhost:99999");

        assertThat(modelManager.getInstalledOllamaModels()).isEmpty();
    }

    @Test
    void getAvailableModels_anthropic_returnsEmpty() {
        modelManager.switchProvider(Provider.ANTHROPIC);
        assertThat(modelManager.getAvailableModels()).isEmpty();
    }

    @Test
    void getAvailableModels_cloudProvider_withoutApiKey_returnsEmpty() {
        modelManager.switchProvider(Provider.OPENAI);
        when(apiKeyManager.getApiKeyForProvider(Provider.OPENAI)).thenReturn(null);

        assertThat(modelManager.getAvailableModels()).isEmpty();
    }
}
