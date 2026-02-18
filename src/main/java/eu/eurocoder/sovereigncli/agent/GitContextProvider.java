package eu.eurocoder.sovereigncli.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Provides git-aware context to inject into agent prompts.
 * Extracts the current branch, working tree status, and recent commit
 * history so agents can make informed decisions about version control.
 * <p>
 * All git commands run with a short timeout and failures are handled
 * gracefully — if the project is not a git repo, an empty context is returned.
 */
@Service
public class GitContextProvider {

    private static final Logger log = LoggerFactory.getLogger(GitContextProvider.class);
    private static final int GIT_TIMEOUT_SECONDS = 5;
    private static final int MAX_RECENT_COMMITS = 5;

    /**
     * Gathers a concise git context snapshot for the given directory.
     *
     * @param directory the project root (or {@code null} for current directory)
     * @return formatted git context string, or empty string if not a git repo
     */
    public String gatherGitContext(String directory) {
        Path dir = (directory == null || directory.isBlank())
                ? Paths.get("").toAbsolutePath()
                : Paths.get(directory).toAbsolutePath();

        if (!isGitRepo(dir)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("GIT CONTEXT:\n");

        String branch = runGit(dir, "git", "rev-parse", "--abbrev-ref", "HEAD");
        if (!branch.isEmpty()) {
            sb.append("  Branch: ").append(branch).append("\n");
        }

        String status = runGit(dir, "git", "status", "--porcelain");
        if (!status.isEmpty()) {
            long modified = status.lines().filter(l -> l.startsWith(" M") || l.startsWith("M ")).count();
            long added = status.lines().filter(l -> l.startsWith("A ") || l.startsWith("??")).count();
            long deleted = status.lines().filter(l -> l.startsWith(" D") || l.startsWith("D ")).count();
            sb.append(String.format("  Working tree: %d modified, %d added/untracked, %d deleted\n",
                    modified, added, deleted));
        } else {
            sb.append("  Working tree: clean\n");
        }

        String recentCommits = runGit(dir, "git", "log",
                "--oneline", "-" + MAX_RECENT_COMMITS, "--no-decorate");
        if (!recentCommits.isEmpty()) {
            sb.append("  Recent commits:\n");
            recentCommits.lines()
                    .forEach(line -> sb.append("    ").append(line).append("\n"));
        }

        String diff = runGit(dir, "git", "diff", "--stat", "--no-color");
        if (!diff.isEmpty()) {
            sb.append("  Uncommitted changes:\n");
            diff.lines()
                    .limit(10)
                    .forEach(line -> sb.append("    ").append(line).append("\n"));
        }

        return sb.toString().trim();
    }

    /**
     * Checks if the given directory is inside a git repository.
     */
    boolean isGitRepo(Path directory) {
        try {
            Path current = directory;
            while (current != null) {
                if (Files.isDirectory(current.resolve(".git"))) {
                    return true;
                }
                current = current.getParent();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Runs a git command and returns the trimmed stdout output.
     * Returns an empty string on any failure.
     */
    String runGit(Path directory, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(directory.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }

            return process.exitValue() == 0 ? output.trim() : "";
        } catch (Exception e) {
            log.debug("Git command failed: {}", e.getMessage());
            return "";
        }
    }
}
