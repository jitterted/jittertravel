package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightsProjector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(BookedFlightsController.class)
class BookedFlightsWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    BookedFlightsProjector projector;

    @Test
    void bookedFlightsPageRendersOk() {
        given(projector.views()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/booked-flights"))
                .hasStatusOk();
    }
}
