package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedPrivateEventsProjector;
import dev.ted.jittertravel.application.TimeView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(PlannedPrivateEventsController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "FAMILY")
class PlannedPrivateEventsControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PlannedPrivateEventsProjector projector;

    @Test
    void plannedPrivateEventsUrlMapsToOkWithHtmlContentType() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/planned-private-events"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void absentFilterParameterDefaultsToFuture() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/planned-private-events")).hasStatusOk();

        then(projector).should().views(eq(TimeView.FUTURE), any());
    }

    @Test
    void filterParameterReachesTheProjector() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/planned-private-events?filter=all")).hasStatusOk();

        then(projector).should().views(eq(TimeView.ALL), any());
    }
}
