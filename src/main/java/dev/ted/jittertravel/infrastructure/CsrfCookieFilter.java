package dev.ted.jittertravel.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Writes the CSRF cookie at the start of every request, before any view starts rendering.
 * <p>
 * <strong>Why this exists.</strong> The token lives in a cookie, and Spring loads it lazily: the
 * cookie is only written the first time something <em>reads</em> the token, which is when a page
 * renders its first form. A successful login deliberately clears the cookie (session-fixation
 * hygiene), so after every login there is no cookie until some page reads the token. A small page
 * manages that, because nothing has been sent yet when its form renders. A page that streams past
 * the response buffer before its first form does not: the response has started, the cookie cannot be
 * added, and the form carries a token whose cookie never existed. The POST is then rejected and the
 * viewer lands on {@code /login?expired}, logged in and baffled. It showed up on the {@code /admin}
 * page (2026-10-05) because that was the first large page with a form that Ted opened after
 * signing in; every other form worked because a smaller page had already minted the cookie.
 * <p>
 * Reading the token here makes the cookie exist before the view, whatever the view's size, so the
 * outcome no longer depends on which page was visited first or how big it is. This is the idiom the
 * Spring Security reference gives for exactly this ("render the token value to a cookie by causing
 * the deferred token to be loaded"). It costs nothing when the cookie is already present: the token
 * is then read from it, not regenerated.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // The value is discarded: asking for it is what loads, and so saves, the token.
            token.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
