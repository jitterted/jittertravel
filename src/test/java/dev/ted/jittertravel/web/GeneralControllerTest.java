package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleGapProjector;
import dev.ted.jittertravel.application.ScheduleProblem;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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

    @BeforeEach
    void setUp() {
        lenient().when(buildProperties.getTime()).thenReturn(Instant.EPOCH);
        lenient().when(scheduleGapProjector.problems()).thenReturn(List.of());
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
        given(scheduleGapProjector.problems()).willReturn(problems(3));

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("3 problems")
                .contains("background: #fef3c7; border-color: #d97706;");   // amber card style
    }

    @Test
    void scheduleProblemsCardIsGreenAndSaysNoProblemsWhenClear() {
        given(persister.countPendingCommands()).willReturn(0);
        given(scheduleGapProjector.problems()).willReturn(List.of());

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
        given(scheduleGapProjector.problems()).willReturn(problems(1));

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
    void readOnlyUrlMapsToOkWithHtmlContentType() {
        assertThat(mockMvc.get().uri("/read-only"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
    }
}
