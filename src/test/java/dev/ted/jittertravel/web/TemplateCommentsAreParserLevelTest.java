package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>Every comment in a Thymeleaf template is a parser-level comment —
 * {@code <!--}{@code /* … *}{@code /-->}, which the engine removes before the response is
 * written.</strong> A plain {@code <!-- … -->} is sent to the browser.
 * <p>
 * Same reasoning as {@link Page#withoutComments}, which strips the comments out of the inlined
 * stylesheet: a comment written for the next developer has no business in a page a stranger can
 * view. It is a disclosure channel nothing else watches — on 2026-09-07 a CSS comment naming an
 * owner-only surface reached {@code CalendarRedactionSecurityTest} — and it is bytes on every load.
 * The delimiter costs four characters and the note keeps its place next to the markup it explains,
 * which is the whole reason this is a convention rather than a rule against commenting.
 * <p>
 * <strong>What this test cannot show</strong> is that Thymeleaf honours the delimiter. That is
 * pinned where a real template is rendered:
 * {@code ChangeHotelControllerTest.getWithKnownBookingIdRendersChangeForm} asserts the response
 * carries no {@code <!--} at all.
 * <p>
 * <strong>The one exemption is {@code <!--!}</strong>, the Font Awesome license notice embedded in
 * each icon's SVG. That text is the vendor's license, not ours to strip, and it names nothing
 * private. Exempted by its exact marker rather than by file, so an ordinary comment in the same
 * file is still caught.
 */
class TemplateCommentsAreParserLevelTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @Test
    void everyTemplateCommentIsRemovedBeforeItReachesABrowser() throws IOException {
        List<String> shipped = new ArrayList<>();
        for (Path template : templates()) {
            List<String> lines = Files.readAllLines(template);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int at = line.indexOf("<!--");
                while (at >= 0) {
                    String opener = line.substring(at);
                    if (!opener.startsWith("<!--/*") && !opener.startsWith("<!--!")) {
                        shipped.add(template.getFileName() + ":" + (i + 1) + " "
                                    + opener.substring(0, Math.min(70, opener.length())));
                    }
                    at = line.indexOf("<!--", at + 4);
                }
            }
        }

        assertThat(shipped)
                .as("a plain <!-- --> is sent to the browser: write <!--/* ... */--> instead")
                .isEmpty();
    }

    private static List<Path> templates() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            return files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".html"))
                        .sorted()
                        .toList();
        }
    }
}
