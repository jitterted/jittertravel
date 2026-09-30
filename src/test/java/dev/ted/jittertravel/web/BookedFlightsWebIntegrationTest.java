package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightsProjector;
import dev.ted.jittertravel.application.CancelledView;
import dev.ted.jittertravel.application.TimeView;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@Tag("spring")
@WebMvcTest(BookedFlightsController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "FAMILY")
class BookedFlightsWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    BookedFlightsProjector projector;

    @Test
    void bookedFlightsPageRendersOk() {
        given(projector.views(any(), any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk();
    }

    @Test
    void cancelledFlightsAreHiddenUnlessAskedFor() {
        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk();

        then(projector).should()
                .views(TimeView.FUTURE, CancelledView.HIDE, WebTodayTestConfig.FIXED_INSTANT);
    }

    @Test
    void cancelledShowAsksTheProjectorForCancelledFlightsAndKeepsTheTimeFilter() {
        given(projector.cancelledCount(TimeView.ALL, WebTodayTestConfig.FIXED_INSTANT)).willReturn(3);

        assertThat(mockMvc.get().uri("/booked-flights?filter=all&cancelled=show"))
                .hasStatusOk()
                .bodyText()
                .contains("aria-pressed=\"true\"")
                .contains("<b>3</b>");

        then(projector).should()
                .views(TimeView.ALL, CancelledView.SHOW, WebTodayTestConfig.FIXED_INSTANT);
    }
}
