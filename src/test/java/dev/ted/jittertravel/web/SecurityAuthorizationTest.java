package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Verifies login-related behavior and the rendered home-page navigation per role under the
 * secured chain. Pure route authorization (role × route → outcome) lives in
 * {@link AuthorizationMatrixTest}.
 */
@WebMvcTest(GeneralController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass"})
class SecurityAuthorizationTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @MockitoBean
    BuildProperties buildProperties;

    @BeforeEach
    void setUp() {
        lenient().when(buildProperties.getTime()).thenReturn(Instant.EPOCH);
    }

    @Test
    @WithAnonymousUser
    void homePageIsPublicAndRendersHomeNotTheLoginForm() {
        given(persister.countPendingCommands()).willReturn(0);

        // Regression: failureUrl("/") made DefaultLoginPageGeneratingFilter render the login
        // form at "/" (HTTP 200), so a status-only check passed while the home page was hidden.
        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("wordmark-text")
                .doesNotContain("Please sign in")
                .doesNotContain("name=\"password\"");
    }

    @Test
    @WithAnonymousUser
    void anonymousHomeShowsCalendarOnly() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("/calendar")
                .doesNotContain("/itinerary")
                .doesNotContain("/booked-flights")
                .doesNotContain("/book-flight")
                .doesNotContain(">Admin</span>");
    }

    @Test
    @WithMockUser(roles = "FAMILY")
    void familyHomeShowsItineraryAndCalendarOnly() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("/itinerary")
                .contains("/calendar")
                .doesNotContain("/booked-flights")
                .doesNotContain("/book-flight")
                .doesNotContain(">Admin</span>");
    }

    @Test
    @WithAnonymousUser
    void failedLoginRedirectsToLoginPageWithError() {
        assertThat(mockMvc.post().uri("/login")
                .param("username", "ted")
                .param("password", "wrong-password")
                .with(csrf()))
                .hasStatus3xxRedirection()
                .hasHeader("Location", "/login?error");
    }
}
