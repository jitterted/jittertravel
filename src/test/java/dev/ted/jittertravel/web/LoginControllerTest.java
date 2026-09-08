package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass",
                                  "REMEMBER_ME_KEY=test-remember-me-key"})
@WithAnonymousUser
class LoginControllerTest {

    @Autowired
    MockMvcTester mockMvc;

    // SecurityConfig's remember-me needs a token store, and a @WebMvcTest slice has no DataSource
    // for the JDBC one. The login page itself only has to render the checkbox.
    @MockitoBean
    PersistentTokenRepository persistentTokenRepository;

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
    void loginPageRendersRememberMeCheckboxTickedByDefault() {
        // The name is Spring's own parameter and the tick is Ted's decision (2026-09-08): staying
        // signed in is the point, so the box arrives checked and unticking it is how you say
        // "not on this device". Asserting the whole input element, not the bare word, so a
        // renamed parameter or a lost `checked` fails here rather than passing on the label text.
        assertThat(mockMvc.get().uri("/login"))
                .hasStatusOk()
                .bodyText()
                .contains("<input type=\"checkbox\" name=\"remember-me\" checked")
                .contains("Stay signed in on this device for 30 days");
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
