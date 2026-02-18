package eu.eurocoder.sovereigncli.shell;

import eu.eurocoder.sovereigncli.agent.HybridAgentRouter;
import eu.eurocoder.sovereigncli.security.AuditEntry;
import eu.eurocoder.sovereigncli.security.AuditLog;
import eu.eurocoder.sovereigncli.security.PermissionService;
import eu.eurocoder.sovereigncli.security.ToolSecurityManager;
import eu.eurocoder.sovereigncli.security.TrustLevel;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shell commands for security configuration: trust level, sandbox, and audit log.
 * <p>
 * Security configuration changes (trust level, sandbox root/allow/enable/disable)
 * automatically reinitialize the agent router to clear stale chat memory, ensuring
 * the agent does not carry over previous denial decisions.
 */
@ShellComponent
public class SecurityCommands {

    private final ToolSecurityManager securityManager;
    private HybridAgentRouter agentRouter;

    public SecurityCommands(ToolSecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    @Autowired(required = false)
    public void setAgentRouter(HybridAgentRouter agentRouter) {
        this.agentRouter = agentRouter;
    }

    // ── Trust level ──────────────────────────────────────────────────

    @ShellMethod(key = "trust", value = "Show or set the agent trust level (ask-always, ask-destructive, trust-all)")
    public String trust(@ShellOption(defaultValue = "") String level) {
        if (level.isBlank()) {
            return showTrustLevel();
        }
        return setTrustLevel(level);
    }

    private String showTrustLevel() {
        PermissionService permissionService = securityManager.getPermissionService();
        TrustLevel current = permissionService.getTrustLevel();

        String levels = Arrays.stream(TrustLevel.values())
                .map(t -> {
                    String active = (t == current) ? " <-- active" : "";
                    return String.format("    %-20s %s%s",
                            colorize(t.id(), AttributedStyle.GREEN),
                            t.description(),
                            colorize(active, AttributedStyle.YELLOW));
                })
                .collect(Collectors.joining("\n"));

        return String.format("""
                
                  Current trust level: %s
                
                %s
                
                  Usage: trust <level>  (e.g. 'trust ask-always')
                """, colorize(current.id(), AttributedStyle.CYAN), levels);
    }

    private String setTrustLevel(String level) {
        TrustLevel parsed = TrustLevel.fromId(level.trim());
        if (!parsed.id().equalsIgnoreCase(level.trim())) {
            return colorize("Unknown trust level: '" + level + "'. Use: ask-always, ask-destructive, trust-all",
                    AttributedStyle.RED);
        }

        securityManager.getPermissionService().setTrustLevel(parsed);
        resetAgentMemory();
        return colorize("Trust level set to: " + parsed.id(), AttributedStyle.GREEN)
                + "\n  " + parsed.description();
    }

    // ── Sandbox ──────────────────────────────────────────────────────

    @ShellMethod(key = "sandbox", value = "Show or configure the sandbox (on, off, root <path>, allow <path>)")
    public String sandbox(
            @ShellOption(defaultValue = "") String action,
            @ShellOption(defaultValue = "") String arg) {

        String a = action.trim().toLowerCase();

        if (a.isEmpty()) {
            return showSandbox();
        }
        return switch (a) {
            case "on" -> enableSandbox(true);
            case "off" -> enableSandbox(false);
            case "root" -> arg.isBlank()
                    ? colorize("Usage: sandbox root <path>", AttributedStyle.YELLOW)
                    : setSandboxRoot(arg.trim());
            case "allow" -> arg.isBlank()
                    ? colorize("Usage: sandbox allow <path>", AttributedStyle.YELLOW)
                    : addAllowedPath(arg.trim());
            case "reset" -> resetSandbox();
            default -> colorize("Unknown action: '" + a + "'. Use: on, off, root <path>, allow <path>, reset",
                    AttributedStyle.RED);
        };
    }

    private String showSandbox() {
        boolean enabled = securityManager.isSandboxEnabled();
        Path root = securityManager.getSandboxRoot();
        var additional = securityManager.getAdditionalAllowedPaths();

        StringBuilder sb = new StringBuilder("\n");
        sb.append(colorize("  Sandbox: " + (enabled ? "ENABLED" : "DISABLED"), AttributedStyle.CYAN)).append("\n");
        sb.append("  Root:    ").append(root).append("\n");

        if (!additional.isEmpty()) {
            sb.append("  Additional allowed paths:\n");
            additional.stream()
                    .map(p -> "    " + p)
                    .forEach(line -> sb.append(line).append("\n"));
        }

        sb.append("\n");
        sb.append(colorize("  Commands:", AttributedStyle.WHITE)).append("\n");
        sb.append("    sandbox on              — Enable sandbox\n");
        sb.append("    sandbox off             — Disable sandbox\n");
        sb.append("    sandbox root <path>     — Set sandbox root directory\n");
        sb.append("    sandbox allow <path>    — Add an additional allowed path\n");
        sb.append("    sandbox reset           — Reset to defaults\n");
        return sb.toString();
    }

    private String enableSandbox(boolean enabled) {
        securityManager.setSandboxEnabled(enabled);
        resetAgentMemory();
        return colorize("Sandbox " + (enabled ? "enabled" : "disabled"), AttributedStyle.GREEN);
    }

    private String setSandboxRoot(String path) {
        securityManager.setSandboxRoot(Paths.get(path));
        resetAgentMemory();
        return colorize("Sandbox root set to: " + securityManager.getSandboxRoot(), AttributedStyle.GREEN);
    }

    private String addAllowedPath(String path) {
        securityManager.addAllowedPath(Paths.get(path));
        resetAgentMemory();
        return colorize("Added allowed path: " + Paths.get(path).toAbsolutePath().normalize(), AttributedStyle.GREEN);
    }

    private String resetSandbox() {
        securityManager.setSandboxEnabled(true);
        securityManager.setSandboxRoot(Paths.get("").toAbsolutePath());
        securityManager.clearAdditionalAllowedPaths();
        resetAgentMemory();
        return colorize("Sandbox reset to defaults", AttributedStyle.GREEN);
    }

    // ── Audit log ────────────────────────────────────────────────────

    @ShellMethod(key = "audit", value = "View or manage the audit log (show, clear, path)")
    public String audit(@ShellOption(defaultValue = "show") String action) {
        AuditLog auditLog = securityManager.getAuditLog();

        return switch (action.trim().toLowerCase()) {
            case "show" -> showAudit(auditLog);
            case "clear" -> clearAudit(auditLog);
            case "path" -> "  Audit log: " + auditLog.getAuditFilePath();
            case "count" -> "  Audit entries: " + auditLog.getEntryCount();
            default -> colorize("Unknown action: '" + action + "'. Use: show, clear, path, count",
                    AttributedStyle.RED);
        };
    }

    private String showAudit(AuditLog auditLog) {
        List<AuditEntry> entries = auditLog.getRecentEntries(20);
        if (entries.isEmpty()) {
            return "  No audit entries recorded.";
        }

        String table = entries.stream()
                .map(e -> String.format("  %s  %-8s  %-25s  %s",
                        formatTimestamp(e.timestamp()),
                        colorize(e.status(), e.status().equals("ALLOWED") ? AttributedStyle.GREEN : AttributedStyle.RED),
                        e.tool(),
                        formatParams(e.parameters())))
                .collect(Collectors.joining("\n"));

        return String.format("""
                
                  Recent audit entries (newest first):
                  ─────────────────────────────────────────────────────────
                %s
                  ─────────────────────────────────────────────────────────
                  Total entries: %d  |  File: %s
                """, table, auditLog.getEntryCount(), auditLog.getAuditFilePath());
    }

    private String clearAudit(AuditLog auditLog) {
        auditLog.clear();
        return colorize("Audit log cleared.", AttributedStyle.GREEN);
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.length() < 19) return timestamp;
        return timestamp.substring(11, 19);
    }

    private String formatParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        return params.entrySet().stream()
                .map(e -> e.getValue())
                .map(v -> v.length() > 50 ? v.substring(0, 47) + "..." : v)
                .collect(Collectors.joining(", "));
    }

    // ── Security status (composite) ──────────────────────────────────

    @ShellMethod(key = "security", value = "Show the complete security configuration")
    public String security() {
        PermissionService perm = securityManager.getPermissionService();
        AuditLog auditLog = securityManager.getAuditLog();

        return String.format("""
                
                  EuroCoder — Security Configuration
                  ═══════════════════════════════════════
                  Trust level:  %s
                  Sandbox:      %s
                  Sandbox root: %s
                  Audit log:    %s (%d entries)
                  ───────────────────────────────────────
                  Commands:
                    trust [level]       — Show/set trust level
                    sandbox [action]    — Configure sandbox
                    audit [action]      — View/manage audit log
                """,
                perm.getTrustLevel().id(),
                securityManager.isSandboxEnabled() ? "ENABLED" : "DISABLED",
                securityManager.getSandboxRoot(),
                auditLog.getAuditFilePath(),
                auditLog.getEntryCount());
    }

    /**
     * Reinitializes the agent router after security config changes, clearing
     * stale chat memory so the agent doesn't carry over previous denial decisions.
     */
    private void resetAgentMemory() {
        if (agentRouter != null) {
            try {
                agentRouter.reinitialize();
            } catch (Exception e) {
                // Agent may not be fully configured yet (e.g. no API key)
            }
        }
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
