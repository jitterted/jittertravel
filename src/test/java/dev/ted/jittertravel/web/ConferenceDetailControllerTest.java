package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceDetailView;
import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;
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
import static org.mockito.BDDMockito.given;

@WebMvcTest(ConferenceDetailController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class ConferenceDetailControllerTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private static final UUID CONFERENCE_UUID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    ConferenceProjector projector;

    @Test
    void rendersTheConferenceAsHtml() {
        given(projector.detailById(ConferenceId.of(CONFERENCE_UUID)))
                .willReturn(Optional.of(jFall()));

        assertThat(mockMvc.get().uri("/conferences/" + CONFERENCE_UUID))
                .hasStatusOk()
                .hasContentTypeCompatibleWith("text/html")
                .bodyText()
                .contains("<title>J-Fall</title>")
                .contains("Reehorst");
    }

    /**
     * The list cannot render a flash, so an unknown conference goes back to it silently — the same
     * shape every other conference route uses.
     */
    @Test
    void anUnknownConferenceRedirectsToTheList() {
        given(projector.detailById(ConferenceId.of(CONFERENCE_UUID)))
                .willReturn(Optional.empty());

        assertThat(mockMvc.get().uri("/conferences/" + CONFERENCE_UUID))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    /** A hand-edited URL is not an error page — it is the same "no such conference" answer. */
    @Test
    void aMalformedIdRedirectsToTheListRatherThanFailing() {
        assertThat(mockMvc.get().uri("/conferences/not-a-uuid"))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/conferences");
    }

    /**
     * A data problem is <strong>not</strong> a missing conference. The {@code try} in the controller
     * wraps the id parse and nothing else, so an {@code IllegalArgumentException} raised while the
     * view is being built — a {@code ZonedTimestamp} with no zone, a future component validation —
     * still propagates. Widening it to cover the lookup would turn that into the same silent
     * redirect a typo'd id gets, and the conference would read as "not there" with nothing logged.
     */
    @Test
    void aFailureBuildingTheViewIsNotDisguisedAsAMissingConference() {
        given(projector.detailById(ConferenceId.of(CONFERENCE_UUID)))
                .willThrow(new IllegalArgumentException("zone is required"));

        assertThat(mockMvc.get().uri("/conferences/" + CONFERENCE_UUID))
                .hasFailed()
                .failure()
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("zone is required");
    }

    /**
     * The page carries non-ASCII (the em dash in its own prose), so the charset has to be declared
     * on the response rather than left to the container's default (CLAUDE.md, j2html encoding).
     */
    @Test
    void theResponseDeclaresUtf8() {
        given(projector.detailById(ConferenceId.of(CONFERENCE_UUID)))
                .willReturn(Optional.of(jFall()));

        String contentType = mockMvc.get().uri("/conferences/" + CONFERENCE_UUID)
                                    .exchange().getResponse().getContentType();

        assertThat(contentType).isEqualTo("text/html;charset=UTF-8");
    }

    private static ConferenceDetailView jFall() {
        return new ConferenceDetailView(
                ConferenceId.of(CONFERENCE_UUID), "J-Fall", "Reehorst",
                new Address("1 Conf St", "Ede", "", "6710", "Netherlands", null),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 9, 0), AMSTERDAM),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 7, 18, 0), AMSTERDAM),
                AttendanceCommitment.GOING, AttendanceBasis.TICKET_PURCHASED, false,
                SpeakingStatus.NOT_SPEAKING, null, "", ConferenceFormat.CALL_FOR_PAPERS, "");
    }
}
