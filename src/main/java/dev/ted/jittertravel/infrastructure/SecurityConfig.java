package dev.ted.jittertravel.infrastructure;

import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.time.Duration;

/**
 * One security chain, always on. There is no permissive/no-auth variant: local development runs
 * the same secured form-login chain as production (via the {@code prod-preview} profile, which
 * supplies local stand-in passwords). A single chain means local and production cannot diverge
 * on who is treated as authenticated.
 *
 * <p>Three access tiers: OWNER (ted) has full access; FAMILY can view the itinerary and the
 * full calendar only; anonymous can only see the redacted calendar and home page.
 * An anonymous request to a protected page is redirected to the login form; an authenticated
 * user who lacks the required role is redirected back to the home page (a friendlier
 * alternative to a bare 403).
 */
@Configuration
public class SecurityConfig {

    /**
     * How long an unused device stays signed in. Each successful remember-me login rotates the
     * token and rewrites {@code last_used}, so a device in regular use never expires — this window
     * only reaches one that has been idle for a month.
     */
    private static final int TOKEN_VALIDITY_SECONDS = (int) Duration.ofDays(30).toSeconds();

    @Bean
    public SecurityFilterChain securedFilterChain(HttpSecurity http,
                                                  RememberMeServices rememberMeServices) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Admin (includes /admin/eventlog, /admin/commandlog, /admin/pending-commands)
                        .requestMatchers("/admin", "/admin/**").hasRole("OWNER")
                        // Actuator: health stays public for Railway's health check; everything
                        // else (metrics, etc.) is owner-only. Order matters: the health matcher
                        // must precede the catch-all actuator matcher.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("OWNER")
                        // Booking / planning data-entry forms and their submit/lookup endpoints
                        .requestMatchers(
                                "/book-flight", "/book-flight/**",
                                "/book-hotel", "/book-hotel/**",
                                "/book-train", "/book-train/**",
                                "/plan-conference", "/plan-conference/**",
                                "/plan-gathering", "/plan-gathering/**",
                                "/plan-private-event", "/plan-private-event/**",
                                "/plan-ground-transfer", "/plan-ground-transfer/**",
                                "/clear-conflict", "/clear-conflict/**",
                                "/api/parse-address",
                                // The prefill's own input is private: a Sessionize URL says Ted is
                                // thinking of submitting there, which is the submission pipeline in
                                // one field. Public page or not, who is reading it stays OWNER-only.
                                "/api/sessionize-prefill").hasRole("OWNER")
                        // Per-item edit pages must be ordered before the list matchers below.
                        // A single * matches one path segment only, so per-item *actions* need
                        // their own entry alongside the page (as /booked-flights/*/lookup does).
                        .requestMatchers("/booked-flights/*", "/booked-flights/*/lookup",
                                "/booked-flights/*/lookup/select",
                                "/booked-trains/*", "/booked-trains/*/cancel",
                                "/booked-hotels/*",
                                "/booked-hotels/*/cancel",
                                "/ground-transfers/*/cancel",
                                // Same shape again: the private event's only per-item action. A
                                // single * matches one segment, so this needs its own entry even
                                // now that the bare /planned-private-events list exists below.
                                // The edit page will take "/planned-private-events/*"
                                // (docs/ChangePrivateEventPlan.md D4).
                                "/planned-private-events/*/cancel",
                                // The matching-location page prints the venue name, the city and
                                // the evening's times — the same content as the cancel page above,
                                // and the value it writes reshapes /schedule-problems.
                                "/planned-private-events/*/matching-location",
                                "/conferences/*", "/conferences/*/decline",
                                "/conferences/*/confirm", "/conferences/*/cfp",
                                // Submission status is the most private thing the conference model
                                // holds — CLAUDE.md keeps the whole pipeline OWNER-only.
                                "/conferences/*/talk",
                                "/planned-gatherings/*").hasRole("OWNER")
                        // Booking lists: OWNER-only (FAMILY cannot view booking details).
                        .requestMatchers(
                                "/booked-flights", "/booked-trains", "/booked-hotels",
                                "/conferences", "/planned-gatherings",
                                // The private-event list prints venue names and street addresses —
                                // the one surface that reads them at all.
                                "/planned-private-events").hasRole("OWNER")
                        // Schedule problems: conflict/gap report over the whole itinerary —
                        // exact arrival and departure times, hotel and gathering names, and
                        // internal ids in its clear-conflict links. Owner-only.
                        .requestMatchers("/schedule-problems").hasRole("OWNER")
                        // Itinerary: FAMILY and OWNER may view; anonymous may not.
                        .requestMatchers("/itinerary", "/itinerary/**").hasAnyRole("FAMILY", "OWNER")
                        // Calendar subscription feed: permitAll at the security layer because the
                        // URL *token* authenticates, not the login session — the iOS Calendar app
                        // cannot submit a login form. CalendarFeedController returns 404 without a
                        // valid token, so this is gated in the controller, not here. The feed is
                        // unredacted OWNER data; the token is the only credential (see that class).
                        .requestMatchers("/calendar/feed/**").permitAll()
                        .anyRequest().permitAll())
                // CSRF token lives in a cookie, not the HTTP session. A session-bound token dies
                // whenever the in-memory session does — every redeploy, every local devtools
                // restart, every idle timeout — and a login page rendered before that death then
                // submits a token with no session to match, which the CsrfFilter rejects (and our
                // accessDeniedHandler used to bounce silently to "/", looking like "not logged in").
                // A cookie outlives the session, so the very first login after a restart still
                // validates. Kept HttpOnly: the server renders the token into the form from the
                // request attribute, so JS never needs to read it — no weaker than a session token
                // against XSS. Covered by SecurityAuthorizationTest.staleCsrfTokenReturnsToLogin.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository()))
                // Custom form login at /login (LoginController + templates/login.html). We replace
                // Spring's generated page so the form can carry a hidden browserZone field, letting
                // ZoneCapturingAuthenticationSuccessHandler set the viewerZone cookie on the very
                // response that redirects to the originally-requested page — so a deep link that
                // bounced through login renders the correct "today" on first paint. A failed login
                // still goes to /login?error, which the template shows. Do NOT set failureUrl("/").
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(new ZoneCapturingAuthenticationSuccessHandler())
                        .permitAll())
                // A successful logout lands on /login?logout, where login.html already renders a
                // "you have been signed out" notice. It used to go to "/", which left that notice
                // unreachable in production (only LoginControllerTest saw it, by requesting the URL
                // directly) and made a deliberate sign-out indistinguishable from arriving signed
                // out. There is still no logout *affordance* anywhere — see docs/Cleanup_Tasks.md,
                // "No logout affordance" — but POST /logout works, and now it says so.
                // Stay signed in across a restart. The HTTP session is in-memory and dies with
                // every redeploy and every devtools restart, and the 2026-08-18 CSRF-cookie fix
                // only made the *next* login succeed — it did not keep anyone logged in. The
                // remember-me cookie re-authenticates instead, so a restart is invisible.
                // Persistent tokens (a row per device in persistent_logins), never the hash-based
                // variant: that one signs the cookie with the *encoded* password, and
                // userDetailsService below BCrypt-encodes at every startup with a fresh salt, so
                // the signature would stop matching on the very restart this is meant to survive.
                // The DSL takes the key from the services (RememberMeConfigurer.getKey()), so it
                // is stated once, on the bean.
                .rememberMe(rememberMe -> rememberMe.rememberMeServices(rememberMeServices))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                // Authenticated-but-unauthorized users are redirected to the home page instead
                // of seeing a bare 403. Anonymous users still go to /login via the entry point.
                // Exception: /api/** callers (our fetch endpoints) get a real 403 — a 302 redirect
                // to an HTML page reads as a 200 success to fetch(), masking the actual failure
                // (this is what turned a CSRF rejection into an opaque client-side "Error").
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDenied) -> {
                            if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                                // fetch() callers get a real 403, never a 302 to an HTML page.
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            } else if (accessDenied instanceof CsrfException) {
                                // A rejected CSRF token means the session that minted it is gone
                                // (restart, timeout) — an expired login, not a permissions problem.
                                // Send the viewer back to the login page with a message rather than
                                // silently home, where an expired login looks identical to never
                                // having signed in. login.html reads ?expired.
                                response.sendRedirect(request.getContextPath() + "/login?expired");
                            } else {
                                // Authenticated but insufficient role: friendlier than a bare 403.
                                response.sendRedirect(request.getContextPath() + "/");
                            }
                        }))
                .build();
    }

    /**
     * One row per remembered device, in the existing Postgres. Revoking a device is a delete, and
     * Spring rotates the token on every use so a replayed cookie is detected as theft — neither of
     * which the hash-based variant offers. The table is created by {@code schema.sql}, not by
     * {@code setCreateTableOnStartup}, so schema lives in one place.
     */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    /**
     * REMEMBER_ME_KEY must be stable across restarts — an absent key would make Spring mint a
     * random one per boot, silently defeating the whole feature, which is why the {@code @Value}
     * has no default and an unset variable fails the boot instead. Changing it invalidates every
     * remembered device, which is the one revocation control that does not need a logged-in
     * browser.
     * <p>
     * It does <em>not</em> sign the cookie, whatever the name suggests: this variant's cookie is a
     * random series + token pair from {@code SecureRandom}, held in {@code persistent_logins}, and
     * the key never enters it. Spring passes the key only to the {@code RememberMeAuthenticationToken}
     * it mints, whose {@code key.hashCode()} {@code RememberMeAuthenticationProvider} compares to
     * decide the token came from a services instance it trusts. So stability is the load-bearing
     * property and secrecy is worth much less here than it is for TED_PASSWORD — generate a long
     * random value anyway, since it costs nothing. (The hash-based variant rejected above is the
     * one that signs with the key.)
     * <p>
     * The cookie's Secure flag is deliberately left to {@code request.isSecure()} rather than
     * pinned true: {@code server.forward-headers-strategy=framework} makes that correct behind
     * Railway's TLS-terminating proxy, and it keeps the cookie usable over the plain-http local
     * prod-preview, where a pinned Secure cookie would be dropped by the browser and the feature
     * would look broken locally while being fine in production. HttpOnly is set unconditionally
     * by AbstractRememberMeServices.
     */
    @Bean
    public RememberMeServices rememberMeServices(@Value("${REMEMBER_ME_KEY}") String rememberMeKey,
                                                 UserDetailsService userDetailsService,
                                                 PersistentTokenRepository tokenRepository) {
        PersistentTokenBasedRememberMeServices services =
                new PersistentTokenBasedRememberMeServices(rememberMeKey, userDetailsService, tokenRepository);
        services.setTokenValiditySeconds(TOKEN_VALIDITY_SECONDS);
        return services;
    }

    private CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        // Explicit rather than relying on the default: the token cookie must never be readable
        // by page scripts (the form gets its token server-side, so JS has no need to).
        repository.setCookieCustomizer(cookie -> cookie.httpOnly(true));
        return repository;
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${TED_PASSWORD}") String tedPassword,
            @Value("${FAMILY_PASSWORD}") String familyPassword,
            PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("ted")
                    .password(encoder.encode(tedPassword))
                    .roles("OWNER")
                    .build(),
                User.withUsername("family")
                    .password(encoder.encode(familyPassword))
                    .roles("FAMILY")
                    .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
