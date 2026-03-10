package eu.eurocoder.sovereigncli.shell;

import eu.eurocoder.sovereigncli.agent.AgentExceptionHandler;
import eu.eurocoder.sovereigncli.agent.HybridAgentRouter;
import eu.eurocoder.sovereigncli.agent.HybridResult;
import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.config.ApiKeyManager;
import eu.eurocoder.sovereigncli.security.PermissionService;
import dev.langchain4j.service.TokenStream;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class AgentCommands {

    private static final int TOOL_ARGS_MAX_DISPLAY = 80;

    private final HybridAgentRouter router;
    private final ModelManager modelManager;
    private final PermissionService permissionService;
    private final ApiKeyManager apiKeyManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentCommands(HybridAgentRouter router, ModelManager modelManager,
                         PermissionService permissionService, ApiKeyManager apiKeyManager) {
        this.router = router;
        this.modelManager = modelManager;
        this.permissionService = permissionService;
        this.apiKeyManager = apiKeyManager;
    }

    @ShellMethod(key = "ask", value = "Ask the Sovereign Agent (auto-routes between Planner and Coder)")
    public String ask(@ShellOption(defaultValue = "Help me understand what you can do") String prompt) {
        permissionService.clearInterrupt();
        System.out.println();

        if (apiKeyManager.isBetaEnabled()) {
            if (modelManager.isAutoMode() && router.isComplexTask(prompt)) {
                return askHybrid(prompt);
            }
            System.out.println();

            return colorize("Agent: ", AttributedStyle.GREEN) + result.toDisplayString();
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED)
                    + AgentExceptionHandler.friendlyMessage(e, modelManager.getProvider());
        }

        return askStandard(prompt);
    }

    @ShellMethod(key = "plan", value = "Force hybrid mode: Planner analyzes, then Coder executes")
    public String plan(@ShellOption(defaultValue = "Help me") String prompt) {
        permissionService.clearInterrupt();
        System.out.println();
        System.out.println(colorize("HYBRID mode — Planner (" + modelManager.getPlannerModelName()
                + ") -> Coder (" + modelManager.getCoderModelName() + ")", AttributedStyle.CYAN));
        System.out.println();

        try {
            HybridResult result = router.chatHybrid(prompt);
            return colorize("Agent: ", AttributedStyle.GREEN) + result.toDisplayString();
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED)
                    + AgentExceptionHandler.friendlyMessage(e, modelManager.getProvider());
        }
    }

    @ShellMethod(key = "code", value = "Generate code directly (uses coder model for speed)")
    public String code(
            @ShellOption(help = "What to build") String description,
            @ShellOption(defaultValue = "", help = "Target file path") String file) {

        permissionService.clearInterrupt();

        String prompt = "Write code for: " + description;
        if (!file.isBlank()) {
            prompt += ". Save it to the file: " + file;
        }

        System.out.println();

        if (apiKeyManager.isBetaEnabled()) {
            System.out.println(colorize(modelManager.getCoderModelName() + " generating code (streaming)...", AttributedStyle.CYAN));
            System.out.println();
            return streamResponse(prompt);
        }

        System.out.println(colorize(modelManager.getCoderModelName() + " generating code...", AttributedStyle.CYAN));
        System.out.println();

        try {
            String response = router.chatDirect(prompt);
            return colorize("Agent: ", AttributedStyle.GREEN) + response;
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

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
