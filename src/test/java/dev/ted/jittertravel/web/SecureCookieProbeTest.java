package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureCookieProbeTest {

    @Test
    void secureRequestReportsCookiesAreProtected() {
        SecureCookieProbe probe = new SecureCookieProbe(true, "https", "https");

        assertThat(probe.summary())
                .isEqualTo("Cookies on this request are marked Secure.");
        assertThat(probe.explanation())
                .isEqualTo("The remember-me and viewerZone cookies are protected in transit.");
    }

    @Test
    void plainRequestWithNoForwardedHeaderNamesTheMissingHeader() {
        // The local prod-preview shape, and also what production would look like if Railway's
        // proxy stopped sending the header. The reading differs by environment, so the sentence
        // says both rather than picking one.
        SecureCookieProbe probe = new SecureCookieProbe(false, "http", "");

        assertThat(probe.summary())
                .isEqualTo("Cookies on this request are NOT marked Secure.");
        assertThat(probe.explanation())
                .contains("No X-Forwarded-Proto header arrived")
                .contains("expected over plain http locally")
                .contains("in production it means the proxy is not sending one");
    }

    @Test
    void forwardedHttpsThatDidNotTakeEffectBlamesTheStrategyNotTheProxy() {
        // The header is there and says https, but isSecure() is still false — so the proxy is
        // doing its part and server.forward-headers-strategy is what is not being applied. This
        // is the case the probe exists for: the two causes are indistinguishable from the
        // summary line alone.
        SecureCookieProbe probe = new SecureCookieProbe(false, "http", "https");

        assertThat(probe.explanation())
                .isEqualTo("X-Forwarded-Proto says https, "
                           + "so server.forward-headers-strategy is not being applied.");
    }

    @Test
    void anAbsentHeaderReadsAsNoneRatherThanBlank() {
        // getHeader returns null when the header is absent; a blank cell would read as a value
        // that happens to be empty rather than a header that never arrived.
        SecureCookieProbe probe = new SecureCookieProbe(false, "http", null);

        assertThat(probe.forwardedProtoOrNone())
                .isEqualTo("(none)");
        assertThat(probe.forwardedProto())
                .as("null normalizes to the empty-string sentinel, never null")
                .isEmpty();
    }

    @Test
    void surroundingWhitespaceIsNormalizedAway() {
        SecureCookieProbe probe = new SecureCookieProbe(false, " http ", "  https  ");

        assertThat(probe.scheme())
                .isEqualTo("http");
        assertThat(probe.forwardedProtoOrNone())
                .isEqualTo("https");
    }
}
