package dev.ted.jittertravel.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the one kind of email this app sends, through Brevo's transactional endpoint, with no SDK.
 * <p>
 * {@link #configured()} means an API key <em>and</em> a recipient: this is the one place that
 * holds both, so it answers for both, and the service asks it before opening a command so an
 * unconfigured notifier writes no {@code command_log} row at all. A blank reply-to is not part of
 * it — the mail still goes — but the {@code replyTo} object is then omitted entirely, because
 * {@code {"email":""}} is a malformed address to Brevo and would turn a missing optional into a
 * failed send.
 * <p>
 * {@link #send} throws on anything but a 2xx. The exception is what fails the command
 * ({@code FAILED_SEND}); there is no retry here and no result object to forget to check.
 * <p>
 * Plain text only: the body is a few lines, renders the same everywhere, and Apple Mail links a bare
 * URL, so there is no email-CSS discipline to take on.
 */
@Component
public class BrevoEmailClient {

    private static final String SEND_URL = "https://api.brevo.com/v3/smtp/email";
    public static final String SENDER_EMAIL = "notifications@jittertravel.com";
    static final String SENDER_NAME = "JitterTravel";

    private final RestClient restClient;
    private final String apiKey;
    private final String recipient;
    private final String replyTo;

    public BrevoEmailClient(RestClient.Builder restClientBuilder,
                            @Value("${jittertravel.family-notify.api-key:}") String apiKey,
                            @Value("${jittertravel.family-notify.recipient:}") String recipient,
                            @Value("${jittertravel.family-notify.reply-to:}") String replyTo) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey.trim();
        this.recipient = recipient.trim();
        this.replyTo = replyTo.trim();
    }

    public boolean configured() {
        return !apiKey.isBlank() && !recipient.isBlank();
    }

    /** Where {@link #send} will deliver, so a probe can say so before and after the click. */
    public String recipient() {
        return recipient;
    }

    /** The reply-to address, or {@code ""} when none is configured. */
    public String replyTo() {
        return replyTo;
    }

    /**
     * The key itself, for the settings page to say whether one is set and how it ends. It is handed
     * out only so that page can reduce it to those two facts; nothing may print or store it.
     */
    public String apiKey() {
        return apiKey;
    }

    /** Whether there is a key to send with, whoever the mail is addressed to. */
    public boolean hasKey() {
        return !apiKey.isBlank();
    }

    /** Sends to the configured family recipient. */
    public void send(FamilyMessage message) {
        if (!configured()) {
            throw new IllegalStateException("Family notification is not configured: no API key or recipient");
        }
        sendTo(recipient, message);
    }

    /**
     * Sends to an explicit address. Exists for the email preview, which sends a copy to Ted himself
     * and must never be able to reach the family address; a caller picks the address, so a preview
     * cannot be sent to family by accident of configuration.
     */
    public void sendTo(String address, FamilyMessage message) {
        if (!hasKey() || address == null || address.isBlank()) {
            throw new IllegalStateException("Cannot send: no API key or no address to send to");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("name", SENDER_NAME, "email", SENDER_EMAIL));
        if (!replyTo.isBlank()) {
            body.put("replyTo", Map.of("email", replyTo));
        }
        body.put("to", List.of(Map.of("email", address.strip())));
        body.put("subject", message.subject());
        body.put("textContent", message.textContent());

        restClient.post()
                .uri(SEND_URL)
                .header("api-key", apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
