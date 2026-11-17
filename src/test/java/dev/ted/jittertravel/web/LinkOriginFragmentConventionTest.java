package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A form opened from the calendar's day menu or from an edit pencil on {@code /calendar} or
 * {@code /itinerary} comes back to that day once it succeeds ({@link LinkOrigin#returnTo}). The
 * controller's half is one line; this is the template's half, which nothing else would notice going
 * missing: a form that drops {@code from} or {@code day} on its POST still saves, and simply lands on
 * its list.
 * <p>
 * The day menu's targets are read off the menu itself, so an eighth "Add …" item fails here until
 * its form is listed in {@link #TEMPLATE_FOR_PATH}.
 */
class LinkOriginFragmentConventionTest {

    static final String FRAGMENT_REFERENCE = "~{fragments/link-origin :: hiddenInputs}";
    private static final Path FRAGMENT = TemplateSources.ROOT.resolve("fragments/link-origin.html");
    private static final Pattern HREF = Pattern.compile("href=\"(/[^\"?]*)");

    /** Every form a day-menu item or an edit pencil opens, and the template that answers it. */
    private static final Map<String, String> TEMPLATE_FOR_PATH = Map.ofEntries(
            Map.entry("/book-flight", "book-flight.html"),
            Map.entry("/book-train", "book-train.html"),
            Map.entry("/book-hotel", "book-hotel.html"),
            Map.entry("/plan-ground-transfer", "plan-ground-transfer.html"),
            Map.entry("/plan-gathering", "plan-gathering.html"),
            Map.entry("/plan-conference", "plan-conference.html"),
            Map.entry("/plan-private-event", "plan-private-event.html"),
            // The edit pencils.
            Map.entry("/booked-flights/{id}", "change-flight.html"),
            Map.entry("/booked-trains/{id}", "change-train.html"),
            Map.entry("/booked-hotels/{id}", "change-hotel.html"),
            Map.entry("/planned-gatherings/{id}", "change-gathering.html"));

    private final TemplateSources templates = new TemplateSources();

    @Test
    void theFragmentCarriesTheOriginAndTheDay() {
        assertThat(templates.read(FRAGMENT))
                .as("the one place the two hidden inputs are written")
                .contains("<input type=\"hidden\" name=\"from\" th:if=\"${linkOrigin != null}\" th:value=\"${linkOrigin}\"/>")
                .contains("<input type=\"hidden\" name=\"day\" th:if=\"${originDay != null}\" th:value=\"${originDay}\"/>");
    }

    @Test
    void everyFormTheDayMenuOpensIsKnownHere() {
        String menu = CalendarViewBuilder.dayMenu(LocalDate.of(2026, 12, 3), "3", "day-number").render();
        List<String> addTargets = HREF.matcher(menu).results()
                .map(match -> match.group(1))
                .filter(path -> !path.equals("/itinerary"))
                .toList();

        assertThat(addTargets)
                .as("a day-menu item opens a form this test does not know about — add it to "
                    + "TEMPLATE_FOR_PATH, and put the fragment in its form")
                .isNotEmpty()
                .isSubsetOf(TEMPLATE_FOR_PATH.keySet());
    }

    /**
     * Looked for <strong>inside the form that posts to the path</strong>, not anywhere in the file:
     * the flight forms each have a lookup form beside the real one, and an input living there is
     * never sent by the save.
     */
    @Test
    void everyFormADayMenuItemOrPencilOpensCarriesItsOriginThroughThePost() {
        assertThat(TEMPLATE_FOR_PATH).allSatisfy((path, template) -> assertThat(
                ProblemContextFragmentConventionTest.formPostingTo(
                        templates.read(TemplateSources.ROOT.resolve(template)), path))
                .as("the form posting to %s must carry from and day through the POST", path)
                .contains(FRAGMENT_REFERENCE));
    }

    /**
     * A flight lookup, and picking a leg from it, re-render the form before it is saved. A form
     * that dropped the origin there would save fine and land on the flights list.
     */
    @Test
    void theFlightLookupsCarryTheOriginToTheReRenderedForm() {
        assertThat(ProblemContextFragmentConventionTest.formPostingTo(
                templates.read(TemplateSources.ROOT.resolve("book-flight.html")), "/book-flight/lookup"))
                .as("book-flight lookup")
                .contains(FRAGMENT_REFERENCE);
        assertThat(ProblemContextFragmentConventionTest.formPostingTo(
                templates.read(TemplateSources.ROOT.resolve("change-flight.html")), "/booked-flights/{id}/lookup"))
                .as("change-flight lookup")
                .contains(FRAGMENT_REFERENCE);
        List<String> legForms = Pattern.compile("<form\\b.*?</form>", Pattern.DOTALL)
                .matcher(templates.read(TemplateSources.ROOT.resolve("fragments/flight-leg-chooser.html")))
                .results().map(MatchResult::group).toList();
        assertThat(legForms)
                .as("the leg chooser's form")
                .singleElement()
                .asString()
                .contains(FRAGMENT_REFERENCE);
    }
}
