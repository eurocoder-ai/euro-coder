package eu.eurocoder.sovereigncli.shell;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reads multiline prompts from the terminal, line by line,
 * until the user submits an empty line or presses Ctrl+C.
 */
@Component
public class MultilineInputReader {

    private static final String FIRST_LINE_PROMPT = "  ┃ ";
    private static final String CONTINUATION_PROMPT = "  ┃ ";
    private static final String HINT_FORMAT = "  Enter your %s prompt (empty line to submit):";

    private final Terminal terminal;
    private LineReader lineReader;

    @Autowired
    public MultilineInputReader(Terminal terminal) {
        this.terminal = terminal;
    }

    public MultilineInputReader(Terminal terminal, LineReader lineReader) {
        this.terminal = terminal;
        this.lineReader = lineReader;
    }

    /**
     * @param commandName shown in the hint, e.g. "ask" or "plan"
     * @return the multiline input joined with newlines, or null if cancelled
     */
    public String read(String commandName) {
        printHint(commandName);

        StringBuilder sb = new StringBuilder();
        String prompt = colorize(FIRST_LINE_PROMPT, AttributedStyle.CYAN);

        try {
            while (true) {
                String line = getOrCreateReader().readLine(prompt);
                if (line.isEmpty()) {
                    break;
                }
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(line);
                prompt = colorize(CONTINUATION_PROMPT, AttributedStyle.CYAN);
            }
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }

        return sb.isEmpty() ? null : sb.toString();
    }

    private void printHint(String commandName) {
        String hint = String.format(HINT_FORMAT, commandName);
        terminal.writer().println(colorize(hint, AttributedStyle.YELLOW));
        terminal.writer().flush();
    }

    private synchronized LineReader getOrCreateReader() {
        if (lineReader == null) {
            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
        }
        return lineReader;
    }

    private String colorize(String text, int color) {
        return new AttributedString(text,
                AttributedStyle.DEFAULT.foreground(color)).toAnsi();
    }
}
