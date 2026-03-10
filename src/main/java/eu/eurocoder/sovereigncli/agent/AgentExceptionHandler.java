package eu.eurocoder.sovereigncli.agent;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.ModelDisabledException;

import java.net.ConnectException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates raw LangChain4j / provider exceptions into clean, actionable
 * messages for the CLI user. Prevents full JSON error bodies and stack traces
 * from leaking into shell output.
 */
public final class AgentExceptionHandler {

    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile(
            "retry\\s+(?:in|after)\\s+(\\d+(?:\\.\\d+)?\\s*s)", Pattern.CASE_INSENSITIVE);

    private AgentExceptionHandler() {}

    public static String friendlyMessage(Exception e, Provider provider) {
        Throwable cause = unwrap(e);

        if (cause instanceof RateLimitException) {
            return formatRateLimit(cause, provider);
        }
        if (cause instanceof AuthenticationException) {
            return formatAuth(provider);
        }
        if (cause instanceof ModelNotFoundException) {
            return "Model not found. Use 'model list' to see available models, "
                    + "or 'model <name>' to switch.";
        }
        if (cause instanceof ContentFilteredException) {
            return "Your request was blocked by " + provider.displayName()
                    + "'s content safety filter. Try rephrasing your prompt.";
        }
        if (cause instanceof TimeoutException) {
            return "Request timed out. The model may be overloaded — try again in a moment.";
        }
        if (cause instanceof InvalidRequestException) {
            return "Invalid request: " + extractShortMessage(cause.getMessage())
                    + "\n  Check your model name and prompt, or try 'model list'.";
        }
        if (cause instanceof InternalServerException) {
            return provider.displayName() + " server error. This is on their end — try again later.";
        }
        if (cause instanceof ModelDisabledException) {
            return "This model has been disabled by " + provider.displayName()
                    + ". Use 'model list' to pick another.";
        }
        if (cause instanceof UnsupportedFeatureException) {
            return "This model doesn't support a required feature (e.g. tool calling). "
                    + "Try a different model with 'model <name>'.";
        }
        if (cause instanceof HttpException) {
            return provider.displayName() + " API error: " + extractShortMessage(cause.getMessage());
        }
        if (cause instanceof LangChain4jException) {
            return "AI error: " + extractShortMessage(cause.getMessage());
        }
        if (cause instanceof ConnectException) {
            return formatConnectionError(provider);
        }
        if (cause instanceof IllegalStateException && cause.getMessage() != null
                && cause.getMessage().contains("API key")) {
            return cause.getMessage();
        }

        return "Unexpected error: " + extractShortMessage(
                cause != null ? cause.getMessage() : e.getMessage());
    }

    private static String formatRateLimit(Throwable cause, Provider provider) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rate limit exceeded for ").append(provider.displayName()).append(".");

        String retryDelay = extractRetryDelay(cause.getMessage());
        if (retryDelay != null) {
            sb.append(" Retry in ~").append(retryDelay).append(".");
        } else {
            sb.append(" Wait a moment and try again.");
        }

        sb.append("\n  Tip: Use 'model list' to check your quota, or switch to a different model/provider.");
        return sb.toString();
    }

    private static String formatAuth(Provider provider) {
        StringBuilder sb = new StringBuilder();
        sb.append("Authentication failed for ").append(provider.displayName()).append(".");
        sb.append("\n  Use 'config-key <key>' to update your API key");
        if (provider.envVarName() != null) {
            sb.append(", or set the ").append(provider.envVarName()).append(" env var");
        }
        sb.append(".");
        return sb.toString();
    }

    private static String formatConnectionError(Provider provider) {
        if (provider == Provider.OLLAMA) {
            return "Cannot connect to Ollama. Make sure it's running: 'ollama serve'";
        }
        return "Cannot reach " + provider.displayName()
                + ". Check your network connection and try again.";
    }

    static String extractRetryDelay(String message) {
        if (message == null) return null;
        Matcher matcher = RETRY_DELAY_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extracts a one-line human-readable message from what might be a
     * multi-line JSON blob. Falls back to the first line capped at 200 chars.
     */
    static String extractShortMessage(String raw) {
        if (raw == null) return "(no details)";

        String cleaned = raw;

        int jsonStart = cleaned.indexOf('{');
        if (jsonStart >= 0) {
            String prefix = cleaned.substring(0, jsonStart).trim();
            if (!prefix.isEmpty()) {
                return cap(prefix);
            }

            int msgIdx = cleaned.indexOf("\"message\"");
            if (msgIdx >= 0) {
                int colonIdx = cleaned.indexOf(':', msgIdx);
                if (colonIdx >= 0) {
                    int quoteStart = cleaned.indexOf('"', colonIdx + 1);
                    int quoteEnd = cleaned.indexOf('"', quoteStart + 1);
                    if (quoteStart >= 0 && quoteEnd > quoteStart) {
                        return cap(cleaned.substring(quoteStart + 1, quoteEnd));
                    }
                }
            }
        }

        String firstLine = cleaned.lines().findFirst().orElse(cleaned);
        return cap(firstLine);
    }

    private static final int MAX_MESSAGE_LENGTH = 200;

    private static String cap(String s) {
        String trimmed = s.trim();
        if (trimmed.length() <= MAX_MESSAGE_LENGTH) return trimmed;
        return trimmed.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            if (current.getCause() instanceof LangChain4jException
                    || current.getCause() instanceof ConnectException) {
                return current.getCause();
            }
            current = current.getCause();
        }
        if (t.getCause() != null && t.getCause() != t) {
            return t.getCause();
        }
        return t;
    }
}
