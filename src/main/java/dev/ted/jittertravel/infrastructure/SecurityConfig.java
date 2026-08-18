package dev.ted.jittertravel.infrastructure;

import jakarta.servlet.http.HttpServletResponse;
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

    @Bean
    public SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
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
                                "/clear-conflict", "/clear-conflict/**",
                                "/api/parse-address").hasRole("OWNER")
                        // Per-item edit pages must be ordered before the list matchers below.
                        // A single * matches one path segment only, so per-item *actions* need
                        // their own entry alongside the page (as /booked-flights/*/lookup does).
                        .requestMatchers("/booked-flights/*", "/booked-flights/*/lookup",
                                "/booked-flights/*/lookup/select",
                                "/booked-trains/*", "/booked-hotels/*",
                                "/booked-hotels/*/cancel",
                                "/tentative-conferences/*", "/tentative-conferences/*/decline",
                                "/planned-gatherings/*").hasRole("OWNER")
                        // Booking lists: OWNER-only (FAMILY cannot view booking details).
                        .requestMatchers(
                                "/booked-flights", "/booked-trains", "/booked-hotels",
                                "/tentative-conferences", "/planned-gatherings").hasRole("OWNER")
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
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // Authenticated-but-unauthorized users are redirected to the home page instead
                // of seeing a bare 403. Anonymous users still go to /login via the entry point.
                // Exception: /api/** callers (our fetch endpoints) get a real 403 — a 302 redirect
                // to an HTML page reads as a 200 success to fetch(), masking the actual failure
                // (this is what turned a CSRF rejection into an opaque client-side "Error").
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDenied) -> {
                            if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            } else {
                                response.sendRedirect(request.getContextPath() + "/");
                            }
                        }))
                .build();
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
