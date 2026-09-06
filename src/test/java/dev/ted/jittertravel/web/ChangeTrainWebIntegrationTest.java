package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeTrain;
import dev.ted.jittertravel.application.TrainDetailsView;
import dev.ted.jittertravel.application.TrainDetailsViewProjector;
import dev.ted.jittertravel.domain.DepartureNotInFuture;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.InvalidTrainEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.TrainNotFound;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.UnresolvedStationZone;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

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
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 9, 0), LONDON),
                new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", ""),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 13, 0), LONDON),
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
    void postOnUnknownTripIdReRendersFormWithError() {
        willThrow(new TrainNotFound("No train exists with that tripId"))
                .given(changeTrain).changeTrain(any(), any(), any());

        // The trip vanished between GET and POST; the error must render on the form, never be
        // handed to the view-only /booked-trains list, which silently drops flash messages.
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
                .hasStatusOk()
                .bodyText()
                .contains("No train exists with that tripId");
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

    @Test
    void stationPastedIntoTheDepartureCityErrorsOnThatCityField() {
        willThrow(new InvalidTrainEntry(List.of(
                new InvalidLocationEntry(LocationRole.DEPARTURE, LocationField.CITY,
                        "Venue name, not a city")), List.of()))
                .given(changeTrain).changeTrain(any(), any(), any());

        MvcTestResult result = mockMvc.post().uri("/booked-trains/" + UUID.randomUUID())
                .with(csrf())
                .param("departureStationName", "Frankfurt (Main) Hbf")
                .param("departureCityName", "Frankfurt (Main) Hbf")
                .param("departureCountry", "DE")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", "Manchester Piccadilly")
                .param("arrivalCityName", "Manchester")
                .param("arrivalCountry", "UK")
                .param("arrivalDateTime", "2026-07-01T13:00")
                .exchange();

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("changeTrain")
                .hasOnlyFieldErrors("departureCityName")
                .hasFieldErrorCode("departureCityName", "invalidLocation");
        // The field error is only half of it: the form has to be able to show it.
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Venue name, not a city</span>");
    }

    @Test
    void aBlankCountryErrorsOnThatCountryFieldOnTheChangeFormToo() {
        // change-train.html has its own copy of the fieldsets, so it needs its own copy of the
        // spans — a field error the form cannot render is half a fix.
        willThrow(new InvalidTrainEntry(List.of(), List.of(
                new UnresolvedStationZone(LocationRole.ARRIVAL,
                        UnresolvedStationZone.Cause.COUNTRY_MISSING))))
                .given(changeTrain).changeTrain(any(), any(), any());

        MvcTestResult result = mockMvc.post().uri("/booked-trains/" + UUID.randomUUID())
                .with(csrf())
                .param("departureStationName", "Aschaffenburg Hbf")
                .param("departureCityName", "Aschaffenburg")
                .param("departureCountry", "Germany")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", "Frankfurt (Main) Hbf")
                .param("arrivalCityName", "Frankfurt")
                .param("arrivalCountry", "")
                .param("arrivalDateTime", "2026-07-01T13:00")
                .exchange();

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("changeTrain")
                .hasOnlyFieldErrors("arrivalCountry")
                .hasFieldErrorCode("arrivalCountry", "zoneUnresolved");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Country or time zone required</span>")
                .contains("1 problem to fix below.");
    }

    @Test
    void anUnrecognisedCountryErrorsOnTheZoneSelectOnTheChangeFormToo() {
        willThrow(new InvalidTrainEntry(List.of(), List.of(
                new UnresolvedStationZone(LocationRole.DEPARTURE,
                        UnresolvedStationZone.Cause.COUNTRY_UNRECOGNISED))))
                .given(changeTrain).changeTrain(any(), any(), any());

        MvcTestResult result = mockMvc.post().uri("/booked-trains/" + UUID.randomUUID())
                .with(csrf())
                .param("departureStationName", "Aschaffenburg Hbf")
                .param("departureCityName", "Aschaffenburg")
                .param("departureCountry", "DE")
                .param("departureDateTime", "2026-07-01T09:00")
                .param("arrivalStationName", "Frankfurt (Main) Hbf")
                .param("arrivalCityName", "Frankfurt")
                .param("arrivalCountry", "Germany")
                .param("arrivalDateTime", "2026-07-01T13:00")
                .exchange();

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("changeTrain")
                .hasOnlyFieldErrors("departureZone");
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">Unknown country — pick a zone, "
                          + "or fix Country name above</span>");
    }
}