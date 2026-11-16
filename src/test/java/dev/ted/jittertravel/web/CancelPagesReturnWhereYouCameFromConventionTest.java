package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every cancel page sends both of its ways out — "Keep it" and a successful cancel — to where the
 * link was clicked ({@link LinkOrigin}, {@code docs/CancelReturnsWhereYouCameFromPlan.md}). The
 * controller's half is one {@link WayBack} it uses for the redirect; this is the template's half,
 * which nothing else would notice going missing: a cancel page with a literal "Keep it" href, or one
 * that drops {@code from} or {@code day} on its POST, still renders and still cancels.
 * <p>
 * The templates are discovered, not listed, so a seventh {@code cancel-*.html} is checked the day
 * it is added.
 */
class CancelPagesReturnWhereYouCameFromConventionTest {

    private static final Pattern KEEP_LINK = Pattern.compile("<a class=\"keep-link\"[^>]*>");

    private final TemplateSources templates = new TemplateSources();

    @Test
    void everyKeepLinkReadsTheResolvedWayBackRatherThanALiteralHref() {
        assertThat(cancelPages()).isNotEmpty().allSatisfy(page -> {
            List<String> keepLinks = keepLinks(page);
            assertThat(keepLinks)
                    .as("%s has a way to back out", page.getFileName())
                    .isNotEmpty();
            assertThat(keepLinks)
                    .as("%s: every keep link goes where the link was clicked", page.getFileName())
                    .allMatch(link -> link.contains("th:href=\"${wayBack.href()}\""));
        });
    }

    @Test
    void everyCancelFormCarriesTheOriginAndTheDayThroughThePost() {
        assertThat(cancelPages()).isNotEmpty().allSatisfy(page -> assertThat(templates.read(page))
                .as("%s carries from and day through its POST", page.getFileName())
                .contains("<input type=\"hidden\" name=\"from\" th:if=\"${linkOrigin != null}\" th:value=\"${linkOrigin}\"/>")
                .contains("<input type=\"hidden\" name=\"day\" th:if=\"${originDay != null}\" th:value=\"${originDay}\"/>"));
    }

    private List<String> keepLinks(Path page) {
        return KEEP_LINK.matcher(templates.read(page)).results().map(MatchResult::group).toList();
    }

    private static List<Path> cancelPages() {
        try (Stream<Path> files = Files.list(TemplateSources.ROOT)) {
            return files.filter(path -> path.getFileName().toString().startsWith("cancel-"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
