package dev.aihelpcenter.sovereigncli.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FileSystemTools} — project exploration, file I/O operations,
 * and shell command execution. Uses a temporary directory for isolation.
 */
class FileSystemToolsTest {

    private FileSystemTools tools;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tools = new FileSystemTools();
    }

    // ── getProjectTree ───────────────────────────────────────────────

    @Test
    void getProjectTree_showsFilesAndDirs() throws IOException {
        Files.createDirectory(tempDir.resolve("src"));
        Files.createFile(tempDir.resolve("src/Main.java"));
        Files.createFile(tempDir.resolve("pom.xml"));

        String result = tools.getProjectTree(tempDir.toString(), 3);

        assertThat(result).contains("src/");
        assertThat(result).contains("Main.java");
        assertThat(result).contains("pom.xml");
        assertThat(result).contains("├──").satisfiesAnyOf(
                r -> assertThat(r).contains("├──"),
                r -> assertThat(r).contains("└──")
        );
    }

    @Test
    void getProjectTree_respectsMaxDepth() throws IOException {
        Files.createDirectories(tempDir.resolve("a/b/c/d"));
        Files.createFile(tempDir.resolve("a/b/c/d/deep.txt"));

        String shallow = tools.getProjectTree(tempDir.toString(), 2);
        assertThat(shallow).contains("a/").contains("b/");
        assertThat(shallow).doesNotContain("deep.txt");

        String deep = tools.getProjectTree(tempDir.toString(), 5);
        assertThat(deep).contains("deep.txt");
    }

    @Test
    void getProjectTree_skipsTargetAndGitDirs() throws IOException {
        Files.createDirectories(tempDir.resolve("target/classes"));
        Files.createDirectories(tempDir.resolve(".git/objects"));
        Files.createDirectories(tempDir.resolve("node_modules/foo"));
        Files.createDirectory(tempDir.resolve("src"));

        String result = tools.getProjectTree(tempDir.toString(), 3);

        assertThat(result).contains("src/");
        assertThat(result).doesNotContain("target");
        assertThat(result).doesNotContain(".git");
        assertThat(result).doesNotContain("node_modules");
    }

    @Test
    void getProjectTree_emptyDirectory() {
        String result = tools.getProjectTree(tempDir.toString(), 3);
        assertThat(result).doesNotContain("├──");
        assertThat(result).doesNotContain("└──");
    }

    @Test
    void getProjectTree_defaultDepthWhenZero() throws IOException {
        Files.createFile(tempDir.resolve("file.txt"));

        String result = tools.getProjectTree(tempDir.toString(), 0);
        assertThat(result).contains("file.txt");
    }

    @Test
    void getProjectTree_directoriesBeforeFiles() throws IOException {
        Files.createFile(tempDir.resolve("aaa.txt"));
        Files.createDirectory(tempDir.resolve("zzz-dir"));

        String result = tools.getProjectTree(tempDir.toString(), 2);

        int dirPos = result.indexOf("zzz-dir");
        int filePos = result.indexOf("aaa.txt");
        assertThat(dirPos).isLessThan(filePos);
    }

    // ── findFiles ────────────────────────────────────────────────────

    @Test
    void findFiles_matchesGlobPattern() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main"));
        Files.createFile(tempDir.resolve("src/main/App.java"));
        Files.createFile(tempDir.resolve("src/main/Utils.java"));
        Files.createFile(tempDir.resolve("src/main/config.xml"));

        String result = tools.findFiles(tempDir.toString(), "*.java");

        assertThat(result).contains("App.java");
        assertThat(result).contains("Utils.java");
        assertThat(result).doesNotContain("config.xml");
    }

    @Test
    void findFiles_searchesRecursively() throws IOException {
        Files.createDirectories(tempDir.resolve("a/b/c"));
        Files.createFile(tempDir.resolve("a/b/c/Deep.java"));

        String result = tools.findFiles(tempDir.toString(), "*.java");

        assertThat(result).contains("Deep.java");
    }

    @Test
    void findFiles_skipsBuildDirs() throws IOException {
        Files.createDirectories(tempDir.resolve("target/classes"));
        Files.createFile(tempDir.resolve("target/classes/Compiled.java"));
        Files.createDirectories(tempDir.resolve("src"));
        Files.createFile(tempDir.resolve("src/Source.java"));

        String result = tools.findFiles(tempDir.toString(), "*.java");

        assertThat(result).contains("Source.java");
        assertThat(result).doesNotContain("Compiled.java");
    }

    @Test
    void findFiles_noMatches_returnsMessage() {
        String result = tools.findFiles(tempDir.toString(), "*.xyz");

        assertThat(result).contains("No files matching");
    }

    @Test
    void findFiles_matchesSpecificFilename() throws IOException {
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createFile(tempDir.resolve("README.md"));

        String result = tools.findFiles(tempDir.toString(), "pom.xml");

        assertThat(result).contains("pom.xml");
        assertThat(result).doesNotContain("README.md");
    }

    // ── readFileRange ─────────────────────────────────────────────────

    @Test
    void readFileRange_returnsSpecifiedLines() throws IOException {
        Path file = tempDir.resolve("lines.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4\nline5\n");

        String result = tools.readFileRange(file.toString(), 2, 4);

        assertThat(result).contains("line2");
        assertThat(result).contains("line3");
        assertThat(result).contains("line4");
        assertThat(result).doesNotContain("line1\n");
        assertThat(result).doesNotContain("line5\n");
    }

    @Test
    void readFileRange_includesLineNumbers() throws IOException {
        Path file = tempDir.resolve("numbered.txt");
        Files.writeString(file, "alpha\nbeta\ngamma\n");

        String result = tools.readFileRange(file.toString(), 1, 3);

        assertThat(result).contains("1 | alpha");
        assertThat(result).contains("2 | beta");
        assertThat(result).contains("3 | gamma");
    }

    @Test
    void readFileRange_showsFileMetadata() throws IOException {
        Path file = tempDir.resolve("meta.txt");
        Files.writeString(file, "a\nb\nc\nd\ne\n");

        String result = tools.readFileRange(file.toString(), 2, 3);

        assertThat(result).contains("lines 2-3 of 5");
        assertThat(result).contains("meta.txt");
    }

    @Test
    void readFileRange_zeroEndLine_readsToEnd() throws IOException {
        Path file = tempDir.resolve("toend.txt");
        Files.writeString(file, "first\nsecond\nthird\n");

        String result = tools.readFileRange(file.toString(), 2, 0);

        assertThat(result).contains("second");
        assertThat(result).contains("third");
    }

    @Test
    void readFileRange_startBeyondFile_returnsMessage() throws IOException {
        Path file = tempDir.resolve("short.txt");
        Files.writeString(file, "only one line\n");

        String result = tools.readFileRange(file.toString(), 50, 60);

        assertThat(result).contains("beyond end of file");
    }

    @Test
    void readFileRange_emptyFile_returnsEmpty() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");

        String result = tools.readFileRange(file.toString(), 1, 10);

        assertThat(result).contains("empty file");
    }

    @Test
    void readFileRange_nonexistent_returnsError() {
        String result = tools.readFileRange(tempDir.resolve("nope.txt").toString(), 1, 5);
        assertThat(result).startsWith("Error reading file:");
    }

    // ── searchContent ────────────────────────────────────────────────

    @Test
    void searchContent_findsMatchesInFiles() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Foo.java"), "public class Foo {\n  int count;\n}\n");
        Files.writeString(tempDir.resolve("src/Bar.java"), "public class Bar {\n  Foo foo;\n}\n");

        String result = tools.searchContent(tempDir.toString(), "Foo");

        assertThat(result).contains("Found");
        assertThat(result).contains("Foo.java");
        assertThat(result).contains("Bar.java");
        assertThat(result).contains("public class Foo");
        assertThat(result).contains("Foo foo");
    }

    @Test
    void searchContent_showsLineNumbers() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "alpha\nbeta\ngamma\nbeta again\n");

        String result = tools.searchContent(tempDir.toString(), "beta");

        assertThat(result).contains(":2:");
        assertThat(result).contains(":4:");
    }

    @Test
    void searchContent_noMatches_returnsMessage() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "nothing special here");

        String result = tools.searchContent(tempDir.toString(), "xyznonexistent");

        assertThat(result).contains("No matches");
    }

    @Test
    void searchContent_emptyPattern_returnsError() {
        String result = tools.searchContent(tempDir.toString(), "");
        assertThat(result).contains("Error");
        assertThat(result).contains("empty");
    }

    @Test
    void searchContent_nullPattern_returnsError() {
        String result = tools.searchContent(tempDir.toString(), null);
        assertThat(result).contains("Error");
    }

    @Test
    void searchContent_skipsBuildDirectories() throws IOException {
        Files.createDirectories(tempDir.resolve("target/classes"));
        Files.writeString(tempDir.resolve("target/classes/Compiled.java"), "class Compiled { // searchMe }");
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Source.java"), "class Source { // searchMe }");

        String result = tools.searchContent(tempDir.toString(), "searchMe");

        assertThat(result).contains("Source.java");
        assertThat(result).doesNotContain("Compiled.java");
    }

    @Test
    void searchContent_skipsBinaryFiles() throws IOException {
        Files.writeString(tempDir.resolve("code.java"), "import Something;");
        Files.write(tempDir.resolve("image.png"), new byte[]{0x00, 0x01, 0x02});

        String result = tools.searchContent(tempDir.toString(), "import");
        assertThat(result).contains("code.java");
    }

    @Test
    void searchContent_searchesRecursively() throws IOException {
        Files.createDirectories(tempDir.resolve("a/b/c"));
        Files.writeString(tempDir.resolve("a/b/c/deep.txt"), "findThisDeep");

        String result = tools.searchContent(tempDir.toString(), "findThisDeep");

        assertThat(result).contains("deep.txt");
        assertThat(result).contains("findThisDeep");
    }

    @Test
    void searchContent_matchCountInHeader() throws IOException {
        Files.writeString(tempDir.resolve("multi.txt"), "match\nmatch\nmatch\n");

        String result = tools.searchContent(tempDir.toString(), "match");

        assertThat(result).contains("Found 3 matches");
    }

    // ── listFiles ────────────────────────────────────────────────────

    @Test
    void listFiles_showsFilesAndDirectories() throws IOException {
        Files.createFile(tempDir.resolve("hello.txt"));
        Files.createDirectory(tempDir.resolve("subdir"));

        String result = tools.listFiles(tempDir.toString());

        assertThat(result).contains("[FILE] hello.txt");
        assertThat(result).contains("[DIR]  subdir");
    }

    @Test
    void listFiles_emptyDirectory() {
        String result = tools.listFiles(tempDir.toString());
        assertThat(result).isEmpty();
    }

    @Test
    void listFiles_invalidDirectory_returnsError() {
        String result = tools.listFiles(tempDir.resolve("nonexistent").toString());
        assertThat(result).startsWith("Error listing directory:");
    }

    // ── readFile ─────────────────────────────────────────────────────

    @Test
    void readFile_returnsContent() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello, World!");

        String result = tools.readFile(file.toString());

        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    void readFile_nonexistent_returnsError() {
        String result = tools.readFile(tempDir.resolve("missing.txt").toString());
        assertThat(result).startsWith("Error reading file:");
    }

    // ── writeFile ────────────────────────────────────────────────────

    @Test
    void writeFile_createsNewFile() {
        Path file = tempDir.resolve("new.txt");

        String result = tools.writeFile(file.toString(), "content here");

        assertThat(result).contains("Successfully wrote");
        assertThat(result).contains("12 characters");
        assertThat(file).hasContent("content here");
    }

    @Test
    void writeFile_overwritesExistingFile() throws IOException {
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "old content");

        tools.writeFile(file.toString(), "new content");

        assertThat(file).hasContent("new content");
    }

    @Test
    void writeFile_createsParentDirectories() {
        Path file = tempDir.resolve("a/b/c/deep.txt");

        String result = tools.writeFile(file.toString(), "deep content");

        assertThat(result).contains("Successfully wrote");
        assertThat(file).hasContent("deep content");
    }

    // ── appendToFile ─────────────────────────────────────────────────

    @Test
    void appendToFile_appendsToExisting() throws IOException {
        Path file = tempDir.resolve("append.txt");
        Files.writeString(file, "first ");

        String result = tools.appendToFile(file.toString(), "second");

        assertThat(result).contains("Successfully appended");
        assertThat(file).hasContent("first second");
    }

    @Test
    void appendToFile_createsIfNotExists() {
        Path file = tempDir.resolve("new-append.txt");

        tools.appendToFile(file.toString(), "created");

        assertThat(file).hasContent("created");
    }

    // ── createDirectory ──────────────────────────────────────────────

    @Test
    void createDirectory_createsNestedDirs() {
        Path dir = tempDir.resolve("x/y/z");

        String result = tools.createDirectory(dir.toString());

        assertThat(result).contains("Successfully created directory");
        assertThat(dir).isDirectory();
    }

    @Test
    void createDirectory_existingDir_noError() throws IOException {
        Path dir = tempDir.resolve("existing-dir");
        Files.createDirectory(dir);

        String result = tools.createDirectory(dir.toString());

        assertThat(result).contains("Successfully created directory");
    }

    // ── deleteFile ───────────────────────────────────────────────────

    @Test
    void deleteFile_deletesExistingFile() throws IOException {
        Path file = tempDir.resolve("to-delete.txt");
        Files.writeString(file, "bye");

        String result = tools.deleteFile(file.toString());

        assertThat(result).contains("Successfully deleted");
        assertThat(file).doesNotExist();
    }

    @Test
    void deleteFile_nonexistent_returnsNotFound() {
        String result = tools.deleteFile(tempDir.resolve("ghost.txt").toString());
        assertThat(result).contains("File not found");
    }

    // ── runCommand ───────────────────────────────────────────────────

    @Test
    void runCommand_echoReturnsOutput() {
        String result = tools.runCommand("echo hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void runCommand_multiLineOutput() {
        String result = tools.runCommand("echo 'line1'; echo 'line2'");
        assertThat(result).contains("line1");
        assertThat(result).contains("line2");
    }

    @Test
    void runCommand_nonZeroExitCode_reportsError() {
        String result = tools.runCommand("exit 42");
        assertThat(result).contains("Command exited with code 42");
    }

    @Test
    void runCommand_invalidCommand_reportsError() {
        String result = tools.runCommand("nonexistent_command_xyz_123");
        assertThat(result).containsAnyOf("Command exited with code", "not found");
    }

    @Test
    void runCommand_silentCommand_returnsNoOutputMessage() {
        String result = tools.runCommand("true");
        assertThat(result).isEqualTo("(command completed with no output)");
    }

    // ── runCommandInDirectory ────────────────────────────────────────

    @Test
    void runCommandInDirectory_runsInSpecifiedDir() throws IOException {
        String result = tools.runCommandInDirectory("pwd", tempDir.toString());
        assertThat(Path.of(result).toRealPath().toString())
                .isEqualTo(tempDir.toRealPath().toString());
    }

    @Test
    void runCommandInDirectory_invalidDir_returnsError() {
        String result = tools.runCommandInDirectory("ls", "/nonexistent/path/xyz");
        assertThat(result).contains("not a valid directory");
    }

    @Test
    void runCommandInDirectory_canAccessFilesInDir() throws IOException {
        Files.writeString(tempDir.resolve("data.txt"), "test-content");

        String result = tools.runCommandInDirectory("cat data.txt", tempDir.toString());

        assertThat(result).isEqualTo("test-content");
    }
}
