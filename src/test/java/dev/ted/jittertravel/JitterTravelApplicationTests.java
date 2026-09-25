package dev.ted.jittertravel;

import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("spring")
@SpringBootTest
class JitterTravelApplicationTests extends AbstractTestcontainerIntegrationTest {

    @SuppressWarnings("EmptyMethod")
    @Test
    void contextLoads() {
    }

}
