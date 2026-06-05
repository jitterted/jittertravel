package dev.ted.jittertravel.web;

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
class ItineraryWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    private static final DateTimeFormatter DATETIME_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void itineraryPageWithNoDateParamRendersSuccessfully() {
        assertThat(mockMvc.get().uri("/itinerary"))
                .hasStatusOk();
    }

    @Test
    void itineraryPageRendersHotelCheckInTimeAndAddress() {
        LocalDate checkInDate = LocalDate.now().plusWeeks(2);
        LocalDateTime checkIn = checkInDate.atTime(15, 0);
        LocalDateTime checkOut = checkInDate.plusDays(2).atTime(11, 0);

        assertThat(mockMvc.post().uri("/book-hotel")
                .param("hotelBookingId", UUID.randomUUID().toString())
                .param("hotelName", "Milton Mill House")
                .param("street", "Milton Hill")
                .param("city", "Steventon")
                .param("region", "Oxfordshire")
                .param("country", "GB")
                .param("postalCode", "OX13 6AF")
                .param("checkIn", checkIn.format(DATETIME_LOCAL))
                .param("checkOut", checkOut.format(DATETIME_LOCAL))
                .param("bookingIntent", "FINAL"))
                .hasStatus3xxRedirection();

        assertThat(mockMvc.get().uri("/itinerary?date=" + checkInDate))
                .hasStatusOk()
                .bodyText()
                .contains("Milton Mill House", "Steventon", "3:00 PM", "google.com/maps");
    }

    @Test
    void itineraryPageRendersHotelCheckOutTimeAndHotelName() {
        LocalDate checkInDate = LocalDate.now().plusWeeks(3);
        LocalDateTime checkIn = checkInDate.atTime(15, 0);
        LocalDate checkOutDate = checkInDate.plusDays(1);
        LocalDateTime checkOut = checkOutDate.atTime(11, 0);

        assertThat(mockMvc.post().uri("/book-hotel")
                .param("hotelBookingId", UUID.randomUUID().toString())
                .param("hotelName", "Grand Hotel Frankfurt")
                .param("street", "Kaiserstrasse 1")
                .param("city", "Frankfurt")
                .param("region", "Hessen")
                .param("country", "DE")
                .param("postalCode", "60311")
                .param("checkIn", checkIn.format(DATETIME_LOCAL))
                .param("checkOut", checkOut.format(DATETIME_LOCAL))
                .param("bookingIntent", "TENTATIVE"))
                .hasStatus3xxRedirection();

        assertThat(mockMvc.get().uri("/itinerary?date=" + checkOutDate))
                .hasStatusOk()
                .bodyText()
                .contains("Grand Hotel Frankfurt", "Frankfurt", "11:00 AM");
    }
}
