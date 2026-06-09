package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedGatheringsProjector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(PlannedGatheringsController.class)
@WithMockUser(roles = "FAMILY")
class PlannedGatheringsControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PlannedGatheringsProjector projector;

    @Test
    void plannedGatheringsUrlMapsToOkWithHtmlContentType() {
        given(projector.views()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/planned-gatherings"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }
}
