package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedTrainView;
import dev.ted.jittertravel.application.BookedTrainsProjector;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class BookTrainWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    private static final DateTimeFormatter DATETIME_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private BookedTrainsProjector bookedTrainsProjector;

    @Test
    void getBookTrainFormRendersDepartureDateTimeInDatetimeLocalFormat() {
        String expectedDeparture = LocalDate.now().plusWeeks(1).atTime(9, 0).format(DATETIME_LOCAL);

        assertThat(mockMvc.get().uri("/book-train"))
                .hasStatusOk()
                .bodyText()
                .contains(expectedDeparture);
    }

    @Test
    void getBookTrainFormRendersArrivalDateTimeInDatetimeLocalFormat() {
        String expectedArrival = LocalDate.now().plusWeeks(1).atTime(13, 0).format(DATETIME_LOCAL);

        assertThat(mockMvc.get().uri("/book-train"))
                .hasStatusOk()
                .bodyText()
                .contains(expectedArrival);
    }

    @Test
    void postWithAllFieldsRedirectsToBookedTrainsAndStoresEvent() {
        LocalDateTime departure = LocalDate.now().plusWeeks(1).atTime(9, 0);
        LocalDateTime arrival = departure.plusHours(4);

        assertThat(mockMvc.post().uri("/book-train")
                .param("trainTripId", UUID.randomUUID().toString())
                .param("serviceId", "LNER - Azuma 1A34")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureMapsUrl", "https://maps.google.com/euston")
                .param("departureDateTime", departure.format(DATETIME_LOCAL))
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalMapsUrl", "https://maps.google.com/piccadilly")
                .param("arrivalDateTime", arrival.format(DATETIME_LOCAL)))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");

        assertThat(bookedTrainsProjector.views())
                .extracting(BookedTrainView::departureStationName)
                .contains("London Euston");
        assertThat(bookedTrainsProjector.views())
                .extracting(BookedTrainView::serviceId)
                .contains("LNER - Azuma 1A34");
    }

    @Test
    void postWithBlankOptionalMapsUrlsRedirectsToBookedTrains() {
        LocalDateTime departure = LocalDate.now().plusWeeks(2).atTime(10, 0);
        LocalDateTime arrival = departure.plusHours(3);

        assertThat(mockMvc.post().uri("/book-train")
                .param("trainTripId", UUID.randomUUID().toString())
                .param("departureStationName", "Paris Gare du Nord")
                .param("departureCityName", "Paris")
                .param("departureCountry", "FR")
                .param("departureMapsUrl", "")
                .param("departureDateTime", departure.format(DATETIME_LOCAL))
                .param("arrivalStationName", "Brussels-Midi")
                .param("arrivalCityName", "Brussels")
                .param("arrivalCountry", "BE")
                .param("arrivalMapsUrl", "")
                .param("arrivalDateTime", arrival.format(DATETIME_LOCAL)))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    @Test
    void postWithPastDepartureRendersFormWithFieldError() {
        LocalDateTime pastDeparture = LocalDate.now().minusDays(1).atTime(9, 0);
        LocalDateTime arrival = LocalDate.now().plusDays(1).atTime(13, 0);

        assertThat(mockMvc.post().uri("/book-train")
                .param("trainTripId", UUID.randomUUID().toString())
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", pastDeparture.format(DATETIME_LOCAL))
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", arrival.format(DATETIME_LOCAL)))
                .hasStatusOk();
    }

    @Test
    void postWithArrivalBeforeDepartureRendersFormWithFieldError() {
        LocalDateTime departure = LocalDate.now().plusWeeks(1).atTime(9, 0);
        LocalDateTime arrivalBefore = departure.minusMinutes(30);

        assertThat(mockMvc.post().uri("/book-train")
                .param("trainTripId", UUID.randomUUID().toString())
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", departure.format(DATETIME_LOCAL))
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", arrivalBefore.format(DATETIME_LOCAL)))
                .hasStatusOk();
    }
}
