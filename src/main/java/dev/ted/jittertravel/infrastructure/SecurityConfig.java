package dev.ted.jittertravel.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    @Profile("!public")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .anonymous(anon -> anon.disable())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    @Profile("public")
    public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Profile("public")
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
    @Profile("public")
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
