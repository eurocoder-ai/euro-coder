package eu.eurocoder.sovereigncli.shell;

import eu.eurocoder.sovereigncli.agent.ModelManager;
import eu.eurocoder.sovereigncli.rag.RagService;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class RagCommands {

    private final RagService ragService;
    private final ModelManager modelManager;

    public RagCommands(RagService ragService, ModelManager modelManager) {
        this.ragService = ragService;
        this.modelManager = modelManager;
    }

    @ShellMethod(key = "rag", value = "Manage RAG semantic search (index, search, status)")
    public String rag(
            @ShellOption(defaultValue = "") String action,
            @ShellOption(defaultValue = "") String query) {

        String normalized = action.trim().toLowerCase();

        return switch (normalized) {
            case "" -> showStatus();
            case "index" -> indexProject();
            case "search" -> searchProject(query);
            case "clear" -> clearIndex();
            default -> colorize("Usage: rag | rag index | rag search <query> | rag clear", AttributedStyle.YELLOW);
        };
    }

    private String showStatus() {
        StringBuilder sb = new StringBuilder("\n");
        sb.append(colorize("  RAG — Semantic Code Search", AttributedStyle.CYAN)).append("\n\n");

        sb.append(colorize("  Provider:  ", AttributedStyle.WHITE))
                .append(modelManager.getProvider().displayName()).append("\n");

        boolean supported = ragService.supportsCurrentProvider();
        sb.append(colorize("  Embeddings: ", AttributedStyle.WHITE))
                .append(supported
                        ? colorize("supported", AttributedStyle.GREEN)
                        : colorize("not available for this provider", AttributedStyle.YELLOW))
                .append("\n");

        if (ragService.isIndexed()) {
            sb.append(colorize("  Status:    ", AttributedStyle.WHITE))
                    .append(colorize("INDEXED", AttributedStyle.GREEN)).append("\n");
            sb.append(String.format("  Files:     %d\n", ragService.getIndexedFileCount()));
            sb.append(String.format("  Chunks:    %d\n", ragService.getIndexedChunkCount()));
        } else {
            sb.append(colorize("  Status:    ", AttributedStyle.WHITE))
                    .append(colorize("NOT INDEXED", AttributedStyle.YELLOW)).append("\n");
        }

        sb.append("\n");
        sb.append(colorize("  Commands:", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    rag index              — Build/rebuild semantic index", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    rag search <query>     — Search codebase by meaning", AttributedStyle.WHITE)).append("\n");
        sb.append(colorize("    rag clear              — Clear the index", AttributedStyle.WHITE));

        return sb.toString();
    }

    private String indexProject() {
        if (!ragService.supportsCurrentProvider()) {
            return colorize(modelManager.getProvider().displayName()
                    + " does not support embeddings. Switch to Mistral, OpenAI, Google, or Ollama.",
                    AttributedStyle.YELLOW);
        }

        System.out.println(colorize("  Building semantic index...", AttributedStyle.CYAN));
        String result = ragService.indexProject();

        return colorize("  " + result, AttributedStyle.GREEN);
    }

    private String searchProject(String query) {
        if (query.isBlank()) {
            return colorize("Usage: rag search <query>  (e.g. 'rag search authentication logic')", AttributedStyle.YELLOW);
        }

        if (!ragService.isIndexed()) {
            if (!ragService.supportsCurrentProvider()) {
                return colorize("RAG not available — "
                        + modelManager.getProvider().displayName() + " does not support embeddings.",
                        AttributedStyle.YELLOW);
            }
            System.out.println(colorize("  Index not built yet — indexing now...", AttributedStyle.CYAN));
            ragService.indexProject();
        }

        String results = ragService.retrieveContext(query);
        if (results.isBlank()) {
            return colorize("  No relevant results found for: " + query, AttributedStyle.YELLOW);
        }

        return "\n" + results;
    }

    private String clearIndex() {
        ragService.invalidateIndex();
        return colorize("  RAG index cleared.", AttributedStyle.GREEN);
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
