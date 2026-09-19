package dev.ted.jittertravel.web;

import java.util.List;

/**
 * What the running app believes about the connection it is answering, so the Secure flag on the
 * cookies we set can be checked rather than assumed.
 * <p>
 * It exists because the one thing local testing cannot answer is whether the *deployed* app marks
 * its cookies Secure. Railway terminates TLS and speaks plain HTTP to the container, so
 * {@code request.isSecure()} is only true if {@code server.forward-headers-strategy} is set and the
 * proxy really is sending {@code X-Forwarded-Proto} — two assumptions that are invisible until
 * something reads them back. Both the remember-me cookie (a credential) and the viewerZone cookie
 * derive their Secure flag from {@code isSecure()}, so this is the value that decides both.
 * <p>
 * Over the plain-http local prod-preview the honest answer is "not secure", and that is correct
 * rather than a fault — pinning the flag true would make the browser drop the cookie locally.
 * <p>
 * <strong>Every value it renders carries a verdict.</strong> A diagnostic that prints raw values
 * and leaves the reader to decide whether they are good is not a diagnostic — the reader has to
 * already know the answer to use it, which is exactly what they came here without. So
 * {@link #values()} pairs each value with what it means <em>in the state the probe is actually in</em>,
 * and there is no way to add a value without deciding on its verdict.
 *
 * @param secure         what {@code HttpServletRequest.isSecure()} reports, after the
 *                       forward-headers strategy has been applied
 * @param scheme         the scheme the app resolved for this request
 * @param forwardedProto the raw {@code X-Forwarded-Proto} header, or {@code ""} when the controller
 *                       could not read one. <strong>Blank is the expected production value, and it
 *                       does not mean the proxy stopped sending it.</strong> With
 *                       {@code server.forward-headers-strategy=framework} Spring wraps the request
 *                       in a {@code ForwardedHeaderExtractingRequest}, which extends
 *                       {@code ForwardedHeaderRemovingRequest} and hides all seven forwarded
 *                       headers from {@code getHeader} once it has applied them. A controller runs
 *                       downstream of that filter, so while the strategy is working this is
 *                       <em>always</em> blank. Blank therefore has three causes — the filter
 *                       consumed it (production), no proxy sent one (local), or one arrived saying
 *                       {@code http} and was applied as such — and {@link #secure()} is what
 *                       separates them.
 */
public record SecureCookieProbe(boolean secure, String scheme, String forwardedProto) {

    public SecureCookieProbe {
        scheme = scheme == null ? "" : scheme.trim();
        forwardedProto = forwardedProto == null ? "" : forwardedProto.trim();
    }

    /**
     * One value the probe read, next to what that value means here. {@code verdict} is never
     * blank: a value shown without one puts the reader back where they started.
     */
    public record ProbeValue(String label, String value, String verdict) {
    }

    /**
     * The five states the readings can be in. Deriving this once keeps every sentence below
     * agreeing with every other one, and the switches over it are exhaustive so a sixth state
     * cannot be added without saying what each line reads in it.
     */
    private enum Outcome {
        /** The expected production shape: the filter applied the header and then consumed it. */
        SECURE_BEHIND_PROXY,
        /** Secure, but a surviving header proves the filter is not the reason. */
        SECURE_HEADER_SURVIVED,
        /** The local prod-preview shape, and a fault if it is what production reports. */
        PLAIN_NO_HEADER,
        /** The proxy is doing its part and the strategy is not: the header says https and is ignored. */
        HTTPS_HEADER_IGNORED,
        /**
         * A surviving header that does <em>not</em> say https. The strategy did not run, but "not
         * secure" agrees with what the proxy reported, so nothing here may claim it should be true.
         */
        PLAIN_HEADER_SURVIVED
    }

    private Outcome outcome() {
        if (secure) {
            return forwardedProto.isBlank() ? Outcome.SECURE_BEHIND_PROXY
                                            : Outcome.SECURE_HEADER_SURVIVED;
        }
        if (forwardedProto.isBlank()) {
            return Outcome.PLAIN_NO_HEADER;
        }
        return forwardedProto.equalsIgnoreCase("https") ? Outcome.HTTPS_HEADER_IGNORED
                                                        : Outcome.PLAIN_HEADER_SURVIVED;
    }

    /**
     * The answer the page leads with: whether a cookie set on this request would carry Secure, and
     * whether that is the answer we wanted.
     */
    public String summary() {
        return switch (outcome()) {
            case SECURE_BEHIND_PROXY ->
                    "Cookies on this request are marked Secure, as desired.";
            case SECURE_HEADER_SURVIVED ->
                    "Cookies on this request are marked Secure, but not by the route we expect.";
            case PLAIN_NO_HEADER, HTTPS_HEADER_IGNORED, PLAIN_HEADER_SURVIVED ->
                    "Cookies on this request are NOT marked Secure.";
        };
    }

    /**
     * Says which of the two assumptions is the one to look at, rather than making the reader work
     * it out from the raw values below.
     */
    public String explanation() {
        return switch (outcome()) {
            case SECURE_BEHIND_PROXY ->
                    "The remember-me and viewerZone cookies are protected in transit, as expected.";
            case SECURE_HEADER_SURVIVED ->
                    "X-Forwarded-Proto reached the app instead of being consumed, so the "
                    + "forward-headers strategy is not what made this secure. Find out what did "
                    + "before relying on it.";
            case PLAIN_NO_HEADER ->
                    "No usable X-Forwarded-Proto reached the app — expected over plain http "
                    + "locally, but in production it means none arrived, or one arrived saying "
                    + "http.";
            case HTTPS_HEADER_IGNORED ->
                    "X-Forwarded-Proto says " + forwardedProto
                    + ", so server.forward-headers-strategy is not being applied.";
            case PLAIN_HEADER_SURVIVED ->
                    "X-Forwarded-Proto says " + forwardedProto
                    + ", so not secure agrees with the proxy — this request reached it without "
                    + "https. The header also reached the app instead of being consumed, so "
                    + "server.forward-headers-strategy is not being applied either.";
        };
    }

    /**
     * The raw readings, each with its verdict. The template loops over this rather than naming the
     * three values itself, so a fourth reading cannot be displayed without one.
     */
    public List<ProbeValue> values() {
        return List.of(
                new ProbeValue("request.isSecure()", String.valueOf(secure), secureVerdict()),
                new ProbeValue("scheme", schemeOrNone(), schemeVerdict()),
                new ProbeValue("X-Forwarded-Proto", forwardedProtoOrNone(), forwardedProtoVerdict()));
    }

    private String secureVerdict() {
        return switch (outcome()) {
            case SECURE_BEHIND_PROXY -> "working as desired — this is the value that marks both cookies";
            case SECURE_HEADER_SURVIVED -> "as desired, but reached by an unexpected route";
            case PLAIN_NO_HEADER -> "expected over plain http locally; wrong in production";
            case HTTPS_HEADER_IGNORED -> "wrong — the proxy said https, so this should be true";
            case PLAIN_HEADER_SURVIVED -> "consistent with the proxy, which said " + forwardedProto;
        };
    }

    private String schemeVerdict() {
        return switch (outcome()) {
            case SECURE_BEHIND_PROXY, SECURE_HEADER_SURVIVED -> "as expected";
            case PLAIN_NO_HEADER -> "expected over plain http locally; wrong in production";
            case HTTPS_HEADER_IGNORED -> "wrong — should be https";
            case PLAIN_HEADER_SURVIVED -> "consistent with the proxy, which said " + forwardedProto;
        };
    }

    /**
     * The line most likely to be misread, which is why it says "correct" rather than leaving
     * "(none)" to look like a gap: while the strategy is working the header is always gone by the
     * time a controller reads it, so its absence here is the evidence that it was applied.
     */
    private String forwardedProtoVerdict() {
        return switch (outcome()) {
            case SECURE_BEHIND_PROXY ->
                    "this is correct — the header was consumed after being applied, which is what "
                    + "the strategy does";
            case PLAIN_NO_HEADER ->
                    "expected locally; in production it means none arrived, or one said http";
            case HTTPS_HEADER_IGNORED ->
                    "arrived but was ignored — the strategy did not run";
            case SECURE_HEADER_SURVIVED, PLAIN_HEADER_SURVIVED ->
                    "unexpected — a header still here was never consumed, so the strategy did not run";
        };
    }

    private String forwardedProtoOrNone() {
        return forwardedProto.isBlank() ? "(none)" : forwardedProto;
    }

    private String schemeOrNone() {
        return scheme.isBlank() ? "(none)" : scheme;
    }
}
