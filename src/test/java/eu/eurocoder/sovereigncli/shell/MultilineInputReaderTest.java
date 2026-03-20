package eu.eurocoder.sovereigncli.shell;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MultilineInputReaderTest {

    private Terminal terminal;
    private LineReader lineReader;
    private MultilineInputReader reader;

    @BeforeEach
    void setUp() {
        terminal = mock(Terminal.class);
        lineReader = mock(LineReader.class);
        when(terminal.writer()).thenReturn(new PrintWriter(new StringWriter()));

        reader = new MultilineInputReader(terminal, lineReader);
    }

    @Test
    void singleLine_returnsLine() {
        when(lineReader.readLine(anyString()))
                .thenReturn("hello world")
                .thenReturn("");

        String result = reader.read("ask");

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void multipleLines_joinedWithNewlines() {
        when(lineReader.readLine(anyString()))
                .thenReturn("line one")
                .thenReturn("line two")
                .thenReturn("line three")
                .thenReturn("");

        String result = reader.read("ask");

        assertThat(result).isEqualTo("line one\nline two\nline three");
    }

    @Test
    void emptyFirstLine_returnsNull() {
        when(lineReader.readLine(anyString())).thenReturn("");

        String result = reader.read("ask");

        assertThat(result).isNull();
    }

    @Test
    void ctrlC_returnsNull() {
        when(lineReader.readLine(anyString()))
                .thenThrow(new UserInterruptException(""));

        String result = reader.read("plan");

        assertThat(result).isNull();
    }

    @Test
    void ctrlD_returnsNull() {
        when(lineReader.readLine(anyString()))
                .thenThrow(new EndOfFileException());

        String result = reader.read("code");

        assertThat(result).isNull();
    }

    @Test
    void ctrlC_afterPartialInput_returnsNull() {
        when(lineReader.readLine(anyString()))
                .thenReturn("partial input")
                .thenThrow(new UserInterruptException(""));

        String result = reader.read("ask");

        assertThat(result).isNull();
    }

    @Test
    void printsHintWithCommandName() {
        StringWriter output = new StringWriter();
        when(terminal.writer()).thenReturn(new PrintWriter(output));
        when(lineReader.readLine(anyString())).thenReturn("");

        reader.read("plan");

        assertThat(output.toString()).contains("plan");
        assertThat(output.toString()).contains("empty line to submit");
    }
}
