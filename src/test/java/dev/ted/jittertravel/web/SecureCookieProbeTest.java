package dev.ted.jittertravel.web;

import dev.ted.jittertravel.web.SecureCookieProbe.ProbeValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureCookieProbeTest {

    @Test
    void secureBehindTheProxySaysSoIsAsDesired() {
        // The production shape: the filter applied X-Forwarded-Proto and then consumed it, so the
        // header is gone by the time a controller reads it. All three readings are correct here,
        // and the page has to say so — "true / https / (none)" with no verdict reads as two
        // successes and a gap.
        SecureCookieProbe probe = new SecureCookieProbe(true, "https", "");

        assertThat(probe.summary())
                .isEqualTo("Cookies on this request are marked Secure, as desired.");
        assertThat(probe.explanation())
                .isEqualTo("The remember-me and viewerZone cookies are protected in transit, as expected.");
    }

    @Test
    void theConsumedHeaderIsCalledCorrectRatherThanLeftLookingLikeAGap() {
        SecureCookieProbe probe = new SecureCookieProbe(true, "https", "");

        assertThat(probe.values())
                .containsExactly(
                        new ProbeValue("request.isSecure()", "true",
                                       "working as desired — this is the value that marks both cookies"),
                        new ProbeValue("scheme", "https", "as expected"),
                        new ProbeValue("X-Forwarded-Proto", "(none)",
                                       "this is correct — the header was consumed after being "
                                       + "applied, which is what the strategy does"));
    }

    @Test
    void everyReadingCarriesAVerdictInEveryState() {
        // The point of the record: a value shown without a verdict puts the reader back where they
        // started. Driven over all four states so a new one cannot ship a blank verdict.
        for (SecureCookieProbe probe : new SecureCookieProbe[]{
                new SecureCookieProbe(true, "https", ""),
                new SecureCookieProbe(true, "https", "https"),
                new SecureCookieProbe(false, "http", ""),
                new SecureCookieProbe(false, "http", "https")}) {
            assertThat(probe.values())
                    .as("readings for %s", probe)
                    .isNotEmpty()
                    .allSatisfy(reading -> assertThat(reading.verdict())
                            .as("verdict for %s", reading.label())
                            .isNotBlank());
        }
    }

    @Test
    void aHeaderThatSurvivedMeansTheStrategyIsNotWhatMadeItSecure() {
        // Secure, but the header is still readable — so the filter never consumed it and cannot be
        // the reason. Worth flagging rather than showing green: the next deploy has nothing
        // holding the flag up.
        SecureCookieProbe probe = new SecureCookieProbe(true, "https", "https");

        assertThat(probe.summary())
                .isEqualTo("Cookies on this request are marked Secure, but not by the route we expect.");
        assertThat(probe.explanation())
                .contains("reached the app instead of being consumed")
                .contains("Find out what did before relying on it");
    }

    @Test
    void plainRequestWithNoUsableHeaderDoesNotClaimTheProxyIsSilent() {
        // The local prod-preview shape, and also what production looks like if the header is
        // missing OR arrives saying http — the filter applies *and* strips that one too, so blank
        // does not prove the proxy sent nothing. The sentence says both rather than picking one.
        SecureCookieProbe probe = new SecureCookieProbe(false, "http", "");

        assertThat(probe.summary())
                .isEqualTo("Cookies on this request are NOT marked Secure.");
        assertThat(probe.explanation())
                .contains("No usable X-Forwarded-Proto reached the app")
                .contains("expected over plain http locally")
                .contains("none arrived, or one arrived saying http");
        assertThat(probe.explanation())
                .as("the old wording asserted a cause the reading cannot support")
                .doesNotContain("the proxy is not sending one");
    }

    @Test
    void forwardedHttpsThatDidNotTakeEffectBlamesTheStrategyNotTheProxy() {
        // The header is there and says https, but isSecure() is still false. A *surviving* header
        // is the tell: while the strategy runs it is always consumed, so reading one back at all
        // proves the filter did not run — the proxy is doing its part. This is the case the probe
        // exists for; the two causes are indistinguishable from the summary line alone.
        SecureCookieProbe probe = new SecureCookieProbe(false, "http", "https");

        assertThat(probe.explanation())
                .isEqualTo("X-Forwarded-Proto says https, "
                           + "so server.forward-headers-strategy is not being applied.");
        assertThat(probe.values())
                .extracting(ProbeValue::verdict)
                .containsExactly("wrong — the proxy said https, so this should be true",
                                 "wrong — should be https",
                                 "arrived but was ignored — the strategy did not run");
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
    void anAbsentSchemeReadsAsNoneToo() {
        SecureCookieProbe probe = new SecureCookieProbe(false, null, "");

        assertThat(probe.values())
                .extracting(ProbeValue::value)
                .containsExactly("false", "(none)", "(none)");
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
