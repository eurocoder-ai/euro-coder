package eu.eurocoder.sovereigncli.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToolSecurityManager} — sandbox enforcement, permission integration,
 * and audit logging across file and command operations.
 */
class ToolSecurityManagerTest {

    @TempDir
    Path tempDir;

    private AuditLog auditLog;
    private PermissionService permissionService;
    private ToolSecurityManager securityManager;

    @BeforeEach
    void setUp() {
        auditLog = new AuditLog(tempDir.resolve("audit.jsonl"));
        // TRUST_ALL avoids console prompts in tests
        permissionService = new PermissionService(TrustLevel.TRUST_ALL);
        securityManager = new ToolSecurityManager(auditLog, permissionService, true, tempDir);
    }

    // ── Sandbox: file access ────────────────────────────────────────

    @Test
    void checkFileAccess_withinSandbox_allowed() {
        Path fileInSandbox = tempDir.resolve("src/Main.java");

        Optional<String> result = securityManager.checkFileAccess("writeFile", fileInSandbox.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void checkFileAccess_outsideSandbox_blocked() {
        Optional<String> result = securityManager.checkFileAccess("writeFile", "/etc/hosts");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("SANDBOX BLOCKED");
    }

    @Test
    void checkFileAccess_sandboxDisabled_allowsEverything() {
        securityManager.setSandboxEnabled(false);

        Optional<String> result = securityManager.checkFileAccess("writeFile", "/etc/hosts");

        assertThat(result).isEmpty();
    }

    @Test
    void checkFileAccess_additionalAllowedPath_allowed() {
        Path extraDir = Paths.get("/tmp/extra-allowed");
        securityManager.addAllowedPath(extraDir);

        Optional<String> result = securityManager.checkFileAccess("readFile",
                extraDir.resolve("file.txt").toString());

        assertThat(result).isEmpty();
    }

    // ── Sandbox: command access ─────────────────────────────────────

    @Test
    void checkCommandAccess_withinSandbox_allowed() {
        Optional<String> result = securityManager.checkCommandAccess(
                "runCommandInDirectory", "ls", tempDir);

        assertThat(result).isEmpty();
    }

    @Test
    void checkCommandAccess_outsideSandbox_blocked() {
        Optional<String> result = securityManager.checkCommandAccess(
                "runCommandInDirectory", "ls", Paths.get("/etc"));

        assertThat(result).isPresent();
        assertThat(result.get()).contains("SANDBOX BLOCKED");
    }

    @Test
    void checkCommandAccess_nullWorkingDir_allowed() {
        Optional<String> result = securityManager.checkCommandAccess(
                "runCommand", "echo hello", null);

        assertThat(result).isEmpty();
    }

    // ── Permission integration ──────────────────────────────────────

    @Test
    void checkFileAccess_askDestructive_deleteFile_deniedWithoutConsole() {
        permissionService.setTrustLevel(TrustLevel.ASK_DESTRUCTIVE);
        Path fileInSandbox = tempDir.resolve("to-delete.txt");

        Optional<String> result = securityManager.checkFileAccess("deleteFile", fileInSandbox.toString());

        // No console in tests -> denied
        assertThat(result).isPresent();
        assertThat(result.get()).contains("DENIED");
    }

    @Test
    void checkFileAccess_askDestructive_writeFile_allowed() {
        permissionService.setTrustLevel(TrustLevel.ASK_DESTRUCTIVE);
        Path fileInSandbox = tempDir.resolve("safe-write.txt");

        // Write operations don't require permission in ASK_DESTRUCTIVE mode
        Optional<String> result = securityManager.checkFileAccess("writeFile", fileInSandbox.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void checkFileAccess_askAlways_writeFile_deniedWithoutConsole() {
        permissionService.setTrustLevel(TrustLevel.ASK_ALWAYS);
        Path fileInSandbox = tempDir.resolve("any-write.txt");

        Optional<String> result = securityManager.checkFileAccess("writeFile", fileInSandbox.toString());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("DENIED");
    }

    @Test
    void checkCommandAccess_askDestructive_runCommand_deniedWithoutConsole() {
        permissionService.setTrustLevel(TrustLevel.ASK_DESTRUCTIVE);

        Optional<String> result = securityManager.checkCommandAccess(
                "runCommand", "echo test", null);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("DENIED");
    }

    // ── Audit logging integration ───────────────────────────────────

    @Test
    void checkFileAccess_allowed_createsAuditEntry() {
        securityManager.checkFileAccess("readFile", tempDir.resolve("file.txt").toString());

        assertThat(auditLog.getEntryCount()).isEqualTo(1);
        assertThat(auditLog.getRecentEntries(1).get(0).status()).isEqualTo("ALLOWED");
    }

    @Test
    void checkFileAccess_denied_createsAuditEntry() {
        securityManager.checkFileAccess("writeFile", "/etc/hosts");

        assertThat(auditLog.getEntryCount()).isEqualTo(1);
        assertThat(auditLog.getRecentEntries(1).get(0).status()).isEqualTo("DENIED");
    }

    @Test
    void checkCommandAccess_allowed_createsAuditEntry() {
        securityManager.checkCommandAccess("runCommand", "echo hi", null);

        assertThat(auditLog.getEntryCount()).isEqualTo(1);
        assertThat(auditLog.getRecentEntries(1).get(0).tool()).isEqualTo("runCommand");
    }

    // ── Sandbox configuration ───────────────────────────────────────

    @Test
    void setSandboxRoot_changesRoot() {
        Path newRoot = Paths.get("/tmp/new-root");
        securityManager.setSandboxRoot(newRoot);

        assertThat(securityManager.getSandboxRoot()).isEqualTo(newRoot.toAbsolutePath().normalize());
    }

    @Test
    void setSandboxEnabled_togglesState() {
        assertThat(securityManager.isSandboxEnabled()).isTrue();

        securityManager.setSandboxEnabled(false);
        assertThat(securityManager.isSandboxEnabled()).isFalse();

        securityManager.setSandboxEnabled(true);
        assertThat(securityManager.isSandboxEnabled()).isTrue();
    }

    @Test
    void addAllowedPath_addsToSet() {
        Path extra = Paths.get("/tmp/allowed");
        securityManager.addAllowedPath(extra);

        assertThat(securityManager.getAdditionalAllowedPaths())
                .contains(extra.toAbsolutePath().normalize());
    }

    @Test
    void clearAdditionalAllowedPaths_emptiesSet() {
        securityManager.addAllowedPath(Paths.get("/tmp/a"));
        securityManager.addAllowedPath(Paths.get("/tmp/b"));

        securityManager.clearAdditionalAllowedPaths();

        assertThat(securityManager.getAdditionalAllowedPaths()).isEmpty();
    }

    // ── isWithinSandbox ─────────────────────────────────────────────

    @Test
    void isWithinSandbox_rootItself_allowed() {
        assertThat(securityManager.isWithinSandbox(tempDir)).isTrue();
    }

    @Test
    void isWithinSandbox_childPath_allowed() {
        assertThat(securityManager.isWithinSandbox(tempDir.resolve("src/Main.java"))).isTrue();
    }

    @Test
    void isWithinSandbox_outsidePath_denied() {
        assertThat(securityManager.isWithinSandbox(Paths.get("/etc/passwd"))).isFalse();
    }

    @Test
    void isWithinSandbox_additionalPath_allowed() {
        Path extra = Paths.get("/tmp/extra").toAbsolutePath().normalize();
        securityManager.addAllowedPath(extra);

        assertThat(securityManager.isWithinSandbox(extra.resolve("child.txt"))).isTrue();
    }
}
