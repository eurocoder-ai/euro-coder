package eu.eurocoder.sovereigncli.shell;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.eurocoder.sovereigncli.agent.AgentExceptionHandler;
import eu.eurocoder.sovereigncli.agent.HybridAgentRouter;
import eu.eurocoder.sovereigncli.agent.HybridResult;
import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.config.ApiKeyManager;
import eu.eurocoder.sovereigncli.rag.RagService;
import eu.eurocoder.sovereigncli.security.PermissionService;
import dev.langchain4j.service.TokenStream;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@ShellComponent
public class AgentCommands {

    private static final int TOOL_ARGS_MAX_DISPLAY = 80;

    private final HybridAgentRouter router;
    private final ModelManager modelManager;
    private final PermissionService permissionService;
    private final ApiKeyManager apiKeyManager;
    private final RagService ragService;
    private final MultilineInputReader multilineInputReader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentCommands(HybridAgentRouter router, ModelManager modelManager,
                         PermissionService permissionService, ApiKeyManager apiKeyManager,
                         RagService ragService, MultilineInputReader multilineInputReader) {
        this.router = router;
        this.modelManager = modelManager;
        this.permissionService = permissionService;
        this.apiKeyManager = apiKeyManager;
        this.ragService = ragService;
        this.multilineInputReader = multilineInputReader;
    }

    @ShellMethod(key = "ask", value = "Ask the Sovereign Agent (auto-routes between Planner and Coder)")
    public String ask(@ShellOption(defaultValue = ShellOption.NONE) String prompt) {
        permissionService.clearInterrupt();
        System.out.println();

        String resolvedPrompt = resolvePrompt(prompt, "ask");
        if (resolvedPrompt == null) {
            return colorize("Cancelled.", AttributedStyle.YELLOW);
        }

        if (apiKeyManager.isBetaEnabled()) {
            autoIndexRagIfNeeded();
            if (modelManager.isAutoMode() && router.isComplexTask(resolvedPrompt)) {
                return askHybrid(resolvedPrompt);
            }
            return askStreaming(resolvedPrompt);
        }

        return askStandard(resolvedPrompt);
    }

    @ShellMethod(key = "plan", value = "Force hybrid mode: Planner analyzes, then Coder executes")
    public String plan(@ShellOption(defaultValue = ShellOption.NONE) String prompt) {
        permissionService.clearInterrupt();
        System.out.println();

        String resolvedPrompt = resolvePrompt(prompt, "plan");
        if (resolvedPrompt == null) {
            return colorize("Cancelled.", AttributedStyle.YELLOW);
        }

        System.out.println(colorize("HYBRID mode — Planner (" + modelManager.getPlannerModelName()
                + ") -> Coder (" + modelManager.getCoderModelName() + ")", AttributedStyle.CYAN));
        System.out.println();

        try {
            HybridResult result = router.chatHybrid(resolvedPrompt);
            return formatAgentResponse(result.toDisplayString());
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED)
                    + AgentExceptionHandler.friendlyMessage(e, modelManager.getProvider());
        }
    }

    @ShellMethod(key = "code", value = "Generate code directly (uses coder model for speed)")
    public String code(
            @ShellOption(defaultValue = ShellOption.NONE, help = "What to build") String description,
            @ShellOption(defaultValue = "", help = "Target file path") String file) {

        permissionService.clearInterrupt();

        String resolvedDescription = resolvePrompt(description, "code");
        if (resolvedDescription == null) {
            return colorize("Cancelled.", AttributedStyle.YELLOW);
        }

        String prompt = "Write code for: " + resolvedDescription;
        if (!file.isBlank()) {
            prompt += ". Save it to the file: " + file;
        }

        System.out.println();

        if (apiKeyManager.isBetaEnabled()) {
            autoIndexRagIfNeeded();
            System.out.println(colorize(modelManager.getCoderModelName() + " generating code (streaming)...", AttributedStyle.CYAN));
            System.out.println();
            return streamResponse(prompt);
        }

        System.out.println(colorize(modelManager.getCoderModelName() + " generating code...", AttributedStyle.CYAN));
        System.out.println();

        try {
            String response = router.chatDirect(prompt);
            return formatAgentResponse(response);
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED)
                    + AgentExceptionHandler.friendlyMessage(e, modelManager.getProvider());
        }
    }

    @ShellMethod(key = "ls", value = "List files in a directory")
    public String ls(@ShellOption(defaultValue = ".") String directory) {
        permissionService.clearInterrupt();
        System.out.println();

        try {
            return router.chatDirect("List all files in the directory: " + directory);
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED)
                    + AgentExceptionHandler.friendlyMessage(e, modelManager.getProvider());
        }
    }

    // ── Response formatting ─────────────────────────────────────────

    private String formatAgentResponse(String rawMarkdown) {
        return colorize("Agent: ", AttributedStyle.GREEN)
                + new MarkdownRenderer().render(rawMarkdown);
    }

    // ── Multiline prompt resolution ─────────────────────────────────

    private String resolvePrompt(String prompt, String commandName) {
        if (prompt != null && !prompt.isBlank()) {
            return prompt;
        }
        return multilineInputReader.read(commandName);
    }

    // ── RAG auto-indexing ─────────────────────────────────────────────

    private void autoIndexRagIfNeeded() {
        if (!ragService.isIndexed() && ragService.supportsCurrentProvider()) {
            System.out.println(colorize("  ⟳ Building semantic index...", AttributedStyle.CYAN));
            String result = ragService.indexProject();
            System.out.println(colorize("  " + result, AttributedStyle.CYAN));
            System.out.println();
        }
    }

    // ── Standard (non-streaming) paths ───────────────────────────────

    private String askStandard(String prompt) {
        System.out.println(colorize("Routing request...", AttributedStyle.CYAN));

        try {
            HybridResult result = router.chat(prompt);

            if (result.wasHybrid()) {
                System.out.println(colorize("  -> Planner (" + modelManager.getPlannerModelName() + ") analyzing...", AttributedStyle.YELLOW));
                System.out.println(colorize("  -> Coder (" + modelManager.getCoderModelName() + ") executing...", AttributedStyle.YELLOW));
            } else {
                System.out.println(colorize("  -> Direct mode (" + modelManager.getCoderModelName() + ")", AttributedStyle.YELLOW));
            }
            System.out.println();

            return formatAgentResponse(result.toDisplayString());
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED)
                    + AgentExceptionHandler.friendlyMessage(e, modelManager.getProvider());
        }
    }

    private String askHybrid(String prompt) {
        System.out.println(colorize("Routing request...", AttributedStyle.CYAN));
        System.out.println(colorize("  -> Planner (" + modelManager.getPlannerModelName() + ") analyzing...", AttributedStyle.YELLOW));
        System.out.println(colorize("  -> Coder (" + modelManager.getCoderModelName() + ") executing...", AttributedStyle.YELLOW));
        System.out.println();

        try {
            HybridResult result = router.chatHybrid(prompt);
            return formatAgentResponse(result.toDisplayString());
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED)
                    + AgentExceptionHandler.friendlyMessage(e, modelManager.getProvider());
        }
    }

    // ── Streaming path (beta) ────────────────────────────────────────

    private String askStreaming(String prompt) {
        System.out.println(colorize("  -> Streaming (" + modelManager.getCoderModelName() + ")", AttributedStyle.YELLOW));
        System.out.println();
        return streamResponse(prompt);
    }

    private String streamResponse(String prompt) {
        System.out.print(colorize("Agent: ", AttributedStyle.GREEN));
        System.out.flush();

        MarkdownRenderer md = new MarkdownRenderer();
        StringBuilder lineBuffer = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();

        try {
            TokenStream stream = router.chatDirectStreaming(prompt);
            stream
                    .onPartialResponse(token -> {
                        lineBuffer.append(token);
                        flushCompleteLines(lineBuffer, md);
                    })
                    .onToolExecuted(execution -> {
                        flushRemainder(lineBuffer, md);
                        String name = execution.request().name();
                        String args = formatToolArgs(execution.request().arguments());
                        System.out.println();
                        System.out.print(colorize("  ⚡ " + name, AttributedStyle.CYAN)
                                + colorize(args, AttributedStyle.WHITE));
                        System.out.println();
                        System.out.flush();
                    })
                    .onCompleteResponse(response -> {
                        flushRemainder(lineBuffer, md);
                        System.out.println();
                        latch.countDown();
                    })
                    .onError(error -> {
                        Exception ex = error instanceof Exception
                                ? (Exception) error
                                : new RuntimeException(error);
                        errorRef.set(AgentExceptionHandler.friendlyMessage(ex, modelManager.getProvider()));
                        latch.countDown();
                    })
                    .start();

            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return colorize("\nInterrupted.", AttributedStyle.YELLOW);
        } catch (Exception e) {
            return colorize("\nError: ", AttributedStyle.RED)
                    + AgentExceptionHandler.friendlyMessage(e, modelManager.getProvider());
        }

        if (errorRef.get() != null) {
            return colorize("\nError: ", AttributedStyle.RED) + errorRef.get();
        }
        return "";
    }

    private void flushCompleteLines(StringBuilder buffer, MarkdownRenderer md) {
        int newlineIdx;
        while ((newlineIdx = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, newlineIdx);
            buffer.delete(0, newlineIdx + 1);
            System.out.println(md.renderLine(line));
        }
        System.out.flush();
    }

    private void flushRemainder(StringBuilder buffer, MarkdownRenderer md) {
        if (!buffer.isEmpty()) {
            System.out.print(md.renderLine(buffer.toString()));
            buffer.setLength(0);
            System.out.flush();
        }
    }

    private String formatToolArgs(String jsonArgs) {
        if (jsonArgs == null || jsonArgs.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(jsonArgs);
            StringBuilder sb = new StringBuilder("(");
            var it = node.fields();
            while (it.hasNext()) {
                if (sb.length() > 1) sb.append(", ");
                var entry = it.next();
                String val = entry.getValue().asText();
                if (val.length() > TOOL_ARGS_MAX_DISPLAY) {
                    val = val.substring(0, TOOL_ARGS_MAX_DISPLAY - 3) + "...";
                }
                sb.append(val);
            }
            sb.append(")");
            return sb.toString();
        } catch (Exception e) {
            String raw = jsonArgs.length() > TOOL_ARGS_MAX_DISPLAY
                    ? jsonArgs.substring(0, TOOL_ARGS_MAX_DISPLAY - 3) + "..."
                    : jsonArgs;
            return "(" + raw + ")";
        }
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
