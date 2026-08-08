package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.CancelHotel;
import dev.ted.jittertravel.domain.CannotCancelAfterCheckIn;
import dev.ted.jittertravel.domain.HotelBookingNotFound;
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

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    CancelHotel cancelHotel;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
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
    void requestCarriesThePathIdTheReasonAndTheBoundaryClock() {
        UUID bookingId = UUID.randomUUID();

        mockMvc.post().uri("/booked-hotels/" + bookingId + "/cancel")
                .with(csrf())
                .param("reason", "Trip called off")
                .exchange();

        then(cancelHotel).should().cancelHotel(any(),
                eq(new CancelHotelRequest(bookingId, "Trip called off")), eq(NOW));
    }

    @Test
    void omittedReasonBecomesEmptyRatherThanNull() {
        UUID bookingId = UUID.randomUUID();

        mockMvc.post().uri("/booked-hotels/" + bookingId + "/cancel")
                .with(csrf())
                .exchange();

        then(cancelHotel).should().cancelHotel(any(),
                eq(new CancelHotelRequest(bookingId, "")), eq(NOW));
    }

    @Test
    void unknownBookingRedirectsWithAFlashMessage() {
        willThrow(new HotelBookingNotFound("No hotel booking found to cancel"))
                .given(cancelHotel).cancelHotel(any(), any(), any());

        assertThat(mockMvc.post().uri("/booked-hotels/" + UUID.randomUUID() + "/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels")
                .flash().containsKey("notFoundMessage");
    }

    @Test
    void cancellingAfterCheckInRedirectsWithAFlashMessage() {
        willThrow(new CannotCancelAfterCheckIn("Check-in has passed"))
                .given(cancelHotel).cancelHotel(any(), any(), any());

        assertThat(mockMvc.post().uri("/booked-hotels/" + UUID.randomUUID() + "/cancel")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/booked-hotels")
                .flash().containsKey("cancelFailedMessage");
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
