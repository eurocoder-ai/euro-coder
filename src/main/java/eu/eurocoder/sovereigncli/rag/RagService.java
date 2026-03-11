package eu.eurocoder.sovereigncli.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import eu.eurocoder.sovereigncli.agent.ModelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RAG (Retrieval-Augmented Generation) service inspired by Recursive Language Models
 * (Zhang et al., 2026 — arxiv:2512.24601).
 * <p>
 * Applies the core RLM principle: treat the codebase as an <b>external environment</b>
 * rather than dumping it into the prompt. The agent accesses it selectively through:
 * <ol>
 *   <li>Semantic pre-filtering — inject only the most relevant chunks</li>
 *   <li>Tool-based refinement — the agent can query for more via {@code semanticSearch}</li>
 *   <li>Symbolic access — existing tools (readFile, searchContent) for precise operations</li>
 * </ol>
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final int CHUNK_SIZE_LINES = 40;
    private static final int CHUNK_OVERLAP_LINES = 5;
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final double MIN_SCORE = 0.3;
    private static final int MAX_RAG_CONTEXT_CHARS = 4000;
    private static final int MAX_FILE_SIZE_BYTES = 50_000;
    private static final int MAX_FILES = 500;
    private static final int EMBEDDING_BATCH_SIZE = 50;
    private static final int MIN_USEFUL_CHARS = 100;

    private static final int FIRST_HOP_RESULTS = 3;
    private static final int SECOND_HOP_RESULTS_PER_QUERY = 2;
    private static final int MAX_SECOND_HOP_TOTAL = 3;
    private static final int MAX_EXTRACTED_REFERENCES = 5;

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("\\b([A-Z][a-z][a-zA-Z0-9]{2,})\\b");

    private static final Set<String> COMMON_TYPE_NAMES = Set.of(
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Character",
            "Object", "Class", "System", "Thread", "Process", "Runtime",
            "Exception", "Error", "Throwable", "RuntimeException",
            "IOException", "NullPointerException", "IllegalArgumentException", "IllegalStateException",
            "List", "Map", "Set", "Collection", "Optional", "Stream", "Iterable", "Iterator",
            "HashMap", "ArrayList", "HashSet", "LinkedList", "TreeMap", "TreeSet", "LinkedHashMap",
            "Override", "Test", "Deprecated", "SuppressWarnings", "FunctionalInterface",
            "Void", "Math", "Arrays", "Collections", "Objects", "Collectors",
            "Logger", "LoggerFactory", "StringBuilder", "StringBuffer",
            "Path", "File", "Files", "Paths",
            "Pattern", "Matcher",
            "Autowired", "Service", "Component", "Repository", "Controller", "Bean", "Configuration"
    );

    private static final Set<String> INDEXED_EXTENSIONS = Set.of(
            ".java", ".py", ".ts", ".js", ".tsx", ".jsx",
            ".go", ".rs", ".rb", ".cpp", ".c", ".h", ".cs",
            ".xml", ".yml", ".yaml", ".json", ".toml",
            ".md", ".txt", ".properties", ".gradle", ".sh",
            ".html", ".css", ".scss"
    );

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "target", "build", "node_modules", ".git", ".eurocoder",
            ".idea", ".vscode", ".cursor", "dist", "out", ".next",
            "__pycache__", ".gradle", "vendor", ".svn", "local_data"
    );

    private final ModelManager modelManager;
    private InMemoryEmbeddingStore<TextSegment> store;
    private EmbeddingModel embeddingModel;
    private int indexedFileCount;
    private int indexedChunkCount;
    private boolean indexed;

    public RagService(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    /**
     * Builds the semantic index if not already built.
     *
     * @return true if index is available, false if provider doesn't support embeddings
     */
    public boolean ensureIndexed() {
        if (indexed) return true;
        if (!supportsCurrentProvider()) return false;
        try {
            indexProject();
            return indexed;
        } catch (Exception e) {
            log.warn("Auto-indexing failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Indexes the current project directory for semantic search.
     *
     * @return summary of indexing operation
     */
    public String indexProject() {
        return indexProject(Paths.get(System.getProperty("user.dir")));
    }

    /**
     * Indexes the given project root for semantic search.
     * Package-private overload for testability.
     */
    String indexProject(Path projectRoot) {
        embeddingModel = modelManager.getEmbeddingModel();
        if (embeddingModel == null) {
            return modelManager.getProvider().displayName() + " does not support embeddings for RAG.";
        }

        store = new InMemoryEmbeddingStore<>();
        indexedFileCount = 0;

        List<TextSegment> allSegments = new ArrayList<>();
        scanProjectFiles(projectRoot, allSegments);

        if (allSegments.isEmpty()) {
            indexed = false;
            return "No indexable files found in project.";
        }

        log.info("Embedding {} chunks from {} files...", allSegments.size(), indexedFileCount);

        for (int i = 0; i < allSegments.size(); i += EMBEDDING_BATCH_SIZE) {
            List<TextSegment> batch = allSegments.subList(
                    i, Math.min(i + EMBEDDING_BATCH_SIZE, allSegments.size()));
            Response<List<Embedding>> response = embeddingModel.embedAll(batch);
            store.addAll(response.content(), batch);
        }

        indexedChunkCount = allSegments.size();
        indexed = true;
        log.info("RAG index built: {} chunks from {} files", indexedChunkCount, indexedFileCount);
        return String.format("Indexed %d chunks from %d files.", indexedChunkCount, indexedFileCount);
    }

    /**
     * Single-hop retrieval — used by the {@code semanticSearch} tool for direct queries.
     */
    public String retrieveContext(String query, int maxResults) {
        if (!indexed || embeddingModel == null || store == null) {
            return "";
        }
        try {
            List<EmbeddingMatch<TextSegment>> matches = searchStore(query, maxResults);
            return matches.isEmpty() ? "" : formatMatches(matches);
        } catch (Exception e) {
            log.warn("RAG retrieval failed: {}", e.getMessage());
            return "";
        }
    }

    public String retrieveContext(String query) {
        return retrieveContext(query, DEFAULT_MAX_RESULTS);
    }

    /**
     * Multi-hop retrieval — first finds directly relevant chunks, then extracts
     * type/class references from those chunks and performs a second round of retrieval
     * to pull in transitive dependencies. This gives the agent a fuller picture
     * (e.g. query about "login" → first hop finds AuthService → second hop finds
     * UserRepository and SessionStore that AuthService depends on).
     */
    String retrieveContextMultiHop(String query) {
        if (!indexed || embeddingModel == null || store == null) {
            return "";
        }
        try {
            List<EmbeddingMatch<TextSegment>> firstHop = searchStore(query, FIRST_HOP_RESULTS);
            if (firstHop.isEmpty()) return "";

            Set<String> seenFiles = firstHop.stream()
                    .map(m -> m.embedded().metadata().getString("file_path"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Set<String> references = extractReferences(firstHop, query);
            List<EmbeddingMatch<TextSegment>> secondHop = new ArrayList<>();
            int added = 0;

            for (String ref : references) {
                if (added >= MAX_SECOND_HOP_TOTAL) break;

                List<EmbeddingMatch<TextSegment>> refMatches = searchStore(ref, SECOND_HOP_RESULTS_PER_QUERY);
                for (EmbeddingMatch<TextSegment> match : refMatches) {
                    String filePath = match.embedded().metadata().getString("file_path");
                    if (!seenFiles.contains(filePath) && added < MAX_SECOND_HOP_TOTAL) {
                        secondHop.add(match);
                        seenFiles.add(filePath);
                        added++;
                    }
                }
            }

            List<EmbeddingMatch<TextSegment>> allMatches = new ArrayList<>(firstHop);
            allMatches.addAll(secondHop);

            if (!secondHop.isEmpty()) {
                log.debug("Multi-hop: {} first-hop + {} second-hop results (refs: {})",
                        firstHop.size(), secondHop.size(), references);
            }

            return formatMatches(allMatches);
        } catch (Exception e) {
            log.warn("Multi-hop retrieval failed, falling back to single-hop: {}", e.getMessage());
            return retrieveContext(query);
        }
    }

    /**
     * Formats RAG context for injection at the top of agent prompts.
     * Uses multi-hop retrieval to include both directly relevant code and
     * transitive dependencies within a strict token budget.
     */
    public String formatForPrompt(String query) {
        if (!indexed) return "";

        String context = retrieveContextMultiHop(query);
        if (context.isBlank()) return "";

        return String.format("""
                RELEVANT CODE CONTEXT (semantic search — review before using tools):
                %s
                
                NOTE: Review the above code context first. If it answers the request, respond directly. \
                Use tools only for write operations or when additional context is needed.
                """, context);
    }

    public boolean isIndexed() {
        return indexed;
    }

    public int getIndexedFileCount() {
        return indexedFileCount;
    }

    public int getIndexedChunkCount() {
        return indexedChunkCount;
    }

    public boolean supportsCurrentProvider() {
        try {
            return modelManager.getEmbeddingModel() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void invalidateIndex() {
        this.indexed = false;
        this.store = null;
        this.embeddingModel = null;
    }

    // ── Search & multi-hop helpers ──────────────────────────────────────

    private List<EmbeddingMatch<TextSegment>> searchStore(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(MIN_SCORE)
                .build();

        return store.search(request).matches();
    }

    /**
     * Extracts PascalCase type/class references from retrieved code chunks.
     * Filters out common JDK types, the file's own class name, and terms
     * already present in the original query to avoid redundant second-hop searches.
     */
    Set<String> extractReferences(List<EmbeddingMatch<TextSegment>> matches, String originalQuery) {
        Set<String> queryWords = Set.of(originalQuery.toLowerCase().split("\\W+"));
        Set<String> references = new LinkedHashSet<>();

        for (EmbeddingMatch<TextSegment> match : matches) {
            String filePath = match.embedded().metadata().getString("file_path");
            String ownClassName = classNameFromPath(filePath);

            Matcher matcher = CLASS_NAME_PATTERN.matcher(match.embedded().text());
            while (matcher.find() && references.size() < MAX_EXTRACTED_REFERENCES) {
                String name = matcher.group(1);
                if (!COMMON_TYPE_NAMES.contains(name)
                        && !name.equals(ownClassName)
                        && !queryWords.contains(name.toLowerCase())) {
                    references.add(name);
                }
            }
        }
        return references;
    }

    private String classNameFromPath(String filePath) {
        String fileName = filePath.contains("/")
                ? filePath.substring(filePath.lastIndexOf('/') + 1)
                : filePath;
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    // ── File scanning & chunking ────────────────────────────────────────

    private void scanProjectFiles(Path root, List<TextSegment> segments) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> isIndexableFile(root, p))
                    .sorted()
                    .limit(MAX_FILES)
                    .forEach(file -> {
                        chunkFile(root, file, segments);
                        indexedFileCount++;
                    });
        } catch (IOException e) {
            log.warn("Error scanning project: {}", e.getMessage());
        }
    }

    private boolean isIndexableFile(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount(); i++) {
            if (EXCLUDED_DIRS.contains(relative.getName(i).toString())) {
                return false;
            }
        }

        try {
            if (Files.size(file) > MAX_FILE_SIZE_BYTES) return false;
        } catch (IOException e) {
            return false;
        }

        String name = file.getFileName().toString().toLowerCase();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx < 0) return false;
        return INDEXED_EXTENSIONS.contains(name.substring(dotIdx));
    }

    void chunkFile(Path root, Path file, List<TextSegment> segments) {
        try {
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty()) return;

            String relativePath = root.relativize(file).toString();
            int step = CHUNK_SIZE_LINES - CHUNK_OVERLAP_LINES;

            for (int start = 0; start < lines.size(); start += step) {
                int end = Math.min(start + CHUNK_SIZE_LINES, lines.size());
                String chunkText = String.join("\n", lines.subList(start, end));
                if (chunkText.isBlank()) continue;

                String textWithPath = String.format("[%s lines %d-%d]\n%s",
                        relativePath, start + 1, end, chunkText);

                Metadata metadata = new Metadata()
                        .put("file_path", relativePath)
                        .put("start_line", start + 1)
                        .put("end_line", end);

                segments.add(TextSegment.from(textWithPath, metadata));
                if (end >= lines.size()) break;
            }
        } catch (IOException e) {
            log.debug("Could not read file for indexing: {}", file);
        }
    }

    private String formatMatches(List<EmbeddingMatch<TextSegment>> matches) {
        StringBuilder sb = new StringBuilder();
        int totalChars = 0;

        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            String text = segment.text();

            if (totalChars + text.length() > MAX_RAG_CONTEXT_CHARS) {
                int remaining = MAX_RAG_CONTEXT_CHARS - totalChars;
                if (remaining > MIN_USEFUL_CHARS) {
                    sb.append(text, 0, remaining).append("\n...(truncated)\n\n");
                }
                break;
            }

            String filePath = segment.metadata().getString("file_path");
            double score = match.score() != null ? match.score() : 0.0;
            sb.append(String.format("─── %s (relevance: %.0f%%) ───\n", filePath, score * 100));
            sb.append(text).append("\n\n");
            totalChars += text.length();
        }

        return sb.toString().trim();
    }
}
