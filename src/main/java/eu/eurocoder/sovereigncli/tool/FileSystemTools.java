package eu.eurocoder.sovereigncli.tool;

import eu.eurocoder.sovereigncli.rag.RagService;
import eu.eurocoder.sovereigncli.security.ToolSecurityManager;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Tools available to the AI agent for interacting with the local system.
 * Includes project exploration, file system operations, and shell command execution.
 * <p>
 * All mutating operations are checked against the {@link ToolSecurityManager}
 * for sandbox boundaries, permission approval, and audit logging.
 */
@Service
public class FileSystemTools {

    private static final Logger log = LoggerFactory.getLogger(FileSystemTools.class);

    private static final Set<String> TREE_SKIP_DIRS = Set.of(
            "target", "build", "node_modules", ".git", ".idea", ".vscode",
            ".cursor", "__pycache__", ".gradle", "dist", "out", ".next");

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".jar", ".class", ".war", ".zip", ".gz", ".tar",
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".pdf", ".woff", ".woff2", ".ttf", ".eot",
            ".exe", ".dll", ".so", ".dylib", ".DS_Store");

    private static final int DEFAULT_TREE_DEPTH = 4;
    private static final int MAX_TREE_DEPTH = 8;
    private static final int COMMAND_TIMEOUT_SECONDS = 60;
    private static final int MAX_COMMAND_OUTPUT_LINES = 200;
    private static final int MAX_SEARCH_MATCHES = 100;
    private static final int SEMANTIC_SEARCH_MAX_RESULTS = 5;

    private final ToolSecurityManager securityManager;
    private final RagService ragService;
    private final Path workingDirectory;

    @Autowired
    public FileSystemTools(ToolSecurityManager securityManager, RagService ragService) {
        this.securityManager = securityManager;
        this.ragService = ragService;
        this.workingDirectory = null;
    }

    public FileSystemTools() {
        this.securityManager = null;
        this.ragService = null;
        this.workingDirectory = null;
    }

    /**
     * Creates a FileSystemTools instance that resolves all relative paths against
     * the given working directory instead of the JVM's current directory.
     * Used for benchmark sandboxes.
     */
    public FileSystemTools(ToolSecurityManager securityManager, Path workingDirectory) {
        this.securityManager = securityManager;
        this.ragService = null;
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    @Tool("Returns a recursive tree view of the project directory structure. " +
          "USE THIS FIRST when starting any task to understand the full project layout. " +
          "Shows all files and directories up to the specified depth (default 4). " +
          "Skips build output, .git, node_modules, etc.")
    public String getProjectTree(String directory, int maxDepth) {
        Path dir = resolveDir(directory);
        int depth = (maxDepth <= 0) ? DEFAULT_TREE_DEPTH : Math.min(maxDepth, MAX_TREE_DEPTH);
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
                            return false;
                        }
                        return !TREE_SKIP_DIRS.contains(name);
                    })
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                    })
                    .toList();

            IntStream.range(0, entries.size()).forEach(i -> {
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
            });
        } catch (IOException e) {
            sb.append(prefix).append("(error: ").append(e.getMessage()).append(")\n");
        }
    }

    @Tool("Finds all files matching a glob pattern recursively. " +
          "Use patterns like '*.java', '*.test.*', '*.xml', 'pom.xml', '*.py'. " +
          "Returns the relative paths of all matching files. " +
          "Use this to discover all source files, test files, or config files in a project.")
    public String findFiles(String directory, String globPattern) {
        Path dir = resolveDir(directory);
        String pattern = (globPattern == null || globPattern.isBlank()) ? "*" : globPattern;
        PathMatcher matcher = dir.getFileSystem().getPathMatcher("glob:" + pattern);

        try (Stream<Path> walk = Files.walk(dir)) {
            String result = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> isNotInSkipDir(dir, p))
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

    @Tool("Lists all files and directories in the specified directory, or the current directory if none is given")
    public String listFiles(String directory) {
        Path dir = resolveDir(directory);
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + p.getFileName())
                    .sorted()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    @Tool("Reads a file and returns its full text content")
    public String readFile(String path) {
        try {
            return Files.readString(resolvePath(path));
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
            List<String> allLines = Files.readAllLines(resolvePath(path));
            int total = allLines.size();

            if (total == 0) {
                return "(empty file)";
            }

            int start = Math.max(1, startLine);
            int end = (endLine <= 0 || endLine > total) ? total : endLine;

            if (start > total) {
                return String.format("Start line %d is beyond end of file (%d lines)", start, total);
            }

            String header = String.format("[%s — lines %d-%d of %d]\n", path, start, end, total);
            String content = IntStream.rangeClosed(start, end)
                    .mapToObj(i -> String.format("%4d | %s", i, allLines.get(i - 1)))
                    .collect(Collectors.joining("\n"));

            return header + content;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool("Searches for a text pattern across all files in a directory recursively. " +
          "Returns matching lines with file paths and line numbers. " +
          "Use this to find usages, references, imports, variable names, or any text pattern. " +
          "For example: searchContent('.', 'UserService') finds every file referencing UserService. " +
          "Case-sensitive by default. Results are capped at 100 matches to save context.")
    public String searchContent(String directory, String pattern) {
        Path dir = resolveDir(directory);

        if (pattern == null || pattern.isBlank()) {
            return "Error: search pattern cannot be empty";
        }

        log.info("Searching for '{}' in {}", pattern, dir);

        try (Stream<Path> walk = Files.walk(dir)) {
            List<String> matches = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> isNotInSkipDir(dir, p))
                    .filter(this::isTextFile)
                    .sorted()
                    .flatMap(file -> findMatchesInFile(dir, file, pattern))
                    .limit(MAX_SEARCH_MATCHES)
                    .toList();

            if (matches.isEmpty()) {
                return "No matches for '" + pattern + "' in " + dir;
            }

            String header = String.format("Found %d match%s for '%s':\n\n",
                    matches.size(), matches.size() == 1 ? "" : "es", pattern);
            String body = String.join("\n", matches);
            String footer = matches.size() >= MAX_SEARCH_MATCHES
                    ? "\n\n... (results capped at " + MAX_SEARCH_MATCHES + " matches)"
                    : "";

            return header + body + footer;
        } catch (IOException e) {
            return "Error searching: " + e.getMessage();
        }
    }

    private Stream<String> findMatchesInFile(Path baseDir, Path file, String pattern) {
        try {
            List<String> lines = Files.readAllLines(file);
            String relPath = baseDir.relativize(file).toString();
            return IntStream.range(0, lines.size())
                    .filter(i -> lines.get(i).contains(pattern))
                    .mapToObj(i -> String.format("%s:%d: %s", relPath, i + 1, lines.get(i).trim()));
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    @Tool("Writes the given content to a file, creating it if it does not exist or overwriting if it does")
    public String writeFile(String path, String content) {
        Optional<String> denied = checkFileAccess("writeFile", path);
        if (denied.isPresent()) return denied.get();

        try {
            Path filePath = resolvePath(path);
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
        Optional<String> denied = checkFileAccess("appendToFile", path);
        if (denied.isPresent()) return denied.get();

        try {
            Path filePath = resolvePath(path);
            Files.writeString(filePath, content,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Successfully appended " + content.length() + " characters to " + path;
        } catch (IOException e) {
            return "Error appending to file: " + e.getMessage();
        }
    }

    @Tool("Creates a new directory at the specified path, including any necessary parent directories")
    public String createDirectory(String path) {
        Optional<String> denied = checkFileAccess("createDirectory", path);
        if (denied.isPresent()) return denied.get();

        try {
            Files.createDirectories(resolvePath(path));
            return "Successfully created directory: " + path;
        } catch (IOException e) {
            return "Error creating directory: " + e.getMessage();
        }
    }

    @Tool("Deletes a file at the specified path")
    public String deleteFile(String path) {
        Optional<String> denied = checkFileAccess("deleteFile", path);
        if (denied.isPresent()) return denied.get();

        try {
            boolean deleted = Files.deleteIfExists(resolvePath(path));
            return deleted ? "Successfully deleted: " + path : "File not found: " + path;
        } catch (IOException e) {
            return "Error deleting file: " + e.getMessage();
        }
    }

    @Tool("Search the project codebase semantically using AI embeddings. " +
          "Returns relevant code snippets matching the MEANING of your query, even if exact keywords differ. " +
          "Use this when you need to find code related to a concept, pattern, or functionality. " +
          "For exact keyword matches, use searchContent instead. " +
          "Example: semanticSearch('authentication middleware') finds auth-related code.")
    public String semanticSearch(String query) {
        if (ragService == null || !ragService.isIndexed()) {
            return "RAG index not available. The project has not been indexed for semantic search.";
        }
        return ragService.retrieveContext(query, SEMANTIC_SEARCH_MAX_RESULTS);
    }

    @Tool("Executes a shell command on the local system and returns its output. " +
          "Use this for any terminal operation: git, docker, npm, curl, grep, ps, etc. " +
          "The command runs in the current working directory with a 60-second timeout.")
    public String runCommand(String command) {
        Optional<String> denied = checkCommandAccess("runCommand", command, workingDirectory);
        if (denied.isPresent()) return denied.get();

        log.info("Executing command: {}", command);
        return executeCommand(command, workingDirectory);
    }

    @Tool("Executes a shell command in a specific directory and returns its output. " +
          "Use this when you need to run a command in a particular location.")
    public String runCommandInDirectory(String command, String directory) {
        Path dir = resolvePath(directory);
        if (!Files.isDirectory(dir)) {
            return "Error: '" + directory + "' is not a valid directory";
        }

        Optional<String> denied = checkCommandAccess("runCommandInDirectory", command, dir);
        if (denied.isPresent()) return denied.get();

        log.info("Executing command in {}: {}", directory, command);
        return executeCommand(command, dir);
    }

    private Optional<String> checkFileAccess(String operation, String path) {
        if (securityManager == null) return Optional.empty();
        return securityManager.checkFileAccess(operation, path);
    }

    private Optional<String> checkCommandAccess(String operation, String command, Path workingDir) {
        if (securityManager == null) return Optional.empty();
        return securityManager.checkCommandAccess(operation, command, workingDir);
    }

    private Path resolveDir(String directory) {
        if (directory == null || directory.isBlank() || directory.equals(".")) {
            return workingDirectory != null ? workingDirectory : Paths.get(".");
        }
        Path path = Paths.get(directory);
        if (path.isAbsolute()) {
            return path;
        }
        return workingDirectory != null ? workingDirectory.resolve(path) : path;
    }

    private Path resolvePath(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p;
        }
        return workingDirectory != null ? workingDirectory.resolve(p) : p;
    }

    private boolean isNotInSkipDir(Path baseDir, Path file) {
        Path relative = baseDir.relativize(file);
        return IntStream.range(0, relative.getNameCount())
                .mapToObj(relative::getName)
                .map(Path::toString)
                .noneMatch(TREE_SKIP_DIRS::contains);
    }

    private boolean isTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex >= 0) {
            String extension = name.substring(dotIndex);
            return !BINARY_EXTENSIONS.contains(extension);
        }
        return !BINARY_EXTENSIONS.contains(name);
    }

    private String executeCommand(String command, Path workingDirectory) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            if (workingDirectory != null) {
                pb.directory(workingDirectory.toFile());
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<String> lines;
            boolean truncated;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                lines = reader.lines()
                        .limit(MAX_COMMAND_OUTPUT_LINES + 1L)
                        .toList();
                truncated = lines.size() > MAX_COMMAND_OUTPUT_LINES;
            }

            String output = lines.stream()
                    .limit(MAX_COMMAND_OUTPUT_LINES)
                    .collect(Collectors.joining("\n"));

            if (truncated) {
                output += "\n\n... (output truncated at " + MAX_COMMAND_OUTPUT_LINES + " lines)";
            }

            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after " + COMMAND_TIMEOUT_SECONDS
                        + " seconds. Partial output:\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.trim();

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
