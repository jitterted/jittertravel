package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The five real conferences the plan is grounded in, sorted into what each needs next.
 */
class ConferenceDashboardTest {

    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    private final ConferenceDashboard dashboard = new ConferenceDashboard();

    @Test
    void sortsTheRealConferencesIntoTheirGroups() {
        List<DashboardSection> sections = dashboard.sections(List.of(
                watching("J-Fall", ConferenceFormat.CALL_FOR_PAPERS, NOW.plus(Duration.ofDays(30))),
                watching("PLoP", ConferenceFormat.ACCEPTANCE_REQUIRED, null),
                watching("ExploreDDD", ConferenceFormat.CALL_FOR_PAPERS, NOW.minus(Duration.ofDays(5))),
                watching("SoCraTes DE", ConferenceFormat.OPEN_SPACE, null),
                going("dev2next", ConferenceFormat.CALL_FOR_PAPERS, null)
        ), NOW);

        assertThat(sections)
                .extracting(DashboardSection::group)
                .as("declaration order is urgency order: someone else's clock first, nothing owed last")
                .containsExactly(DashboardGroup.CFP_CLOSES_SOON, DashboardGroup.CFP_DATE_UNKNOWN,
                                 DashboardGroup.DECIDE, DashboardGroup.NOTHING_TO_SUBMIT, DashboardGroup.GOING);
        assertThat(sections).allSatisfy(section ->
                assertThat(section.conferences()).hasSize(1));
        assertThat(namesIn(sections, DashboardGroup.CFP_CLOSES_SOON)).containsExactly("J-Fall");
        assertThat(namesIn(sections, DashboardGroup.CFP_DATE_UNKNOWN)).containsExactly("PLoP");
        assertThat(namesIn(sections, DashboardGroup.DECIDE)).containsExactly("ExploreDDD");
        assertThat(namesIn(sections, DashboardGroup.NOTHING_TO_SUBMIT)).containsExactly("SoCraTes DE");
        assertThat(namesIn(sections, DashboardGroup.GOING)).containsExactly("dev2next");
    }

    /**
     * The ordering rule inside {@code groupFor}: a conference Ted has committed to needs nothing
     * from him whatever its CFP is doing. Showing it under "CFP closes soon" would be nagging about
     * a question he has already answered.
     */
    @Test
    void aCommittedConferenceIsGoingEvenWithACfpStillOpen() {
        List<DashboardSection> sections = dashboard.sections(List.of(
                going("dev2next", ConferenceFormat.CALL_FOR_PAPERS, NOW.plus(Duration.ofDays(30)))
        ), NOW);

        assertThat(sections)
                .singleElement()
                .extracting(DashboardSection::group)
                .isEqualTo(DashboardGroup.GOING);
    }

    /**
     * The one group where order is the point: a deadline three days out matters more than one three
     * months out, whatever order the conferences themselves run in.
     */
    @Test
    void theClosingGroupIsSortedBySoonestDeadlineNotByConferenceDate() {
        List<DashboardSection> sections = dashboard.sections(List.of(
                watching("Closes Later", ConferenceFormat.CALL_FOR_PAPERS, NOW.plus(Duration.ofDays(60))),
                watching("Closes Tomorrow", ConferenceFormat.CALL_FOR_PAPERS, NOW.plus(Duration.ofDays(1)))
        ), NOW);

        assertThat(namesIn(sections, DashboardGroup.CFP_CLOSES_SOON))
                .containsExactly("Closes Tomorrow", "Closes Later");
    }

    /**
     * A conference crosses from one group to the next as its deadline passes — no event, no write,
     * just a later {@code now}. That is why status is derived rather than stored.
     */
    @Test
    void theSameConferenceMovesToDecideOnceItsDeadlineHasPassed() {
        Instant deadline = NOW.plus(Duration.ofDays(1));
        List<ConferenceView> conferences =
                List.of(watching("J-Fall", ConferenceFormat.CALL_FOR_PAPERS, deadline));

        assertThat(dashboard.sections(conferences, NOW))
                .singleElement()
                .extracting(DashboardSection::group)
                .isEqualTo(DashboardGroup.CFP_CLOSES_SOON);
        assertThat(dashboard.sections(conferences, deadline.plus(Duration.ofSeconds(1))))
                .singleElement()
                .extracting(DashboardSection::group)
                .isEqualTo(DashboardGroup.DECIDE);
    }

    @Test
    void anOpenSpaceConferenceNeverAsksForACfpDate() {
        List<DashboardSection> sections = dashboard.sections(List.of(
                watching("SoCraTes DE", ConferenceFormat.OPEN_SPACE, null)
        ), NOW);

        assertThat(sections)
                .singleElement()
                .extracting(DashboardSection::group)
                .as("there is no CFP to find a date for")
                .isEqualTo(DashboardGroup.NOTHING_TO_SUBMIT);
    }

    @Test
    void groupsWithNoConferencesAreLeftOutEntirely() {
        List<DashboardSection> sections = dashboard.sections(List.of(
                going("dev2next", ConferenceFormat.CALL_FOR_PAPERS, null)
        ), NOW);

        assertThat(sections)
                .as("a heading over nothing is noise on a page whose job is to be scanned")
                .singleElement()
                .extracting(DashboardSection::group)
                .isEqualTo(DashboardGroup.GOING);
    }

    @Test
    void noConferencesAtAllMeansNoSections() {
        assertThat(dashboard.sections(List.of(), NOW)).isEmpty();
    }

    private static List<String> namesIn(List<DashboardSection> sections, DashboardGroup group) {
        return sections.stream()
                .filter(section -> section.group() == group)
                .flatMap(section -> section.conferences().stream())
                .map(ConferenceView::name)
                .toList();
    }

    private static ConferenceView watching(String name, ConferenceFormat format, Instant cfpClosesOn) {
        return conference(name, AttendanceCommitment.WATCHING, format, cfpClosesOn);
    }

    private static ConferenceView going(String name, ConferenceFormat format, Instant cfpClosesOn) {
        return conference(name, AttendanceCommitment.GOING, format, cfpClosesOn);
    }

    private static ConferenceView conference(String name, AttendanceCommitment commitment,
                                             ConferenceFormat format, Instant cfpClosesOn) {
        return new ConferenceView(
                ConferenceId.random(),
                name,
                "Venue",
                new Address("1 Street", "Ede", "", "6710", "Netherlands", null),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 5, 9, 0), ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 11, 6, 17, 0), ZONE),
                commitment,
                false,
                cfpClosesOn == null ? null : new ZonedTimestamp(cfpClosesOn, ZONE),
                format);
    }
}
