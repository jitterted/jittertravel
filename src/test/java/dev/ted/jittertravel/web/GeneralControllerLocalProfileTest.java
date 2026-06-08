package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// Imports the real SecurityConfig under the `local` profile, so the permissive dev chain
// (permit-all, no auth) is active — anonymous requests reach the controller, as they do locally.
@WebMvcTest(GeneralController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("local")
class GeneralControllerLocalProfileTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @Test
    void localProfileShowsRunningLocallyBadgeAndDataEntryNavWithoutLogin() {
        given(persister.countPendingCommands()).willReturn(0);

        assertThat(mockMvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("JitterTravel Running Locally")
                // local profile = full access, so the data-entry/admin nav shows without login
                .contains("/book-flight")
                .contains(">Admin</span>");
    }
}
