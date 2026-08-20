package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.OneOffTaskView;
import dev.ted.jittertravel.application.OneOffTasks;
import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@WebMvcTest(GeneralController.class)
@WithMockUser(roles = "OWNER")
class GeneralControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @MockitoBean
    BuildProperties buildProperties;

    @MockitoBean
    ScheduleGapProjector scheduleGapProjector;

    @MockitoBean
    OneOffTasks oneOffTasks;

    @MockitoBean
    EventStore eventStore;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setUp() {
        lenient().when(buildProperties.getTime()).thenReturn(Instant.EPOCH);
        lenient().when(clock.instant()).thenReturn(Instant.EPOCH);
        lenient().when(scheduleGapProjector.problems(any())).thenReturn(List.of());
        lenient().when(eventStore.isReadOnly()).thenReturn(false);
        lenient().when(oneOffTasks.outstanding()).thenReturn(List.of());
    }

    @Test
    void homeUrlMapsToOkWithHtmlContentType() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }

    @Test
    void homeHidesPendingBannerWhenNonePending() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("still pending");
    }

    @Test
    void homeShowsPendingBannerWhenCommandsPending() {
        given(persister.countPendingCommands()).willReturn(3);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("3 commands are still pending");
    }

    @Test
    void homeUsesSingularBannerForOnePending() {
        given(persister.countPendingCommands()).willReturn(1);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("1 command is still pending");
    }

    @Test
    void homeHidesTaskBannerWhenNothingIsOutstanding() {
        // The banner exists to prompt action, so having done the work is rewarded with silence.
        given(persister.countPendingCommands()).willReturn(0);
        given(oneOffTasks.outstanding()).willReturn(List.of());

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("need doing after the latest deploy")
                .doesNotContain("needs doing after the latest deploy");
    }

    @Test
    void homeShowsTaskBannerLinkingToTheTaskListWhenTasksAreOutstanding() {
        given(persister.countPendingCommands()).willReturn(0);
        given(oneOffTasks.outstanding()).willReturn(List.of(task("a"), task("b")));

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("2 tasks need doing after the latest deploy")
                .contains("href=\"/admin/tasks\"");
    }

    @Test
    void homeUsesSingularTaskBannerForOneOutstandingTask() {
        given(persister.countPendingCommands()).willReturn(0);
        given(oneOffTasks.outstanding()).willReturn(List.of(task("a")));

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("1 task needs doing after the latest deploy");
    }

    private static OneOffTaskView task(String id) {
        return new OneOffTaskView(id, "Title", "Detail", "/admin", "Do it",
                LocalDate.of(2026, 8, 19), null);
    }

    @Test
    void homeShowsAllNavGroupsForOwner() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("/book-flight")
                .contains(">Admin</span>")
                .contains("/booked-flights")
                .contains("/itinerary")
                .contains("/calendar");
    }

    @Test
    void homeHasNoLocalBadgeByDefault() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("JitterTravel Running Locally");
    }

    @Test
    void scheduleProblemsCardIsAmberAndCountedWhenProblemsExist() {
        given(persister.countPendingCommands()).willReturn(0);
        given(scheduleGapProjector.problems(any())).willReturn(problems(3));

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("3 problems")
                .contains("background: #fef3c7; border-color: #d97706;");   // amber card style
    }

    @Test
    void scheduleProblemsCardIsGreenAndSaysNoProblemsWhenClear() {
        given(persister.countPendingCommands()).willReturn(0);
        given(scheduleGapProjector.problems(any())).willReturn(List.of());

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("No problems")
                .contains("background: #dcfce7; border-color: #16a34a;")   // green card style
                .doesNotContain("background: #fef3c7; border-color: #d97706;"); // not the amber card
    }

    @Test
    void scheduleProblemsCardUsesSingularForOneProblem() {
        given(persister.countPendingCommands()).willReturn(0);
        given(scheduleGapProjector.problems(any())).willReturn(problems(1));

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("1 problem")
                .doesNotContain("1 problems");
    }

    private static List<ScheduleProblem> problems(int count) {
        return Stream.<ScheduleProblem>generate(() ->
                        new ScheduleProblem.MissingHotel("Berlin", LocalDate.now(), LocalDate.now().plusDays(1), ""))
                .limit(count)
                .toList();
    }

    @Test
    void homeShowsReadOnlyBannerWhenEventStoreIsReadOnly() {
        given(persister.countPendingCommands()).willReturn(0);
        given(eventStore.isReadOnly()).willReturn(true);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("Read-only mode")
                .contains("changes are disabled");
    }

    @Test
    void homeHidesReadOnlyBannerWhenEventStoreIsWritable() {
        given(persister.countPendingCommands()).willReturn(0);
        given(eventStore.isReadOnly()).willReturn(false);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain("Read-only mode");
    }

    @Test
    void readOnlyUrlMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/read-only"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }
}
