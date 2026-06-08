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
     * Data-entry and admin pages require authentication; read-only projection views (calendar,
     * itinerary, booking lists, etc.), the health endpoint, and the login page stay public.
     * An anonymous request to a protected page is redirected to the login form; a failed login
     * returns the user to the home page.
     */
    @Bean
    @Profile("!local")
    public SecurityFilterChain securedFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Admin (includes /admin/eventlog, /admin/commandlog, /admin/pending-commands)
                        .requestMatchers("/admin", "/admin/**").authenticated()
                        // Booking / planning data-entry forms and their submit/lookup endpoints
                        .requestMatchers(
                                "/book-flight", "/book-flight/**",
                                "/book-hotel", "/book-hotel/**",
                                "/book-train", "/book-train/**",
                                "/plan-conference", "/plan-conference/**",
                                "/plan-gathering", "/plan-gathering/**",
                                "/clear-conflict", "/clear-conflict/**",
                                "/api/parse-address").authenticated()
                        // Change-flight edit lives under the (public) booked-flights list:
                        // protect the per-flight edit/lookup, not the list itself.
                        .requestMatchers("/booked-flights/*", "/booked-flights/*/lookup").authenticated()
                        .anyRequest().permitAll())
                // Standard form login: a failed login goes to /login?error and the login page
                // shows the error. Do NOT set failureUrl("/") — that makes
                // DefaultLoginPageGeneratingFilter render the login form at "/" on every visit.
                .formLogin(Customizer.withDefaults())
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
                    .roles("USER")
                    .build(),
                User.withUsername("family")
                    .password(encoder.encode(familyPassword))
                    .roles("USER")
                    .build()
        );
    }

    @Bean
    @Profile("!local")
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
