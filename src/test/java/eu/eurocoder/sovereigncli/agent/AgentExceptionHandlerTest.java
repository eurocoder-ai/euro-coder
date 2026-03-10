package eu.eurocoder.sovereigncli.agent;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExceptionHandlerTest {

    // ── Rate limit ──────────────────────────────────────────────────

    @Test
    void rateLimitException_showsFriendlyMessage() {
        var ex = new RateLimitException("rate limit exceeded");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.GOOGLE_GEMINI);

        assertThat(msg).contains("Rate limit exceeded");
        assertThat(msg).contains("Google Gemini");
        assertThat(msg).contains("model list");
    }

    @Test
    void rateLimitException_extractsRetryDelay() {
        var ex = new RateLimitException("Quota exceeded. Please retry in 19.268467691s.");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.OPENAI);

        assertThat(msg).contains("Rate limit exceeded");
        assertThat(msg).contains("19.268467691s");
    }

    @Test
    void rateLimitException_wrappedInRuntimeException() {
        var cause = new RateLimitException("too many requests");
        var wrapper = new RuntimeException("wrapped", cause);
        String msg = AgentExceptionHandler.friendlyMessage(wrapper, Provider.MISTRAL);

        assertThat(msg).contains("Rate limit exceeded");
        assertThat(msg).contains("Mistral");
    }

    // ── Authentication ──────────────────────────────────────────────

    @Test
    void authenticationException_suggestsConfigKey() {
        var ex = new AuthenticationException("invalid api key");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.OPENAI);

        assertThat(msg).contains("Authentication failed");
        assertThat(msg).contains("config-key");
        assertThat(msg).contains("OPENAI_API_KEY");
    }

    @Test
    void authenticationException_ollama_noEnvVarHint() {
        var ex = new AuthenticationException("unauthorized");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.OLLAMA);

        assertThat(msg).contains("Authentication failed");
        assertThat(msg).doesNotContain("env var");
    }

    // ── Model not found ─────────────────────────────────────────────

    @Test
    void modelNotFoundException_suggestsModelList() {
        var ex = new ModelNotFoundException("model xyz not found");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.ANTHROPIC);

        assertThat(msg).contains("Model not found");
        assertThat(msg).contains("model list");
    }

    // ── Content filtered ────────────────────────────────────────────

    @Test
    void contentFilteredException_mentionsProvider() {
        var ex = new ContentFilteredException("content blocked");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.OPENAI);

        assertThat(msg).contains("content safety filter");
        assertThat(msg).contains("OpenAI");
        assertThat(msg).contains("rephrasing");
    }

    // ── Timeout ─────────────────────────────────────────────────────

    @Test
    void timeoutException_showsFriendlyMessage() {
        var ex = new TimeoutException("request timed out");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.DEEPSEEK);

        assertThat(msg).contains("timed out");
        assertThat(msg).contains("try again");
    }

    // ── Invalid request ─────────────────────────────────────────────

    @Test
    void invalidRequestException_showsShortMessage() {
        var ex = new InvalidRequestException("invalid model parameter");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.MISTRAL);

        assertThat(msg).contains("Invalid request");
        assertThat(msg).contains("model list");
    }

    // ── Internal server error ───────────────────────────────────────

    @Test
    void internalServerException_blamesProvider() {
        var ex = new InternalServerException("internal error");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.XAI);

        assertThat(msg).contains("xAI (Grok)");
        assertThat(msg).contains("server error");
        assertThat(msg).contains("try again");
    }

    // ── Unsupported feature ─────────────────────────────────────────

    @Test
    void unsupportedFeatureException_suggestsModelSwitch() {
        var ex = new UnsupportedFeatureException("tool calling not supported");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.DEEPSEEK);

        assertThat(msg).contains("tool calling");
        assertThat(msg).contains("model <name>");
    }

    // ── Connection errors ───────────────────────────────────────────

    @Test
    void connectException_ollama_suggestsServe() {
        var cause = new ConnectException("Connection refused");
        var ex = new RuntimeException("wrapped", cause);
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.OLLAMA);

        assertThat(msg).contains("Cannot connect to Ollama");
        assertThat(msg).contains("ollama serve");
    }

    @Test
    void connectException_cloudProvider_suggestsNetwork() {
        var cause = new ConnectException("Connection refused");
        var ex = new RuntimeException("wrapped", cause);
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.OPENAI);

        assertThat(msg).contains("Cannot reach");
        assertThat(msg).contains("network");
    }

    // ── IllegalStateException (missing API key) ─────────────────────

    @Test
    void illegalStateException_apiKeyMessage_passedThrough() {
        var ex = new IllegalStateException("No OpenAI API key configured. Use 'config-key <key>'.");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.OPENAI);

        assertThat(msg).contains("No OpenAI API key configured");
    }

    // ── Generic / unknown exceptions ────────────────────────────────

    @Test
    void unknownException_showsShortMessage() {
        var ex = new RuntimeException("something went wrong");
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.MISTRAL);

        assertThat(msg).contains("Unexpected error");
        assertThat(msg).contains("something went wrong");
    }

    @Test
    void nullMessageException_showsFallback() {
        var ex = new RuntimeException((String) null);
        String msg = AgentExceptionHandler.friendlyMessage(ex, Provider.MISTRAL);

        assertThat(msg).contains("no details");
    }

    // ── extractRetryDelay ───────────────────────────────────────────

    @Test
    void extractRetryDelay_findsSeconds() {
        assertThat(AgentExceptionHandler.extractRetryDelay("Please retry in 19.268467691s."))
                .isEqualTo("19.268467691s");
    }

    @Test
    void extractRetryDelay_findsRetryAfter() {
        assertThat(AgentExceptionHandler.extractRetryDelay("Retry after 5s"))
                .isEqualTo("5s");
    }

    @Test
    void extractRetryDelay_returnsNullWhenNone() {
        assertThat(AgentExceptionHandler.extractRetryDelay("just an error")).isNull();
        assertThat(AgentExceptionHandler.extractRetryDelay(null)).isNull();
    }

    // ── extractShortMessage ─────────────────────────────────────────

    @Test
    void extractShortMessage_fromJsonBlob_extractsMessageField() {
        String json = "{\"error\":{\"code\":429,\"message\":\"Quota exceeded for model\"}}";
        String result = AgentExceptionHandler.extractShortMessage(json);

        assertThat(result).isEqualTo("Quota exceeded for model");
    }

    @Test
    void extractShortMessage_plainText_returnsFirstLine() {
        String result = AgentExceptionHandler.extractShortMessage("simple error message");
        assertThat(result).isEqualTo("simple error message");
    }

    @Test
    void extractShortMessage_longMessage_isTruncated() {
        String longMsg = "x".repeat(300);
        String result = AgentExceptionHandler.extractShortMessage(longMsg);

        assertThat(result.length()).isLessThanOrEqualTo(200);
        assertThat(result).endsWith("...");
    }

    @Test
    void extractShortMessage_null_returnsFallback() {
        assertThat(AgentExceptionHandler.extractShortMessage(null)).isEqualTo("(no details)");
    }

    // ── All providers produce non-empty messages ────────────────────

    @ParameterizedTest
    @EnumSource(Provider.class)
    void allProviders_produceNonEmptyMessages(Provider provider) {
        var ex = new RuntimeException("test error");
        String msg = AgentExceptionHandler.friendlyMessage(ex, provider);

        assertThat(msg).isNotBlank();
    }
}
