package dev.ted.jittertravel.web;

import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the Sessionize prefill widget
 * ({@code templates/fragments/sessionize-prefill.html}), on the one form that hosts it.
 * <p>
 * The claim most worth pinning here is the field mapping. The widget writes {@code infoUrl}
 * (public — it renders on the anonymous calendar) and {@code cfpSubmissionUrl} (private, per
 * CLAUDE.md — a Sessionize link is the submission pipeline in one field) <em>in the same action</em>,
 * and one swapped name in the {@code set()} calls would put a submission link on a public surface.
 * This tier is also the only one that catches a {@code querySelector} that quietly matches nothing.
 * <p>
 * Still no server, Spring, DB or auth: Playwright serves the template itself and stands in for the
 * endpoint. A real origin (rather than {@code setContent}'s {@code about:blank}) is what lets the
 * script's root-relative {@code fetch} resolve at all.
 */
class SessionizePrefillJsTest extends JsBehaviorTest {

    private static final String ORIGIN = "https://jittertravel.test";
    private static final Path FORM = TemplateSources.ROOT.resolve("plan-conference.html");
    private static final String FRAGMENT_REFERENCE =
            "<div th:replace=\"~{fragments/sessionize-prefill :: sessionizePrefill}\"></div>";

    private static final String PASTED = "https://sessionize.com/jfokus-2027/";

    private static final String SESSIONIZE_HIT = """
            {"name": "Jfokus 2027",
             "infoUrl": "https://www.jfokus.se/",
             "startDate": "2027-02-08T09:00",
             "endDate": "2027-02-10T17:00",
             "cfpClosesOn": "2026-10-01T08:30",
             "cfpSubmissionUrl": "https://sessionize.com/jfokus-2027/",
             "venueName": "Stockholm Waterfront Congress Centre",
             "venueCity": "Stockholm",
             "venueCountry": "Sweden",
             "deadlineZone": "Europe/Stockholm"}""";

    /** A reachable page whose {@code .ics} did not parse: no deadline, so no submission URL either. */
    private static final String SESSIONIZE_HIT_WITHOUT_DEADLINE = """
            {"name": "Jfokus 2027",
             "infoUrl": "https://www.jfokus.se/",
             "startDate": "2027-02-08T09:00",
             "endDate": "2027-02-10T17:00",
             "cfpClosesOn": "",
             "cfpSubmissionUrl": "",
             "venueName": "Stockholm Waterfront Congress Centre",
             "venueCity": "Stockholm",
             "venueCountry": "Sweden",
             "deadlineZone": ""}""";

    private final TemplateSources templates = new TemplateSources();

    @Test
    void theWidgetAsksTheEndpointForThePastedUrlAsAGetQueryParam() {
        PrefillEndpointStub endpoint = PrefillEndpointStub.answering(200, SESSIONIZE_HIT);
        serve(endpoint);

        pasteAndFill();

        Request request = endpoint.onlyRequest();
        assertThat(request.method())
                .as("the endpoint is GET-only, so a POST 405s and the user sees a bogus wiring error")
                .isEqualTo("GET");
        assertThat(queryOf(request)).isEqualTo(PASTED);
    }

    @Test
    void everyFieldIsWrittenToItsOwnNamedInput() {
        serve(PrefillEndpointStub.answering(200, SESSIONIZE_HIT));

        pasteAndFill();

        assertThat(valueOf("name")).isEqualTo("Jfokus 2027");
        assertThat(valueOf("startDate")).isEqualTo("2027-02-08T09:00");
        assertThat(valueOf("endDate")).isEqualTo("2027-02-10T17:00");
        assertThat(valueOf("cfpClosesOn")).isEqualTo("2026-10-01T08:30");
        assertThat(valueOf("venueName")).isEqualTo("Stockholm Waterfront Congress Centre");
        assertThat(valueOf("venueCity")).isEqualTo("Stockholm");
        assertThat(valueOf("venueCountry")).isEqualTo("Sweden");
    }

    @Test
    void thePublicAndPrivateUrlsGoToDifferentFieldsAndAreNotSwapped() {
        serve(PrefillEndpointStub.answering(200, SESSIONIZE_HIT));

        pasteAndFill();

        assertThat(valueOf("infoUrl"))
                .as("the conference's own site — public, and it renders on the anonymous calendar")
                .isEqualTo("https://www.jfokus.se/");
        assertThat(valueOf("cfpSubmissionUrl"))
                .as("where the talk gets submitted — private, and it must never land in infoUrl")
                .isEqualTo(PASTED);
    }

    @Test
    void fieldsSessionizeCannotKnowAreLeftExactlyAsTyped() {
        serve(PrefillEndpointStub.answering(200, SESSIONIZE_HIT));
        page.locator("[name='venueStreet']").fill("Nils Ericsons Plan 4");
        page.locator("[name='venuePostalCode']").fill("111 64");

        pasteAndFill();

        assertThat(valueOf("venueStreet"))
                .as("the widget writes only what came back, so re-pasting cannot clear typed values")
                .isEqualTo("Nils Ericsons Plan 4");
        assertThat(valueOf("venuePostalCode")).isEqualTo("111 64");
    }

    @Test
    void aValueSessionizeDidNotReturnDoesNotClearWhatIsAlreadyThere() {
        // The same set() semantic, on a field the widget *does* write: a page whose .ics failed
        // must not wipe a deadline Ted had already typed or looked up by hand.
        serve(PrefillEndpointStub.answering(200, SESSIONIZE_HIT_WITHOUT_DEADLINE));
        page.locator("[name='cfpClosesOn']").fill("2026-10-01T08:30");
        page.locator("[name='cfpSubmissionUrl']").fill("https://sessionize.com/jfokus-2027/");

        pasteAndFill();

        assertThat(valueOf("cfpClosesOn")).isEqualTo("2026-10-01T08:30");
        assertThat(valueOf("cfpSubmissionUrl")).isEqualTo(PASTED);
    }

    @Test
    void theReportSaysHowMuchWasFilledAndWhichValuesAreGuesses() {
        serve(PrefillEndpointStub.answering(200, SESSIONIZE_HIT));

        pasteAndFill();

        String report = page.locator("#sessionizeReport").textContent();
        assertThat(report)
                .as("a blank left by a restyled page must be distinguishable from one Sessionize "
                    + "never had, and only this line can tell them apart")
                .contains("Filled 9 fields from Sessionize")
                .contains("start and end times are guesses")
                .contains("CFP closes 08:30 Europe/Stockholm")
                .contains("street and postal code aren't on the page");
    }

    @Test
    void aDeadlineThatCouldNotBeWorkedOutIsSaidOutLoudRatherThanLeftUnexplained() {
        serve(PrefillEndpointStub.answering(200, SESSIONIZE_HIT_WITHOUT_DEADLINE));

        pasteAndFill();

        assertThat(valueOf("cfpClosesOn")).isEmpty();
        assertThat(valueOf("cfpSubmissionUrl"))
                .as("Submit At without Closes On is refused at submit (CfpDeadlineMissing)")
                .isEmpty();
        assertThat(page.locator("#sessionizeReport").textContent())
                .contains("the CFP deadline could not be worked out");
    }

    @Test
    void aCfpFoundWhileOpenSpaceIsSelectedSaysSoRatherThanWaitingForTheSubmitToRefuseIt() {
        serve(PrefillEndpointStub.answering(200, SESSIONIZE_HIT));
        page.locator("[name='format']").check();

        pasteAndFill();

        assertThat(page.locator("#sessionizeReport").textContent())
                .as("a call-for-speakers page is proof the conference has a CFP; the round trip "
                    + "to ConferenceHasNoCfp is avoidable and the contradiction is visible now")
                .contains("this page has a call for speakers, so Open Space is not the format");
    }

    @Test
    void anOrdinaryFillDoesNotAccuseTheFormatOfAnything() {
        serve(PrefillEndpointStub.answering(200, SESSIONIZE_HIT));

        pasteAndFill();

        assertThat(page.locator("#sessionizeReport").textContent())
                .doesNotContain("Open Space is not the format");
    }

    @Test
    void aPageThatCouldNotBeReadShowsAnInlineErrorAndLeavesTheButtonUsable() {
        // 422 is what the controller returns when neither document yielded anything.
        serve(PrefillEndpointStub.answering(422, ""));

        pasteAndFill();

        assertThat(page.locator("#sessionizeError").isVisible()).isTrue();
        assertThat(page.locator("#sessionizeError").textContent())
                .contains("Could not read that page");
        assertThat(page.locator("#sessionizeBtn").textContent())
                .as("the button goes straight back to being a button")
                .isEqualTo("Fill from Sessionize");
        assertThat(page.locator("#sessionizeBtn").isDisabled())
                .as("a failed lookup must not lock the user out of retrying")
                .isFalse();
    }

    @Test
    void aBrokenEndpointSaysSoRatherThanBlamingTheUrl() {
        serve(PrefillEndpointStub.answering(405, ""));

        pasteAndFill();

        assertThat(page.locator("#sessionizeError").textContent())
                .as("a wiring failure must not be reported as a fact about the pasted URL")
                .contains("wiring problem");
    }

    private void pasteAndFill() {
        page.locator("#sessionizeUrl").fill(PASTED);
        page.waitForResponse("**/api/sessionize-prefill**",
                             () -> page.locator("#sessionizeBtn").click());
        // Re-enabling happens in the handler's finally, after every field is set: the script is done.
        page.waitForFunction("() => !document.getElementById('sessionizeBtn').disabled");
    }

    private String valueOf(String field) {
        return page.locator("[name='" + field + "']").inputValue();
    }

    private void serve(PrefillEndpointStub endpoint) {
        page.route("**/*", route -> {
            String url = route.request().url();
            if (url.contains("/api/sessionize-prefill")) {
                endpoint.fulfill(route);
            } else if (url.endsWith(".css")) {
                route.fulfill(new Route.FulfillOptions().setContentType("text/css").setBody(""));
            } else {
                route.fulfill(new Route.FulfillOptions()
                                      .setContentType("text/html; charset=utf-8")
                                      .setBody(rendered()));
            }
        });
        page.navigate(ORIGIN + "/plan-conference");
    }

    /**
     * The real template, as a browser would receive it. Nothing here runs Thymeleaf, so the test
     * performs the expansions the script depends on: pulling in the fragment, turning
     * {@code th:field} into the {@code name} attributes the widget writes into, and giving the
     * format radio a concrete value (the real page renders one per {@code ConferenceFormat}; the
     * widget only ever asks which is checked, so one is enough, and Open Space is the one with a
     * behavior attached). Everything actually under test — the script, its ids, and this page's
     * decision to include it — is the shipped source untouched.
     */
    private String rendered() {
        return templates.read(FORM)
                        .replace(FRAGMENT_REFERENCE, fragmentBody())
                        .replace("th:value=\"${cf.name()}\"", "value=\"OPEN_SPACE\"")
                        .replaceAll("th:field=\"\\*\\{(\\w+)}\"", "name=\"$1\"");
    }

    private String fragmentBody() {
        String fragment = templates.read(
                TemplateSources.ROOT.resolve("fragments/sessionize-prefill.html"));
        int openTagEnd = fragment.indexOf('>', fragment.indexOf("<th:block")) + 1;
        return fragment.substring(openTagEnd, fragment.indexOf("</th:block>"));
    }

    private static String queryOf(Request request) {
        String url = request.url();
        return URLDecoder.decode(url.substring(url.indexOf("?url=") + "?url=".length()),
                                 StandardCharsets.UTF_8);
    }

    /** Stands in for {@code SessionizePrefillController}, recording how the page called it. */
    private static final class PrefillEndpointStub {
        private final int status;
        private final String body;
        private final List<Request> requests = new ArrayList<>();

        private PrefillEndpointStub(int status, String body) {
            this.status = status;
            this.body = body;
        }

        static PrefillEndpointStub answering(int status, String body) {
            return new PrefillEndpointStub(status, body);
        }

        void fulfill(Route route) {
            requests.add(route.request());
            route.fulfill(new Route.FulfillOptions()
                                  .setStatus(status)
                                  .setContentType("application/json")
                                  .setBody(body));
        }

        Request onlyRequest() {
            assertThat(requests).hasSize(1);
            return requests.getFirst();
        }
    }
}
