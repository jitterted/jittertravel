package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class HomeWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void homePageHasTitleAndLink() {
        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText().contains("JitterTravel")
                .contains("/plan-conference")
                .contains("Add Planned Conference");
    }
}
