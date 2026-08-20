package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.OneOffTaskView;
import dev.ted.jittertravel.application.OneOffTasks;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The task page is Thymeleaf, so it only renders at request time — a slice test is the only place a
 * template error surfaces.
 */
@WebMvcTest(OneOffTasksController.class)
@Import(WebTodayTestConfig.class)
@WithMockUser(roles = "OWNER")
class OneOffTasksControllerTest {

    private static final Instant COMPLETED_ON = Instant.parse("2026-08-20T14:00:00Z");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    OneOffTasks oneOffTasks;

    private static OneOffTaskView outstanding() {
        return new OneOffTaskView(
                "normalize-event-log-type", "Run the event_log.type normalization",
                "Back up first: this rewrites rows.",
                "/admin/migrate-legacy-events", "Open the migration page",
                LocalDate.of(2026, 8, 19), null);
    }

    private static OneOffTaskView completed() {
        return new OneOffTaskView(
                "backfill-conference-attendance", "Backfill conference attendance",
                "Confirm each conference through the real UI.",
                "/conferences", "Open the conference list",
                LocalDate.of(2026, 8, 19), COMPLETED_ON);
    }

    @Test
    void outstandingTaskRendersWithItsActionLinkAndAMarkDoneButton() {
        given(oneOffTasks.views()).willReturn(List.of(outstanding()));

        assertThat(mockMvc.get().uri("/admin/tasks"))
                .hasStatusOk()
                .bodyText()
                .contains("Run the event_log.type normalization")
                .contains("Back up first: this rewrites rows.")
                .contains("href=\"/admin/migrate-legacy-events\"")
                .contains("action=\"/admin/tasks/normalize-event-log-type/complete\"")
                .contains("Mark done");
    }

    @Test
    void completedTaskIsGreyedAndOffersNoMarkDoneButton() {
        // Decision 2 (2026-08-19): a done-but-still-declared task stays visible here, greyed, as
        // the reminder that its declaration is now dead code — but it can no longer be ticked off.
        given(oneOffTasks.views()).willReturn(List.of(completed()));

        assertThat(mockMvc.get().uri("/admin/tasks"))
                .hasStatusOk()
                .bodyText()
                .contains("task--completed")
                .contains("OneOffTaskRegistry")
                .doesNotContain("Mark done");
    }

    @Test
    void emptyRegistryRendersTheEmptyState() {
        given(oneOffTasks.views()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/admin/tasks"))
                .hasStatusOk()
                .bodyText()
                .contains("Nothing declared.");
    }

    @Test
    void markingATaskDoneRecordsItWithTheInjectedClockAndReturnsToTheList() {
        assertThat(mockMvc.post().uri("/admin/tasks/normalize-event-log-type/complete")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/admin/tasks");

        then(oneOffTasks).should().complete(any(), eq("normalize-event-log-type"),
                eq(WebTodayTestConfig.FIXED_INSTANT));
    }

    @Test
    void readOnlyModeSendsTheOwnerToTheReadOnlyPageInsteadOfFailing() {
        willThrow(new ReadOnlyModeException("read-only"))
                .given(oneOffTasks).complete(any(), any(), any());

        assertThat(mockMvc.post().uri("/admin/tasks/normalize-event-log-type/complete")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/read-only");
    }
}
