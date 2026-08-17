package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The custom login page must render (template errors only surface at render time) and carry the
 * hidden browserZone field plus the script that fills it — that field is the whole reason we
 * replaced Spring's generated login page.
 */
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass"})
@WithAnonymousUser
class LoginControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    @Test
    void loginPageRendersFormWithBrowserZoneCaptureField() {
        assertThat(mockMvc.get().uri("/login"))
                .hasStatusOk()
                .bodyText()
                .contains("name=\"username\"")
                .contains("name=\"password\"")
                .contains("name=\"browserZone\"")
                .contains("Intl.DateTimeFormat().resolvedOptions().timeZone");
    }

    @Test
    void loginPageShowsErrorMessageWhenErrorParamPresent() {
        assertThat(mockMvc.get().uri("/login?error"))
                .hasStatusOk()
                .bodyText()
                .contains("Incorrect username or password");
    }

    @Test
    void loginPageShowsSignedOutMessageWhenLogoutParamPresent() {
        assertThat(mockMvc.get().uri("/login?logout"))
                .hasStatusOk()
                .bodyText()
                .contains("signed out");
    }
}
