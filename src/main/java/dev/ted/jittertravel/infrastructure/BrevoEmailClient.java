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
    static final String SENDER_EMAIL = "notifications@jittertravel.com";
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

    public void send(FamilyMessage message) {
        if (!configured()) {
            throw new IllegalStateException("Family notification is not configured: no API key or recipient");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("name", SENDER_NAME, "email", SENDER_EMAIL));
        if (!replyTo.isBlank()) {
            body.put("replyTo", Map.of("email", replyTo));
        }
        body.put("to", List.of(Map.of("email", recipient)));
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
