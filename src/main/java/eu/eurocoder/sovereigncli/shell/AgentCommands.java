package eu.eurocoder.sovereigncli.shell;

import eu.eurocoder.sovereigncli.agent.HybridAgentRouter;
import eu.eurocoder.sovereigncli.agent.HybridResult;
import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.security.PermissionService;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * Shell commands for interacting with the AI agent: ask, plan, code, ls.
 */
@ShellComponent
public class AgentCommands {

    private final HybridAgentRouter router;
    private final ModelManager modelManager;
    private final PermissionService permissionService;

    public AgentCommands(HybridAgentRouter router, ModelManager modelManager,
                         PermissionService permissionService) {
        this.router = router;
        this.modelManager = modelManager;
        this.permissionService = permissionService;
    }

    @ShellMethod(key = "ask", value = "Ask the Sovereign Agent (auto-routes between Planner and Coder)")
    public String ask(@ShellOption(defaultValue = "Help me understand what you can do") String prompt) {
        permissionService.clearInterrupt();
        System.out.println();
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

            return colorize("Agent: ", AttributedStyle.GREEN) + result.toDisplayString();
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED) + e.getMessage();
        }
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
            return colorize("Error: ", AttributedStyle.RED) + e.getMessage();
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
        System.out.println(colorize(modelManager.getCoderModelName() + " generating code...", AttributedStyle.CYAN));
        System.out.println();

        try {
            String response = router.chatDirect(prompt);
            return colorize("Agent: ", AttributedStyle.GREEN) + response;
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED) + e.getMessage();
        }
    }

    @ShellMethod(key = "ls", value = "List files in a directory")
    public String ls(@ShellOption(defaultValue = ".") String directory) {
        permissionService.clearInterrupt();
        System.out.println();

        try {
            return router.chatDirect("List all files in the directory: " + directory);
        } catch (Exception e) {
            return colorize("Error: ", AttributedStyle.RED) + e.getMessage();
        }
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
