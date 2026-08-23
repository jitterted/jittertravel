package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rules that keep the Sessionize prefill widget one copy, wired the way the controller answers.
 * <p>
 * Same shape as {@link AddressPasteFragmentConventionTest}, and for the same reason: the widget's
 * behavior is proven by {@link SessionizePrefillJsTest}, which runs only under {@code -Pjs-tests}
 * and so not in CI's default build. This test is plain file reading, so it is the guard CI actually
 * executes.
 * <p>
 * The address widget is <em>not</em> included on this form — its venue fields are venue-prefixed —
 * so the two are independent and neither convention test fires on the other's page.
 */
class SessionizePrefillFragmentConventionTest {

    private static final Path FRAGMENT =
            TemplateSources.ROOT.resolve("fragments/sessionize-prefill.html");
    private static final String FRAGMENT_REFERENCE =
            "~{fragments/sessionize-prefill :: sessionizePrefill}";
    private static final Path PLAN_CONFERENCE = TemplateSources.ROOT.resolve("plan-conference.html");
    private static final String ENDPOINT = "/api/sessionize-prefill";

    private final TemplateSources templates = new TemplateSources();

    @Test
    void thePlanConferenceFormOffersTheWidget() {
        assertThat(templates.read(PLAN_CONFERENCE))
                .as("the form the prefill exists to fill must actually include it")
                .contains(FRAGMENT_REFERENCE);
    }

    @Test
    void onlyTheFragmentTalksToThePrefillEndpoint() {
        assertThat(templates.containing(ENDPOINT))
                .as("a page with its own copy of the call is a copy that can drift")
                .containsExactly(FRAGMENT);
    }

    @Test
    void theFragmentCallsTheEndpointTheWayTheControllerAnswers() {
        String fragment = templates.read(FRAGMENT);

        assertThat(fragment)
                .as("SessionizePrefillController is @GetMapping taking a required 'url' request param")
                .contains("fetch('" + ENDPOINT + "?url=' + encodeURIComponent(raw))");
        assertThat(fragment)
                .as("a POST 405s, and the page would report that to the user as a wiring problem")
                .doesNotContain("method: 'POST'");
    }

    @Test
    void theWidgetWritesEveryFieldByTheNameThisFormActuallyUses() {
        String fragment = templates.read(FRAGMENT);
        String form = templates.read(PLAN_CONFERENCE);

        // The property names are the interface between the two halves. A widget copied from the
        // address fragment and not renamed would look right and write to nothing: this form has
        // no [name="city"], only [name="venueCity"].
        for (String property : new String[]{"name", "infoUrl", "startDate", "endDate",
                                            "cfpClosesOn", "cfpSubmissionUrl",
                                            "venueName", "venueCity", "venueCountry"}) {
            assertThat(fragment)
                    .as("the widget writes %s", property)
                    .contains("set('" + property + "', d." + property + ")");
            assertThat(form)
                    .as("%s is a field on the form the widget writes into", property)
                    .contains("th:field=\"*{" + property + "}\"");
        }
    }

    @Test
    void theWidgetNeverWritesAVenueFieldByItsUnprefixedName() {
        assertThat(templates.read(FRAGMENT))
                .as("[name=\"city\"] does not exist on this form, so writing it would silently "
                    + "do nothing — the failure a copied-and-unrenamed widget wears")
                .doesNotContain("set('city',")
                .doesNotContain("set('country',")
                .doesNotContain("set('street',")
                .doesNotContain("set('postalCode',");
    }
}
