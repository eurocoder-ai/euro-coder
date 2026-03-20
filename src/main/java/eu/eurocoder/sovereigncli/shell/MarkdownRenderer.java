package eu.eurocoder.sovereigncli.shell;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts markdown text to ANSI-formatted terminal output.
 * <p>
 * Stateful: tracks code block fences across lines.
 * Create a new instance per response to reset state.
 */
public class MarkdownRenderer {

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String CYAN = "\033[36m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";

    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern BOLD_ASTERISK = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern BOLD_UNDERSCORE = Pattern.compile("__(.+?)__");

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*```(.*)$");
    private static final Pattern HEADER = Pattern.compile("^(#{1,6}) (.+)$");
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^\\s*[-*_]{3,}\\s*$");
    private static final Pattern BULLET_LIST = Pattern.compile("^(\\s*)[*+\\-] (.+)$");
    private static final Pattern NUMBERED_LIST = Pattern.compile("^(\\s*)(\\d+\\.) (.+)$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^> ?(.*)$");

    private boolean inCodeBlock;

    public String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        inCodeBlock = false;
        return Arrays.stream(markdown.split("\n", -1))
                .map(this::renderLine)
                .collect(Collectors.joining("\n"));
    }

    public String renderLine(String line) {
        Matcher codeFence = CODE_FENCE.matcher(line);
        if (codeFence.matches()) {
            boolean opening = !inCodeBlock;
            inCodeBlock = !inCodeBlock;
            if (opening) {
                String lang = codeFence.group(1).trim();
                return lang.isEmpty()
                        ? DIM + "  ─────" + RESET
                        : DIM + "  ── " + lang + " ──" + RESET;
            }
            return DIM + "  ─────" + RESET;
        }

        if (inCodeBlock) {
            return GREEN + "  " + line + RESET;
        }

        if (HORIZONTAL_RULE.matcher(line).matches()) {
            return DIM + "─".repeat(40) + RESET;
        }

        Matcher header = HEADER.matcher(line);
        if (header.matches()) {
            return BOLD + CYAN + header.group(2) + RESET;
        }

        Matcher bq = BLOCKQUOTE.matcher(line);
        if (bq.matches()) {
            return DIM + CYAN + "│ " + RESET + renderInline(bq.group(1));
        }

        Matcher bullet = BULLET_LIST.matcher(line);
        if (bullet.matches()) {
            return bullet.group(1) + CYAN + "•" + RESET + " " + renderInline(bullet.group(2));
        }

        Matcher numbered = NUMBERED_LIST.matcher(line);
        if (numbered.matches()) {
            return numbered.group(1) + CYAN + numbered.group(2) + RESET + " " + renderInline(numbered.group(3));
        }

        return renderInline(line);
    }

    String renderInline(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        text = INLINE_CODE.matcher(text).replaceAll(YELLOW + "$1" + RESET);
        text = BOLD_ASTERISK.matcher(text).replaceAll(BOLD + "$1" + RESET);
        text = BOLD_UNDERSCORE.matcher(text).replaceAll(BOLD + "$1" + RESET);

        return text;
    }
}
