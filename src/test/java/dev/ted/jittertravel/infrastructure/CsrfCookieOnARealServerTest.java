package dev.ted.jittertravel.infrastructure;

import jakarta.servlet.Filter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CSRF cookie reaches the browser on the first page after a fresh login, even when that page has
 * started sending before its form renders — through a <strong>real Tomcat</strong>, because that is
 * the only place the failure exists. A successful login clears the CSRF cookie, and Spring writes it
 * again only when something reads the token; a response that has already started can no longer add a
 * cookie, so its form posts a token whose cookie never existed and lands on {@code /login?expired}.
 * {@link CsrfCookieFilter} reads the token at the start of every request to make that impossible.
 * MockMvc never commits a response early, so no slice test can see this (see CLAUDE.md, "The CSRF
 * cookie is written on every request").
 *
 * <p><strong>The response is committed early on purpose, rather than a large page found.</strong>
 * Which page is large depends on the data and moves as pages change — {@code /admin}, where this was
 * found, no longer has a form at all — and Tomcat's {@code setBufferSize} only ever grows the buffer,
 * so it cannot be shrunk to make a small page behave like a large one. Instead a test filter placed
 * <em>after</em> the security chain flushes the response before the view runs: exactly the state a
 * large page reaches by the time its form renders, and the only state in which the cookie can be
 * lost. Placed before the chain it would commit before {@code CsrfCookieFilter} too, and the test
 * would fail with the fix in place. Mutation-verified: removing {@code CsrfCookieFilter} from
 * {@code SecurityConfig} fails it, on the missing cookie. The page is {@code /plan-private-event},
 * whose form renders whatever is stored; the POST is {@code /logout}, which is CSRF-protected and
 * harmless.
 *
 * <p><strong>Run only when it can have changed</strong> — a new or moved POST form, or a change to
 * {@code SecurityConfig}, {@code CsrfCookieFilter} or response buffering:
 * <pre>
 *   ./mvnw test -Preal-server
 * </pre>
 * Tagged {@code real-server}, which the default build, the pre-push hook and both PIT runs exclude.
 */
@Tag("real-server")
@Tag("spring")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsrfCookieOnARealServerTest extends AbstractTestcontainerIntegrationTest {

    private static final String FORM_PAGE = "/plan-private-event";

    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @TestConfiguration
    static class ResponseStartsBeforeTheView {
        @Bean
        FilterRegistrationBean<Filter> commitBeforeTheView() {
            Filter filter = (request, response, chain) -> {
                response.flushBuffer();
                chain.doFilter(request, response);
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            // After Spring Security's chain (and so after CsrfCookieFilter), before the servlet.
            registration.setOrder(Ordered.LOWEST_PRECEDENCE);
            // Only the page under test: the login page must still mint its own cookie, or a broken
            // filter fails the login step instead of the assertion that names the bug.
            registration.addUrlPatterns(FORM_PAGE);
            return registration;
        }
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder()
                                              .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                                              .followRedirects(HttpClient.Redirect.NEVER)
                                              .build();

    @Test
    void theFirstFormPageAfterAFreshLoginSetsTheCsrfCookieAndItsFormPosts() throws Exception {
        HttpResponse<String> loginPage = get("/login");
        HttpResponse<String> login = post("/login", "username=ted&password=test&_csrf="
                                                     + encode(csrfToken(loginPage.body())));
        assertThat(login.headers().firstValue("Location"))
                .as("Login should redirect")
                .isPresent()
                .get(InstanceOfAssertFactories.STRING)
                .as("Login should succeed, not bounce back to /login")
                .doesNotContain("/login");

        HttpResponse<String> formPage = get(FORM_PAGE);
        assertThat(formPage.statusCode())
                .as(FORM_PAGE + " status for a logged-in owner")
                .isEqualTo(200);
        assertThat(formPage.headers().allValues("Set-Cookie"))
                .as("The first page after login must write the CSRF cookie itself")
                .anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") && !cookie.startsWith("XSRF-TOKEN=;"));

        HttpResponse<String> logout = post("/logout", "_csrf=" + encode(csrfToken(formPage.body())));
        assertThat(logout.headers().firstValue("Location"))
                .as("Logout should redirect")
                .isPresent()
                .get(InstanceOfAssertFactories.STRING)
                .as("The page's form posts with its token and cookie, rather than landing on /login?expired")
                .isEqualTo(uri("/login?logout").toString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                         HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String form) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri(path))
                                    .header("Content-Type", "application/x-www-form-urlencoded")
                                    .POST(HttpRequest.BodyPublishers.ofString(form))
                                    .build(),
                         HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String csrfToken(String html) {
        Matcher matcher = CSRF_FIELD.matcher(html);
        assertThat(matcher.find())
                .as("The page renders a CSRF hidden field")
                .isTrue();
        return matcher.group(1);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
