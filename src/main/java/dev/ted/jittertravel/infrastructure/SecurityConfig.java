package dev.ted.jittertravel.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security is <strong>secured by default</strong>: the production form-login chain is active
 * unless the {@code local} profile is explicitly enabled. This makes the safe configuration
 * the one you get when you forget to set a profile (e.g. on deploy), and the permissive,
 * no-auth dev chain something you must opt into with {@code local}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Profile("local")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .anonymous(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Three access tiers: OWNER (ted) has full access; FAMILY can view the itinerary and the
     * full calendar only; anonymous can only see the redacted calendar and home page.
     * An anonymous request to a protected page is redirected to the login form; an authenticated
     * user who lacks the required role is redirected back to the home page (a friendlier
     * alternative to a bare 403).
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
                        // Per-flight edit must be ordered before the list matcher below.
                        .requestMatchers("/booked-flights/*", "/booked-flights/*/lookup").hasRole("OWNER")
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
    @Profile("!local")
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
    @Profile("!local")
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
