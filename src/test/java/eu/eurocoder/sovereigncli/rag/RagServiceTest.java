package eu.eurocoder.sovereigncli.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.agent.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    private static final float[] AUTH_VECTOR = {1.0f, 0.0f, 0.0f};
    private static final float[] DB_VECTOR = {0.0f, 1.0f, 0.0f};
    private static final float[] AUTH_QUERY_VECTOR = {0.95f, 0.1f, 0.0f};
    private static final float[] DB_QUERY_VECTOR = {0.1f, 0.95f, 0.0f};
    private static final float[] GENERIC_VECTOR = {0.5f, 0.5f, 0.0f};

    @Mock
    private ModelManager modelManager;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(modelManager);
    }

    // ── Basic state tests ────────────────────────────────────────────

    @Test
    void isIndexed_falseByDefault() {
        assertThat(ragService.isIndexed()).isFalse();
    }

    @Test
    void supportsCurrentProvider_falseWhenNoEmbeddingModel() {
        when(modelManager.getEmbeddingModel()).thenReturn(null);
        assertThat(ragService.supportsCurrentProvider()).isFalse();
    }

    @Test
    void supportsCurrentProvider_falseWhenEmbeddingThrows() {
        when(modelManager.getEmbeddingModel()).thenThrow(new IllegalStateException("no key"));
        assertThat(ragService.supportsCurrentProvider()).isFalse();
    }

    @Test
    void supportsCurrentProvider_trueWhenModelAvailable() {
        when(modelManager.getEmbeddingModel()).thenReturn(mock(EmbeddingModel.class));
        assertThat(ragService.supportsCurrentProvider()).isTrue();
    }

    @Test
    void retrieveContext_emptyWhenNotIndexed() {
        String result = ragService.retrieveContext("test query");
        assertThat(result).isEmpty();
    }

    @Test
    void formatForPrompt_emptyWhenNotIndexed() {
        String result = ragService.formatForPrompt("test query");
        assertThat(result).isEmpty();
    }

    @Test
    void invalidateIndex_resetsState() {
        ragService.invalidateIndex();
        assertThat(ragService.isIndexed()).isFalse();
    }

    // ── File chunking tests ──────────────────────────────────────────

    @Test
    void chunkFile_createsSegmentsFromJavaFile(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("Test.java");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            content.append("line ").append(i).append("\n");
        }
        Files.writeString(javaFile, content.toString());

        List<TextSegment> segments = new ArrayList<>();
        ragService.chunkFile(tempDir, javaFile, segments);

        assertThat(segments).isNotEmpty();
        assertThat(segments.getFirst().text()).contains("Test.java");
        assertThat(segments.getFirst().metadata().getString("file_path")).isEqualTo("Test.java");
        assertThat(segments.getFirst().metadata().getInteger("start_line")).isEqualTo(1);
    }

    @Test
    void chunkFile_skipsEmptyFile(@TempDir Path tempDir) throws IOException {
        Path emptyFile = tempDir.resolve("Empty.java");
        Files.writeString(emptyFile, "");

        List<TextSegment> segments = new ArrayList<>();
        ragService.chunkFile(tempDir, emptyFile, segments);

        assertThat(segments).isEmpty();
    }

    @Test
    void chunkFile_smallFileSingleChunk(@TempDir Path tempDir) throws IOException {
        Path smallFile = tempDir.resolve("Small.java");
        Files.writeString(smallFile, "public class Small {\n}\n");

        List<TextSegment> segments = new ArrayList<>();
        ragService.chunkFile(tempDir, smallFile, segments);

        assertThat(segments).hasSize(1);
    }

    @Test
    void chunkFile_largeFileMultipleChunks(@TempDir Path tempDir) throws IOException {
        Path bigFile = tempDir.resolve("Big.java");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            content.append("public void method").append(i).append("() { }\n");
        }
        Files.writeString(bigFile, content.toString());

        List<TextSegment> segments = new ArrayList<>();
        ragService.chunkFile(tempDir, bigFile, segments);

        assertThat(segments.size()).isGreaterThan(1);
    }

    @Test
    void chunkFile_preservesLineMetadata(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Meta.java");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 80; i++) {
            content.append("// line ").append(i).append("\n");
        }
        Files.writeString(file, content.toString());

        List<TextSegment> segments = new ArrayList<>();
        ragService.chunkFile(tempDir, file, segments);

        assertThat(segments.size()).isGreaterThanOrEqualTo(2);
        assertThat(segments.get(0).metadata().getInteger("start_line")).isEqualTo(1);
        assertThat(segments.get(0).metadata().getInteger("end_line")).isEqualTo(40);

        TextSegment second = segments.get(1);
        assertThat(second.metadata().getInteger("start_line")).isEqualTo(36);
    }

    // ── Indexing error handling ──────────────────────────────────────

    @Test
    void indexProject_returnsErrorWhenNoEmbeddingModel() {
        when(modelManager.getEmbeddingModel()).thenReturn(null);
        when(modelManager.getProvider()).thenReturn(Provider.ANTHROPIC);

        String result = ragService.indexProject();

        assertThat(result).contains("does not support embeddings");
        assertThat(ragService.isIndexed()).isFalse();
    }

    @Test
    void indexProject_reportsNoIndexableFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("notes.dat"), "binary data");

        EmbeddingModel mockModel = mock(EmbeddingModel.class);
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        String result = ragService.indexProject(tempDir);

        assertThat(result).contains("No indexable files found");
        assertThat(ragService.isIndexed()).isFalse();
    }

    // ── Full pipeline: index → embed → search → retrieve ────────────

    @Test
    void fullPipeline_indexAndRetrieveAuthCode(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModel();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        String indexResult = ragService.indexProject(tempDir);

        assertThat(indexResult).contains("Indexed");
        assertThat(ragService.isIndexed()).isTrue();
        assertThat(ragService.getIndexedFileCount()).isGreaterThanOrEqualTo(2);
        assertThat(ragService.getIndexedChunkCount()).isGreaterThanOrEqualTo(2);

        String context = ragService.retrieveContext("authentication login");
        assertThat(context).isNotEmpty();
        assertThat(context).contains("AuthService.java");
    }

    @Test
    void fullPipeline_indexAndRetrieveDbCode(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModel();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String context = ragService.retrieveContext("database query connection");
        assertThat(context).isNotEmpty();
        assertThat(context).contains("DatabaseService.java");
    }

    @Test
    void fullPipeline_formatForPromptIncludesContextAndInstructions(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModel();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String prompt = ragService.formatForPrompt("authentication login");
        assertThat(prompt).contains("RELEVANT CODE CONTEXT");
        assertThat(prompt).contains("Review the above code context first");
        assertThat(prompt).contains("AuthService.java");
    }

    @Test
    void fullPipeline_formatForPromptEmptyWhenNoMatches(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModelNoMatches();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String prompt = ragService.formatForPrompt("completely unrelated topic xyz");
        assertThat(prompt).isEmpty();
    }

    @Test
    void fullPipeline_retrieveRespectsMaxResults(@TempDir Path tempDir) throws IOException {
        createTestProjectManyFiles(tempDir, 20);
        EmbeddingModel mockModel = buildMockEmbeddingModelUniform();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String contextAll = ragService.retrieveContext("some query", 10);
        String contextLimited = ragService.retrieveContext("some query", 2);

        long allSections = countOccurrences(contextAll, "relevance:");
        long limitedSections = countOccurrences(contextLimited, "relevance:");

        assertThat(limitedSections).isLessThanOrEqualTo(2);
        assertThat(allSections).isGreaterThanOrEqualTo(limitedSections);
    }

    @Test
    void fullPipeline_invalidateResetsAndAllowsReindex(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModel();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);
        assertThat(ragService.isIndexed()).isTrue();
        assertThat(ragService.retrieveContext("authentication")).isNotEmpty();

        ragService.invalidateIndex();
        assertThat(ragService.isIndexed()).isFalse();
        assertThat(ragService.retrieveContext("authentication")).isEmpty();

        ragService.indexProject(tempDir);
        assertThat(ragService.isIndexed()).isTrue();
        assertThat(ragService.retrieveContext("authentication")).isNotEmpty();
    }

    @Test
    void fullPipeline_excludedDirectoriesAreSkipped(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(tempDir.resolve("target/Generated.java"),
                "public class Generated { /* should not be indexed */ }");
        Files.writeString(tempDir.resolve("App.java"),
                "public class App { public void run() { } }");

        EmbeddingModel mockModel = buildMockEmbeddingModelUniform();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        assertThat(ragService.getIndexedFileCount()).isEqualTo(1);
    }

    @Test
    void fullPipeline_oversizedFilesAreSkipped(@TempDir Path tempDir) throws IOException {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 60_000; i++) huge.append("x");
        Files.writeString(tempDir.resolve("Huge.java"), huge.toString());
        Files.writeString(tempDir.resolve("Small.java"), "public class Small {}");

        EmbeddingModel mockModel = buildMockEmbeddingModelUniform();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        assertThat(ragService.getIndexedFileCount()).isEqualTo(1);
    }

    @Test
    void ensureIndexed_buildsIndexLazily(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModelUniform();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        assertThat(ragService.isIndexed()).isFalse();

        ragService.indexProject(tempDir);
        assertThat(ragService.isIndexed()).isTrue();

        boolean result = ragService.ensureIndexed();
        assertThat(result).isTrue();
    }

    @Test
    void ensureIndexed_returnsFalseWhenUnsupported() {
        when(modelManager.getEmbeddingModel()).thenReturn(null);

        boolean result = ragService.ensureIndexed();
        assertThat(result).isFalse();
    }

    @Test
    void retrieveContext_handlesEmbeddingFailureGracefully(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = mock(EmbeddingModel.class);
        when(mockModel.embedAll(anyList())).thenReturn(Response.from(
                List.of(Embedding.from(GENERIC_VECTOR), Embedding.from(GENERIC_VECTOR))
        ));
        when(mockModel.embed(anyString())).thenThrow(new RuntimeException("API quota exceeded"));
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);
        assertThat(ragService.isIndexed()).isTrue();

        String context = ragService.retrieveContext("anything");
        assertThat(context).isEmpty();
    }

    @Test
    void retrieveContext_includesRelevanceScores(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModel();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String context = ragService.retrieveContext("authentication login");
        assertThat(context).containsPattern("relevance: \\d+%");
    }

    // ── Multi-hop retrieval tests ─────────────────────────────────

    @Test
    void multiHop_findsTransitiveDependencies(@TempDir Path tempDir) throws IOException {
        createTestProjectWithTransitiveDeps(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModelMultiHop();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String context = ragService.retrieveContextMultiHop("api controller endpoint");
        assertThat(context).contains("ApiController.java");
        assertThat(context).contains("AuthService.java");
    }

    @Test
    void multiHop_deduplicatesFilesBetweenHops(@TempDir Path tempDir) throws IOException {
        createTestProjectWithTransitiveDeps(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModelMultiHop();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String context = ragService.retrieveContextMultiHop("api controller endpoint");
        long headerCount = countOccurrences(context, "─── ApiController.java");
        assertThat(headerCount).isEqualTo(1);
    }

    @Test
    void multiHop_formatForPromptUsesMultiHop(@TempDir Path tempDir) throws IOException {
        createTestProjectWithTransitiveDeps(tempDir);
        EmbeddingModel mockModel = buildMockEmbeddingModelMultiHop();
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String prompt = ragService.formatForPrompt("api controller endpoint");
        assertThat(prompt).contains("RELEVANT CODE CONTEXT");
        assertThat(prompt).contains("ApiController.java");
        assertThat(prompt).contains("AuthService.java");
    }

    @Test
    void multiHop_emptyWhenNotIndexed() {
        String result = ragService.retrieveContextMultiHop("some query");
        assertThat(result).isEmpty();
    }

    @Test
    void multiHop_fallsBackOnError(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        EmbeddingModel mockModel = mock(EmbeddingModel.class);
        when(mockModel.embedAll(anyList())).thenReturn(Response.from(
                List.of(Embedding.from(GENERIC_VECTOR), Embedding.from(GENERIC_VECTOR))
        ));
        when(mockModel.embed(anyString())).thenThrow(new RuntimeException("API error"));
        when(modelManager.getEmbeddingModel()).thenReturn(mockModel);

        ragService.indexProject(tempDir);

        String context = ragService.retrieveContextMultiHop("anything");
        assertThat(context).isEmpty();
    }

    @Test
    void extractReferences_findsClassNames() {
        var matches = new ArrayList<dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment>>();
        var segment = TextSegment.from(
                "[AuthService.java lines 1-20]\npublic class AuthService {\n" +
                "    private final UserRepository userRepo;\n" +
                "    private final SessionStore sessionStore;\n}",
                new dev.langchain4j.data.document.Metadata()
                        .put("file_path", "AuthService.java")
                        .put("start_line", 1).put("end_line", 20));

        matches.add(new dev.langchain4j.store.embedding.EmbeddingMatch<>(
                0.9, "id1", Embedding.from(AUTH_VECTOR), segment));

        var refs = ragService.extractReferences(matches, "how does auth work");

        assertThat(refs).contains("UserRepository", "SessionStore");
        assertThat(refs).doesNotContain("AuthService");
        assertThat(refs).doesNotContain("String");
    }

    @Test
    void extractReferences_filtersQueryTerms() {
        var segment = TextSegment.from(
                "[FooService.java]\npublic class FooService { " +
                "private BarHandler bar; private BazProcessor baz; }",
                new dev.langchain4j.data.document.Metadata()
                        .put("file_path", "FooService.java")
                        .put("start_line", 1).put("end_line", 5));

        var matches = List.of(new dev.langchain4j.store.embedding.EmbeddingMatch<>(
                0.9, "id1", Embedding.from(GENERIC_VECTOR), segment));

        var refs = ragService.extractReferences(matches, "FooService class");
        assertThat(refs).doesNotContain("FooService");
        assertThat(refs).contains("BarHandler", "BazProcessor");
    }

    @Test
    void extractReferences_filtersCommonTypes() {
        var segment = TextSegment.from(
                "[Service.java]\npublic class Service {\n" +
                "    private final Logger log;\n    private final CustomRepo repo;\n" +
                "    public Optional<String> find() { return Optional.empty(); }\n}",
                new dev.langchain4j.data.document.Metadata()
                        .put("file_path", "Service.java")
                        .put("start_line", 1).put("end_line", 10));

        var matches = List.of(new dev.langchain4j.store.embedding.EmbeddingMatch<>(
                0.9, "id1", Embedding.from(GENERIC_VECTOR), segment));

        var refs = ragService.extractReferences(matches, "service");
        assertThat(refs).contains("CustomRepo");
        assertThat(refs).doesNotContain("Logger", "Optional", "String", "Service");
    }

    // ── Helper: create test project structures ──────────────────────

    private void createTestProject(Path root) throws IOException {
        Files.writeString(root.resolve("AuthService.java"), """
                package com.example.auth;
                
                public class AuthService {
                    private final UserRepository userRepo;
                
                    public AuthService(UserRepository userRepo) {
                        this.userRepo = userRepo;
                    }
                
                    public boolean login(String username, String password) {
                        User user = userRepo.findByUsername(username);
                        return user != null && user.checkPassword(password);
                    }
                
                    public void logout(String sessionId) {
                        sessionStore.invalidate(sessionId);
                    }
                
                    public String createSession(User user) {
                        return sessionStore.create(user.getId());
                    }
                }
                """);

        Files.writeString(root.resolve("DatabaseService.java"), """
                package com.example.db;
                
                import java.sql.Connection;
                import java.sql.PreparedStatement;
                
                public class DatabaseService {
                    private final ConnectionPool pool;
                
                    public DatabaseService(ConnectionPool pool) {
                        this.pool = pool;
                    }
                
                    public ResultSet query(String sql, Object... params) {
                        Connection conn = pool.getConnection();
                        PreparedStatement stmt = conn.prepareStatement(sql);
                        return stmt.executeQuery();
                    }
                
                    public int update(String sql, Object... params) {
                        Connection conn = pool.getConnection();
                        return conn.createStatement().executeUpdate(sql);
                    }
                }
                """);
    }

    private void createTestProjectWithTransitiveDeps(Path root) throws IOException {
        Files.writeString(root.resolve("ApiController.java"), """
                package com.example.api;
                
                public class ApiController {
                    private final AuthService authService;
                    private final ResponseBuilder responseBuilder;
                
                    public ApiController(AuthService authService, ResponseBuilder responseBuilder) {
                        this.authService = authService;
                        this.responseBuilder = responseBuilder;
                    }
                
                    public Response handleLogin(Request request) {
                        boolean ok = authService.login(request.getUsername(), request.getPassword());
                        return responseBuilder.build(ok);
                    }
                
                    public Response handleLogout(Request request) {
                        authService.logout(request.getSessionId());
                        return responseBuilder.success();
                    }
                }
                """);

        Files.writeString(root.resolve("AuthService.java"), """
                package com.example.auth;
                
                public class AuthService {
                    private final UserRepository userRepo;
                    private final SessionStore sessionStore;
                
                    public AuthService(UserRepository userRepo, SessionStore sessionStore) {
                        this.userRepo = userRepo;
                        this.sessionStore = sessionStore;
                    }
                
                    public boolean login(String username, String password) {
                        User user = userRepo.findByUsername(username);
                        return user != null && user.checkPassword(password);
                    }
                
                    public void logout(String sessionId) {
                        sessionStore.invalidate(sessionId);
                    }
                }
                """);

        Files.writeString(root.resolve("UserRepository.java"), """
                package com.example.repo;
                
                public class UserRepository {
                    private final DatabaseConnection dbConn;
                
                    public UserRepository(DatabaseConnection dbConn) {
                        this.dbConn = dbConn;
                    }
                
                    public User findByUsername(String username) {
                        return dbConn.query("SELECT * FROM users WHERE username = ?", username);
                    }
                }
                """);
    }

    private void createTestProjectManyFiles(Path root, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            Files.writeString(root.resolve("Service" + i + ".java"),
                    "public class Service" + i + " {\n    public void process() { }\n}\n");
        }
    }

    // ── Helper: build mock embedding models ─────────────────────────

    /**
     * Builds a mock model that assigns distinct vectors to auth vs database content,
     * allowing cosine-similarity-based retrieval to rank results correctly.
     */
    private EmbeddingModel buildMockEmbeddingModel() {
        EmbeddingModel model = mock(EmbeddingModel.class);

        when(model.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            List<Embedding> embeddings = segments.stream()
                    .map(s -> {
                        String text = s.text().toLowerCase();
                        if (text.contains("auth") || text.contains("login") || text.contains("session")) {
                            return Embedding.from(AUTH_VECTOR);
                        } else if (text.contains("database") || text.contains("query") || text.contains("connection")) {
                            return Embedding.from(DB_VECTOR);
                        }
                        return Embedding.from(GENERIC_VECTOR);
                    })
                    .toList();
            return Response.from(embeddings);
        });

        when(model.embed(anyString())).thenAnswer(invocation -> {
            String query = ((String) invocation.getArgument(0)).toLowerCase();
            if (query.contains("auth") || query.contains("login")) {
                return Response.from(Embedding.from(AUTH_QUERY_VECTOR));
            } else if (query.contains("database") || query.contains("query") || query.contains("connection")) {
                return Response.from(Embedding.from(DB_QUERY_VECTOR));
            }
            return Response.from(Embedding.from(GENERIC_VECTOR));
        });

        return model;
    }

    /**
     * Builds a mock model where query embeddings are anti-correlated to stored embeddings.
     * InMemoryEmbeddingStore uses relevance = (cosine + 1) / 2, so anti-correlated vectors
     * (cosine ≈ -1) produce relevance ≈ 0, well below MIN_SCORE of 0.3.
     */
    private EmbeddingModel buildMockEmbeddingModelNoMatches() {
        EmbeddingModel model = mock(EmbeddingModel.class);

        when(model.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            List<Embedding> embeddings = segments.stream()
                    .map(s -> Embedding.from(new float[]{1.0f, 0.0f, 0.0f}))
                    .toList();
            return Response.from(embeddings);
        });

        when(model.embed(anyString())).thenReturn(
                Response.from(Embedding.from(new float[]{-1.0f, 0.0f, 0.0f}))
        );

        return model;
    }

    /**
     * Builds a mock model where all embeddings are identical (all match equally).
     * The embed(String) stub is lenient because some tests only call indexProject()
     * without triggering retrieval.
     */
    private EmbeddingModel buildMockEmbeddingModelUniform() {
        EmbeddingModel model = mock(EmbeddingModel.class);

        when(model.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            List<Embedding> embeddings = segments.stream()
                    .map(s -> Embedding.from(GENERIC_VECTOR))
                    .toList();
            return Response.from(embeddings);
        });

        lenient().when(model.embed(anyString())).thenReturn(
                Response.from(Embedding.from(GENERIC_VECTOR))
        );

        return model;
    }

    /**
     * Builds a mock model for multi-hop testing with 3 distinct vector spaces:
     * - ApiController content → dimension 0
     * - AuthService content → dimension 1
     * - UserRepository content → dimension 2
     * Queries map to the same space based on keywords, allowing second-hop
     * searches for "AuthService" to find AuthService code.
     */
    private EmbeddingModel buildMockEmbeddingModelMultiHop() {
        EmbeddingModel model = mock(EmbeddingModel.class);

        when(model.embedAll(anyList())).thenAnswer(inv -> {
            List<TextSegment> segments = inv.getArgument(0);
            List<Embedding> embeddings = segments.stream()
                    .map(s -> {
                        String text = s.text().toLowerCase();
                        if (text.contains("apicontroller") || text.contains("handlelogin")) {
                            return Embedding.from(new float[]{1.0f, 0.0f, 0.0f, 0.0f});
                        } else if (text.contains("authservice") && !text.contains("apicontroller")) {
                            return Embedding.from(new float[]{0.0f, 1.0f, 0.0f, 0.0f});
                        } else if (text.contains("userrepository") || text.contains("dbconn")) {
                            return Embedding.from(new float[]{0.0f, 0.0f, 1.0f, 0.0f});
                        }
                        return Embedding.from(new float[]{0.0f, 0.0f, 0.0f, 1.0f});
                    })
                    .toList();
            return Response.from(embeddings);
        });

        when(model.embed(anyString())).thenAnswer(inv -> {
            String query = ((String) inv.getArgument(0)).toLowerCase();
            if (query.contains("api") || query.contains("controller") || query.contains("endpoint")) {
                return Response.from(Embedding.from(new float[]{0.95f, 0.1f, 0.0f, 0.0f}));
            } else if (query.contains("authservice") || query.contains("auth")) {
                return Response.from(Embedding.from(new float[]{0.1f, 0.95f, 0.0f, 0.0f}));
            } else if (query.contains("userrepository") || query.contains("user")) {
                return Response.from(Embedding.from(new float[]{0.0f, 0.1f, 0.95f, 0.0f}));
            }
            return Response.from(Embedding.from(new float[]{0.0f, 0.0f, 0.0f, 0.95f}));
        });

        return model;
    }

    private long countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
