package eu.eurocoder.sovereigncli.agent;

import eu.eurocoder.sovereigncli.config.RuleManager;
import eu.eurocoder.sovereigncli.rag.RagService;
import eu.eurocoder.sovereigncli.tool.FileSystemTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HybridAgentRouter} — task complexity detection, project context
 * gathering, git context integration, and result formatting.
 * <p>
 * Note: The actual AI agent calls (chat, chatHybrid, chatDirect) require live model
 * connections and are not tested here. These are integration/E2E concerns.
 */
@ExtendWith(MockitoExtension.class)
class HybridAgentRouterTest {

    @Mock
    private ModelManager modelManager;

    @Mock
    private FileSystemTools fileSystemTools;

    @Mock
    private GitContextProvider gitContextProvider;

    @Mock
    private RuleManager ruleManager;

    @Mock
    private RagService ragService;

    // ── isComplexTask ────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "Refactor the authentication module",
            "Please rewrite this function",
            "Redesign the database schema",
            "Architect a new microservice",
            "Migrate from MySQL to PostgreSQL",
            "Convert this Python script to Java",
            "Optimize the search algorithm",
            "Review my pull request",
            "Build a REST API for users",
            "Create a project with Spring Boot",
            "Generate a multi-file React app",
            "Build a full application for inventory",
            "Plan the deployment strategy",
            "Add tests for all classes",
            "Add junit tests for the service layer",
            "Write tests for the API endpoints",
            "Improve test coverage for the project",
            "Implement user authentication"
    })
    void isComplexTask_recognizesComplexKeywords(String message) {
        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        assertThat(router.isComplexTask(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What is Docker?",
            "List my files",
            "Show the current directory",
            "Check running containers",
            "Hello",
            "How do I use git?",
            "Read the README file",
            "Write hello world to test.txt"
    })
    void isComplexTask_recognizesSimpleTasks(String message) {
        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        assertThat(router.isComplexTask(message)).isFalse();
    }

    @Test
    void isComplexTask_isCaseInsensitive() {
        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        assertThat(router.isComplexTask("REFACTOR everything")).isTrue();
        assertThat(router.isComplexTask("Please OPTIMIZE this")).isTrue();
        assertThat(router.isComplexTask("ADD TESTS for everything")).isTrue();
    }

    // ── gatherProjectContext ─────────────────────────────────────────

    @Test
    void gatherProjectContext_returnsTreeFromTools() {
        String fakeTree = "├── src/\n│   └── Main.java\n└── pom.xml";
        when(fileSystemTools.getProjectTree(anyString(), anyInt())).thenReturn(fakeTree);

        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        String context = router.gatherProjectContext();

        assertThat(context).contains("src/");
        assertThat(context).contains("Main.java");
        assertThat(context).contains("pom.xml");
    }

    @Test
    void gatherProjectContext_handlesToolFailure() {
        when(fileSystemTools.getProjectTree(anyString(), anyInt()))
                .thenThrow(new RuntimeException("disk error"));

        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        String context = router.gatherProjectContext();

        assertThat(context).contains("unavailable");
    }

    // ── gatherGitContext ─────────────────────────────────────────────

    @Test
    void gatherGitContext_returnsProviderOutput() {
        String fakeGit = "GIT CONTEXT:\n  Branch: main\n  Working tree: clean";
        when(gitContextProvider.gatherGitContext(null)).thenReturn(fakeGit);

        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        String context = router.gatherGitContext();

        assertThat(context).contains("Branch: main");
        assertThat(context).contains("Working tree: clean");
    }

    @Test
    void gatherGitContext_handlesProviderFailure() {
        when(gitContextProvider.gatherGitContext(null))
                .thenThrow(new RuntimeException("git error"));

        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        String context = router.gatherGitContext();

        assertThat(context).isEmpty();
    }

    // ── gatherRagContext ────────────────────────────────────────────

    @Test
    void gatherRagContext_returnsFormattedContext() {
        String fakeRag = "RELEVANT CODE CONTEXT:\nAuthService.java — login method";
        when(ragService.formatForPrompt("how does login work?")).thenReturn(fakeRag);

        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        String context = router.gatherRagContext("how does login work?");

        assertThat(context).contains("AuthService.java");
        assertThat(context).contains("login method");
    }

    @Test
    void gatherRagContext_returnsEmptyWhenNotIndexed() {
        when(ragService.formatForPrompt(anyString())).thenReturn("");

        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        String context = router.gatherRagContext("some query");

        assertThat(context).isEmpty();
    }

    @Test
    void gatherRagContext_handlesRagFailure() {
        when(ragService.formatForPrompt(anyString()))
                .thenThrow(new RuntimeException("embedding API down"));

        HybridAgentRouter router = new HybridAgentRouter(modelManager, fileSystemTools, gitContextProvider, ruleManager, ragService);
        String context = router.gatherRagContext("some query");

        assertThat(context).isEmpty();
    }

    // ── HybridResult ─────────────────────────────────────────────────

    @Test
    void hybridResult_withPlanAndExecution_showsBoth() {
        var result = new HybridResult(
                "Step 1: Read file\nStep 2: Modify",
                "Done. Modified 3 files.",
                true
        );

        String display = result.toDisplayString();

        assertThat(display).contains("PLAN:");
        assertThat(display).contains("Step 1: Read file");
        assertThat(display).contains("EXECUTION:");
        assertThat(display).contains("Done. Modified 3 files.");
    }

    @Test
    void hybridResult_informationalOnly_showsPlanOnly() {
        var result = new HybridResult(
                "Docker is a containerization platform.",
                null,
                false
        );

        String display = result.toDisplayString();

        assertThat(display).isEqualTo("Docker is a containerization platform.");
        assertThat(display).doesNotContain("PLAN:");
        assertThat(display).doesNotContain("EXECUTION:");
    }

    @Test
    void hybridResult_directMode_showsExecutionOnly() {
        var result = new HybridResult(
                null,
                "Here are your running containers...",
                false
        );

        String display = result.toDisplayString();

        assertThat(display).isEqualTo("Here are your running containers...");
    }

    @Test
    void hybridResult_nullEverything_showsFallback() {
        var result = new HybridResult(null, null, false);

        assertThat(result.toDisplayString()).isEqualTo("(no response)");
    }

    @Test
    void hybridResult_hybridWithNullExecution_showsNoExecutionNeeded() {
        var result = new HybridResult(
                "The plan is complete",
                null,
                true
        );

        String display = result.toDisplayString();

        assertThat(display).contains("PLAN:");
        assertThat(display).contains("(no execution needed)");
    }

    @Test
    void hybridResult_wasHybrid_flag() {
        var hybrid = new HybridResult("plan", "exec", true);
        var direct = new HybridResult(null, "exec", false);

        assertThat(hybrid.wasHybrid()).isTrue();
        assertThat(direct.wasHybrid()).isFalse();
    }
}
