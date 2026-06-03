package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class CalendarWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void calendarRendersBothConferenceAndFlightEntries() {
        LocalDate confStartDate = LocalDate.now().plusDays(4);
        LocalDateTime confStart = confStartDate.atTime(9, 0);
        LocalDateTime confEnd = confStartDate.plusDays(2).atTime(17, 0);

        assertThat(mockMvc.post().uri("/plan-conference")
                .param("conferenceId", UUID.randomUUID().toString())
                .param("name", "Calendar Conference")
                .param("startDate", confStart.toString())
                .param("endDate", confEnd.toString())
                .param("venueName", "Calendar Venue")
                .param("venueStreet", "Calendar Street")
                .param("venueCity", "Calendar City")
                .param("venueCountry", "Calendar Country")
                .param("venuePostalCode", "Calendar Postal Code"))
                .hasStatus3xxRedirection();

        LocalDateTime departure = LocalDate.now().plusDays(2).atTime(13, 55);
        LocalDateTime arrival = departure.plusDays(1).withHour(9).withMinute(45);

        assertThat(mockMvc.post().uri("/book-flight")
                .param("flightId", UUID.randomUUID().toString())
                .param("airline", "United")
                .param("flightNumber", "UA59")
                .param("departureAirport", "SFO")
                .param("departureDateTime", departure.toString())
                .param("arrivalAirport", "FRA")
                .param("arrivalDateTime", arrival.toString()))
                .hasStatus3xxRedirection();

        // The flight may or may not cross a calendar week boundary depending on
        // when this test runs (dates are dynamic relative to today). So we only
        // assert the entries that always render: the conference and the flight's
        // departure segment.
        assertThat(mockMvc.get().uri("/calendar"))
                .hasStatusOk()
                .bodyText()
                .contains(
                        "Calendar Conference",
                        "(Calendar City, Calendar Country)",
                        "✈️ SFO\u2192FRA",
                        "Departs 1:55 PM"
                );
    }
}
