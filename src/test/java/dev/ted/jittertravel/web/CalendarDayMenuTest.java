package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.EntryKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct tests of {@code CalendarViewBuilder.dayMenu} — the owner's tap-to-open menu on a
 * strictly-future day number.
 * <p>
 * {@code CalendarViewBuilderTest} covers <em>where</em> the menu appears (owner only, future days
 * only) by driving {@code render(...)}; this covers <em>what is in it</em>. The split is the point:
 * those tests assert a handful of hrefs, so adding an eighth item and renaming "Open day" to
 * "Open itinerary" both shipped green (`6360aa1`). The list assertion below is written so that any
 * item added, removed, renamed, reordered or repointed fails it.
 */
class CalendarDayMenuTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 20);
    private static final String CLASS = "day-number is-month-start";

    private static String menu(LocalDate date) {
        return CalendarViewBuilder.dayMenu(date, "20", CLASS).render();
    }

    private static String item(String label, String href) {
        return "<a href=\"" + href + "\" class=\"disclosure-menu-item\">" + label + "</a>";
    }

    /** One assertion per item, so a failure names the item rather than the whole menu. */
    @Test
    void everyBookableKindIsOfferedUnderItsOwnWordsAndItsOwnDatedLink() {
        String html = menu(DAY);

        assertThat(html)
                .contains(item("Open itinerary", "/itinerary?date=2026-06-20"))
                .contains(item("Add flight", "/book-flight?date=2026-06-20"))
                .contains(item("Add train", "/book-train?date=2026-06-20"))
                .contains(item("Add hotel", "/book-hotel?date=2026-06-20"))
                .contains(item("Add ground transfer", "/plan-ground-transfer?date=2026-06-20"))
                .contains(item("Add gathering", "/plan-gathering?date=2026-06-20"))
                .contains(item("Add conference", "/plan-conference?date=2026-06-20"))
                .contains(item("Add private event", "/plan-private-event?date=2026-06-20"));
    }

    /**
     * The whole list in one claim: these eight, in this order, and nothing between or beside them.
     * This is the assertion the previous coverage was missing — a ninth item is invisible to a set
     * of {@code contains} checks, and reordering is invisible to all of them.
     * <p>
     * Order is not cosmetic: "Open itinerary" leads because it is the one item that reads rather
     * than creates, and the seven that follow run booked travel and lodging first, then the events
     * Ted turns up to. That is deliberately <em>not</em> {@code EntryKind} declaration order
     * (which leads with the conference) — do not "fix" it into that on the strength of the lanes
     * below being drawn that way.
     */
    @Test
    void theMenuOffersExactlyTheseEightItemsInThisOrder() {
        String html = menu(DAY);

        assertThat(html)
                .contains("<div class=\"disclosure-menu-list\">"
                          + item("Open itinerary", "/itinerary?date=2026-06-20")
                          + item("Add flight", "/book-flight?date=2026-06-20")
                          + item("Add train", "/book-train?date=2026-06-20")
                          + item("Add hotel", "/book-hotel?date=2026-06-20")
                          + item("Add ground transfer", "/plan-ground-transfer?date=2026-06-20")
                          + item("Add gathering", "/plan-gathering?date=2026-06-20")
                          + item("Add conference", "/plan-conference?date=2026-06-20")
                          + item("Add private event", "/plan-private-event?date=2026-06-20")
                          + "</div>");
    }

    /**
     * Every link carries the menu's <em>own</em> day, so a form opened from it lands on the day
     * that was tapped. A second date proves nothing is pinned to the fixture above — the failure
     * this guards is an off-by-one day, which reads as correct until the form opens.
     */
    @Test
    void aDifferentDayDatesEverySingleLink() {
        String html = menu(LocalDate.of(2027, 1, 1));

        assertThat(html)
                .contains("<div class=\"disclosure-menu-list\">"
                          + item("Open itinerary", "/itinerary?date=2027-01-01")
                          + item("Add flight", "/book-flight?date=2027-01-01")
                          + item("Add train", "/book-train?date=2027-01-01")
                          + item("Add hotel", "/book-hotel?date=2027-01-01")
                          + item("Add ground transfer", "/plan-ground-transfer?date=2027-01-01")
                          + item("Add gathering", "/plan-gathering?date=2027-01-01")
                          + item("Add conference", "/plan-conference?date=2027-01-01")
                          + item("Add private event", "/plan-private-event?date=2027-01-01")
                          + "</div>")
                .doesNotContain("2026-06-20");
    }

    /**
     * The forcing function: every {@link EntryKind} must be creatable from a day.
     * <p>
     * This is the failure that produced this test class. The private-event kind shipped on
     * 2026-08-13 with its own lane, its own plan form and its own nav card, and nobody noticed for
     * eleven days that the one surface Ted actually starts from — tapping a future day — could not
     * create one (he added it himself in {@code 6360aa1}). Nothing anywhere connected "a new
     * bookable kind exists" to "the day menu offers it".
     * <p>
     * Two mechanisms, and they are separate on purpose:
     * <ul>
     *   <li>The cases are driven from {@code EntryKind.values()}, so a new constant becomes a new
     *       case here without anyone adding one — the test cannot be left behind.</li>
     *   <li>{@link #expectedAddItem} is an <em>exhaustive switch</em>, so that new constant stops
     *       this class compiling until someone writes down how the kind is created. A kind that
     *       genuinely has no create form says so there, in a case with a comment, rather than by
     *       being quietly absent.</li>
     * </ul>
     * What this does not check is whether the words and the path are the right ones — it reads
     * them from the same place the assertion above states them literally, which is where a typo in
     * either is caught.
     */
    @ParameterizedTest
    @EnumSource(EntryKind.class)
    void everyEntryKindCanBeCreatedFromTheDayItBelongsOn(EntryKind kind) {
        assertThat(menu(DAY))
                .as("no way to add a %s from a day on the calendar — the day menu has to offer "
                    + "every kind, or the kind is unreachable from where Ted starts", kind)
                .contains(expectedAddItem(kind, "2026-06-20"));
    }

    /**
     * How each kind is created from a day. Exhaustive over {@link EntryKind} — see the test above
     * for why that matters. Add a constant to the enum and this stops compiling, which is the
     * whole mechanism.
     */
    private static String expectedAddItem(EntryKind kind, String iso) {
        return switch (kind) {
            case FLIGHT -> item("Add flight", "/book-flight?date=" + iso);
            case TRAIN -> item("Add train", "/book-train?date=" + iso);
            case LODGING -> item("Add hotel", "/book-hotel?date=" + iso);
            case GROUND_TRANSFER -> item("Add ground transfer", "/plan-ground-transfer?date=" + iso);
            case GATHERING -> item("Add gathering", "/plan-gathering?date=" + iso);
            case CONFERENCE -> item("Add conference", "/plan-conference?date=" + iso);
            case PRIVATE_EVENT -> item("Add private event", "/plan-private-event?date=" + iso);
        };
    }

    /**
     * The number itself is the menu's handle, wearing the class the caller styles it with — the
     * month-start variant included, since that is the cell where the label is "Jun 20" rather than
     * a bare number and the menu still has to open from it.
     */
    @Test
    void theDayNumberIsTheHandleAndKeepsTheCallersClass() {
        String html = menu(DAY);

        assertThat(html)
                .startsWith("<details class=\"disclosure-menu\">")
                .contains("<summary class=\"day-number is-month-start\">20</summary>");
    }

    /** A label that is not a bare number (month starts, and the first cell of the grid) renders as given. */
    @Test
    void aMonthStartLabelIsRenderedAsTheHandleUnchanged() {
        String html = CalendarViewBuilder.dayMenu(DAY, "Jun 20", CLASS).render();

        assertThat(html)
                .contains("<summary class=\"day-number is-month-start\">Jun 20</summary>");
    }
}
