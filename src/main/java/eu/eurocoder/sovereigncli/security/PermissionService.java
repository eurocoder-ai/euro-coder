package eu.eurocoder.sovereigncli.security;

import eu.eurocoder.sovereigncli.config.ApiKeyManager;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Console;

/**
 * Manages user permission prompts for agent operations based on the
 * configured {@link TrustLevel}.
 * <p>
 * When a destructive or write operation is attempted:
 * <ul>
 *   <li>{@link TrustLevel#TRUST_ALL} — always allows without prompting</li>
 *   <li>{@link TrustLevel#ASK_DESTRUCTIVE} — prompts for destructive ops only</li>
 *   <li>{@link TrustLevel#ASK_ALWAYS} — prompts for all write and destructive ops</li>
 * </ul>
 * <p>
 * Uses JLine's {@link Terminal} for interactive prompting inside Spring Shell.
 * Falls back to {@link System#console()} for standalone execution.
 * If no interactive input is available (e.g. CI/CD), the operation is
 * denied unless trust level is {@link TrustLevel#TRUST_ALL}.
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final ApiKeyManager apiKeyManager;
    private volatile TrustLevel trustLevel;
    private volatile boolean interrupted;
    private Terminal terminal;
    private LineReader promptReader;

    @Autowired
    public PermissionService(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;
        String saved = apiKeyManager.getTrustLevel();
        this.trustLevel = TrustLevel.fromId(saved);
        log.info("PermissionService initialized — trust level: {}", trustLevel.id());
    }

    /**
     * Package-private constructor for testing with a fixed trust level.
     */
    PermissionService(TrustLevel trustLevel) {
        this.apiKeyManager = null;
        this.trustLevel = trustLevel;
    }

    /**
     * Setter injection for JLine Terminal — available inside Spring Shell,
     * null in standalone or test environments.
     */
    @Autowired(required = false)
    public void setTerminal(Terminal terminal) {
        this.terminal = terminal;
    }

    /**
     * Requests user permission for an operation. Returns {@code true} if allowed.
     *
     * @param operation   the tool method name
     * @param description a human-readable description of what will happen
     */
    public boolean requestPermission(String operation, String description) {
        if (trustLevel == TrustLevel.TRUST_ALL) {
            return true;
        }

        if (interrupted) {
            log.info("Auto-denying {} on '{}' — user interrupted this agent turn", operation, description);
            return false;
        }

        if (terminal != null) {
            return promptViaTerminal(operation, description);
        }

        Console console = System.console();
        if (console != null) {
            return promptViaConsole(operation, description, console);
        }

        log.warn("No interactive input available — denying {} on '{}'", operation, description);
        return false;
    }

    /**
     * Resets the interrupt flag. Call at the start of each new agent request
     * so that a previous Ctrl+C does not auto-deny future turns.
     */
    public void clearInterrupt() {
        interrupted = false;
    }

    public TrustLevel getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(TrustLevel level) {
        this.trustLevel = level;
        if (apiKeyManager != null) {
            try {
                apiKeyManager.saveTrustLevel(level.id());
            } catch (Exception e) {
                log.warn("Failed to persist trust level: {}", e.getMessage());
            }
        }
        log.info("Trust level changed to: {}", level.id());
    }

    private boolean promptViaTerminal(String operation, String description) {
        try {
            terminal.writer().println();
            terminal.writer().println(formatPrompt(operation, description));
            terminal.writer().flush();

            LineReader reader = getOrCreatePromptReader();
            String response = reader.readLine("  Allow? [y/N]: ");

            boolean allowed = response != null && response.trim().equalsIgnoreCase("y");
            log.info("Permission {} for {} on '{}'",
                    allowed ? "GRANTED" : "DENIED", operation, description);
            return allowed;
        } catch (UserInterruptException e) {
            interrupted = true;
            log.info("Permission prompt interrupted — denying all remaining operations this turn");
            return false;
        } catch (Exception e) {
            log.warn("Terminal prompt failed — denying {} on '{}': {}", operation, description, e.getMessage());
            return false;
        }
    }

    private boolean promptViaConsole(String operation, String description, Console console) {
        String prompt = formatPrompt(operation, description);
        System.out.println();
        System.out.println(prompt);
        String response = console.readLine("  Allow? [y/N]: ");

        boolean allowed = response != null && response.trim().equalsIgnoreCase("y");
        log.info("Permission {} for {} on '{}'",
                allowed ? "GRANTED" : "DENIED", operation, description);
        return allowed;
    }

    private synchronized LineReader getOrCreatePromptReader() {
        if (promptReader == null) {
            promptReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
        }
        return promptReader;
    }

    private String formatPrompt(String operation, String description) {
        String action = switch (operation) {
            case "deleteFile" -> "DELETE file";
            case "writeFile" -> "WRITE to file";
            case "appendToFile" -> "APPEND to file";
            case "createDirectory" -> "CREATE directory";
            case "runCommand", "runCommandInDirectory" -> "EXECUTE command";
            default -> operation;
        };
        return String.format("  \u26a0 SECURITY: Agent wants to %s: %s", action, description);
    }
}
