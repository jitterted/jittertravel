package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.Page.NavAudience;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static j2html.TagCreator.span;
import static org.assertj.core.api.Assertions.assertThat;

class PageTest {

    @Test
    void ownerNavLinksToEveryViewPage() {
        String nav = Page.viewNav(NavAudience.OWNER, "/booked-hotels").render();

        assertThat(nav)
                .contains("href=\"/\"")
                .contains("href=\"/itinerary\"")
                .contains("href=\"/calendar\"")
                .contains("href=\"/booked-flights\"")
                .contains("href=\"/booked-trains\"")
                .contains("href=\"/planned-gatherings\"")
                .contains("href=\"/planned-private-events\"")
                .contains("href=\"/conferences\"")
                // Always present: the bar reflects the viewer's tier, not what the
                // linked pages contain. An empty report is the report page's problem.
                .contains("href=\"/schedule-problems\"");
    }

    @Test
    void scheduleProblemsPageShowsItsOwnActiveMarker() {
        String nav = Page.viewNav(NavAudience.OWNER, "/schedule-problems").render();

        assertThat(nav)
                .contains("<span class=\"active\" aria-current=\"page\">Schedule Problems</span>")
                .doesNotContain("href=\"/schedule-problems\"");
    }

    @Test
    void currentPageRendersAsNonLinkActiveSpanNotAHref() {
        String nav = Page.viewNav(NavAudience.OWNER, "/booked-hotels").render();

        assertThat(nav)
                // The active page is a span carrying aria-current, never a self-link.
                .contains("<span class=\"active\" aria-current=\"page\">Hotels</span>")
                .doesNotContain("href=\"/booked-hotels\"");
    }

    @Test
    void familyNavHasOnlyItineraryAndCalendarNotOwnerViews() {
        String nav = Page.viewNav(NavAudience.FAMILY, "/itinerary").render();

        assertThat(nav)
                .contains("href=\"/\"")
                .contains("href=\"/calendar\"")
                // On the itinerary page itself the itinerary link is the active span.
                .contains("<span class=\"active\" aria-current=\"page\">Itinerary</span>")
                // Family cannot reach the booking/planning views — never link to them,
                // Schedule Problems included.
                .doesNotContain("/booked-flights")
                .doesNotContain("/booked-trains")
                .doesNotContain("/booked-hotels")
                .doesNotContain("/planned-gatherings")
                .doesNotContain("/planned-private-events")
                .doesNotContain("/conferences")
                .doesNotContain("/schedule-problems");
    }

    @Test
    void anonymousNavHasOnlyTheHomeLink() {
        String nav = Page.viewNav(NavAudience.ANONYMOUS, "/calendar").render();

        assertThat(nav)
                .contains("href=\"/\"")
                .contains("JitterTravel")
                // Nothing that reveals an owner/family surface exists.
                .doesNotContain("href=\"/itinerary\"")
                .doesNotContain("/booked-")
                .doesNotContain("/planned-")
                .doesNotContain("/conferences")
                .doesNotContain("/schedule-problems");
    }

    @Test
    void navWithNoTrailingControlsEmitsNothingExtra() {
        // The slot is varargs, so the ten callers that pass no controls keep the call they had —
        // and must keep the markup they had. This is what stops the slot leaking onto other pages.
        String nav = Page.viewNav(NavAudience.OWNER, "/booked-hotels").render();

        assertThat(nav)
                .as("the last nav link closes the bar directly — nothing slipped in behind it")
                .contains("<a href=\"/schedule-problems\">Schedule Problems</a></nav>");
    }

    @Test
    void trailingControlsRenderInsideTheNavSoTheyStickWithIt() {
        // Inside the <nav>, never after it: the bar is the only sticky element on /calendar, and a
        // sibling would scroll away — which is the whole problem the slot exists to solve.
        String nav = Page.viewNav(NavAudience.OWNER, "/calendar",
                span("Jump to month").withId("a-control")).render();

        int control = nav.indexOf("id=\"a-control\"");
        int navClose = nav.indexOf("</nav>");
        assertThat(control).isGreaterThan(0);
        assertThat(control)
                .as("the control sits before the nav closes, not after it")
                .isLessThan(navClose);
    }

    /**
     * The renderers comment their CSS heavily and deliberately — see {@link Page#withoutComments}
     * for why that is allowed to stay. These cases are what make it safe: nothing they write
     * reaches a browser, so a comment can neither name an owner-only surface nor cost bytes.
     */
    @Test
    void inlinedCssShipsWithoutItsComments() {
        String head = Page.head("T", """
                /* Names .year-overview, which anonymous viewers must not learn exists. */
                .thing { color: red; }
                """).render();

        assertThat(head)
                .as("the declaration survives")
                .contains(".thing { color: red; }")
                .as("the comment does not")
                .doesNotContain("/*")
                .doesNotContain("*/")
                .doesNotContain("year-overview");
    }

    @Test
    void aMultiLineCommentGoesInFullAndTakesItsBlankLineWithIt() {
        String head = Page.head("T", """
                .a { color: red; }
                /* One line,
                   and a second. */
                .b { color: blue; }
                """).render();

        assertThat(head)
                .contains(".a { color: red; }")
                .contains(".b { color: blue; }")
                .doesNotContain("and a second")
                .as("the emptied line goes too, or the saving is only half taken")
                .contains(".a { color: red; }\n.b { color: blue; }");
    }

    @Test
    void twoCommentsDoNotSwallowTheCssBetweenThem() {
        String head = Page.head("T", "/* one */ .keep { color: red; } /* two */").render();

        assertThat(head)
                .as("a greedy match would eat this declaration whole")
                .contains(".keep { color: red; }")
                .doesNotContain("one")
                .doesNotContain("two");
    }

    /**
     * <strong>A known limitation, written down rather than left as a surprise.</strong> A comment
     * marker inside a CSS <em>value</em> opens a comment, so everything up to the next real
     * {@code *}{@code /} is eaten — including whole rules in between. No such value exists in this
     * tree — checked when the stripping shipped — so this pins the boundary rather than a bug.
     * Whoever first needs {@code content: "/*"} has to teach {@link Page#withoutComments} about
     * strings before writing it.
     * <p>
     * Note what it takes to do damage: an <em>unterminated</em> marker matches nothing and passes
     * through untouched. It needs a later comment to close against, which is why the fixture has
     * one.
     */
    @Test
    void aCommentMarkerInsideAValueEatsEverythingUpToTheNextRealComment() {
        String head = Page.head("T", """
                .marker::after { content: "/*"; }
                .caught-in-between { color: red; }
                .b { color: blue; } /* a real comment */
                """).render();

        assertThat(head)
                .as("the limitation, not the behaviour we want: the rule between them is gone")
                .doesNotContain(".caught-in-between")
                .as("the opening rule is left truncated at the quote, not removed cleanly")
                .doesNotContain("content: \"/*\";")
                .contains(".marker::after { content: \"");
    }

    @Test
    void aRealPageCarriesNoCssComment() {
        String page = CalendarRenderer.render(List.of(), LocalDate.of(2026, 9, 7), false, true);

        assertThat(page)
                .as("the calendar inlines the most heavily commented CSS in the tree")
                .contains("<style>")
                .doesNotContain("/*");
    }

    @Test
    void navAudienceIsDerivedFromViewerFlags() {
        assertThat(NavAudience.of(true, false)).isEqualTo(NavAudience.ANONYMOUS);
        // isOwner is meaningless when public; public always wins.
        assertThat(NavAudience.of(true, true)).isEqualTo(NavAudience.ANONYMOUS);
        assertThat(NavAudience.of(false, false)).isEqualTo(NavAudience.FAMILY);
        assertThat(NavAudience.of(false, true)).isEqualTo(NavAudience.OWNER);
    }
}
