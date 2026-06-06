package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(CalendarController.class)
@WithMockUser
class CalendarWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CalendarAggregator calendarAggregator;

    @Test
    void calendarPageRendersOk() {
        given(calendarAggregator.allEntries()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk();
    }
}
