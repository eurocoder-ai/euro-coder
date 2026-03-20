package eu.eurocoder.sovereigncli.config;

import org.jline.reader.LineReader;
import org.jline.reader.Parser;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the JLine terminal: SIGINT handling, multiline-aware parser,
 * and bracketed paste support for pasting multiline prompts.
 */
@Configuration
public class TerminalConfig {

    private static final Logger log = LoggerFactory.getLogger(TerminalConfig.class);

    @Bean
    public Parser jlineParser() {
        return new MultilineAwareParser();
    }

    @Bean
    public ApplicationRunner configureTerminal(Terminal terminal,
                                               @Autowired(required = false) LineReader lineReader) {
        return args -> {
            terminal.handle(Terminal.Signal.INT, signal ->
                    log.debug("Ctrl+C caught — ignoring (use 'exit' to quit)")
            );
            log.debug("SIGINT handler registered on terminal");

            if (lineReader != null) {
                lineReader.setOpt(LineReader.Option.BRACKETED_PASTE);
                log.debug("Bracketed paste enabled on LineReader");
            }
        };
    }
}
