package eu.eurocoder.sovereigncli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.shell.interactive.enabled=false",
    "eurocoder.first-run.enabled=false"
})
class SovereignCliApplicationTests {

    @Test
    void contextLoads() {
    }

}
