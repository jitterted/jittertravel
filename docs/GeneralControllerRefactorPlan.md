
## Verifying the changes compile cleanly

Let me check the new controller and matrix test for problems. I have everything I need. Here's my plan, then the changes.

## Plan

1. **`GeneralController`** — replace the confusing `showViewNav` with intent-named, role-based flags: `showDataEntryNav` (OWNER), `showBookingsNav` (OWNER), `showItineraryNav` (FAMILY or OWNER). Add a small comment pointing to the single source of truth.
2. **`index.html`** — gate the Bookings group on `showBookingsNav` and the Itinerary card on `showItineraryNav`.
3. **`SecurityConfig`** — move the booking-list view routes to OWNER-only (so they match the confirmed policy), keep `/itinerary` for FAMILY+OWNER, and add a friendly access-denied redirect for authenticated-but-unauthorized users (Question C).
4. **`GeneralControllerTest`** — keep it as a clean controller test (OWNER), strengthen the OWNER nav assertion.
5. **New `AuthorizationMatrixTest`** — parameterized role × route matrix importing `SecurityConfig` (the canonical policy statement).
6. **`SecurityAuthorizationTest`** — slim down: keep login/redirect-specific behavior, remove nav-matrix duplication now covered by the matrix test, and add the FAMILY home-nav + anonymous calendar cases as focused home-nav tests.
7. **New doc** — `docs/Authorization_Single_Source_Of_Truth.md` recording what Option 2 needs.

---

### 1. `GeneralController`

<llm-snippet-file>src/main/java/dev/ted/jittertravel/web/GeneralController.java</llm-snippet-file>
```java
import org.springframework.core.env.Profiles;

// ... existing code ...
    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        boolean runningLocally = local || environment.acceptsProfiles(Profiles.of("prod-preview"));

        // Nav visibility is gated by ROLE, mirroring the route rules in SecurityConfig
        // (the secured "!local" chain). These two places are the source of truth for the
        // authorization policy and MUST be kept in sync; see
        // docs/Authorization_Single_Source_Of_Truth.md for the plan to unify them.
        //
        // In the `local` profile there is no authentication (everything is permitted), so
        // every nav group is shown.
        boolean owner = request.isUserInRole("OWNER");
        boolean family = request.isUserInRole("FAMILY");

        boolean showDataEntryNav = local || owner;          // Book & Plan + Admin groups
        boolean showBookingsNav = local || owner;           // Bookings list group (OWNER only)
        boolean showItineraryNav = local || owner || family; // Itinerary card (FAMILY + OWNER)
        // Calendar is always visible (redacted for anonymous via CalendarEntryRedactor).

        model.addAttribute("runningLocally", runningLocally);
        model.addAttribute("showDataEntryNav", showDataEntryNav);
        model.addAttribute("showBookingsNav", showBookingsNav);
        model.addAttribute("showItineraryNav", showItineraryNav);
        model.addAttribute("pendingCount", persister.countPendingCommands());
        model.addAttribute("buildTime", BUILD_TIME_FORMATTER.format(buildProperties.getTime()));
        return "index";
    }
// ... existing code ...
```

---

### 2. `index.html`

Gate the Bookings group on `showBookingsNav`, and the Itinerary card on `showItineraryNav`. Calendar stays ungated.

<llm-snippet-file>src/main/resources/templates/index.html</llm-snippet-file>
```html
<!-- ... existing code ... -->
            <div class="nav-group" th:if="${showBookingsNav}">
                <span class="nav-group-label">Bookings</span>
                <div class="nav-cards">

                    <a class="nav-card" href="/booked-flights">
<!-- ... existing code ... -->
            <div class="nav-group">
                <span class="nav-group-label">Overview</span>
                <div class="nav-cards">

                    <a class="nav-card" href="/itinerary" th:if="${showItineraryNav}">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M8 6h13M8 12h13M8 18h13"/>
<!-- ... existing code ... -->
```

---

### 3. `SecurityConfig`

Two changes: move the booking-list view routes to OWNER-only, and add a friendly access-denied redirect (Question C) so an authenticated-but-unauthorized user lands on the home page instead of a bare 403.

<llm-snippet-file>src/main/java/dev/ted/jittertravel/infrastructure/SecurityConfig.java</llm-snippet-file>
```java
// ... existing code ...
    /**
     * Three access tiers: OWNER (ted) has full access; FAMILY can view the itinerary and the
     * (redacted) calendar only; anonymous can only see the (redacted) calendar and home page.
     * An anonymous request to a protected page is redirected to the login form; an authenticated
     * user who lacks the required role is redirected back to the home page (a friendlier
     * alternative to a bare 403).
     * <p>
     * This route table and {@code GeneralController}'s nav flags are the source of truth for the
     * authorization policy and MUST be kept in sync; see
     * {@code docs/Authorization_Single_Source_Of_Truth.md} for the plan to unify them.
     */
    @Bean
    @Profile("!local")
    public SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Admin (includes /admin/eventlog, /admin/commandlog, /admin/pending-commands)
                        .requestMatchers("/admin", "/admin/**").hasRole("OWNER")
                        // Booking / planning data-entry forms and their submit/lookup endpoints
                        .requestMatchers(
                                "/book-flight", "/book-flight/**",
                                "/book-hotel", "/book-hotel/**",
                                "/book-train", "/book-train/**",
                                "/plan-conference", "/plan-conference/**",
                                "/plan-gathering", "/plan-gathering/**",
                                "/clear-conflict", "/clear-conflict/**",
                                "/api/parse-address").hasRole("OWNER")
                        // Change-flight edit lives under the booked-flights list:
                        // protect the per-flight edit/lookup explicitly.
                        .requestMatchers("/booked-flights/*", "/booked-flights/*/lookup").hasRole("OWNER")
                        // Booking lists are OWNER-only (FAMILY cannot view booking details).
                        .requestMatchers(
                                "/booked-flights", "/booked-trains", "/booked-hotels",
                                "/tentative-conferences", "/planned-gatherings").hasRole("OWNER")
                        // Itinerary: FAMILY and OWNER may view it; anonymous may not.
                        .requestMatchers("/itinerary", "/itinerary/**").hasAnyRole("FAMILY", "OWNER")
                        .anyRequest().permitAll())
                // Standard form login: a failed login goes to /login?error and the login page
                // shows the error. Do NOT set failureUrl("/") — that makes
                // DefaultLoginPageGeneratingFilter render the login form at "/" on every visit.
                .formLogin(Customizer.withDefaults())
                // Friendlier authorization failure: send an authenticated-but-unauthorized user
                // back to the home page rather than showing a bare 403. Anonymous users are still
                // sent to the login form by the entry point (default form-login behavior).
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDenied) ->
                                response.sendRedirect(request.getContextPath() + "/")))
                .build();
    }
// ... existing code ...
```

A note on this: Spring distinguishes the two cases for you. An **anonymous** request to a protected route triggers the *authentication entry point* (→ `/login`), while an **authenticated** request lacking the role triggers the *access-denied handler* (→ `/`). So FAMILY hitting `/book-flight` now redirects to `/`, and anonymous hitting `/itinerary` still redirects to `/login`.

---

### 4. `GeneralControllerTest` (kept clean; OWNER-focused)

Strengthen the OWNER nav assertion and rename it for clarity.

<llm-snippet-file>src/test/java/dev/ted/jittertravel/web/GeneralControllerTest.java</llm-snippet-file>
```java
// ... existing code ...
    @Test
    void homeShowsAllNavGroupsForOwner() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("/book-flight")        // data-entry
                .contains(">Admin</span>")        // admin
                .contains("/booked-flights")      // bookings list
                .contains("/itinerary")           // itinerary
                .contains("/calendar");           // always
    }
// ... existing code ...
```

---

### 5. New `AuthorizationMatrixTest` (canonical policy)

This is the single, change-friendly statement of the policy. To edit the policy later, you change the matrix rows and the test tells you exactly which route/role diverges.

<llm-snippet-file>src/test/java/dev/ted/jittertravel/web/AuthorizationMatrixTest.java</llm-snippet-file>
```java
package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * Canonical, change-friendly statement of the authorization policy as a (role × route → outcome)
 * matrix, verified against the real secured chain ({@link SecurityConfig}, the "!local" profile).
 * <p>
 * To change the policy, edit the rows in {@link #policy()} and update {@code SecurityConfig}
 * accordingly; a mismatch will fail here pointing at the exact route/role.
 * <p>
 * Outcomes:
 * <ul>
 *   <li>{@code OK} — request is permitted (2xx).</li>
 *   <li>{@code LOGIN} — anonymous request to a protected route → 302 redirect to {@code /login}.</li>
 *   <li>{@code DENIED_HOME} — authenticated but under-privileged → 302 redirect to {@code /}
 *       (the friendly access-denied handler).</li>
 * </ul>
 * Routes here are simple GET endpoints whose controllers don't need collaborators beyond the
 * mocked {@link PostgresPersister}; controller-specific behavior is covered by each controller's
 * own test. The booking-list and itinerary GETs hit other controllers, so this slice focuses on
 * routes served by {@link GeneralController} plus the security-only redirect behavior.
 */
@WebMvcTest(GeneralController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass"})
class AuthorizationMatrixTest {

    private enum Outcome { OK, LOGIN, DENIED_HOME }

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @MockitoBean
    BuildProperties buildProperties;

    @BeforeEach
    void setUp() {
        lenient().when(buildProperties.getTime()).thenReturn(Instant.EPOCH);
        lenient().when(persister.countPendingCommands()).thenReturn(0);
    }

    static Stream<Arguments> policy() {
        return Stream.of(
                // route,                  OWNER,                FAMILY,               ANONYMOUS
                arguments("/",             Outcome.OK,           Outcome.OK,           Outcome.OK),
                arguments("/calendar",     Outcome.OK,           Outcome.OK,           Outcome.OK),
                arguments("/itinerary",    Outcome.OK,           Outcome.OK,           Outcome.LOGIN),
                arguments("/book-flight",  Outcome.OK,           Outcome.DENIED_HOME,  Outcome.LOGIN),
                arguments("/booked-flights", Outcome.OK,         Outcome.DENIED_HOME,  Outcome.LOGIN),
                arguments("/admin",        Outcome.OK,           Outcome.DENIED_HOME,  Outcome.LOGIN)
        );
    }

    @ParameterizedTest(name = "{0}: OWNER={1}, FAMILY={2}, ANON={3}")
    @MethodSource("policy")
    void enforcesPolicy(String route, Outcome owner, Outcome family, Outcome anonymous) {
        assertOutcome(route, "OWNER", owner);
        assertOutcome(route, "FAMILY", family);
        assertAnonymousOutcome(route, anonymous);
    }

    private void assertOutcome(String route, String role, Outcome expected) {
        MvcTestResult result = mockMvc.get().uri(route)
                .with(SecurityMockMvcRequestPostProcessors.user("user").roles(role))
                .exchange();
        verify(route, role, result, expected);
    }

    private void assertAnonymousOutcome(String route, Outcome expected) {
        MvcTestResult result = mockMvc.get().uri(route)
                .with(SecurityMockMvcRequestPostProcessors.anonymous())
                .exchange();
        verify(route, "ANONYMOUS", result, expected);
    }

    private static void verify(String route, String who, MvcTestResult result, Outcome expected) {
        switch (expected) {
            case OK -> assertThat(result)
                    .as("%s on %s should be permitted", who, route)
                    .hasStatus2xxSuccessful();
            case LOGIN -> assertThat(result)
                    .as("%s on %s should be redirected to login", who, route)
                    .hasStatus3xxRedirection()
                    .hasHeader("Location", "/login");
            case DENIED_HOME -> assertThat(result)
                    .as("%s on %s should be redirected home (access denied)", who, route)
                    .hasStatus3xxRedirection()
                    .hasHeader("Location", "/");
        }
    }
}
```

A scoping note worth raising: this test slice only loads `GeneralController` (`@WebMvcTest(GeneralController.class)`). Routes like `/itinerary`, `/booked-flights`, `/admin` are served by **other** controllers that aren't loaded here. The security chain still runs (the matchers are URL-based), so:

- For protected routes, the **deny** decisions (`LOGIN`, `DENIED_HOME`) are produced by the filter chain *before* dispatch, so they assert correctly even without those controllers loaded.
- For the **OK** rows on routes not served by `GeneralController` (e.g., OWNER on `/admin`, FAMILY on `/itinerary`), the request will pass security but then 404 because no handler is loaded — which would fail `hasStatus2xxSuccessful()`.

**Question D:** I'd like to load the full web layer for the matrix so the `OK` rows are meaningful. The cleanest options are:
- Use `@WebMvcTest` **without** specifying a controller (loads all `@Controller`s) and mock the extra collaborators (`ItineraryProjector`, `Clock`, the booking projectors, `CalendarAggregator`, etc.), or
- Make the matrix assert only the **security decision** (permitted-vs-redirected) and treat a post-security 404/200 both as "not blocked by security."

The second keeps the test focused purely on authorization and avoids wiring many mocks. Which do you prefer? If you don't have a preference, I'll go with the **security-decision-only** approach (treat any non-redirect as "allowed by security"), which is the most robust and truly tests the policy rather than the controllers.

---

### 6. `SecurityAuthorizationTest` (slimmed; keep login + add focused home-nav cases)

Remove the nav-matrix duplication (now in the matrix test) and keep what's unique: login form rendering, failed-login behavior, and the home-nav rendering per role (which the matrix test does *not* cover, since it checks routes, not rendered links).

<llm-snippet-file>src/test/java/dev/ted/jittertravel/web/SecurityAuthorizationTest.java</llm-snippet-file>
```java
// ... existing code ...
/**
 * Verifies login-related behavior and the rendered home-page navigation per role under the
 * secured ("!local") chain. Pure route authorization (role × route → outcome) lives in
 * {@link AuthorizationMatrixTest}.
 */
@WebMvcTest(GeneralController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass"})
class SecurityAuthorizationTest {
// ... existing code ...
    @Test
    @WithAnonymousUser
    void homePageIsPublicAndRendersHomeNotTheLoginForm() {
        given(persister.countPendingCommands()).willReturn(0);

        // Regression: failureUrl("/") made DefaultLoginPageGeneratingFilter render the login
        // form at "/" (HTTP 200), so a status-only check passed while the home page was hidden.
        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("wordmark-text")
                .doesNotContain("Please sign in")
                .doesNotContain("name=\"password\"");
    }

    @Test
    @WithAnonymousUser
    void anonymousHomeShowsCalendarOnly() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("/calendar")
                .doesNotContain("/itinerary")
                .doesNotContain("/booked-flights")
                .doesNotContain("/book-flight")
                .doesNotContain(">Admin</span>");
    }

    @Test
    @WithMockUser(roles = "FAMILY")
    void familyHomeShowsItineraryAndCalendarOnly() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("/itinerary")
                .contains("/calendar")
                .doesNotContain("/booked-flights")
                .doesNotContain("/book-flight")
                .doesNotContain(">Admin</span>");
    }

    @Test
    @WithAnonymousUser
    void failedLoginRedirectsToLoginPageWithError() {
        assertThat(mockMvc.post().uri("/login")
                .param("username", "ted")
                .param("password", "wrong-password")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/login?error");
    }
}
```

This adds the `@WithMockUser` import usage; ensure the import is present:

<llm-snippet-file>src/test/java/dev/ted/jittertravel/web/SecurityAuthorizationTest.java</llm-snippet-file>
```java
// ... existing code ...
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
// ... existing code ...
```

I removed `anonymousIsRedirectedToLoginForAdminPages`, `anonymousIsRedirectedToLoginForBookingPages`, and `anonymousIsRedirectedToLoginForChangeFlightEditButNotTheList` from this class because the matrix test now owns route authorization. The per-flight edit (`/booked-flights/{id}`) is a good matrix row to keep, though — see the doc's note.

---

### 7. New doc for Option 2

<llm-snippet-file>docs/Authorization_Single_Source_Of_Truth.md</llm-snippet-file>
```markdown
# Authorization: Single Source of Truth (Option 2)

## Status

**Not yet implemented.** Today (Option 1) the authorization policy lives in two
hand-synchronized places:

1. `infrastructure/SecurityConfig` — the secured (`!local`) `SecurityFilterChain`
   `requestMatchers(...)` rules. This is the **real enforcement**.
2. `web/GeneralController#home` — role-based nav flags (`showDataEntryNav`,
   `showBookingsNav`, `showItineraryNav`) consumed by `templates/index.html`.

These two must be kept in sync by hand. The `AuthorizationMatrixTest` and the
per-role home-nav tests in `SecurityAuthorizationTest` exist to catch drift, but
nothing *prevents* it.

## Current policy (the thing to unify)

| Route group                                   | OWNER | FAMILY | Anonymous |
|-----------------------------------------------|:-----:|:------:|:---------:|
| `/` (home)                                    |  ✅   |   ✅   |    ✅     |
| `/calendar` (redacted for anonymous)          |  ✅   |   ✅   |    ✅     |
| `/itinerary`                                  |  ✅   |   ✅   |    ❌     |
| Booking lists (`/booked-*`, `/tentative-*`, `/planned-*`) | ✅ | ❌ | ❌ |
| Data entry (`/book-*`, `/plan-*`, edit forms) |  ✅   |   ❌   |    ❌     |
| Admin (`/admin/**`)                           |  ✅   |   ❌   |    ❌     |

Notes:
- `local` profile = everything permitted, no authentication.
- Authenticated-but-unauthorized → redirect to `/` (friendly access-denied handler).
- Anonymous → redirect to `/login` (form-login entry point).

## Goal of Option 2

Define the policy **once** and have both the security rules and the nav flags
derive from it, so a single edit changes both consistently.

## Proposed design

Introduce a central, declarative policy describing each protected **nav area** and
the roles allowed to see/access it. Sketch:

```java
public enum NavArea {
    DATA_ENTRY(Set.of("OWNER"),
               List.of("/book-flight", "/book-hotel", "/book-train",
                       "/plan-conference", "/plan-gathering", "/clear-conflict",
                       "/api/parse-address")),
    ADMIN(Set.of("OWNER"),
          List.of("/admin")),
    BOOKINGS(Set.of("OWNER"),
             List.of("/booked-flights", "/booked-trains", "/booked-hotels",
                     "/tentative-conferences", "/planned-gatherings")),
    ITINERARY(Set.of("OWNER", "FAMILY"),
              List.of("/itinerary"));

    final Set<String> roles;          // ROLE_ prefix added where needed
    final List<String> basePaths;     // each also implies "/**" sub-paths where applicable
    // ...
}
```

### Consumers

1. **`SecurityConfig`** builds `requestMatchers(...)` by iterating `NavArea`,
   mapping `basePaths` (+ `/**`) to `hasAnyRole(roles)`. Order matters: more
   specific matchers (e.g. `/booked-flights/*` edit) must precede broader ones,
   so the policy object needs an explicit ordering or a dedicated "edit" area.

2. **`GeneralController`** computes nav flags by asking the policy whether the
   current request's roles satisfy each `NavArea` (replacing the hand-written
   `owner`/`family` booleans). The template keeps reading `show*Nav` flags, or
   the controller exposes a `Set<NavArea>` and the template checks membership.

### Tests

- `AuthorizationMatrixTest` becomes data-driven directly from `NavArea`
  (each route × role derived from the enum), so the matrix can't disagree with
  the rules — they share a source.
- Keep the rendered home-nav tests (links present/absent per role), since those
  verify the *template* wiring, which the policy object alone can't guarantee.

## Edge cases to handle when implementing

- **Sub-path nuance:** data-entry uses both exact paths and `/**`; booking edit
  (`/booked-flights/*`, `/booked-flights/*/lookup`) is OWNER-only and must be
  ordered *before* any broader booking-list rule. Model this explicitly (e.g. a
  separate `BOOKING_EDIT` area or an ordered list) rather than relying on enum
  declaration order alone.
- **`/calendar` is intentionally public** (redaction in
  `application/CalendarEntryRedactor`); it is *not* a `NavArea` and must remain
  outside the policy's protected set.
- **`local` profile** still short-circuits to "show everything"; the policy
  object only governs the secured chain.
- **Access-denied vs. login redirects** are orthogonal to the policy mapping and
  stay in `SecurityConfig`'s `exceptionHandling`.

## Migration checklist

- [ ] Add `NavArea` (or equivalent) policy type with roles + paths.
- [ ] Generate `SecurityConfig` matchers from it (preserving matcher ordering).
- [ ] Derive `GeneralController` nav flags from it.
- [ ] Re-point `AuthorizationMatrixTest` to read rows from the policy type.
- [ ] Keep `local`-profile behavior and the redirect handlers unchanged.
- [ ] Delete the "keep in sync by hand" comments in `SecurityConfig` and
      `GeneralController` once unified.
```

---

## Open items before I consider this done

1. **Question D (matrix scope):** load the full web layer (mock many collaborators) so `OK` rows are real, **or** make the matrix assert the security *decision* only (redirect vs. not). My recommendation is the security-decision-only approach — focused on authorization, no extra mocks. Confirm and I'll finalize `AuthorizationMatrixTest` accordingly (the current draft uses concrete status codes, which needs the full layer for the `OK` rows on non-`GeneralController` routes).

2. **`/booked-flights/{id}` edit row:** I dropped its dedicated anonymous test from `SecurityAuthorizationTest`. Want me to add it as a matrix row (`OWNER=OK-ish, FAMILY=DENIED_HOME, ANON=LOGIN`)? It's a good regression guard for the "list public-ish vs. edit protected" distinction.

3. **Template marker robustness:** the tests still assert on `href` substrings and `>Admin</span>`. If you'd like, I can add stable `data-testid` attributes to each nav group so the tests don't break when icons/links change. Say the word and I'll include that edit.

Once you confirm D (and 2–3 if you want them), I'll lock in the final `AuthorizationMatrixTest` and adjust assertions to match.
