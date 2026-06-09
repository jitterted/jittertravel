package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TrainBooking;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidDateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(BookTrainController.class)
@WithMockUser(roles = "OWNER")
class BookTrainWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    TrainBooking trainBooking;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void getBookTrainFormRendersSuccessfully() {
        assertThat(mockMvc.get().uri("/book-train"))
                .hasStatusOk();
    }

    @Test
    void postValidTrainRedirectsToBookedTrains() {
        assertThat(mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureMapsUrl", "")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalMapsUrl", "")
                .param("arrivalDateTime", "2026-07-01T13:00"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    @Test
    void postWithPastDepartureRendersFormAgain() {
        willThrow(new DepartureNotInFuture("Departure must be in the future"))
                .given(trainBooking).bookTrain(any());

        assertThat(mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2025-01-01T09:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2025-01-01T13:00"))
                .hasStatusOk();
    }

    @Test
    void postWithArrivalBeforeDepartureRendersFormAgain() {
        willThrow(new InvalidDateRange("Arrival must be after departure"))
                .given(trainBooking).bookTrain(any());

        assertThat(mockMvc.post().uri("/book-train")
                .with(csrf())
                .param("trainTripId", "550e8400-e29b-41d4-a716-446655440000")
                .param("departureStationName", "London Euston")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2026-07-01T13:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2026-07-01T09:00"))
                .hasStatusOk();
    }
}
