package eu.eurocoder.sovereigncli.security;

import java.time.Instant;
import java.util.Map;

/**
 * An immutable record of a single tool operation for the audit trail.
 * Serialized as JSON to the audit log file.
 *
 * @param timestamp  ISO-8601 timestamp of the operation
 * @param tool       the tool method name (e.g. {@code "deleteFile"}, {@code "runCommand"})
 * @param parameters key-value pairs describing the operation inputs
 * @param status     outcome: {@code "ALLOWED"}, {@code "DENIED"}, or {@code "ERROR"}
 * @param message    optional detail message (e.g. denial reason)
 */
public record AuditEntry(
        String timestamp,
        String tool,
        Map<String, String> parameters,
        String status,
        String message
) {

    /**
     * Creates an audit entry timestamped to the current instant.
     */
    public static AuditEntry now(String tool, Map<String, String> parameters, String status, String message) {
        return new AuditEntry(Instant.now().toString(), tool, parameters, status, message);
    }

    /**
     * Creates an ALLOWED audit entry timestamped to the current instant.
     */
    public static AuditEntry allowed(String tool, Map<String, String> parameters) {
        return now(tool, parameters, "ALLOWED", null);
    }

    /**
     * Creates a DENIED audit entry timestamped to the current instant.
     */
    public static AuditEntry denied(String tool, Map<String, String> parameters, String reason) {
        return now(tool, parameters, "DENIED", reason);
    }
}
