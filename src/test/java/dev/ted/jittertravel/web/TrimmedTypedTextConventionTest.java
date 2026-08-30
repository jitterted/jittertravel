package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.GatheringPlanning;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Pins {@link TrimTypedTextAdvice}: text typed into a form reaches the application trimmed.
 * <p>
 * The advice is action at a distance — no controller mentions it, and deleting it breaks nothing
 * that any other test asserts. This is the test that goes red, in the style of
 * {@code ProblemContextFragmentConventionTest}, which guards the other {@code @ControllerAdvice}
 * the same way.
 * <p>
 * Two controllers, because the binder covers two different bindings and a fix for one would not
 * prove the other: {@link PlanGatheringController} takes an {@code @ModelAttribute} form object,
 * and {@link AdminController} takes a bare {@code @RequestParam}. The padding in every fixture is
 * what an iPhone keyboard actually produces — a trailing space from committing an autocorrect
 * suggestion with the space bar — plus a leading one, which a paste produces.
 * <p>
 * Deliberately <em>not</em> a test of {@code Address}: the fields that end up in an address
 * normalize themselves in their own compact constructors, and would pass this test with the advice
 * deleted. The claim here is about the free text nothing compares — a title, a venue name — which
 * has no other net.
 */
@WebMvcTest({PlanGatheringController.class, AdminController.class})
@WithMockUser(roles = "OWNER")
class TrimmedTypedTextConventionTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        BackupSource backupSource() {
            return new BackupSource("");
        }
    }

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    GatheringPlanning gatheringPlanning;
    @MockitoBean
    BackupService backupService;
    @MockitoBean
    PostgresPersister persister;
    @MockitoBean
    LegacyEventMigration legacyEventMigration;

    @Test
    void freeTextOnAFormReachesTheApplicationTrimmed() {
        assertThat(mockMvc.post().uri("/plan-gathering")
                .with(csrf())
                .param("gatheringId", "550e8400-e29b-41d4-a716-446655440000")
                .param("title", "London Java Community ")
                .param("venueName", " Skills Matter ")
                .param("street", "1 Example Street")
                .param("city", "London")
                .param("region", "")
                .param("country", "GB")
                .param("postalCode", "EC1A 1BB")
                .param("date", "2026-07-15")
                .param("startTime", "18:00")
                .param("endTime", "21:00")
                .param("speaking", "true")
                .param("infoUrl", " https://example.com/meetup "))
                .hasStatus3xxRedirection();

        ArgumentCaptor<PlanGatheringRequest> submitted =
                ArgumentCaptor.forClass(PlanGatheringRequest.class);
        verify(gatheringPlanning).planGathering(submitted.capture(), any());

        assertThat(submitted.getValue().getTitle())
                .isEqualTo("London Java Community");
        assertThat(submitted.getValue().getVenueName())
                .isEqualTo("Skills Matter");
        assertThat(submitted.getValue().getInfoUrl())
                .isEqualTo("https://example.com/meetup");
    }

    /**
     * An empty field stays the empty string rather than becoming null — {@code
     * StringTrimmerEditor(false)}, chosen so the no-null-Strings house rule is untouched. Asserted
     * because the other constructor is the more commonly copied one and would change this quietly.
     */
    @Test
    void aFieldLeftEmptyStaysTheEmptyStringRatherThanBecomingNull() {
        assertThat(mockMvc.post().uri("/plan-gathering")
                .with(csrf())
                .param("gatheringId", "550e8400-e29b-41d4-a716-446655440000")
                .param("title", "London Java Community")
                .param("venueName", "Skills Matter")
                .param("street", "1 Example Street")
                .param("city", "London")
                .param("region", "")
                .param("country", "GB")
                .param("postalCode", "EC1A 1BB")
                .param("date", "2026-07-15")
                .param("startTime", "18:00")
                .param("endTime", "21:00")
                .param("speaking", "true")
                .param("infoUrl", "   "))
                .hasStatus3xxRedirection();

        ArgumentCaptor<PlanGatheringRequest> submitted =
                ArgumentCaptor.forClass(PlanGatheringRequest.class);
        verify(gatheringPlanning).planGathering(submitted.capture(), any());

        assertThat(submitted.getValue().getInfoUrl())
                .isEqualTo("");
    }

    /**
     * The other binding: a bare {@code @RequestParam}, here the Danger Zone's typed confirmation.
     * A padded {@code DELETE} opens the gate, agreed deliberately (Ted, 2026-08-30) — the word
     * proves intent, and a phone's trailing space is not a change of mind. The database is a mock,
     * so this asserts the call was made and truncates nothing.
     */
    @Test
    void aTypedConfirmationWordReachesTheControllerTrimmed() {
        assertThat(mockMvc.post().uri("/admin/database/truncate")
                .with(csrf())
                .param("confirm", " DELETE "))
                .hasStatus3xxRedirection();

        verify(persister).truncateAllTables();
    }
}
