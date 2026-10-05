package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrevoEmailClientTest {

    private static final FamilyMessage MESSAGE = new FamilyMessage("Ted booked a flight: SFO → LHR", "UA 195\nSFO → LHR");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private BrevoEmailClient client(String apiKey, String recipient, String replyTo) {
        return new BrevoEmailClient(builder, apiKey, recipient, replyTo);
    }

    @Test
    void postsTheExactBodyWithTheApiKeyHeader() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "secret-key"))
                .andExpect(content().json("""
                        {
                          "sender": {"name": "JitterTravel", "email": "notifications@jittertravel.com"},
                          "replyTo": {"email": "ted@example.com"},
                          "to": [{"email": "family@example.com"}],
                          "subject": "Ted booked a flight: SFO → LHR",
                          "textContent": "UA 195\\nSFO → LHR"
                        }
                        """, true))
                .andRespond(withSuccess());

        client("secret-key", "family@example.com", "ted@example.com").send(MESSAGE);

        server.verify();
    }

    @Test
    void aBlankReplyToOmitsTheObjectEntirelyRatherThanSendingAnEmptyAddress() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(content().json("""
                        {
                          "sender": {"name": "JitterTravel", "email": "notifications@jittertravel.com"},
                          "to": [{"email": "family@example.com"}],
                          "subject": "Ted booked a flight: SFO → LHR",
                          "textContent": "UA 195\\nSFO → LHR"
                        }
                        """, true))
                .andRespond(withSuccess());

        client("secret-key", "family@example.com", "").send(MESSAGE);

        server.verify();
    }

    @Test
    void aNon2xxThrowsSoTheCommandCanBeMarkedFailed() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client("secret-key", "family@example.com", "").send(MESSAGE))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void anUnconfiguredClientSendsNothingAndSaysSo() {
        assertThat(client("", "family@example.com", "").configured())
                .as("no key")
                .isFalse();
        assertThat(client("secret-key", "", "").configured())
                .as("no recipient")
                .isFalse();
        assertThat(client("  ", "family@example.com", "").configured())
                .as("a blank key is no key")
                .isFalse();
        assertThat(client("secret-key", "family@example.com", "").configured())
                .as("a reply-to is optional")
                .isTrue();

        assertThatThrownBy(() -> client("", "", "").send(MESSAGE))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void sendToAddressesTheMailToTheGivenAddressAndNeverTheConfiguredRecipient() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(content().json("""
                        {
                          "sender": {"name": "JitterTravel", "email": "notifications@jittertravel.com"},
                          "replyTo": {"email": "ted@example.com"},
                          "to": [{"email": "ted@example.com"}],
                          "subject": "Ted booked a flight: SFO → LHR",
                          "textContent": "UA 195\\nSFO → LHR"
                        }
                        """, true))
                .andRespond(withSuccess());

        client("secret-key", "family@example.com", "ted@example.com").sendTo("ted@example.com", MESSAGE);

        server.verify();
    }

    @Test
    void sendToNeedsAKeyAndAnAddressButNotAConfiguredRecipient() {
        assertThat(client("secret-key", "", "").hasKey()).isTrue();
        assertThat(client("", "family@example.com", "").hasKey()).isFalse();

        assertThatThrownBy(() -> client("", "family@example.com", "").sendTo("ted@example.com", MESSAGE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client("secret-key", "family@example.com", "").sendTo("  ", MESSAGE))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void reportsTheRecipientItWillSendTo() {
        assertThat(client("secret-key", " family@example.com ", "").recipient())
                .isEqualTo("family@example.com");
    }
}
