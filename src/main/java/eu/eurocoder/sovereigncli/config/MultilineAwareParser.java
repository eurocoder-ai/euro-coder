package eu.eurocoder.sovereigncli.config;

import org.jline.reader.ParsedLine;
import org.jline.reader.impl.DefaultParser;

import java.util.List;
import java.util.Set;

/**
 * Extends JLine's {@link DefaultParser} to preserve newlines in agent command arguments.
 * <p>
 * When pasted text containing newlines is detected for {@code ask}, {@code plan}, or
 * {@code code} commands, everything after the command name is treated as a single argument
 * with newlines intact. For all other commands (or single-line input), delegates to the
 * default parser unchanged.
 * <p>
 * This works because terminals with bracketed paste support deliver pasted content
 * (including embedded newlines) into JLine's buffer as a single chunk. Typed Enter
 * never places a newline in the buffer — it triggers accept-line immediately.
 */
public class MultilineAwareParser extends DefaultParser {

    private static final Set<String> MULTILINE_COMMANDS = Set.of("ask", "plan", "code");

    @Override
    public ParsedLine parse(String line, int cursor, ParseContext context) {
        if (context != ParseContext.ACCEPT_LINE || line == null || !line.contains("\n")) {
            return super.parse(line, cursor, context);
        }

        String trimmed = line.stripLeading();
        for (String cmd : MULTILINE_COMMANDS) {
            if (trimmed.startsWith(cmd + " ") || trimmed.startsWith(cmd + "\n")) {
                return buildMultilineParsedLine(line, cursor, trimmed, cmd);
            }
        }

        return super.parse(line, cursor, context);
    }

    private ParsedLine buildMultilineParsedLine(String line, int cursor, String trimmed, String command) {
        String argument = trimmed.substring(command.length()).stripLeading();

        List<String> words = argument.isEmpty()
                ? List.of(command)
                : List.of(command, argument);

        int wordIndex = words.size() - 1;
        String currentWord = words.get(wordIndex);
        int wordCursor = Math.min(cursor, currentWord.length());

        return new ArgumentList(
                line, words, wordIndex, wordCursor, cursor,
                null, wordCursor, currentWord.length()
        );
    }
}
