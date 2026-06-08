package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(GeneralController.class)
@WithMockUser
class GeneralControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @Test
    void homeUrlMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void readOnlyUrlMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/read-only"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }
}
