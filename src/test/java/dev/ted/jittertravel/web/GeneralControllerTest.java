package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(GeneralController.class)
@WithMockUser
class GeneralControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @Test
    void homeUrlMapsToOkWithHtmlContentType() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void homeHidesPendingBannerWhenNonePending() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("still pending");
    }

    @Test
    void homeShowsPendingBannerWhenCommandsPending() {
        given(persister.countPendingCommands()).willReturn(3);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("3 commands are still pending");
    }

    @Test
    void homeUsesSingularBannerForOnePending() {
        given(persister.countPendingCommands()).willReturn(1);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("1 command is still pending");
    }

    @Test
    void readOnlyUrlMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/read-only"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }
}
