package eu.eurocoder.sovereigncli.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TrustLevel} enum — ID resolution, properties, and edge cases.
 */
class TrustLevelTest {

    // ── fromId ──────────────────────────────────────────────────────

    @Test
    void fromId_askAlways() {
        assertThat(TrustLevel.fromId("ask-always")).isEqualTo(TrustLevel.ASK_ALWAYS);
    }

    @Test
    void fromId_askDestructive() {
        assertThat(TrustLevel.fromId("ask-destructive")).isEqualTo(TrustLevel.ASK_DESTRUCTIVE);
    }

    @Test
    void fromId_trustAll() {
        assertThat(TrustLevel.fromId("trust-all")).isEqualTo(TrustLevel.TRUST_ALL);
    }

    @Test
    void fromId_isCaseInsensitive() {
        assertThat(TrustLevel.fromId("ASK-ALWAYS")).isEqualTo(TrustLevel.ASK_ALWAYS);
        assertThat(TrustLevel.fromId("Trust-All")).isEqualTo(TrustLevel.TRUST_ALL);
        assertThat(TrustLevel.fromId("ASK-DESTRUCTIVE")).isEqualTo(TrustLevel.ASK_DESTRUCTIVE);
    }

    @Test
    void fromId_trimsWhitespace() {
        assertThat(TrustLevel.fromId("  ask-always  ")).isEqualTo(TrustLevel.ASK_ALWAYS);
        assertThat(TrustLevel.fromId("  trust-all  ")).isEqualTo(TrustLevel.TRUST_ALL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "invalid", "  ", "ask_always", "trustall"})
    void fromId_unknownValues_defaultToAskDestructive(String input) {
        assertThat(TrustLevel.fromId(input)).isEqualTo(TrustLevel.ASK_DESTRUCTIVE);
    }

    // ── Properties ──────────────────────────────────────────────────

    @Test
    void idAndDescription_areNotBlank() {
        for (TrustLevel level : TrustLevel.values()) {
            assertThat(level.id()).isNotBlank();
            assertThat(level.description()).isNotBlank();
        }
    }

    @Test
    void allLevels_haveUniqueIds() {
        var ids = java.util.Arrays.stream(TrustLevel.values())
                .map(TrustLevel::id)
                .toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void threeValues_exist() {
        assertThat(TrustLevel.values()).hasSize(3);
    }
}
