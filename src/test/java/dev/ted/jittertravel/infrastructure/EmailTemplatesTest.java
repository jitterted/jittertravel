package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The mechanics of the email files; what each email says is {@code FamilyNotificationMessagesTest}. */
class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates();

    @Test
    void theFirstLineIsTheSubjectAndTheRestIsTheBody() {
        FamilyMessage message = templates.render("test-email", Map.of());

        assertThat(message.subject())
                .isEqualTo("JitterTravel test email");
        assertThat(message.textContent())
                .startsWith("This is a test from JitterTravel")
                .doesNotContain("Subject:");
    }

    /**
     * The snippet a template uses for an optional link: it shows when {@code calendarUrl} is given
     * and leaves no trace when it is not (no base URL configured), which must not be an error.
     */
    @Test
    void anOptionalLinkLineAppearsWhenTheValueIsGiven() {
        FamilyMessage message = templates.render("with-link",
                Map.of("calendarUrl", "https://jittertravel.com/calendar?day=2026-10-18"));

        assertThat(message.textContent())
                .contains("See it here: https://jittertravel.com/calendar?day=2026-10-18")
                .contains("Before the link.")
                .contains("After the link.");
    }

    @Test
    void anOptionalLinkLineLeavesNoTraceWhenTheValueIsAbsent() {
        FamilyMessage message = templates.render("with-link", Map.of());

        assertThat(message.textContent())
                .doesNotContain("See it here")
                .contains("Before the link.")
                .contains("After the link.");
    }

    @Test
    void neverMoreThanOneBlankLineInARowWhateverTheFileAndTheLoopBetweenThemAdd() {
        FamilyMessage message = templates.render("blank-lines", Map.of("items", List.of("one", "two")));

        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "First paragraph.",
                        "",
                        "Second paragraph, after three blank lines.",
                        "",
                        "- one",
                        "",
                        "- two");
    }

    /**
     * {@code trip-changed.txt} is approved wording ({@code docs/FlightItineraryRebookingPlan.md}),
     * sent by {@code NotifyFamily.notifyOfChangedItinerary} since 2026-11-14. This pins the file's
     * layout with fixed values; {@code NotifyFamilyItineraryChangeTest} pins what is passed to it.
     */
    @Test
    void theTripChangedEmailSaysWhatChangedThenListsTheNewLegsThenTheOldOnes() {
        FamilyMessage message = templates.render("trip-changed", Map.of(
                "chain", "SFO → ORD → YOW",
                "calendarUrl", "https://jittertravel.com/calendar?day=2026-10-18",
                "journeys", List.of(new FamilyNotificationMessages.JourneyView(
                        "San Francisco (SFO) → Ottawa (YOW)",
                        "Sun Oct 18, 07:05 PDT   was Sun Oct 18, 06:00 PDT",
                        "Sun Oct 18, 18:50 EDT   same as before")),
                "legs", List.of(
                        new FamilyNotificationMessages.LegView(1, "UA2091", "SFO → ORD",
                                "Sun Oct 18, 07:05 PDT", "Sun Oct 18, 13:10 CDT", "SFO", "ORD"),
                        new FamilyNotificationMessages.LegView(2, "AC8201", "ORD → YOW",
                                "Sun Oct 18, 16:05 CDT", "Sun Oct 18, 18:50 EDT", "ORD", "YOW")),
                "oldLegs", List.of(
                        new FamilyNotificationMessages.LegView(1, "UA2089", "SFO → ORD",
                                "Sun Oct 18, 06:00 PDT", "Sun Oct 18, 12:05 CDT", "SFO", "ORD"))));

        assertThat(message.subject())
                .isEqualTo("(JitterTravel) Ted's trip has changed: SFO → ORD → YOW");
        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "Hi, JitterTravel here. Ted's trip has changed, and he wanted you to know.",
                        "",
                        "What changed:",
                        "",
                        "San Francisco (SFO) → Ottawa (YOW)",
                        "   Leaves   Sun Oct 18, 07:05 PDT   was Sun Oct 18, 06:00 PDT",
                        "   Arrives  Sun Oct 18, 18:50 EDT   same as before",
                        "",
                        "You can see the trip in his calendar here: https://jittertravel.com/calendar?day=2026-10-18",
                        "",
                        "These are his flights now.",
                        "",
                        "1. UA2091",
                        "   SFO → ORD",
                        "   Departs  Sun Oct 18, 07:05 PDT",
                        "   Arrives  Sun Oct 18, 13:10 CDT",
                        "",
                        "2. AC8201",
                        "   ORD → YOW",
                        "   Departs  Sun Oct 18, 16:05 CDT",
                        "   Arrives  Sun Oct 18, 18:50 EDT",
                        "",
                        "These are the old flights that were changed:",
                        "",
                        "UA2089",
                        "   SFO → ORD",
                        "   Was due to depart  Sun Oct 18, 06:00 PDT",
                        "   Was due to arrive  Sun Oct 18, 12:05 CDT");
    }

    @Test
    void theTripChangedEmailSaysNothingAboutOldFlightsOrChangedJourneysWhenThereAreNone() {
        FamilyMessage message = templates.render("trip-changed", Map.of(
                "chain", "SFO → ORD",
                "journeys", List.of(),
                "legs", List.of(new FamilyNotificationMessages.LegView(1, "UA2091", "SFO → ORD",
                        "Sun Oct 18, 07:05 PDT", "Sun Oct 18, 13:10 CDT", "SFO", "ORD")),
                "oldLegs", List.of()));

        assertThat(message.textContent())
                .doesNotContain("These are the old flights that were changed:")
                .doesNotContain("What changed:")
                .doesNotContain("You can see the trip in his calendar here")
                .endsWith("   Arrives  Sun Oct 18, 13:10 CDT");
    }

    @Test
    void aFileWithNoSubjectLineIsRefusedRatherThanSentWithAnEmptySubject() {
        assertThatThrownBy(() -> templates.render("no-subject", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("email/no-subject.txt must start with \"Subject: \"");
    }
}
