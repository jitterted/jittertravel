package dev.ted.jittertravel.web;

import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JS-behavior tests for the shared address paste-and-parse widget
 * ({@code templates/fragments/address-paste.html}), run against every form that includes it.
 * <p>
 * The bug this covers was drift between per-page copies of the script: the endpoint moved to
 * {@code GET ?q=}, one page followed, the others kept POSTing JSON, and the resulting 405 reached
 * the user as a bare "Not found". The copies are now one fragment, and
 * {@link AddressPasteFragmentConventionTest} keeps them that way — this tier proves the surviving
 * copy actually works in a browser, on each page that hosts it.
 * <p>
 * The forms are <em>discovered</em> rather than listed, so a new address form is covered the day
 * it is written.
 * <p>
 * Still no server, Spring, DB or auth: Playwright serves the template itself and stands in for the
 * endpoint. A real origin (rather than {@code setContent}'s {@code about:blank}) is what lets the
 * script's root-relative {@code fetch} resolve at all.
 */
class AddressParseJsTest extends JsBehaviorTest {

    private static final String ORIGIN = "https://jittertravel.test";
    private static final String FRAGMENT_REFERENCE =
            "<div th:replace=\"~{fragments/address-paste :: addressPaste}\"></div>";

    /** Pasted verbatim, newline and all — the shape of a real copy out of a venue page. */
    private static final String PASTED_ADDRESS = """
            1 Blue Jays Way
            Toronto, ON M5V 1J1""";

    private static final String GEOCODER_HIT = """
            {"street": "1 Blue Jays Way", "city": "Toronto", "region": "Ontario",
             "postalCode": "M5V 1J1", "country": "Canada", "locationForMatching": "Toronto"}""";

    private final TemplateSources templates = new TemplateSources();

    static List<Path> addressForms() {
        return new TemplateSources().containing(FRAGMENT_REFERENCE);
    }

    @ParameterizedTest
    @MethodSource("addressForms")
    void parseAsksTheEndpointForThePastedAddressAsAGetQueryParam(Path form) {
        AddressEndpointStub endpoint = AddressEndpointStub.answering(200, GEOCODER_HIT);
        serve(form, endpoint);

        pasteAndParse();

        Request request = endpoint.onlyRequest();
        assertThat(request.method())
                .as("the endpoint is GET-only, so a POST 405s and the user sees a bogus 'not found'")
                .isEqualTo("GET");
        assertThat(queryOf(request))
                .as("the pasted text reaches the geocoder intact, newline included")
                .isEqualTo(PASTED_ADDRESS);
    }

    @ParameterizedTest
    @MethodSource("addressForms")
    void parsedAddressIsWrittenIntoTheFormFields(Path form) {
        serve(form, AddressEndpointStub.answering(200, GEOCODER_HIT));

        pasteAndParse();

        assertThat(valueOf("street")).isEqualTo("1 Blue Jays Way");
        assertThat(valueOf("city")).isEqualTo("Toronto");
        assertThat(valueOf("region")).isEqualTo("Ontario");
        assertThat(valueOf("postalCode")).isEqualTo("M5V 1J1");
        assertThat(valueOf("country")).isEqualTo("Canada");
        assertThat(valueOf("locationForMatching")).isEqualTo("Toronto");
    }

    @ParameterizedTest
    @MethodSource("addressForms")
    void anAddressTheEndpointCannotPlaceShowsAnInlineErrorAndLeavesTheButtonUsable(Path form) {
        // 422 is what the controller returns when the geocoder places nothing.
        serve(form, AddressEndpointStub.answering(422, ""));

        pasteAndParse();

        assertThat(page.locator("#parseError").isVisible())
                .as("the failure is explained next to the field, not by defacing the button")
                .isTrue();
        assertThat(page.locator("#parseError").textContent())
                .contains("Address not found");
        assertThat(page.locator("#parseBtn").textContent())
                .as("the button goes straight back to being a button")
                .isEqualTo("Parse ▶");
        assertThat(page.locator("#parseBtn").isDisabled())
                .as("a failed lookup must not lock the user out of retrying")
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("addressForms")
    void aBrokenEndpointSaysSoRatherThanBlamingTheAddress(Path form) {
        // The original bug wore this disguise for the user: a 405 that read as "Not found".
        serve(form, AddressEndpointStub.answering(405, ""));

        pasteAndParse();

        assertThat(page.locator("#parseError").textContent())
                .as("a wiring failure must not be reported as a fact about the address")
                .contains("wiring problem");
    }

    private void pasteAndParse() {
        page.locator("#rawAddress").fill(PASTED_ADDRESS);
        page.waitForResponse("**/api/parse-address**", () -> page.locator("#parseBtn").click());
        // Re-enabling happens in the handler's finally, after every field is set: the script is done.
        page.waitForFunction("() => !document.getElementById('parseBtn').disabled");
    }

    private String valueOf(String field) {
        return page.locator("[name='" + field + "']").inputValue();
    }

    private void serve(Path form, AddressEndpointStub endpoint) {
        page.route("**/*", route -> {
            String url = route.request().url();
            if (url.contains("/api/parse-address")) {
                endpoint.fulfill(route);
            } else if (url.endsWith(".css")) {
                route.fulfill(new Route.FulfillOptions().setContentType("text/css").setBody(""));
            } else {
                route.fulfill(new Route.FulfillOptions()
                                      .setContentType("text/html; charset=utf-8")
                                      .setBody(rendered(form)));
            }
        });
        page.navigate(ORIGIN + "/" + form.getFileName());
    }

    /**
     * The real template, as a browser would receive it. Nothing here runs Thymeleaf, so the test
     * performs the two expansions the script depends on: pulling in the fragment it is defined in,
     * and turning {@code th:field} into the {@code name} attributes it writes into. Everything
     * actually under test — the script, the ids it hangs off, and each page's decision to include
     * it — is the shipped source untouched.
     */
    private String rendered(Path form) {
        return templates.read(form)
                        .replace(FRAGMENT_REFERENCE, fragmentBody())
                        .replaceAll("th:field=\"\\*\\{(\\w+)}\"", "name=\"$1\"");
    }

    private String fragmentBody() {
        String fragment = templates.read(TemplateSources.ROOT.resolve("fragments/address-paste.html"));
        int openTagEnd = fragment.indexOf('>', fragment.indexOf("<th:block")) + 1;
        return fragment.substring(openTagEnd, fragment.indexOf("</th:block>"));
    }

    private static String queryOf(Request request) {
        String url = request.url();
        return URLDecoder.decode(url.substring(url.indexOf("?q=") + "?q=".length()),
                                 StandardCharsets.UTF_8);
    }

    /** Stands in for {@code AddressParseController}, recording how the page called it. */
    private static final class AddressEndpointStub {
        private final int status;
        private final String body;
        private final List<Request> requests = new ArrayList<>();

        private AddressEndpointStub(int status, String body) {
            this.status = status;
            this.body = body;
        }

        static AddressEndpointStub answering(int status, String body) {
            return new AddressEndpointStub(status, body);
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
