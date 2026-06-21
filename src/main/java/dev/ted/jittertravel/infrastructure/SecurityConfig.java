package dev.ted.jittertravel.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
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
                                "/clear-conflict", "/clear-conflict/**",
                                "/api/parse-address").hasRole("OWNER")
                        // Per-flight and per-train edit must be ordered before the list matchers below.
                        .requestMatchers("/booked-flights/*", "/booked-flights/*/lookup",
                                "/booked-trains/*", "/booked-hotels/*").hasRole("OWNER")
                        // Booking lists: OWNER-only (FAMILY cannot view booking details).
                        .requestMatchers(
                                "/booked-flights", "/booked-trains", "/booked-hotels",
                                "/tentative-conferences", "/planned-gatherings").hasRole("OWNER")
                        // Itinerary: FAMILY and OWNER may view; anonymous may not.
                        .requestMatchers("/itinerary", "/itinerary/**").hasAnyRole("FAMILY", "OWNER")
                        .anyRequest().permitAll())
                // Standard form login: a failed login goes to /login?error and the login page
                // shows the error. Do NOT set failureUrl("/") — that makes
                // DefaultLoginPageGeneratingFilter render the login form at "/" on every visit.
                .formLogin(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // Authenticated-but-unauthorized users are redirected to the home page instead
                // of seeing a bare 403. Anonymous users still go to /login via the entry point.
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, accessDenied) ->
                                response.sendRedirect(request.getContextPath() + "/")))
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
