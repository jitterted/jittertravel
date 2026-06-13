package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedTrainsProjector;
import dev.ted.jittertravel.application.TimeView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebMvcTest(BookedTrainsController.class)
@WithMockUser(roles = "OWNER")
class BookedTrainsWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    BookedTrainsProjector projector;

    @Test
    void noFilterParamDefaultsToFutureView() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/booked-trains"))
                .hasStatusOk();

        verify(projector).views(eq(TimeView.FUTURE), any(LocalDateTime.class));
    }

    @Test
    void filterAllRequestsAllView() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/booked-trains?filter=all"))
                .hasStatusOk();

        verify(projector).views(eq(TimeView.ALL), any(LocalDateTime.class));
    }

    @Test
    void unknownFilterFallsBackToFutureView() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/booked-trains?filter=bogus"))
                .hasStatusOk();

        verify(projector).views(eq(TimeView.FUTURE), any(LocalDateTime.class));
    }
}