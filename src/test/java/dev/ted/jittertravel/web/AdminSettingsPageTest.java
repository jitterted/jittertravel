package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BackupService;
import dev.ted.jittertravel.application.BackupSource;
import dev.ted.jittertravel.application.LegacyEventMigration;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * The settings page and its card on {@code /admin}: option A of
 * https://claude.ai/artifact/7QRKoUEwoq8v9JWgzUzqrF with every environment setting. What each row says
 * in each state is {@code SettingsReporterTest}'s claim; here it is that the controller feeds the
 * reporter the real configuration, the page draws it, the card's colour follows the problem count,
 * and no secret reaches the markup in full.
 */
@Tag("spring")
@WebMvcTest(AdminController.class)
@WithMockUser(roles = "OWNER")
@TestPropertySource(properties = {
        "TED_PASSWORD=owner-password-0000",
        "FAMILY_PASSWORD=family-password-1111",
        "REMEMBER_ME_KEY=remember-me-key-value-2222",
        "jittertravel.calendar-feed.token=calendar-token-abcdef1234",
        "jittertravel.base-url=https://jittertravel.example",
        "jittertravel.aerodatabox.api-key=aerodatabox-key-0000-81c0",
        "jittertravel.home-cities=Hamburg,Berlin",
        "jittertravel.family-notify.enabled=true"})
class AdminSettingsPageTest {

    private static final String BREVO_KEY = "xkeysib-0123456789abcdef-f3a9";

    @TestConfiguration
    static class TestBeans {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-05T22:42:00Z"), ZoneOffset.UTC);
        }

        @Bean
        BackupSource backupSource() {
            return new BackupSource("production");
        }
    }

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    BackupService backupService;
    @MockitoBean
    PostgresPersister persister;
    @MockitoBean
    LegacyEventMigration legacyEventMigration;
    @MockitoBean
    BrevoEmailClient brevo;
    @MockitoBean
    FamilyTestMemory memory;

    private void healthyFamilyEmail() {
        given(brevo.configured()).willReturn(true);
        given(brevo.recipient()).willReturn("family@example.com");
        given(brevo.replyTo()).willReturn("ted@example.com");
        given(brevo.apiKey()).willReturn(BREVO_KEY);
    }

    @Test
    void thePageShowsWhatTheRunningAppIsUsingGroupedWithEachStateInWords() {
        healthyFamilyEmail();

        assertThat(mockMvc.get().uri("/admin/settings"))
                .hasStatusOk()
                .bodyText()
                .contains("<h1>Settings</h1>")
                .contains("<span class=\"group-title\">Family email</span>")
                .contains("<span class=\"mono\">family@example.com</span>")
                .contains("<span class=\"mono\">ted@example.com</span>")
                .contains("<span class=\"hint\">ends …f3a9</span>")
                .contains("<span class=\"what\">Booking a flight or trip emails family.</span>")
                .contains("<span class=\"mono\">https://jittertravel.example</span>")
                .contains("<span class=\"mono\">production</span>")
                .contains("<span class=\"mono\">Hamburg,Berlin</span>");
    }

    @Test
    void noSecretReachesThePageInFull() {
        healthyFamilyEmail();

        assertThat(mockMvc.get().uri("/admin/settings"))
                .hasStatusOk()
                .bodyText()
                .doesNotContain(BREVO_KEY)
                .doesNotContain("owner-password-0000")
                .doesNotContain("family-password-1111")
                .doesNotContain("remember-me-key-value-2222")
                .doesNotContain("calendar-token-abcdef1234")
                .doesNotContain("aerodatabox-key-0000-81c0")
                .contains("ends …1234")
                .contains("ends …81c0");
    }

    @Test
    void theLeftColumnIsLeftAlignedAndTheStateOfAProblemIsAmber() {
        given(brevo.configured()).willReturn(false); // switched on (see properties) but cannot send

        assertThat(mockMvc.get().uri("/admin/settings"))
                .hasStatusOk()
                .bodyText()
                .contains("<span class=\"pill warn\">On, cannot send</span>")
                .contains("<span class=\"pill warn\">Needs attention</span>")
                .contains("text-align: left;");
    }

    @Test
    void theAdminCardIsAmberWithTheProblemCountWhenSomethingIsWrong() {
        given(brevo.configured()).willReturn(false); // switch on, no key: three rows are wrong

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .containsPattern("<a class=\"nav-card\" href=\"/admin/settings\"\\s+style=\"background: #fef3c7; "
                                 + "border-color: #d97706;\">")
                .containsPattern("<span class=\"nav-card-sub\"[^>]*>\\d+ problems?</span>");
    }

    @Test
    void theAdminCardIsGreenWhenNothingIsWrong() {
        healthyFamilyEmail();
        given(memory.last()).willReturn(Optional.of(new FamilyTestMemory.Test(
                Instant.parse("2026-10-05T22:42:00Z"), true, "")));

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .containsPattern("<a class=\"nav-card\" href=\"/admin/settings\"\\s+style=\"background: #dcfce7; "
                                 + "border-color: #16a34a;\">")
                .containsPattern("<span class=\"nav-card-sub\"[^>]*>What the running app is using</span>");
    }

    @Test
    void theNavCardsAreALeftAlignedThreeColumnGrid() {
        healthyFamilyEmail();

        assertThat(mockMvc.get().uri("/admin"))
                .hasStatusOk()
                .bodyText()
                .contains("grid-template-columns: repeat(3, minmax(0, 1fr));")
                .doesNotContain("justify-content: center;\n            flex-wrap: wrap;")
                .contains("text-align: left;");
    }
}
