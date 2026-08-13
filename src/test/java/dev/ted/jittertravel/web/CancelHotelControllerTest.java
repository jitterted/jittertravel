package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelHotel;
import dev.ted.jittertravel.application.HotelDetailsView;
import dev.ted.jittertravel.application.HotelDetailsViewProjector;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.HotelBookingNotFound;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(CancelHotelController.class)
@WithMockUser(roles = "OWNER")
class CancelHotelControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CancelHotel cancelHotel;

    @MockitoBean
    HotelDetailsViewProjector detailsProjector;

    private static HotelDetailsView viewFor(UUID bookingId) {
        return new HotelDetailsView(
                HotelBookingId.of(bookingId),
                "Grand Hotel",
                new Address("123 Unter den Linden", "Berlin", "", "10117", "Germany", "Berlin"),
                LocalDateTime.of(2026, 7, 1, 15, 0),
                LocalDateTime.of(2026, 7, 5, 11, 0),
                BookingIntent.TENTATIVE,
                "https://maps.example/grand",
                LocalDateTime.of(2026, 6, 24, 18, 0));
    }

    @Test
    void getRendersCancelConfirmationPageForKnownBooking() {
        UUID bookingId = UUID.randomUUID();
        given(detailsProjector.findById(any())).willReturn(Optional.of(viewFor(bookingId)));

        assertThat(mockMvc.get().uri("/booked-hotels/" + bookingId + "/cancel"))
                .hasStatusOk()
                .bodyText()
                .contains("Grand Hotel")
                .contains("Berlin, Germany");
    }

    @Test
    void getOnUnknownBookingRedirectsToList() {
        given(detailsProjector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/booked-hotels/" + UUID.randomUUID() + "/cancel"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");
    }

    @Test
    void cancellingRedirectsBackToTheList() {
        assertThat(mockMvc.post().uri("/booked-hotels/" + UUID.randomUUID() + "/cancel")
                .with(csrf())
                .param("reason", "Trip called off"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");
    }

    @Test
    void requestCarriesThePathIdAndTheReason() {
        UUID bookingId = UUID.randomUUID();

        mockMvc.post().uri("/booked-hotels/" + bookingId + "/cancel")
                .with(csrf())
                .param("reason", "Trip called off")
                .exchange();

        then(cancelHotel).should().cancelHotel(any(),
                eq(new CancelHotelRequest(bookingId, "Trip called off")));
    }

    @Test
    void omittedReasonBecomesEmptyRatherThanNull() {
        UUID bookingId = UUID.randomUUID();

        mockMvc.post().uri("/booked-hotels/" + bookingId + "/cancel")
                .with(csrf())
                .exchange();

        then(cancelHotel).should().cancelHotel(any(),
                eq(new CancelHotelRequest(bookingId, "")));
    }

    @Test
    void unknownBookingRedirectsWithAFlashMessage() {
        willThrow(new HotelBookingNotFound("No hotel booking found to cancel"))
                .given(cancelHotel).cancelHotel(any(), any());

        assertThat(mockMvc.post().uri("/booked-hotels/" + UUID.randomUUID() + "/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels")
                .flash().containsKey("notFoundMessage");
    }

    @Test
    void malformedBookingIdRedirectsInsteadOfThrowing() {
        assertThat(mockMvc.post().uri("/booked-hotels/not-a-uuid/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels");

        then(cancelHotel).shouldHaveNoInteractions();
    }
}
