package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
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
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(BookHotelController.class)
@WithMockUser(roles = "OWNER")
class BookHotelWebIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    HotelBooking hotelBooking;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(Instant.parse("2026-06-01T00:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    @Test
    void getBookHotelFormRendersSuccessfully() {
        assertThat(mockMvc.get().uri("/book-hotel"))
                .hasStatusOk();
    }

    @Test
    void postValidHotelRedirectsToBookedHotels() {
        assertThat(mockMvc.post().uri("/book-hotel")
                .with(csrf())
                .param("hotelBookingId", "550e8400-e29b-41d4-a716-446655440000")
                .param("hotelName", "Grand Hotel")
                .param("street", "123 Main St")
                .param("city", "Springfield")
                .param("region", "IL")
                .param("country", "US")
                .param("postalCode", "62701")
                .param("checkIn", "2026-07-01T15:00")
                .param("checkOut", "2026-07-02T11:00")
                .param("bookingIntent", "TENTATIVE"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");
    }

    @Test
    void postWithCheckInInPastRendersFormAgain() {
        willThrow(new CheckInNotInFuture("Check-in must be in the future"))
                .given(hotelBooking).bookHotel(any(), any());

        assertThat(mockMvc.post().uri("/book-hotel")
                .with(csrf())
                .param("hotelBookingId", "550e8400-e29b-41d4-a716-446655440000")
                .param("hotelName", "Grand Hotel")
                .param("street", "123 Main St")
                .param("city", "Springfield")
                .param("country", "US")
                .param("postalCode", "62701")
                .param("checkIn", "2025-01-01T15:00")
                .param("checkOut", "2025-01-02T11:00")
                .param("bookingIntent", "TENTATIVE"))
                .hasStatusOk();
    }

    @Test
    void postWithUnparsableDateRendersFormInsteadOfThrowing() {
        assertThat(mockMvc.post().uri("/book-hotel")
                .with(csrf())
                .param("hotelBookingId", "550e8400-e29b-41d4-a716-446655440000")
                .param("hotelName", "Grand Hotel")
                .param("checkIn", "notadate")
                .param("checkOut", "2026-07-02T11:00")
                .param("bookingIntent", "TENTATIVE"))
                .hasStatusOk();
    }

    @Test
    void postWithCheckOutSameDayRendersFormAgain() {
        willThrow(new InvalidHotelDateRange("Check-out must be at least one day after check-in"))
                .given(hotelBooking).bookHotel(any(), any());

        assertThat(mockMvc.post().uri("/book-hotel")
                .with(csrf())
                .param("hotelBookingId", "550e8400-e29b-41d4-a716-446655440000")
                .param("hotelName", "Grand Hotel")
                .param("street", "123 Main St")
                .param("city", "Springfield")
                .param("country", "US")
                .param("postalCode", "62701")
                .param("checkIn", "2026-07-01T15:00")
                .param("checkOut", "2026-07-01T23:59")
                .param("bookingIntent", "TENTATIVE"))
                .hasStatusOk();
    }

    @Test
    void hotelNamePastedIntoTheCityErrorsOnTheCityField() {
        willThrow(new InvalidLocationEntry(LocationRole.STAY, LocationField.CITY,
                "This looks like a station or venue name, not a city"))
                .given(hotelBooking).bookHotel(any(), any());

        MvcTestResult result = mockMvc.post().uri("/book-hotel")
                .with(csrf())
                .param("hotelBookingId", "550e8400-e29b-41d4-a716-446655440000")
                .param("hotelName", "Grand Hotel")
                .param("street", "123 Main St")
                .param("city", "Grand Hotel")
                .param("country", "US")
                .param("postalCode", "62701")
                .param("checkIn", "2026-07-01T15:00")
                .param("checkOut", "2026-07-02T11:00")
                .param("bookingIntent", "TENTATIVE")
                .exchange();

        assertThat(result)
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookHotel")
                .hasOnlyFieldErrors("city")
                .hasFieldErrorCode("city", "invalidLocation");
        // The field error is only half of it: the form has to be able to show it.
        assertThat(result)
                .bodyText()
                .contains("<span class=\"error\">This looks like a station or venue name, not a city</span>");
    }

    @Test
    void missingHotelNameErrorsOnTheNameField() {
        willThrow(new InvalidLocationEntry(LocationRole.STAY, LocationField.VENUE_NAME,
                "Name is required"))
                .given(hotelBooking).bookHotel(any(), any());

        assertThat(mockMvc.post().uri("/book-hotel")
                .with(csrf())
                .param("hotelBookingId", "550e8400-e29b-41d4-a716-446655440000")
                .param("hotelName", "")
                .param("street", "123 Main St")
                .param("city", "Springfield")
                .param("country", "US")
                .param("postalCode", "62701")
                .param("checkIn", "2026-07-01T15:00")
                .param("checkOut", "2026-07-02T11:00")
                .param("bookingIntent", "TENTATIVE"))
                .hasStatusOk()
                .model()
                .extractingBindingResult("bookHotel")
                .hasOnlyFieldErrors("hotelName");
    }
}
