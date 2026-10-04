package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter's whole job is to <em>read</em> the CSRF token, because reading is what writes the
 * cookie. That is what the first test pins; {@code FamilyNotifyProbeCsrfTest} proves the filter is
 * wired into the real chain.
 * <p>
 * Deliberately a plain unit test and not a MockMvc one. Spring Security's {@code .with(csrf())}
 * swaps the shared {@code CsrfFilter}'s token repository for a test one that writes no cookie and
 * never swaps it back, so any cookie assertion in a class whose cached context is shared with a
 * {@code csrf()} user passes or fails by test order.
 */
class CsrfCookieFilterTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();
    private final CsrfCookieFilter filter = new CsrfCookieFilter();

    /** Records that its value was asked for: the call that loads, and so saves, the real token. */
    private static final class RecordingToken implements CsrfToken {
        int reads;

        @Override
        public String getToken() {
            reads++;
            return "token";
        }

        @Override
        public String getHeaderName() {
            return "X-CSRF-TOKEN";
        }

        @Override
        public String getParameterName() {
            return "_csrf";
        }
    }

    @Test
    void readsTheTokenSoTheCookieIsWrittenBeforeTheViewStarts() throws Exception {
        RecordingToken token = new RecordingToken();
        request.setAttribute(CsrfToken.class.getName(), token);

        filter.doFilter(request, response, chain);

        assertThat(token.reads)
                .as("the token's value is asked for, which is what makes Spring write the cookie")
                .isEqualTo(1);
    }

    @Test
    void thenLetsTheRequestContinue() throws Exception {
        request.setAttribute(CsrfToken.class.getName(), new RecordingToken());

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest())
                .as("the chain ran, so the controller and the view still render")
                .isSameAs(request);
    }

    @Test
    void aRequestWithNoTokenAttributeIsPassedThroughUntouched() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest())
                .isSameAs(request);
    }
}
