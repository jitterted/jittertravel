package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.ConfirmConferenceAttendance;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The Thymeleaf confirm page renders only at request time, so it needs a slice test (template
 * errors do not surface anywhere else). The {@code basis} radio values are asserted against
 * {@link AttendanceBasis} itself, since the template writes them out as literals.
 */
@WebMvcTest(ConfirmConferenceAttendanceController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class ConfirmConferenceAttendanceControllerTest {

    private static final ZoneId VENUE_ZONE = ZoneId.of("Europe/Amsterdam");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ConfirmConferenceAttendance confirmConferenceAttendance;

    @MockitoBean
    ConferenceProjector projector;

    private static ConferenceView viewFor(UUID conferenceId) {
        return new ConferenceView(
                ConferenceId.of(conferenceId),
                "J-Fall",
                "ReeHorst",
                new Address("Bennekomseweg 24", "Ede", "", "6717 LM", "Netherlands", "Ede"),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 9, 0), VENUE_ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 18, 0), VENUE_ZONE),
                AttendanceCommitment.WATCHING, false, SpeakingStatus.NOT_SPEAKING,
                null, "", ConferenceFormat.CALL_FOR_PAPERS, "");
    }

    @Test
    void getRendersConfirmPageForKnownConference() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(any())).willReturn(Optional.of(viewFor(conferenceId)));

        assertThat(mockMvc.get().uri("/conferences/" + conferenceId + "/confirm"))
                .hasStatusOk()
                .bodyText()
                .contains("J-Fall")
                .contains("Ede, Netherlands");
    }

    /**
     * The dashboard's row actions already know the reason — "Ticket Bought" and "Invitation
     * Accepted" are two different reasons reaching this one page — so the radio opens selected and
     * the click here is a confirmation rather than the same decision asked twice.
     */
    @Test
    void aBasisInTheQueryStringArrivesPreselected() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(any())).willReturn(Optional.of(viewFor(conferenceId)));

        assertThat(mockMvc.get().uri("/conferences/" + conferenceId + "/confirm?basis=TICKET_PURCHASED"))
                .hasStatusOk()
                .bodyText()
                .contains("value=\"TICKET_PURCHASED\" checked")
                .doesNotContain("value=\"SPEAKING_ACCEPTED\" checked");
    }

    @Test
    void anUnrecognisedBasisLeavesThePageAsking() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(any())).willReturn(Optional.of(viewFor(conferenceId)));

        assertThat(mockMvc.get().uri("/conferences/" + conferenceId + "/confirm?basis=ATTENDING_ANYWAY"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain(" checked");
    }

    @ParameterizedTest
    @EnumSource(AttendanceBasis.class)
    void confirmPageOffersARadioForEveryBasis(AttendanceBasis basis) {
        // The template spells the three labels out rather than looping the enum, so this is what
        // catches a value added to AttendanceBasis with no way to choose it.
        given(projector.findById(any())).willReturn(Optional.of(viewFor(UUID.randomUUID())));

        assertThat(mockMvc.get().uri("/conferences/" + UUID.randomUUID() + "/confirm"))
                .hasStatusOk()
                .bodyText()
                .contains("value=\"" + basis.name() + "\"");
    }

    @Test
    void getOnUnknownConferenceRedirectsToList() {
        given(projector.findById(any())).willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/conferences/" + UUID.randomUUID() + "/confirm"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void confirmingRedirectsBackToTheList() {
        assertThat(mockMvc.post().uri("/conferences/" + UUID.randomUUID() + "/confirm")
                .with(csrf())
                .param("basis", "TICKET_PURCHASED"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @ParameterizedTest
    @EnumSource(AttendanceBasis.class)
    void requestCarriesThePathIdAndTheChosenBasis(AttendanceBasis basis) {
        UUID conferenceId = UUID.randomUUID();

        mockMvc.post().uri("/conferences/" + conferenceId + "/confirm")
                .with(csrf())
                .param("basis", basis.name())
                .exchange();

        // confirmedOn is the injected Clock's instant, not an ambient read: pinning it here is
        // what makes that visible, since the fixed Clock comes from WebTodayTestConfig.
        then(confirmConferenceAttendance).should().confirmAttendance(any(),
                eq(new ConfirmConferenceAttendanceRequest(conferenceId, basis)),
                eq(WebTodayTestConfig.FIXED_INSTANT));
    }

    @Test
    void missingBasisRendersTheErrorOnTheConfirmPageItself() {
        // /conferences is a j2html view that cannot render a flash, so an unchosen radio must come
        // back on the page hosting the form rather than be redirected away.
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(any())).willReturn(Optional.of(viewFor(conferenceId)));

        assertThat(mockMvc.post().uri("/conferences/" + conferenceId + "/confirm")
                .with(csrf()))
                .hasStatusOk()
                .bodyText()
                .contains("<p class=\"error\">Choose why you are going.</p>")
                .contains("J-Fall");

        then(confirmConferenceAttendance).shouldHaveNoInteractions();
    }

    @Test
    void unrecognizedBasisRendersTheErrorRatherThanFailing() {
        UUID conferenceId = UUID.randomUUID();
        given(projector.findById(any())).willReturn(Optional.of(viewFor(conferenceId)));

        assertThat(mockMvc.post().uri("/conferences/" + conferenceId + "/confirm")
                .with(csrf())
                .param("basis", "ATTENDING_ANYWAY"))
                .hasStatusOk()
                .bodyText()
                .contains("<p class=\"error\">Choose why you are going.</p>");

        then(confirmConferenceAttendance).shouldHaveNoInteractions();
    }

    @Test
    void alreadyGoneConferenceRedirectsToListInsteadOfThrowing() {
        willThrow(new ConferenceNotFound("No conference found to confirm attendance for"))
                .given(confirmConferenceAttendance).confirmAttendance(any(), any(), any());

        assertThat(mockMvc.post().uri("/conferences/" + UUID.randomUUID() + "/confirm")
                .with(csrf())
                .param("basis", "TICKET_PURCHASED"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    @Test
    void malformedConferenceIdRedirectsInsteadOfThrowing() {
        assertThat(mockMvc.post().uri("/conferences/not-a-uuid/confirm")
                .with(csrf())
                .param("basis", "TICKET_PURCHASED"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");

        then(confirmConferenceAttendance).shouldHaveNoInteractions();
    }
}
