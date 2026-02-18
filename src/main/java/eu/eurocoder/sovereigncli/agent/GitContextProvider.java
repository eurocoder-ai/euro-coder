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
 * Provides git-aware context (branch, status, recent commits) for agent prompts.
 * All git commands run with a short timeout; returns empty context if not a git repo.
 */
@Service
public class GitContextProvider {

    private static final Logger log = LoggerFactory.getLogger(GitContextProvider.class);
    private static final int GIT_TIMEOUT_SECONDS = 5;
    private static final int MAX_RECENT_COMMITS = 5;
    private static final int MAX_DIFF_STAT_LINES = 10;

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
                    .limit(MAX_DIFF_STAT_LINES)
                    .forEach(line -> sb.append("    ").append(line).append("\n"));
        }

        return sb.toString().trim();
    }

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
