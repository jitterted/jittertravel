package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The mechanics of the email files; what each email says is {@code FamilyNotificationMessagesTest}. */
class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates();

    @Test
    void theFirstLineIsTheSubjectAndTheRestIsTheBody() {
        FamilyMessage message = templates.render("test-email", Map.of());

        assertThat(message.subject())
                .isEqualTo("JitterTravel test email");
        assertThat(message.textContent())
                .startsWith("This is a test from JitterTravel")
                .doesNotContain("Subject:");
    }

    /**
     * The snippet a template uses for an optional link: it shows when {@code calendarUrl} is given
     * and leaves no trace when it is not (no base URL configured), which must not be an error.
     */
    @Test
    void anOptionalLinkLineAppearsWhenTheValueIsGiven() {
        FamilyMessage message = templates.render("with-link",
                Map.of("calendarUrl", "https://jittertravel.com/calendar?day=2026-10-18"));

        assertThat(message.textContent())
                .contains("See it here: https://jittertravel.com/calendar?day=2026-10-18")
                .contains("Before the link.")
                .contains("After the link.");
    }

    @Test
    void anOptionalLinkLineLeavesNoTraceWhenTheValueIsAbsent() {
        FamilyMessage message = templates.render("with-link", Map.of());

        assertThat(message.textContent())
                .doesNotContain("See it here")
                .contains("Before the link.")
                .contains("After the link.");
    }

    @Test
    void neverMoreThanOneBlankLineInARowWhateverTheFileAndTheLoopBetweenThemAdd() {
        FamilyMessage message = templates.render("blank-lines", Map.of("items", List.of("one", "two")));

        assertThat(message.textContent().lines().toList())
                .containsExactly(
                        "First paragraph.",
                        "",
                        "Second paragraph, after three blank lines.",
                        "",
                        "- one",
                        "",
                        "- two");
    }

    @Test
    void aFileWithNoSubjectLineIsRefusedRatherThanSentWithAnEmptySubject() {
        assertThatThrownBy(() -> templates.render("no-subject", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("email/no-subject.txt must start with \"Subject: \"");
    }
}
