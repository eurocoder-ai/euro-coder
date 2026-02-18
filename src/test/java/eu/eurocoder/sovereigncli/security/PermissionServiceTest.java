package eu.eurocoder.sovereigncli.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PermissionService} — trust level logic and permission decisions.
 * <p>
 * Note: Interactive console prompting cannot be tested in unit tests (no console
 * in CI). These tests verify the non-interactive code paths and trust level behavior.
 */
class PermissionServiceTest {

    // ── Trust level initialization ──────────────────────────────────

    @Test
    void constructor_setsDefaultTrustLevel() {
        PermissionService service = new PermissionService(TrustLevel.ASK_DESTRUCTIVE);

        assertThat(service.getTrustLevel()).isEqualTo(TrustLevel.ASK_DESTRUCTIVE);
    }

    @Test
    void constructor_acceptsCustomTrustLevel() {
        PermissionService service = new PermissionService(TrustLevel.TRUST_ALL);

        assertThat(service.getTrustLevel()).isEqualTo(TrustLevel.TRUST_ALL);
    }

    // ── TRUST_ALL always allows ─────────────────────────────────────

    @Test
    void requestPermission_trustAll_alwaysReturnsTrue() {
        PermissionService service = new PermissionService(TrustLevel.TRUST_ALL);

        assertThat(service.requestPermission("deleteFile", "/critical/file.txt")).isTrue();
        assertThat(service.requestPermission("runCommand", "rm -rf /")).isTrue();
        assertThat(service.requestPermission("writeFile", "/etc/hosts")).isTrue();
    }

    // ── Non-interactive (no console) denies when not TRUST_ALL ──────

    @Test
    void requestPermission_noConsole_askDestructive_denies() {
        PermissionService service = new PermissionService(TrustLevel.ASK_DESTRUCTIVE);

        // In test environment, System.console() returns null
        // Non-TRUST_ALL should deny when no console is available
        boolean result = service.requestPermission("deleteFile", "/some/file.txt");

        // When running in an IDE/CI (no console), this should return false
        // since the user can't be prompted
        assertThat(result).isFalse();
    }

    @Test
    void requestPermission_noConsole_askAlways_denies() {
        PermissionService service = new PermissionService(TrustLevel.ASK_ALWAYS);

        boolean result = service.requestPermission("writeFile", "/some/file.txt");

        assertThat(result).isFalse();
    }

    // ── setTrustLevel ───────────────────────────────────────────────

    @Test
    void setTrustLevel_changesLevel() {
        PermissionService service = new PermissionService(TrustLevel.ASK_DESTRUCTIVE);

        service.setTrustLevel(TrustLevel.TRUST_ALL);

        assertThat(service.getTrustLevel()).isEqualTo(TrustLevel.TRUST_ALL);
    }

    @Test
    void setTrustLevel_affectsPermissionDecisions() {
        PermissionService service = new PermissionService(TrustLevel.ASK_DESTRUCTIVE);

        // Initially denies (no console)
        assertThat(service.requestPermission("deleteFile", "file.txt")).isFalse();

        // After changing to TRUST_ALL, allows
        service.setTrustLevel(TrustLevel.TRUST_ALL);
        assertThat(service.requestPermission("deleteFile", "file.txt")).isTrue();
    }
}
