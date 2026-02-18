package eu.eurocoder.sovereigncli.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GitContextProvider} — git detection, context gathering,
 * and graceful fallback for non-git directories.
 */
class GitContextProviderTest {

    @TempDir
    Path tempDir;

    private GitContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GitContextProvider();
    }

    // ── isGitRepo ───────────────────────────────────────────────────

    @Test
    void isGitRepo_withGitDir_returnsTrue() throws IOException {
        Files.createDirectory(tempDir.resolve(".git"));

        assertThat(provider.isGitRepo(tempDir)).isTrue();
    }

    @Test
    void isGitRepo_withoutGitDir_returnsFalse() {
        assertThat(provider.isGitRepo(tempDir)).isFalse();
    }

    @Test
    void isGitRepo_childOfGitRepo_returnsTrue() throws IOException {
        Files.createDirectory(tempDir.resolve(".git"));
        Path child = Files.createDirectories(tempDir.resolve("src/main"));

        assertThat(provider.isGitRepo(child)).isTrue();
    }

    @Test
    void isGitRepo_nullSafe() {
        assertThat(provider.isGitRepo(tempDir.resolve("nonexistent"))).isFalse();
    }

    // ── gatherGitContext for non-git directories ────────────────────

    @Test
    void gatherGitContext_nonGitDir_returnsEmpty() {
        String context = provider.gatherGitContext(tempDir.toString());

        assertThat(context).isEmpty();
    }

    @Test
    void gatherGitContext_nullDirectory_doesNotThrow() {
        // Uses CWD, which may or may not be a git repo
        // The point is it doesn't throw
        String context = provider.gatherGitContext(null);
        assertThat(context).isNotNull();
    }

    @Test
    void gatherGitContext_blankDirectory_doesNotThrow() {
        String context = provider.gatherGitContext("");
        assertThat(context).isNotNull();
    }

    // ── gatherGitContext for real git repo ───────────────────────────

    @Test
    void gatherGitContext_realGitRepo_containsBranch() throws Exception {
        initGitRepo();

        String context = provider.gatherGitContext(tempDir.toString());

        assertThat(context).contains("GIT CONTEXT:");
        assertThat(context).contains("Branch:");
    }

    @Test
    void gatherGitContext_realGitRepo_containsRecentCommits() throws Exception {
        initGitRepo();

        String context = provider.gatherGitContext(tempDir.toString());

        assertThat(context).contains("Recent commits:");
        assertThat(context).contains("Initial commit");
    }

    @Test
    void gatherGitContext_realGitRepo_showsWorkingTreeStatus() throws Exception {
        initGitRepo();

        // Add an untracked file
        Files.writeString(tempDir.resolve("untracked.txt"), "hello");

        String context = provider.gatherGitContext(tempDir.toString());

        assertThat(context).contains("Working tree:");
    }

    @Test
    void gatherGitContext_realGitRepo_showsUncommittedChanges() throws Exception {
        initGitRepo();

        // Modify the tracked file
        Files.writeString(tempDir.resolve("README.md"), "modified content");

        String context = provider.gatherGitContext(tempDir.toString());

        assertThat(context).contains("Uncommitted changes:");
    }

    // ── runGit ──────────────────────────────────────────────────────

    @Test
    void runGit_validCommand_returnsOutput() throws Exception {
        initGitRepo();

        String result = provider.runGit(tempDir, "git", "branch", "--show-current");

        assertThat(result).isNotBlank();
    }

    @Test
    void runGit_invalidCommand_returnsEmpty() {
        String result = provider.runGit(tempDir, "nonexistent_command_xyz");

        assertThat(result).isEmpty();
    }

    @Test
    void runGit_gitCommandInNonRepo_returnsEmpty() {
        String result = provider.runGit(tempDir, "git", "status");

        assertThat(result).isEmpty();
    }

    // ── Helper ──────────────────────────────────────────────────────

    private void initGitRepo() throws Exception {
        provider.runGit(tempDir, "git", "init");
        provider.runGit(tempDir, "git", "config", "user.email", "test@test.com");
        provider.runGit(tempDir, "git", "config", "user.name", "Test");
        Files.writeString(tempDir.resolve("README.md"), "# Test");
        provider.runGit(tempDir, "git", "add", ".");
        provider.runGit(tempDir, "git", "commit", "-m", "Initial commit");
    }
}
