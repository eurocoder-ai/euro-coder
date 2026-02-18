package eu.eurocoder.sovereigncli.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AuditLog} — writing, reading, counting, and clearing audit entries.
 * Uses a temporary directory to isolate from real config.
 */
class AuditLogTest {

    @TempDir
    Path tempDir;

    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        auditLog = new AuditLog(tempDir.resolve("audit.jsonl"));
    }

    // ── Record and read ─────────────────────────────────────────────

    @Test
    void record_createsAuditFile() {
        auditLog.record("writeFile", Map.of("path", "/test.txt"), "ALLOWED", null);

        assertThat(auditLog.getAuditFilePath()).exists();
    }

    @Test
    void record_andGetRecentEntries_returnsEntry() {
        auditLog.record("writeFile", Map.of("path", "/test.txt"), "ALLOWED", null);

        List<AuditEntry> entries = auditLog.getRecentEntries(10);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).tool()).isEqualTo("writeFile");
        assertThat(entries.get(0).status()).isEqualTo("ALLOWED");
        assertThat(entries.get(0).parameters()).containsEntry("path", "/test.txt");
        assertThat(entries.get(0).timestamp()).isNotBlank();
    }

    @Test
    void record_multipleEntries_orderedNewestFirst() {
        auditLog.record("readFile", Map.of("path", "first.txt"), "ALLOWED", null);
        auditLog.record("writeFile", Map.of("path", "second.txt"), "ALLOWED", null);
        auditLog.record("deleteFile", Map.of("path", "third.txt"), "DENIED", "sandbox");

        List<AuditEntry> entries = auditLog.getRecentEntries(10);

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).tool()).isEqualTo("deleteFile");
        assertThat(entries.get(1).tool()).isEqualTo("writeFile");
        assertThat(entries.get(2).tool()).isEqualTo("readFile");
    }

    @Test
    void record_prebuiltEntry() {
        AuditEntry entry = AuditEntry.denied("runCommand", Map.of("command", "rm -rf /"), "blocked");
        auditLog.record(entry);

        List<AuditEntry> entries = auditLog.getRecentEntries(10);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).tool()).isEqualTo("runCommand");
        assertThat(entries.get(0).status()).isEqualTo("DENIED");
        assertThat(entries.get(0).message()).isEqualTo("blocked");
    }

    // ── Limit ───────────────────────────────────────────────────────

    @Test
    void getRecentEntries_respectsLimit() {
        for (int i = 0; i < 10; i++) {
            auditLog.record("op" + i, Map.of("i", String.valueOf(i)), "ALLOWED", null);
        }

        List<AuditEntry> limited = auditLog.getRecentEntries(3);
        assertThat(limited).hasSize(3);
        assertThat(limited.get(0).tool()).isEqualTo("op9");
    }

    // ── Count ───────────────────────────────────────────────────────

    @Test
    void getEntryCount_emptyLog_returnsZero() {
        assertThat(auditLog.getEntryCount()).isZero();
    }

    @Test
    void getEntryCount_afterRecords_returnsCorrectCount() {
        auditLog.record("op1", Map.of(), "ALLOWED", null);
        auditLog.record("op2", Map.of(), "DENIED", "reason");
        auditLog.record("op3", Map.of(), "ALLOWED", null);

        assertThat(auditLog.getEntryCount()).isEqualTo(3);
    }

    // ── Clear ───────────────────────────────────────────────────────

    @Test
    void clear_removesAllEntries() {
        auditLog.record("op1", Map.of(), "ALLOWED", null);
        auditLog.record("op2", Map.of(), "ALLOWED", null);

        auditLog.clear();

        assertThat(auditLog.getEntryCount()).isZero();
        assertThat(auditLog.getRecentEntries(10)).isEmpty();
    }

    @Test
    void clear_noErrorWhenEmpty() {
        auditLog.clear();
        assertThat(auditLog.getEntryCount()).isZero();
    }

    // ── getRecentEntries edge cases ─────────────────────────────────

    @Test
    void getRecentEntries_noFile_returnsEmpty() {
        assertThat(auditLog.getRecentEntries(10)).isEmpty();
    }

    // ── AuditEntry factory methods ──────────────────────────────────

    @Test
    void auditEntry_allowed_hasTimestampAndStatus() {
        AuditEntry entry = AuditEntry.allowed("readFile", Map.of("path", "a.txt"));

        assertThat(entry.timestamp()).isNotBlank();
        assertThat(entry.status()).isEqualTo("ALLOWED");
        assertThat(entry.tool()).isEqualTo("readFile");
        assertThat(entry.message()).isNull();
    }

    @Test
    void auditEntry_denied_includesMessage() {
        AuditEntry entry = AuditEntry.denied("deleteFile", Map.of("path", "b.txt"), "outside sandbox");

        assertThat(entry.status()).isEqualTo("DENIED");
        assertThat(entry.message()).isEqualTo("outside sandbox");
    }

    @Test
    void auditEntry_now_setsCurrentTimestamp() {
        AuditEntry entry = AuditEntry.now("tool", Map.of(), "ALLOWED", null);

        assertThat(entry.timestamp()).startsWith("20");
        assertThat(entry.timestamp()).contains("T");
    }

    // ── Audit file path ─────────────────────────────────────────────

    @Test
    void getAuditFilePath_returnsConfiguredPath() {
        assertThat(auditLog.getAuditFilePath()).isEqualTo(tempDir.resolve("audit.jsonl"));
    }
}
