package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.HotelBooking;
import dev.ted.jittertravel.domain.CheckInNotInFuture;
import dev.ted.jittertravel.domain.InvalidHotelDateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(BookHotelController.class)
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
                .given(hotelBooking).bookHotel(any());

        assertThat(mockMvc.post().uri("/book-hotel")
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
    void postWithCheckOutSameDayRendersFormAgain() {
        willThrow(new InvalidHotelDateRange("Check-out must be at least one day after check-in"))
                .given(hotelBooking).bookHotel(any());

        assertThat(mockMvc.post().uri("/book-hotel")
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
}
