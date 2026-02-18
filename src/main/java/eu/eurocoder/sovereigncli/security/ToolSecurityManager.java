package eu.eurocoder.sovereigncli.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central security orchestrator for all agent tool operations.
 * Combines three security layers:
 * <ol>
 *   <li><b>Sandbox</b> — restricts file/command access to allowed directories</li>
 *   <li><b>Permissions</b> — prompts user approval for destructive operations</li>
 *   <li><b>Audit</b> — logs every tool invocation with timestamps and outcomes</li>
 * </ol>
 * <p>
 * Each tool method in {@code FileSystemTools} calls either
 * {@link #checkFileAccess(String, String)} or {@link #checkCommandAccess(String, String, Path)}
 * before proceeding. If the check returns a non-empty Optional, the operation is
 * blocked and the denial message is returned to the agent.
 */
@Service
public class ToolSecurityManager {

    private static final Logger log = LoggerFactory.getLogger(ToolSecurityManager.class);

    private static final Set<String> DESTRUCTIVE_OPERATIONS = Set.of(
            "deleteFile", "runCommand", "runCommandInDirectory"
    );

    private static final Set<String> WRITE_OPERATIONS = Set.of(
            "writeFile", "appendToFile", "createDirectory"
    );

    private final AuditLog auditLog;
    private final PermissionService permissionService;

    @Value("${eurocoder.security.sandbox.enabled:true}")
    private boolean sandboxEnabled;

    private volatile Path sandboxRoot;
    private final Set<Path> additionalAllowedPaths = ConcurrentHashMap.newKeySet();

    @Autowired
    public ToolSecurityManager(AuditLog auditLog, PermissionService permissionService) {
        this.auditLog = auditLog;
        this.permissionService = permissionService;
        this.sandboxRoot = Paths.get("").toAbsolutePath().normalize();
        log.info("Security manager initialized — sandbox root: {}", sandboxRoot);
    }

    /**
     * Package-private constructor for testing with explicit configuration.
     */
    ToolSecurityManager(AuditLog auditLog, PermissionService permissionService,
                        boolean sandboxEnabled, Path sandboxRoot) {
        this.auditLog = auditLog;
        this.permissionService = permissionService;
        this.sandboxEnabled = sandboxEnabled;
        this.sandboxRoot = sandboxRoot;
    }

    /**
     * Checks whether a file operation is allowed.
     *
     * @param operation the tool method name (e.g. {@code "writeFile"}, {@code "deleteFile"})
     * @param path      the target file path
     * @return empty if allowed; a denial message if blocked
     */
    public Optional<String> checkFileAccess(String operation, String path) {
        Path resolved = Paths.get(path).toAbsolutePath().normalize();

        if (sandboxEnabled && !isWithinSandbox(resolved)) {
            String msg = String.format("SANDBOX BLOCKED: '%s' is outside allowed directory '%s'", path, sandboxRoot);
            auditLog.record(AuditEntry.denied(operation, Map.of("path", path), msg));
            return Optional.of(msg);
        }

        if (requiresPermission(operation)
                && !permissionService.requestPermission(operation, path)) {
            String msg = "DENIED: User declined " + operation + " on '" + path + "'";
            auditLog.record(AuditEntry.denied(operation, Map.of("path", path), msg));
            return Optional.of(msg);
        }

        auditLog.record(AuditEntry.allowed(operation, Map.of("path", path)));
        return Optional.empty();
    }

    /**
     * Checks whether a shell command execution is allowed.
     *
     * @param operation        the tool method name ({@code "runCommand"} or {@code "runCommandInDirectory"})
     * @param command          the shell command string
     * @param workingDirectory the directory to run in, or {@code null} for CWD
     * @return empty if allowed; a denial message if blocked
     */
    public Optional<String> checkCommandAccess(String operation, String command, Path workingDirectory) {
        if (sandboxEnabled && workingDirectory != null) {
            Path resolved = workingDirectory.toAbsolutePath().normalize();
            if (!isWithinSandbox(resolved)) {
                String msg = String.format("SANDBOX BLOCKED: Working directory '%s' is outside sandbox '%s'",
                        workingDirectory, sandboxRoot);
                auditLog.record(AuditEntry.denied(operation, Map.of("command", command), msg));
                return Optional.of(msg);
            }
        }

        if (requiresPermission(operation)
                && !permissionService.requestPermission(operation, command)) {
            String msg = "DENIED: User declined execution of '" + command + "'";
            auditLog.record(AuditEntry.denied(operation, Map.of("command", command), msg));
            return Optional.of(msg);
        }

        auditLog.record(AuditEntry.allowed(operation, Map.of("command", command)));
        return Optional.empty();
    }

    // ── Sandbox configuration ────────────────────────────────────────

    public Path getSandboxRoot() {
        return sandboxRoot;
    }

    public void setSandboxRoot(Path root) {
        this.sandboxRoot = root.toAbsolutePath().normalize();
        log.info("Sandbox root changed to: {}", sandboxRoot);
    }

    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }

    public void setSandboxEnabled(boolean enabled) {
        this.sandboxEnabled = enabled;
        log.info("Sandbox {}", enabled ? "enabled" : "disabled");
    }

    public void addAllowedPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        additionalAllowedPaths.add(normalized);
        log.info("Added allowed path: {}", normalized);
    }

    public Set<Path> getAdditionalAllowedPaths() {
        return Set.copyOf(additionalAllowedPaths);
    }

    public void clearAdditionalAllowedPaths() {
        additionalAllowedPaths.clear();
    }

    // ── Delegated accessors ──────────────────────────────────────────

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    // ── Internal ─────────────────────────────────────────────────────

    boolean isWithinSandbox(Path absolutePath) {
        if (absolutePath.startsWith(sandboxRoot)) {
            return true;
        }
        return additionalAllowedPaths.stream().anyMatch(absolutePath::startsWith);
    }

    private boolean requiresPermission(String operation) {
        TrustLevel trustLevel = permissionService.getTrustLevel();
        return switch (trustLevel) {
            case TRUST_ALL -> false;
            case ASK_DESTRUCTIVE -> DESTRUCTIVE_OPERATIONS.contains(operation);
            case ASK_ALWAYS -> DESTRUCTIVE_OPERATIONS.contains(operation)
                    || WRITE_OPERATIONS.contains(operation);
        };
    }
}
