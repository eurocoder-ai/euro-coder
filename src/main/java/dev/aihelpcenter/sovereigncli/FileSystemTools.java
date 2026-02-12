package dev.aihelpcenter.sovereigncli;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tools available to the AI agent for interacting with the local system.
 * Includes project exploration, file system operations, and shell command execution.
 */
@Service
public class FileSystemTools {

    private static final Logger log = LoggerFactory.getLogger(FileSystemTools.class);

    // Directories to always skip when building the project tree
    private static final List<String> TREE_SKIP_DIRS = List.of(
            "target", "build", "node_modules", ".git", ".idea", ".vscode",
            ".cursor", "__pycache__", ".gradle", "dist", "out", ".next");

    // ── Project exploration tools ────────────────────────────────────

    @Tool("Returns a recursive tree view of the project directory structure. " +
          "USE THIS FIRST when starting any task to understand the full project layout. " +
          "Shows all files and directories up to the specified depth (default 4). " +
          "Skips build output, .git, node_modules, etc.")
    public String getProjectTree(String directory, int maxDepth) {
        Path dir = (directory == null || directory.isBlank()) ? Paths.get(".") : Paths.get(directory);
        int depth = (maxDepth <= 0) ? 4 : Math.min(maxDepth, 8);
        StringBuilder sb = new StringBuilder();
        sb.append(dir.toAbsolutePath().normalize()).append("/\n");
        buildTree(dir, sb, "", 0, depth);
        String result = sb.toString().trim();
        return result.isEmpty() ? "(empty directory)" : result;
    }

    private void buildTree(Path dir, StringBuilder sb, String prefix, int currentDepth, int maxDepth) {
        if (currentDepth >= maxDepth) {
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            List<Path> entries = paths
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (name.startsWith(".") && currentDepth == 0 && Files.isDirectory(p)) {
                            return false; // skip hidden dirs at root
                        }
                        return !TREE_SKIP_DIRS.contains(name);
                    })
                    .sorted((a, b) -> {
                        // Directories first, then files
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                    })
                    .toList();

            for (int i = 0; i < entries.size(); i++) {
                Path entry = entries.get(i);
                boolean isLast = (i == entries.size() - 1);
                String connector = isLast ? "└── " : "├── ";
                String childPrefix = isLast ? "    " : "│   ";

                sb.append(prefix).append(connector).append(entry.getFileName());
                if (Files.isDirectory(entry)) {
                    sb.append("/\n");
                    buildTree(entry, sb, prefix + childPrefix, currentDepth + 1, maxDepth);
                } else {
                    sb.append("\n");
                }
            }
        } catch (IOException e) {
            sb.append(prefix).append("(error: ").append(e.getMessage()).append(")\n");
        }
    }

    @Tool("Finds all files matching a glob pattern recursively. " +
          "Use patterns like '*.java', '*.test.*', '*.xml', 'pom.xml', '*.py'. " +
          "Returns the relative paths of all matching files. " +
          "Use this to discover all source files, test files, or config files in a project.")
    public String findFiles(String directory, String globPattern) {
        Path dir = (directory == null || directory.isBlank()) ? Paths.get(".") : Paths.get(directory);
        String pattern = (globPattern == null || globPattern.isBlank()) ? "*" : globPattern;

        // Ensure the pattern uses glob: prefix for the PathMatcher
        PathMatcher matcher = dir.getFileSystem().getPathMatcher("glob:" + pattern);

        try (Stream<Path> walk = Files.walk(dir)) {
            String result = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        // Skip build/hidden directories
                        for (Path component : dir.relativize(p)) {
                            if (TREE_SKIP_DIRS.contains(component.toString())) return false;
                        }
                        return true;
                    })
                    .filter(p -> matcher.matches(p.getFileName()))
                    .map(p -> dir.relativize(p).toString())
                    .sorted()
                    .collect(Collectors.joining("\n"));

            return result.isEmpty()
                    ? "No files matching '" + pattern + "' found in " + dir
                    : result;
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }
    }

    // ── Single-directory listing ─────────────────────────────────────

    @Tool("Lists all files and directories in the specified directory, or the current directory if none is given")
    public String listFiles(String directory) {
        Path dir = (directory == null || directory.isBlank()) ? Paths.get(".") : Paths.get(directory);
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + p.getFileName())
                    .sorted()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    // ── File reading ───────────────────────────────────────────────

    @Tool("Reads a file and returns its full text content")
    public String readFile(String path) {
        try {
            return Files.readString(Paths.get(path));
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("Reads a specific range of lines from a file. " +
          "Use this for large files when you only need a portion (e.g., a specific method or class header). " +
          "Lines are 1-indexed. Each line is prefixed with its line number for reference. " +
          "If endLine is 0 or beyond the file, reads to the end.")
    public String readFileRange(String path, int startLine, int endLine) {
        try {
            List<String> allLines = Files.readAllLines(Paths.get(path));
            int total = allLines.size();

            if (total == 0) {
                return "(empty file)";
            }

            int start = Math.max(1, startLine);
            int end = (endLine <= 0 || endLine > total) ? total : endLine;

            if (start > total) {
                return String.format("Start line %d is beyond end of file (%d lines)", start, total);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[%s — lines %d-%d of %d]\n", path, start, end, total));
            for (int i = start - 1; i < end; i++) {
                sb.append(String.format("%4d | %s\n", i + 1, allLines.get(i)));
            }
            return sb.toString().trim();

        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    // ── Content search (grep) ────────────────────────────────────────

    @Tool("Searches for a text pattern across all files in a directory recursively. " +
          "Returns matching lines with file paths and line numbers. " +
          "Use this to find usages, references, imports, variable names, or any text pattern. " +
          "For example: searchContent('.', 'UserService') finds every file referencing UserService. " +
          "Case-sensitive by default. Results are capped at 100 matches to save context.")
    public String searchContent(String directory, String pattern) {
        Path dir = (directory == null || directory.isBlank()) ? Paths.get(".") : Paths.get(directory);

        if (pattern == null || pattern.isBlank()) {
            return "Error: search pattern cannot be empty";
        }

        log.info("Searching for '{}' in {}", pattern, dir);
        List<String> matches = new ArrayList<>();
        int maxMatches = 100;

        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        for (Path component : dir.relativize(p)) {
                            if (TREE_SKIP_DIRS.contains(component.toString())) return false;
                        }
                        return true;
                    })
                    .filter(p -> isTextFile(p))
                    .sorted()
                    .toList();

            for (Path file : files) {
                if (matches.size() >= maxMatches) break;

                try {
                    List<String> lines = Files.readAllLines(file);
                    String relPath = dir.relativize(file).toString();

                    for (int i = 0; i < lines.size(); i++) {
                        if (matches.size() >= maxMatches) break;
                        if (lines.get(i).contains(pattern)) {
                            matches.add(String.format("%s:%d: %s",
                                    relPath, i + 1, lines.get(i).trim()));
                        }
                    }
                } catch (IOException e) {
                    // Skip binary/unreadable files silently (includes MalformedInputException)
                }
            }
        } catch (IOException e) {
            return "Error searching: " + e.getMessage();
        }

        if (matches.isEmpty()) {
            return "No matches for '" + pattern + "' in " + dir;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d match%s for '%s':\n\n",
                matches.size(), matches.size() == 1 ? "" : "es", pattern));
        for (String match : matches) {
            sb.append(match).append("\n");
        }
        if (matches.size() >= maxMatches) {
            sb.append("\n... (results capped at ").append(maxMatches).append(" matches)");
        }
        return sb.toString().trim();
    }

    /**
     * Heuristic to detect text files (skip binaries, images, archives).
     */
    private boolean isTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        // Skip common binary extensions
        if (name.endsWith(".jar") || name.endsWith(".class") || name.endsWith(".war") ||
                name.endsWith(".zip") || name.endsWith(".gz") || name.endsWith(".tar") ||
                name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".gif") || name.endsWith(".ico") || name.endsWith(".svg") ||
                name.endsWith(".pdf") || name.endsWith(".woff") || name.endsWith(".woff2") ||
                name.endsWith(".ttf") || name.endsWith(".eot") || name.endsWith(".exe") ||
                name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib") ||
                name.endsWith(".DS_Store")) {
            return false;
        }
        return true;
    }

    @Tool("Writes the given content to a file, creating it if it does not exist or overwriting if it does")
    public String writeFile(String path, String content) {
        try {
            Path filePath = Paths.get(path);
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, content);
            return "Successfully wrote " + content.length() + " characters to " + path;
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool("Appends the given content to the end of an existing file")
    public String appendToFile(String path, String content) {
        try {
            Path filePath = Paths.get(path);
            String existing = Files.exists(filePath) ? Files.readString(filePath) : "";
            Files.writeString(filePath, existing + content);
            return "Successfully appended " + content.length() + " characters to " + path;
        } catch (IOException e) {
            return "Error appending to file: " + e.getMessage();
        }
    }

    @Tool("Creates a new directory at the specified path, including any necessary parent directories")
    public String createDirectory(String path) {
        try {
            Files.createDirectories(Paths.get(path));
            return "Successfully created directory: " + path;
        } catch (IOException e) {
            return "Error creating directory: " + e.getMessage();
        }
    }

    @Tool("Deletes a file at the specified path")
    public String deleteFile(String path) {
        try {
            boolean deleted = Files.deleteIfExists(Paths.get(path));
            return deleted ? "Successfully deleted: " + path : "File not found: " + path;
        } catch (IOException e) {
            return "Error deleting file: " + e.getMessage();
        }
    }

    // ── Shell command execution ─────────────────────────────────────

    @Tool("Executes a shell command on the local system and returns its output. " +
          "Use this for any terminal operation: git, docker, npm, curl, grep, ps, etc. " +
          "The command runs in the current working directory with a 60-second timeout.")
    public String runCommand(String command) {
        log.info("Executing command: {}", command);
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    lineCount++;
                    // Cap output to prevent overwhelming the model context
                    if (lineCount >= 200) {
                        output.append("\n... (output truncated at 200 lines)\n");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after 60 seconds. Partial output:\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode != 0) {
                return "Command exited with code " + exitCode + ":\n" + result;
            }
            return result.isEmpty() ? "(command completed with no output)" : result;

        } catch (IOException e) {
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Command interrupted: " + e.getMessage();
        }
    }

    @Tool("Executes a shell command in a specific directory and returns its output. " +
          "Use this when you need to run a command in a particular location.")
    public String runCommandInDirectory(String command, String workingDirectory) {
        log.info("Executing command in {}: {}", workingDirectory, command);
        try {
            Path dir = Paths.get(workingDirectory);
            if (!Files.isDirectory(dir)) {
                return "Error: '" + workingDirectory + "' is not a valid directory";
            }

            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    lineCount++;
                    if (lineCount >= 200) {
                        output.append("\n... (output truncated at 200 lines)\n");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after 60 seconds. Partial output:\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode != 0) {
                return "Command exited with code " + exitCode + ":\n" + result;
            }
            return result.isEmpty() ? "(command completed with no output)" : result;

        } catch (IOException e) {
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Command interrupted: " + e.getMessage();
        }
    }
}
