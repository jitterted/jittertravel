package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeTrain;
import dev.ted.jittertravel.application.TrainDetailsView;
import dev.ted.jittertravel.application.TrainDetailsViewProjector;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.TrainNotFound;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ChangeTrainController.class)
@WithMockUser(roles = "OWNER")
class ChangeTrainWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ChangeTrain changeTrain;

    @MockitoBean
    TrainDetailsViewProjector detailsProjector;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void getWithKnownTripIdRendersChangeForm() {
        String tripId = UUID.randomUUID().toString();
        TrainDetailsView view = new TrainDetailsView(
                TrainTripId.of(UUID.fromString(tripId)),
                new TrainStationAddress("London Euston", "London", "UK", ""),
                LocalDateTime.of(2026, 7, 1, 9, 0),
                new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", ""),
                LocalDateTime.of(2026, 7, 1, 13, 0),
                "LNER - Azuma 1A34");
        given(detailsProjector.findById(any())).willReturn(Optional.of(view));

        assertThat(mockMvc.get().uri("/booked-trains/" + tripId))
                .hasStatusOk();
    }

    @Test
    void getOnUnknownTripIdRedirectsToBookedTrains() {
        given(detailsProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/booked-trains/" + UUID.randomUUID()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    @Test
    void postWithKnownTripIdRedirectsToBookedTrains() {
        String tripId = UUID.randomUUID().toString();

        assertThat(mockMvc.post().uri("/booked-trains/" + tripId)
                .with(csrf())
                .param("serviceId", "Avanti - 9M12")
                .param("departureStationName", "London Kings Cross")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureMapsUrl", "")
                .param("departureDateTime", "2026-07-01T10:30")
                .param("arrivalStationName", "Edinburgh Waverley")
                .param("arrivalCityName", "Edinburgh")
                .param("arrivalCountry", "UK")
                .param("arrivalMapsUrl", "")
                .param("arrivalDateTime", "2026-07-01T15:00"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    @Test
    void postOnUnknownTripIdRedirectsToBookedTrains() {
        willThrow(new TrainNotFound("No train exists with that tripId"))
                .given(changeTrain).changeTrain(any(), any(), any());

        assertThat(mockMvc.post().uri("/booked-trains/" + UUID.randomUUID())
                .with(csrf())
                .param("departureStationName", "London")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", "Manchester")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2026-07-01T13:00"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-trains");
    }

    @Test
    void postWithPastDepartureRendersFormAgain() {
        willThrow(new DepartureNotInFuture("Departure date/time must be in the future"))
                .given(changeTrain).changeTrain(any(), any(), any());

        assertThat(mockMvc.post().uri("/booked-trains/" + UUID.randomUUID())
                .with(csrf())
                .param("departureStationName", "London")
                .param("departureCityName", "London")
                .param("departureCountry", "UK")
                .param("departureDateTime", "2020-01-01T09:00")
                .param("arrivalStationName", "Manchester")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2020-01-01T13:00"))
                .hasStatusOk();
    }
}