# JS-Behavior Tests (Playwright, tag `js`)

## What these are for

A small, deliberately narrow tier of tests that exercise the **tiny inline scripts**
our renderers embed (e.g. the calendar "Show/Hide past weeks" toggle, or
auto-filling a hotel check-out date one day after the check-in date).

This behavior lives only in the browser. Our other tiers structurally cannot reach it:

- **Renderer unit tests** assert on the HTML *string* — they never execute the JS.
- **`@WebMvcTest` slice tests** check URL mapping / status / content type — no browser.

So a JS-behavior test is the *only* place this kind of bug (two controls fighting over
one piece of UI state, an input handler that doesn't fire, etc.) can be caught.

## The one rule: test JS, and only JS

These tests must **not** boot a server, a Spring context, a database, or security.
They render HTML by calling the renderer directly and load the string into the browser:

```java
String html = ConfirmedCalendarRenderer.render(entries, today, isPublicUser);
page.setContent(html);   // no HTTP, no Spring, no DB, no auth
```

Because there is no backend in the loop, there is nothing *but* the JS to test. This is
the constraint that keeps the tier honest — it is enforced by construction, not by
discipline.

### Do
- Extend `JsBehaviorTest` (it owns the Playwright/Browser/Page lifecycle and is `@Tag("js")`).
- Get HTML from the real renderer, then `page.setContent(...)`.
- Assert only on **browser-produced** state: DOM classes toggled, element visibility,
  input `value` changes, label text rewritten by script.
- Drive the page the way a user would: `locator.click()`, `fill(...)`, `press(...)`.

### Don't
- ❌ `@SpringBootTest`, `MockMvc`, `WebTestClient`, `Testcontainers`, or any `@Autowired`.
- ❌ Start an embedded server or hit a URL. There is no URL.
- ❌ Re-assert server-rendered content correctness — that's the renderer unit test's job.
  (Set up just enough entries to produce the markup the script acts on, then stop.)
- ❌ Assert on anything that would be true *without* the script running.

If a test needs the server, auth, or the DB, it is not a JS-behavior test — write it as a
`@WebMvcTest` slice or an integration test instead.

## Tagging & running

Every class in this tier carries `@Tag("js")` (inherited from `JsBehaviorTest`).

- Default build excludes the `js` group, so `mvn test` stays fast and needs no browser:
  configured via surefire `<excludedGroups>js</excludedGroups>` in `pom.xml`.
- Run the JS tier explicitly:

  ```
  ./mvnw test -Pjs-tests
  ```

  The first run auto-downloads the Chromium binary into Playwright's cache; later runs reuse it.

## Naming

Suffix the class `...JsTest` and place it next to the renderer it covers in
`src/test/java/.../web/`. Example: `ConfirmedCalendarToggleJsTest`.