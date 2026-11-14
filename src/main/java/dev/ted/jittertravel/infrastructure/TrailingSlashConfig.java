package dev.ted.jittertravel.infrastructure;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * A path typed with a trailing slash ({@code /admin/}) redirects to the path without one
 * ({@code /admin}), query string kept. Spring stopped matching a trailing slash in 6.0, so
 * without this every such URL is a 404 — and the slash is easy to type by habit (2026-11-14).
 *
 * <p><strong>It runs before the security chain, and that is deliberate.</strong> The redirect
 * fires for any path, mapped or not, and for every viewer, so it reveals nothing about which
 * routes exist. And the security chain then only ever sees the canonical path, so a trailing-slash
 * spelling can never slip past a matcher written without one ({@code /booked-trains/*} does not
 * match {@code /booked-trains/abc/cancel/}).
 *
 * <p>308 rather than 301 so a POST stays a POST with its body; the root path {@code /} is left
 * alone by the filter itself.
 */
@Configuration
public class TrailingSlashConfig {

    @Bean
    FilterRegistrationBean<UrlHandlerFilter> trailingSlashRedirect() {
        UrlHandlerFilter filter = UrlHandlerFilter.trailingSlashHandler("/**")
                                                  .redirect(HttpStatus.PERMANENT_REDIRECT)
                                                  .build();
        FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
