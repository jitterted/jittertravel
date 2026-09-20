package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>These four forms validate on the server, so none of their inputs carries the HTML
 * {@code required} attribute.</strong>
 * <p>
 * The browser's {@code required} blocks the submit and shows a bubble the server never hears about,
 * so the page stays exactly as it was — which reads as "my fix changed nothing", and cost a real
 * session on the train forms (2026-09-06). {@code EnteredLocation} reports a blank venue name and a
 * blank city through the same field-level channel as every other location problem, so removing the
 * attribute loses nothing and leaves one error vocabulary rather than two.
 * <p>
 * <strong>This is a list of the forms that have made that choice, not a rule for every form.</strong>
 * The train pair dropped it when the field-level errors landed; the hotel pair followed on
 * 2026-09-20. A form not named here is simply one nobody has converted — add it to the list in the
 * same change that drops its attributes, and do not add it before, because the test is what says
 * the decision was made rather than that the markup happens to be missing an attribute today.
 * <p>
 * The trap this closes is that nothing else notices: re-adding {@code required} breaks no test and
 * looks like an improvement, while quietly restoring the failure the removal was for.
 */
class NoBrowserRequiredOnServerValidatedFormsTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** The forms whose blank-field errors are reported and rendered by the server. */
    private static final List<String> SERVER_VALIDATED_FORMS = List.of(
            "book-train.html", "change-train.html",
            "book-hotel.html", "change-hotel.html");

    @Test
    void noServerValidatedFormLetsTheBrowserBlockTheSubmit() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String form : SERVER_VALIDATED_FORMS) {
            Path template = TEMPLATES.resolve(form);
            assertThat(template)
                    .as("a form named here must exist: " + form)
                    .exists();
            List<String> lines = Files.readAllLines(template);
            for (int i = 0; i < lines.size(); i++) {
                if (isRequiredAttribute(lines.get(i))) {
                    offenders.add(form + ":" + (i + 1) + " " + lines.get(i).trim());
                }
            }
        }

        assertThat(offenders)
                .as("the browser would block the submit and the server would never hear about it, "
                    + "so the page comes back unchanged and reads as having ignored the click")
                .isEmpty();
    }

    /**
     * The attribute on its own, never the word inside another one. {@code required} appears in
     * these templates as prose in a hint and as {@code @RequestParam}-ish text elsewhere, so
     * matching the bare word would fail for the wrong reason.
     */
    private static boolean isRequiredAttribute(String line) {
        return line.contains(" required/>")
               || line.contains(" required >")
               || line.contains(" required>")
               || line.contains(" required=");
    }
}
