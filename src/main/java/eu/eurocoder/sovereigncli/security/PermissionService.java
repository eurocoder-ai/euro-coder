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
 * Prompts the user for permission before agent operations, based on {@link TrustLevel}.
 * <p>
 * Uses JLine's {@link Terminal} inside Spring Shell, falls back to {@link System#console()}
 * for standalone execution, and denies if no interactive input is available (CI/CD).
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private static final String PROMPT_FORMAT = "  \u26a0 SECURITY: Agent wants to %s: %s";
    private static final String CONFIRM_PROMPT = "  Allow? [y/N]: ";

    private final ApiKeyManager apiKeyManager;
    private volatile TrustLevel trustLevel;
    private volatile boolean interrupted;
    private Terminal terminal;
    private LineReader promptReader;

    @Autowired
    public PermissionService(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;
        this.trustLevel = TrustLevel.fromId(apiKeyManager.getTrustLevel());
        log.info("PermissionService initialized — trust level: {}", trustLevel.id());
    }

    public PermissionService(TrustLevel trustLevel) {
        this.apiKeyManager = null;
        this.trustLevel = trustLevel;
    }

    @Autowired(required = false)
    public void setTerminal(Terminal terminal) {
        this.terminal = terminal;
    }

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

            String response = getOrCreatePromptReader().readLine(CONFIRM_PROMPT);

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
        System.out.println();
        System.out.println(formatPrompt(operation, description));
        String response = console.readLine(CONFIRM_PROMPT);

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
        String humanReadableAction = switch (operation) {
            case "deleteFile" -> "DELETE file";
            case "writeFile" -> "WRITE to file";
            case "appendToFile" -> "APPEND to file";
            case "createDirectory" -> "CREATE directory";
            case "runCommand", "runCommandInDirectory" -> "EXECUTE command";
            default -> operation;
        };
        return String.format(PROMPT_FORMAT, humanReadableAction, description);
    }
}
