package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which forms link their second date to their first, and that each is wired the way
 * {@code fragments/linked-dates.html} reads it. The behaviour is proven by {@link LinkedDatesJsTest},
 * which runs only under {@code -Pjs-tests}; this is plain file reading, so it is the guard the
 * default build executes.
 * <p>
 * Nothing at a form mentions the script except the fragment reference, so a form that loses it
 * keeps its {@code data-follows} attribute and quietly stops following — the failure this catches.
 */
class LinkedDatesFragmentConventionTest {

    private static final String FRAGMENT_REFERENCE =
            "<div th:replace=\"~{fragments/linked-dates :: linkedDates}\"></div>";
    private static final Pattern FOLLOWS = Pattern.compile("data-follows=\"(\\w+)\"");
    private static final Path FRAGMENT = TemplateSources.ROOT.resolve("fragments/linked-dates.html");

    private final TemplateSources templates = new TemplateSources();

    @ParameterizedTest(name = "{0}: {2} follows {1}")
    @CsvSource({
            "book-hotel.html,              checkIn,           checkOut,        Moved with check-in",
            "change-hotel.html,            checkIn,           checkOut,        Moved with check-in",
            "book-train.html,              departureDateTime, arrivalDateTime, Moved with departure",
            "change-train.html,            departureDateTime, arrivalDateTime, Moved with departure",
            "book-flight.html,             departureDateTime, arrivalDateTime, Moved with departure",
            "change-flight.html,           departureDateTime, arrivalDateTime, Moved with departure",
            "plan-conference.html,         startDate,         endDate,         Moved with start date",
            "change-conference-dates.html, startDate,         endDate,         Moved with start date"
    })
    void theSecondDateFollowsTheFirst(String form, String first, String second, String note) {
        String template = templates.read(TemplateSources.ROOT.resolve(form));

        assertThat(template)
                .as("%s: the first date is a datetime-local input, which is all the script looks for", form)
                .contains("<input type=\"datetime-local\" th:field=\"*{" + first + "}\"")
                .as("%s: the second date names the first and says what it moved with", form)
                .containsPattern("<input type=\"datetime-local\" th:field=\"\\*\\{" + second + "}\"\\s+"
                        + "data-follows=\"" + first + "\" data-follows-note=\"" + note + "\"/>");
    }

    @Test
    void everyFormWithALinkedDateIncludesTheScriptExactlyOnce() {
        for (Path form : formsWithALinkedDate()) {
            String template = templates.read(form);

            assertThat(template.split(Pattern.quote(FRAGMENT_REFERENCE), -1).length - 1)
                    .as("%s marks a linked date, so it must include the script once", form)
                    .isEqualTo(1);
        }
    }

    @Test
    void everyFormIncludingTheScriptHasALinkedDate() {
        assertThat(templates.containing(FRAGMENT_REFERENCE))
                .as("a script with nothing to link is dead weight on the page")
                .allSatisfy(form -> assertThat(templates.read(form)).contains("data-follows=\""));
    }

    @Test
    void everyLinkedDateNamesAFirstDateOnItsOwnForm() {
        for (Path form : formsWithALinkedDate()) {
            String template = templates.read(form);
            Matcher follows = FOLLOWS.matcher(template);
            while (follows.find()) {
                assertThat(template)
                        .as("%s: data-follows=\"%s\" must name a datetime-local field on the same form",
                                form, follows.group(1))
                        .contains("<input type=\"datetime-local\" th:field=\"*{" + follows.group(1) + "}\"");
            }
        }
    }

    /** Every template marking a linked date, less the fragment, whose comment explains the marker. */
    private List<Path> formsWithALinkedDate() {
        List<Path> forms = templates.containing("data-follows=\"").stream()
                                    .filter(template -> !template.equals(FRAGMENT))
                                    .toList();
        assertThat(forms)
                .as("the eight forms in the table above, at least")
                .hasSizeGreaterThanOrEqualTo(8);
        return forms;
    }
}
