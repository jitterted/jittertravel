package dev.ted.jittertravel.web;

import com.microsoft.playwright.Locator;
import dev.ted.jittertravel.domain.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the linked date fields ({@code templates/fragments/linked-dates.html}),
 * on {@code /book-hotel}: change check-in and check-out moves by the same amount. The approved
 * mockup is {@code docs/mockups/LinkedDateFields.html}.
 * <p>
 * No server, Spring, DB or auth. The real template is loaded with the fragment pulled in and
 * {@code th:field} expanded into the {@code name} attributes the script looks the first date up
 * by; the script, the {@code data-follows} wiring and this page's decision to include it are the
 * shipped source untouched. The form opens with the values the server would render, written
 * before anything has focus, which is also how a script-written value (a prefill) arrives.
 */
class LinkedDatesJsTest extends JsBehaviorTest {

    private static final Path FORM = TemplateSources.ROOT.resolve("book-hotel.html");
    private static final String FRAGMENT_REFERENCE =
            "<div th:replace=\"~{fragments/linked-dates :: linkedDates}\"></div>";

    private final TemplateSources templates = new TemplateSources();

    private Locator checkIn;
    private Locator checkOut;
    private Locator note;

    @BeforeEach
    void openTheFormOnOneNightInNovember2026() {
        loadRendered(rendered());
        checkIn = page.locator("input[name=checkIn]");
        checkOut = page.locator("input[name=checkOut]");
        note = page.locator(".follows-note");
        setWithoutEvents(checkIn, "2026-11-28T15:00");
        setWithoutEvents(checkOut, "2026-11-29T11:00");
    }

    @Test
    void finishingANewYearMovesCheckOutByTheSameAmountAndSaysSo() {
        checkIn.fill("2027-11-28T15:00");

        assertThat(checkOut.inputValue())
                .as("one night still, the time of day kept")
                .isEqualTo("2027-11-29T11:00");
        assertThat(note.isVisible())
                .as("the moved field says it moved")
                .isTrue();
        assertThat(note.textContent())
                .isEqualTo("Moved with check-in");
    }

    @Test
    void halfTypedYearsMoveNothing() {
        checkIn.focus();
        for (String partial : List.of("0002", "0020", "0202")) {
            typeWithoutLeaving(checkIn, partial + "-11-28T15:00");
        }

        assertThat(checkOut.inputValue())
                .as("0002, 0020 and 0202 are the year being typed, not a year")
                .isEqualTo("2026-11-29T11:00");
        assertThat(note.isVisible())
                .isFalse();

        typeWithoutLeaving(checkIn, "2027-11-28T15:00");

        assertThat(checkOut.inputValue())
                .as("the fourth digit finishes the year, measured from where check-in started")
                .isEqualTo("2027-11-29T11:00");
    }

    @Test
    void aNewDayInTheSameYearMovesCheckOutOnlyOnLeavingCheckIn() {
        checkIn.focus();
        typeWithoutLeaving(checkIn, "2026-12-08T15:00");

        assertThat(checkOut.inputValue())
                .as("still typing in check-in")
                .isEqualTo("2026-11-29T11:00");

        checkIn.blur();

        assertThat(checkOut.inputValue())
                .as("ten days later, as check-in moved")
                .isEqualTo("2026-12-09T11:00");
    }

    @Test
    void afterCheckOutIsEditedByHandItStopsFollowing() {
        checkIn.fill("2027-03-08T15:00");
        checkIn.blur();
        checkOut.fill("2027-03-11T11:00");

        assertThat(note.isVisible())
                .as("the line goes when Ted edits check-out")
                .isFalse();

        checkIn.fill("2027-03-09T15:00");
        checkIn.blur();

        assertThat(checkOut.inputValue())
                .as("check-out stays where Ted put it")
                .isEqualTo("2027-03-11T11:00");
    }

    @Test
    void aValueWrittenByScriptBeforeCheckInIsFocusedIsNotTreatedAsAChange() {
        checkIn.focus();
        checkIn.blur();
        setWithoutEvents(checkIn, "2027-02-08T15:00");
        setWithoutEvents(checkOut, "2027-02-10T11:00");

        checkIn.focus();
        checkIn.blur();

        assertThat(checkOut.inputValue())
                .as("a prefill set both; leaving check-in untouched must not shift check-out by the prefill's jump")
                .isEqualTo("2027-02-10T11:00");
    }

    @Test
    void theStartingValueIsTakenWhenCheckInGainsFocus() {
        setWithoutEvents(checkIn, "2027-02-08T15:00");
        setWithoutEvents(checkOut, "2027-02-10T11:00");

        checkIn.fill("2027-02-15T15:00");
        checkIn.blur();

        assertThat(checkOut.inputValue())
                .as("seven days from the prefilled check-in, not from the value the page opened with")
                .isEqualTo("2027-02-17T11:00");
    }

    private void setWithoutEvents(Locator input, String value) {
        input.evaluate("(el, v) => { el.value = v; }", value);
    }

    /** A value change as typing makes it: an input event, with the focus left where it is. */
    private void typeWithoutLeaving(Locator input, String value) {
        input.evaluate("(el, v) => { el.value = v; el.dispatchEvent(new Event('input', {bubbles: true})); }", value);
    }

    private String rendered() {
        return new PlacePickerMarkup(List.of(new Country("US", "United States")))
                .expand(templates.read(FORM).replace(FRAGMENT_REFERENCE, fragmentBody()))
                .replaceAll("th:field=\"\\*\\{(\\w+)}\"", "name=\"$1\"");
    }

    private String fragmentBody() {
        String fragment = templates.read(TemplateSources.ROOT.resolve("fragments/linked-dates.html"));
        int openTagEnd = fragment.indexOf('>', fragment.indexOf("<th:block")) + 1;
        return fragment.substring(openTagEnd, fragment.indexOf("</th:block>"));
    }
}
