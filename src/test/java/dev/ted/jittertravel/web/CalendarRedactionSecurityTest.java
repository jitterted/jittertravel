package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CalendarAggregator;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryKind;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/**
 * Verifies that the calendar applies redaction for anonymous users and shows
 * full details for authenticated users. Unlike the standard @WebMvcTest slice
 * tests, these assert on response body content because the behavior under test
 * is security-driven (which code path the controller takes), not rendering.
 */
@WebMvcTest(CalendarController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("public")
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass"})
class CalendarRedactionSecurityTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 7, 1, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 7, 3, 11, 0);

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    CalendarAggregator calendarAggregator;

    @BeforeEach
    void setUp() {
        given(calendarAggregator.allEntries()).willReturn(List.of(new CalendarEntry(
                EntryKind.LODGING, CHECK_IN, CHECK_OUT,
                "Grand Hotel", List.of("Berlin, Germany"),
                "Grand Hotel cont'd", List.of("Berlin, Germany"),
                "https://maps.google.com/grand-hotel"
        )));
    }

    @Test
    @WithMockUser(username = "ted")
    void tedSeesFullHotelName() {
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText().contains("Grand Hotel");
    }

    @Test
    @WithMockUser(username = "family")
    void familySeesFullHotelName() {
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText().contains("Grand Hotel");
    }

    @Test
    void anonymousUserSeesRedactedHotel() {
        assertThat(mockMvc.get().uri("/calendar").with(anonymous()))
                .hasStatusOk()
                .bodyText()
                .contains("Hotel")
                .doesNotContain("Grand Hotel");
    }
}
