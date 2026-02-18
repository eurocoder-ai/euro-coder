package eu.eurocoder.sovereigncli.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.eurocoder.sovereigncli.config.ApiKeyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Append-only audit log recording every tool invocation with timestamps,
 * parameters, and outcomes. Stored as JSON-Lines ({@code .jsonl}) in the
 * EuroCoder config directory ({@code ~/.eurocoder/audit.jsonl}).
 * <p>
 * Thread-safe: all writes are synchronized and use atomic append operations.
 */
@Service
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private static final String AUDIT_FILENAME = "audit.jsonl";

    private final Path auditFile;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditLog(ApiKeyManager apiKeyManager) {
        this(apiKeyManager.getConfigDir().resolve(AUDIT_FILENAME));
    }

    /**
     * Constructor for testing — allows redirecting the audit file to a temp path.
     */
    AuditLog(Path auditFile) {
        this.auditFile = auditFile;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Records a tool operation in the audit log.
     *
     * @param tool       the tool method name
     * @param parameters operation parameters
     * @param status     outcome ({@code "ALLOWED"}, {@code "DENIED"}, {@code "ERROR"})
     * @param message    optional detail message
     */
    public synchronized void record(String tool, Map<String, String> parameters, String status, String message) {
        AuditEntry entry = AuditEntry.now(tool, parameters, status, message);
        writeEntry(entry);
    }

    /**
     * Records a pre-built audit entry.
     */
    public synchronized void record(AuditEntry entry) {
        writeEntry(entry);
    }

    /**
     * Returns the most recent audit entries (newest first), up to the specified limit.
     */
    public List<AuditEntry> getRecentEntries(int limit) {
        if (!Files.exists(auditFile)) {
            return Collections.emptyList();
        }
        try (Stream<String> lines = Files.lines(auditFile)) {
            List<AuditEntry> all = lines
                    .filter(line -> !line.isBlank())
                    .map(this::parseLine)
                    .filter(entry -> entry != null)
                    .toList();

            // Return newest first, limited
            return all.reversed().stream()
                    .limit(limit)
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to read audit log: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Returns the total number of entries in the audit log.
     */
    public long getEntryCount() {
        if (!Files.exists(auditFile)) {
            return 0;
        }
        try (Stream<String> lines = Files.lines(auditFile)) {
            return lines.filter(line -> !line.isBlank()).count();
        } catch (IOException e) {
            log.warn("Failed to count audit entries: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Clears all entries from the audit log.
     */
    public synchronized void clear() {
        try {
            Files.deleteIfExists(auditFile);
            log.info("Audit log cleared: {}", auditFile);
        } catch (IOException e) {
            log.warn("Failed to clear audit log: {}", e.getMessage());
        }
    }

    public Path getAuditFilePath() {
        return auditFile;
    }

    private void writeEntry(AuditEntry entry) {
        try {
            Files.createDirectories(auditFile.getParent());
            String json = objectMapper.writeValueAsString(entry);
            Files.writeString(auditFile, json + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write audit entry: {}", e.getMessage());
        }
    }

    private AuditEntry parseLine(String line) {
        try {
            return objectMapper.readValue(line, AuditEntry.class);
        } catch (Exception e) {
            log.debug("Failed to parse audit line: {}", e.getMessage());
            return null;
        }
    }
}
