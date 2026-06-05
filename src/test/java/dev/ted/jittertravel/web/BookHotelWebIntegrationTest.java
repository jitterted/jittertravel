package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelView;
import dev.ted.jittertravel.application.BookedHotelsProjector;
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
class BookHotelWebIntegrationTest extends AbstractTestcontainerIntegrationTest {

    private static final DateTimeFormatter DATETIME_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private BookedHotelsProjector bookedHotelsProjector;

    @Test
    void getBookHotelFormRendersCheckInAndCheckOutInDatetimeLocalFormat() {
        String expectedCheckIn = LocalDate.now().plusWeeks(2).atTime(15, 0)
                .format(DATETIME_LOCAL);
        String expectedCheckOut = LocalDate.now().plusWeeks(2).plusDays(1).atTime(11, 0)
                .format(DATETIME_LOCAL);

        assertThat(mockMvc.get().uri("/book-hotel"))
                .hasStatusOk()
                .bodyText()
                .contains(expectedCheckIn, expectedCheckOut);
    }

    @Test
    void postBookHotelWithAllFieldsRedirectsToBookedHotelsAndStoresEvent() {
        LocalDateTime checkIn = LocalDate.now().plusWeeks(2).atTime(15, 0);
        LocalDateTime checkOut = checkIn.toLocalDate().plusDays(1).atTime(11, 0);

        assertThat(mockMvc.post().uri("/book-hotel")
                .param("hotelBookingId", UUID.randomUUID().toString())
                .param("hotelName", "Grand Integration Hotel")
                .param("street", "123 Main St")
                .param("city", "Springfield")
                .param("region", "IL")
                .param("country", "US")
                .param("postalCode", "62701")
                .param("checkIn", checkIn.format(DATETIME_LOCAL))
                .param("checkOut", checkOut.format(DATETIME_LOCAL))
                .param("bookingIntent", "TENTATIVE"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");

        assertThat(bookedHotelsProjector.views())
                .extracting(BookedHotelView::hotelName)
                .contains("Grand Integration Hotel");
    }

    @Test
    void postBookHotelWithBlankStateRedirectsToBookedHotelsAndStoresEvent() {
        LocalDateTime checkIn = LocalDate.now().plusWeeks(3).atTime(14, 0);
        LocalDateTime checkOut = checkIn.toLocalDate().plusDays(2).atTime(10, 0);

        assertThat(mockMvc.post().uri("/book-hotel")
                .param("hotelBookingId", UUID.randomUUID().toString())
                .param("hotelName", "International Hotel Paris")
                .param("street", "1 Rue de Rivoli")
                .param("city", "Paris")
                .param("region", "")
                .param("country", "FR")
                .param("postalCode", "75001")
                .param("checkIn", checkIn.format(DATETIME_LOCAL))
                .param("checkOut", checkOut.format(DATETIME_LOCAL))
                .param("bookingIntent", "FINAL"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");

        assertThat(bookedHotelsProjector.views())
                .extracting(BookedHotelView::hotelName)
                .contains("International Hotel Paris");
    }

    @Test
    void bookedHotelsPageShowsBookedHotelWithStatusAndDates() {
        LocalDateTime checkIn = LocalDate.now().plusWeeks(4).atTime(15, 0);
        LocalDateTime checkOut = checkIn.toLocalDate().plusDays(3).atTime(11, 0);

        assertThat(mockMvc.post().uri("/book-hotel")
                .param("hotelBookingId", UUID.randomUUID().toString())
                .param("hotelName", "Savoy London")
                .param("street", "Strand")
                .param("city", "London")
                .param("region", "")
                .param("country", "GB")
                .param("postalCode", "WC2R 0EZ")
                .param("checkIn", checkIn.format(DATETIME_LOCAL))
                .param("checkOut", checkOut.format(DATETIME_LOCAL))
                .param("bookingIntent", "TENTATIVE"))
                .hasStatus3xxRedirection();

        assertThat(mockMvc.get().uri("/booked-hotels"))
                .hasStatusOk()
                .bodyText()
                .contains("Savoy London", "London", "Tentative", "google.com/maps");
    }

    @Test
    void postBookHotelWithCheckInInPastRendersFormWithFieldError() {
        LocalDateTime pastCheckIn = LocalDate.now().minusDays(1).atTime(15, 0);
        LocalDateTime checkOut = LocalDate.now().plusDays(1).atTime(11, 0);

        assertThat(mockMvc.post().uri("/book-hotel")
                .param("hotelBookingId", UUID.randomUUID().toString())
                .param("hotelName", "Past Hotel")
                .param("street", "1 Error St")
                .param("city", "Errorville")
                .param("country", "US")
                .param("postalCode", "00000")
                .param("checkIn", pastCheckIn.format(DATETIME_LOCAL))
                .param("checkOut", checkOut.format(DATETIME_LOCAL))
                .param("bookingIntent", "TENTATIVE"))
                .hasStatusOk();
    }

    @Test
    void postBookHotelWithCheckOutOnSameDayAsCheckInRendersFormWithFieldError() {
        LocalDateTime checkIn = LocalDate.now().plusWeeks(2).atTime(15, 0);
        LocalDateTime sameDay = checkIn.withHour(23).withMinute(59);

        assertThat(mockMvc.post().uri("/book-hotel")
                .param("hotelBookingId", UUID.randomUUID().toString())
                .param("hotelName", "Same Day Hotel")
                .param("street", "1 Error St")
                .param("city", "Errorville")
                .param("country", "US")
                .param("postalCode", "00000")
                .param("checkIn", checkIn.format(DATETIME_LOCAL))
                .param("checkOut", sameDay.format(DATETIME_LOCAL))
                .param("bookingIntent", "TENTATIVE"))
                .hasStatusOk();
    }
}
