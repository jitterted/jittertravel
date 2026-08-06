package dev.ted.jittertravel.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for the JS-behavior test tier — tests of the tiny inline scripts our
 * renderers embed (toggles, auto-fill, etc.). See {@code docs/JS-Behavior-Tests.md}.
 * <p>
 * These tests load renderer output straight into a real browser with
 * {@link #loadRendered(String)} — there is no HTTP server, Spring context, DB, or
 * security in the loop, so there is nothing but the JS to test. Subclasses must keep
 * it that way: no {@code @SpringBootTest}, {@code MockMvc}, or {@code @Autowired}.
 */
@Tag("js")
abstract class JsBehaviorTest {

    private static Playwright playwright;
    private static Browser browser;
    protected Page page;
    private final List<BrowserContext> zonedContexts = new ArrayList<>();

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPage() {
        page = browser.newPage();
    }

    @AfterEach
    void closePage() {
        if (page != null) {
            page.close();
        }
        zonedContexts.forEach(BrowserContext::close);
        zonedContexts.clear();
    }

    /**
     * Loads server-rendered HTML directly into the browser and runs its embedded
     * scripts. Deliberately the only way these tests get markup onto the page — no
     * server is started, so only client-side behavior is under test.
     */
    protected void loadRendered(String html) {
        page.setContent(html);
    }

    /**
     * A page whose browser reports {@code timezoneId} as its zone, for testing anything that
     * localizes to the viewer. Pinning the zone is the whole point: the test JVM runs in UTC
     * (see {@code pom.xml}), so an unpinned browser would prove nothing about zone handling.
     * <p>
     * Contexts opened this way are closed after each test.
     */
    protected Page pageInZone(String timezoneId) {
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setTimezoneId(timezoneId));
        zonedContexts.add(context);
        return context.newPage();
    }
}