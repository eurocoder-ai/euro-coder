package eu.eurocoder.sovereigncli.config;

import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the JLine terminal to handle Ctrl+C (SIGINT) gracefully.
 * <p>
 * Without this, Ctrl+C at the shell prompt kills the JVM — especially
 * when running under {@code mvn spring-boot:run} where Maven intercepts
 * SIGINT and sends SIGTERM to the child process.
 * <p>
 * JLine's {@code LineReader.readLine()} temporarily installs its own
 * SIGINT handler that throws {@code UserInterruptException}, so interactive
 * prompts are unaffected by this default handler.
 */
@Configuration
public class TerminalConfig {

    private static final Logger log = LoggerFactory.getLogger(TerminalConfig.class);

    @Bean
    public ApplicationRunner configureTerminalSignals(Terminal terminal) {
        return args -> {
            terminal.handle(Terminal.Signal.INT, signal ->
                    log.debug("Ctrl+C caught — ignoring (use 'exit' to quit)")
            );
            log.debug("SIGINT handler registered on terminal");
        };
    }
}
