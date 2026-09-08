package dev.ted.jittertravel.web;

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
 *
 * @param secure         what {@code HttpServletRequest.isSecure()} reports, after the
 *                       forward-headers strategy has been applied
 * @param scheme         the scheme the app resolved for this request
 * @param forwardedProto the raw {@code X-Forwarded-Proto} header, or {@code ""} when the request
 *                       arrived without one (as it does locally, and as it would if Railway's proxy
 *                       stopped sending it)
 */
public record SecureCookieProbe(boolean secure, String scheme, String forwardedProto) {

    public SecureCookieProbe {
        scheme = scheme == null ? "" : scheme.trim();
        forwardedProto = forwardedProto == null ? "" : forwardedProto.trim();
    }

    /**
     * The answer the page leads with: whether a cookie set on this request would carry Secure.
     */
    public String summary() {
        return secure
                ? "Cookies on this request are marked Secure."
                : "Cookies on this request are NOT marked Secure.";
    }

    /**
     * Says which of the two assumptions is the one to look at, rather than making the reader work
     * it out from the raw values below.
     */
    public String explanation() {
        if (secure) {
            return "The remember-me and viewerZone cookies are protected in transit.";
        }
        if (forwardedProto.isBlank()) {
            return "No X-Forwarded-Proto header arrived — expected over plain http locally, "
                   + "but in production it means the proxy is not sending one.";
        }
        return "X-Forwarded-Proto says " + forwardedProto
               + ", so server.forward-headers-strategy is not being applied.";
    }

    public String forwardedProtoOrNone() {
        return forwardedProto.isBlank() ? "(none)" : forwardedProto;
    }
}
