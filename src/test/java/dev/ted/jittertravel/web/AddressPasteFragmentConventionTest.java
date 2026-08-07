package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The address paste-and-parse widget is shared, and these are the rules that keep it that way.
 * <p>
 * It began as a copy in each address form. When {@code /api/parse-address} moved to
 * {@code GET ?q=}, book-hotel.html was updated and the two gathering forms were not: they went on
 * POSTing JSON, and the 405 reached the user as a bare "Not found" — indistinguishable from an
 * address the geocoder genuinely couldn't place. A fourth form, change-hotel.html, had quietly
 * shipped with no widget at all.
 * <p>
 * {@link AddressParseJsTest} proves the widget behaves, but it runs only under
 * {@code -Pjs-tests}, which CI's {@code ./mvnw -B -ntp test} excludes. This test is plain file
 * reading, so it runs in the default build — it is the guard CI actually executes.
 */
class AddressPasteFragmentConventionTest {

    private static final Path FRAGMENT = TemplateSources.ROOT.resolve("fragments/address-paste.html");
    private static final String FRAGMENT_REFERENCE = "~{fragments/address-paste :: addressPaste}";
    private static final String ADDRESS_FIELD = "th:field=\"*{street}\"";
    private static final String ENDPOINT = "/api/parse-address";

    private final TemplateSources templates = new TemplateSources();

    @Test
    void everyFormCollectingAnAddressOffersTheSharedPasteWidget() {
        assertThat(templates.containing(ADDRESS_FIELD))
                .isNotEmpty()
                .allSatisfy(form -> assertThat(templates.read(form))
                        .as("%s collects an address, so it must include the shared paste widget", form)
                        .contains(FRAGMENT_REFERENCE));
    }

    @Test
    void onlyTheFragmentTalksToTheAddressEndpoint() {
        assertThat(templates.containing(ENDPOINT))
                .as("a page with its own copy of the call is a copy that can drift — which is "
                    + "precisely how the gathering forms kept POSTing after the endpoint moved")
                .containsExactly(FRAGMENT);
    }

    @Test
    void theFragmentCallsTheEndpointTheWayTheControllerAnswers() {
        String fragment = templates.read(FRAGMENT);

        assertThat(fragment)
                .as("AddressParseController is @GetMapping taking a required 'q' request param")
                .contains("fetch('" + ENDPOINT + "?q=' + encodeURIComponent(raw))");
        assertThat(fragment)
                .as("a POST 405s, and the page would report that to the user as 'not found'")
                .doesNotContain("method: 'POST'");
    }
}
