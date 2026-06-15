package dev.ted.jittertravel.web;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.*;

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
    }

    /**
     * Loads server-rendered HTML directly into the browser and runs its embedded
     * scripts. Deliberately the only way these tests get markup onto the page — no
     * server is started, so only client-side behavior is under test.
     */
    protected void loadRendered(String html) {
        page.setContent(html);
    }
}