package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.application.HotelBooking;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Pins {@link RequiredEntryAdvice}: a date or time left blank comes back as a field error, and the
 * write path is never reached.
 * <p>
 * The advice is action at a distance — no controller mentions it — so this is the test that goes
 * red when it is deleted, the same arrangement {@code TrimmedTypedTextConventionTest} has for the
 * other binder advice.
 * <p>
 * Before it existed, an empty {@code datetime-local} bound to null and the handler called
 * {@code ZonedTimestamp.fromLocal(null, zone)}: a 500 on a form that had already dropped
 * {@code required} from its inputs, so nothing stopped it.
 * <p>
 * Two controllers, because the two claims are different: a gathering shows the required entry
 * working, and a hotel's free-cancellation deadline shows {@link OptionalEntry} opting out of it.
 */
@WebMvcTest({PlanGatheringController.class, BookHotelController.class})
@WithMockUser(roles = "OWNER")
class RequiredEntryConventionTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    GatheringPlanning gatheringPlanning;

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
    void aTimeLeftBlankIsAFieldErrorAndTheServiceIsNeverCalled() {
        assertThat(gathering("2026-07-15", "", "21:00"))
                .hasStatusOk()
                .bodyText()
                .contains("Required");

        verifyNoInteractions(gatheringPlanning);
    }

    /**
     * The message belongs to the blank input and to no other. Asserted on the binding result
     * rather than on the markup, because the rendered span carries no field name — position in the
     * page is what says which field it is about, and only the binding result names it here.
     */
    @Test
    void onlyTheBlankFieldIsMarked() {
        MvcTestResult result = gathering("2026-07-15", "18:00", "").exchange();

        assertThat(result).model().extractingBindingResult("planGathering")
                .hasFieldErrors("endTime");
        assertThat(result).bodyText()
                .contains("<span class=\"error\">Required</span>");
    }

    @Test
    void aDateLeftBlankIsReportedToo() {
        assertThat(gathering("", "18:00", "21:00")).model()
                .extractingBindingResult("planGathering")
                .hasFieldErrors("date");

        verifyNoInteractions(gatheringPlanning);
    }

    /**
     * The opt-out, and why it is an annotation rather than a list somewhere: a hotel's
     * free-cancellation deadline is genuinely optional, and requiring it would make every booking
     * without one impossible to record.
     */
    @Test
    void anOptionalEntryMayStillBeLeftBlank() {
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
                .param("cancelBy", "")
                .param("bookingIntent", "TENTATIVE"))
                .hasStatus3xxRedirection();

        verify(hotelBooking).bookHotel(any(), any());
    }

    /** A complete gathering apart from the three temporal values, which each case supplies. */
    private MockMvcTester.MockMvcRequestBuilder gathering(String date, String start, String end) {
        return mockMvc.post().uri("/plan-gathering")
                .with(csrf())
                .param("gatheringId", "550e8400-e29b-41d4-a716-446655440000")
                .param("title", "London Java Community")
                .param("venueName", "Skills Matter")
                .param("street", "1 Example Street")
                .param("city", "London")
                .param("region", "")
                .param("country", "GB")
                .param("postalCode", "EC1A 1BB")
                .param("date", date)
                .param("startTime", start)
                .param("endTime", end);
    }
}
