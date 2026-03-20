package eu.eurocoder.sovereigncli.shell;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String CYAN = "\033[36m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";

    private MarkdownRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new MarkdownRenderer();
    }

    // ── Headers ──────────────────────────────────────────────────────

    @Test
    void h1_renderedBoldCyan() {
        String result = renderer.renderLine("# Main Title");

        assertThat(result).contains(BOLD + CYAN + "Main Title" + RESET);
        assertThat(result).doesNotContain("#");
    }

    @Test
    void h2_renderedBoldCyan() {
        String result = renderer.renderLine("## Section");

        assertThat(result).contains(BOLD + CYAN + "Section" + RESET);
    }

    @Test
    void h3_renderedBoldCyan() {
        String result = renderer.renderLine("### Subsection");

        assertThat(result).contains(BOLD + CYAN + "Subsection" + RESET);
    }

    // ── Bold ─────────────────────────────────────────────────────────

    @Test
    void boldAsterisk_renderedBold() {
        String result = renderer.renderLine("This is **bold** text");

        assertThat(result).contains(BOLD + "bold" + RESET);
        assertThat(result).doesNotContain("**");
    }

    @Test
    void boldUnderscore_renderedBold() {
        String result = renderer.renderLine("This is __bold__ text");

        assertThat(result).contains(BOLD + "bold" + RESET);
        assertThat(result).doesNotContain("__");
    }

    @Test
    void multipleBold_allRendered() {
        String result = renderer.renderLine("**first** and **second**");

        assertThat(result).doesNotContain("**");
        assertThat(result).contains("first");
        assertThat(result).contains("second");
    }

    // ── Inline code ──────────────────────────────────────────────────

    @Test
    void inlineCode_renderedYellow() {
        String result = renderer.renderLine("Run `git status` to check");

        assertThat(result).contains(YELLOW + "git status" + RESET);
        assertThat(result).doesNotContain("`");
    }

    @Test
    void multipleInlineCode_allRendered() {
        String result = renderer.renderLine("Use `foo` or `bar`");

        assertThat(result).doesNotContain("`");
        assertThat(result).contains("foo");
        assertThat(result).contains("bar");
    }

    // ── Code blocks ──────────────────────────────────────────────────

    @Test
    void codeBlock_contentRenderedGreen() {
        MarkdownRenderer md = new MarkdownRenderer();

        String fence1 = md.renderLine("```java");
        String codeLine = md.renderLine("public class Foo {}");
        String fence2 = md.renderLine("```");

        assertThat(fence1).contains("java");
        assertThat(codeLine).startsWith(GREEN + "  ");
        assertThat(codeLine).contains("public class Foo {}");
        assertThat(codeLine).endsWith(RESET);
        assertThat(fence2).contains("─");
    }

    @Test
    void codeBlock_noLanguage_showsSeparator() {
        MarkdownRenderer md = new MarkdownRenderer();

        String fence = md.renderLine("```");

        assertThat(fence).contains("─");
    }

    @Test
    void codeBlock_inlineFormattingNotApplied() {
        MarkdownRenderer md = new MarkdownRenderer();
        md.renderLine("```");

        String result = md.renderLine("**not bold** and `not code`");

        assertThat(result).contains("**not bold**");
        assertThat(result).contains("`not code`");
    }

    // ── Bullet lists ─────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"- item", "* item", "+ item"})
    void bulletList_bulletReplacedWithDot(String line) {
        String result = renderer.renderLine(line);

        assertThat(result).contains(CYAN + "•" + RESET);
        assertThat(result).contains("item");
    }

    @Test
    void bulletList_indentPreserved() {
        String result = renderer.renderLine("  - nested item");

        assertThat(result).startsWith("  ");
        assertThat(result).contains("•");
    }

    @Test
    void bulletList_inlineFormattingApplied() {
        String result = renderer.renderLine("- **bold** item");

        assertThat(result).contains("•");
        assertThat(result).contains(BOLD + "bold" + RESET);
        assertThat(result).doesNotContain("**");
    }

    // ── Numbered lists ───────────────────────────────────────────────

    @Test
    void numberedList_numberStyled() {
        String result = renderer.renderLine("1. First item");

        assertThat(result).contains(CYAN + "1." + RESET);
        assertThat(result).contains("First item");
    }

    // ── Blockquotes ──────────────────────────────────────────────────

    @Test
    void blockquote_styledWithBar() {
        String result = renderer.renderLine("> Some quote");

        assertThat(result).contains("│");
        assertThat(result).contains("Some quote");
        assertThat(result).doesNotStartWith(">");
    }

    // ── Horizontal rules ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"---", "***", "___", "-----"})
    void horizontalRule_renderedAsLine(String rule) {
        String result = renderer.renderLine(rule);

        assertThat(result).contains("─".repeat(40));
        assertThat(result).doesNotContain(rule);
    }

    // ── Plain text ───────────────────────────────────────────────────

    @Test
    void plainText_passedThrough() {
        String result = renderer.renderLine("Just regular text");

        assertThat(result).isEqualTo("Just regular text");
    }

    // ── Full render ──────────────────────────────────────────────────

    @Test
    void render_multipleLines_preservesStructure() {
        String markdown = "# Title\n\nSome **bold** text\n\n```java\nint x = 1;\n```";
        String result = renderer.render(markdown);

        assertThat(result).contains("Title");
        assertThat(result).doesNotContain("**");
        assertThat(result).contains(GREEN);
        assertThat(result.split("\n")).hasSize(7);
    }

    @Test
    void render_nullInput_returnsEmpty() {
        assertThat(renderer.render(null)).isEmpty();
    }

    @Test
    void render_emptyInput_returnsEmpty() {
        assertThat(renderer.render("")).isEmpty();
    }

    // ── State reset ──────────────────────────────────────────────────

    @Test
    void render_resetsCodeBlockState() {
        renderer.render("```\ncode\n```");

        String result = renderer.renderLine("**bold**");
        assertThat(result).contains(BOLD);
        assertThat(result).doesNotContain("**");
    }
}
