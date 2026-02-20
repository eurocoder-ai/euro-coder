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
 * Central security orchestrator combining sandbox, permissions, and audit.
 * Every tool in {@code FileSystemTools} calls {@link #checkFileAccess} or
 * {@link #checkCommandAccess} before proceeding.
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

    private static final String SANDBOX_FILE_BLOCKED_FORMAT =
            "SANDBOX BLOCKED: '%s' is outside allowed directory '%s'";
    private static final String SANDBOX_DIR_BLOCKED_FORMAT =
            "SANDBOX BLOCKED: Working directory '%s' is outside sandbox '%s'";
    private static final String USER_DENIED_FILE_FORMAT = "DENIED: User declined %s on '%s'";
    private static final String USER_DENIED_COMMAND_FORMAT = "DENIED: User declined execution of '%s'";

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

    public ToolSecurityManager(AuditLog auditLog, PermissionService permissionService,
                               boolean sandboxEnabled, Path sandboxRoot) {
        this.auditLog = auditLog;
        this.permissionService = permissionService;
        this.sandboxEnabled = sandboxEnabled;
        this.sandboxRoot = sandboxRoot;
    }

    public Optional<String> checkFileAccess(String operation, String path) {
        Path resolved = Paths.get(path).toAbsolutePath().normalize();

        if (sandboxEnabled && !isWithinSandbox(resolved)) {
            String denial = String.format(SANDBOX_FILE_BLOCKED_FORMAT, path, sandboxRoot);
            auditLog.record(AuditEntry.denied(operation, Map.of("path", path), denial));
            return Optional.of(denial);
        }

        if (requiresPermission(operation)
                && !permissionService.requestPermission(operation, path)) {
            String denial = String.format(USER_DENIED_FILE_FORMAT, operation, path);
            auditLog.record(AuditEntry.denied(operation, Map.of("path", path), denial));
            return Optional.of(denial);
        }

        auditLog.record(AuditEntry.allowed(operation, Map.of("path", path)));
        return Optional.empty();
    }

    public Optional<String> checkCommandAccess(String operation, String command, Path workingDirectory) {
        if (sandboxEnabled && workingDirectory != null) {
            Path resolved = workingDirectory.toAbsolutePath().normalize();
            if (!isWithinSandbox(resolved)) {
                String denial = String.format(SANDBOX_DIR_BLOCKED_FORMAT, workingDirectory, sandboxRoot);
                auditLog.record(AuditEntry.denied(operation, Map.of("command", command), denial));
                return Optional.of(denial);
            }
        }

        if (requiresPermission(operation)
                && !permissionService.requestPermission(operation, command)) {
            String denial = String.format(USER_DENIED_COMMAND_FORMAT, command);
            auditLog.record(AuditEntry.denied(operation, Map.of("command", command), denial));
            return Optional.of(denial);
        }

        auditLog.record(AuditEntry.allowed(operation, Map.of("command", command)));
        return Optional.empty();
    }

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

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

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
