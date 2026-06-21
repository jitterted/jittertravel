package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeHotel;
import dev.ted.jittertravel.application.HotelDetailsView;
import dev.ted.jittertravel.application.HotelDetailsViewProjector;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelBookingNotFound;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
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

@WebMvcTest(ChangeHotelController.class)
@WithMockUser(roles = "OWNER")
class ChangeHotelControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ChangeHotel changeHotel;

    @MockitoBean
    HotelDetailsViewProjector detailsProjector;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void getWithKnownBookingIdRendersChangeForm() {
        String bookingId = UUID.randomUUID().toString();
        HotelDetailsView view = new HotelDetailsView(
                HotelBookingId.of(UUID.fromString(bookingId)),
                "Grand Hotel",
                new Address("123 Unter den Linden", "Berlin", "", "10117", "Germany", "Berlin"),
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                BookingIntent.TENTATIVE,
                "https://maps.example/grand");
        given(detailsProjector.findById(any())).willReturn(Optional.of(view));

        assertThat(mockMvc.get().uri("/booked-hotels/" + bookingId))
                .hasStatusOk();
    }

    @Test
    void getOnUnknownBookingIdRedirectsToBookedHotels() {
        given(detailsProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/booked-hotels/" + UUID.randomUUID()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");
    }

    @Test
    void postWithKnownBookingIdRedirectsToBookedHotels() {
        String bookingId = UUID.randomUUID().toString();

        assertThat(mockMvc.post().uri("/booked-hotels/" + bookingId)
                .with(csrf())
                .param("hotelName", "Bavaria Inn")
                .param("street", "1 Marienplatz")
                .param("city", "Munich")
                .param("region", "")
                .param("country", "Germany")
                .param("postalCode", "80331")
                .param("locationForMatching", "Munich")
                .param("mapsUrl", "")
                .param("checkIn", "2026-08-02T16:00")
                .param("checkOut", "2026-08-06T10:00")
                .param("bookingIntent", "FINAL"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");
    }

    @Test
    void postOnUnknownBookingIdRedirectsToBookedHotels() {
        willThrow(new HotelBookingNotFound("No hotel booking exists with that id"))
                .given(changeHotel).changeHotel(any(), any(), any());

        assertThat(mockMvc.post().uri("/booked-hotels/" + UUID.randomUUID())
                .with(csrf())
                .param("hotelName", "Grand Hotel")
                .param("city", "Berlin")
                .param("country", "Germany")
                .param("checkIn", "2026-08-02T16:00")
                .param("checkOut", "2026-08-06T10:00")
                .param("bookingIntent", "FINAL"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");
    }

    @Test
    void postWithPastCheckInRendersFormAgain() {
        willThrow(new CheckInNotInFuture("Check-in date/time must be in the future"))
                .given(changeHotel).changeHotel(any(), any(), any());

        assertThat(mockMvc.post().uri("/booked-hotels/" + UUID.randomUUID())
                .with(csrf())
                .param("hotelName", "Grand Hotel")
                .param("city", "Berlin")
                .param("country", "Germany")
                .param("checkIn", "2020-01-01T15:00")
                .param("checkOut", "2020-01-05T11:00")
                .param("bookingIntent", "FINAL"))
                .hasStatusOk();
    }

    @Test
    void postWithInvalidDateRangeRendersFormAgain() {
        willThrow(new InvalidHotelDateRange("Check-out must be at least one calendar day after check-in"))
                .given(changeHotel).changeHotel(any(), any(), any());

        assertThat(mockMvc.post().uri("/booked-hotels/" + UUID.randomUUID())
                .with(csrf())
                .param("hotelName", "Grand Hotel")
                .param("city", "Berlin")
                .param("country", "Germany")
                .param("checkIn", "2026-08-02T16:00")
                .param("checkOut", "2026-08-02T23:00")
                .param("bookingIntent", "FINAL"))
                .hasStatusOk();
    }
}