package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.lenient;

/**
 * Canonical, change-friendly statement of the authorization policy as a (role × route → outcome)
 * matrix, verified against the real secured chain ({@link SecurityConfig}).
 * <p>
 * To change the policy, edit the rows in {@link #policy()} and update {@code SecurityConfig}
 * accordingly; a mismatch will fail here pointing at the exact route/role.
 * <p>
 * This is a security-decision-only test: for {@code OK} rows the response may be 200 or 404
 * depending on which controllers are loaded — only the absence of a security redirect matters.
 * Controller-specific behavior is covered by each controller's own test.
 */
@WebMvcTest(GeneralController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass"})
class AuthorizationMatrixTest {

    private enum Outcome { OK, LOGIN, DENIED_HOME }

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    PostgresPersister persister;

    @MockitoBean
    BuildProperties buildProperties;

    @BeforeEach
    void setUp() {
        lenient().when(buildProperties.getTime()).thenReturn(Instant.EPOCH);
        lenient().when(persister.countPendingCommands()).thenReturn(0);
    }

    static Stream<Arguments> policy() {
        return Stream.of(
                // route,                          OWNER,            FAMILY,               ANONYMOUS
                arguments("/",                     Outcome.OK,       Outcome.OK,           Outcome.OK),
                arguments("/calendar",             Outcome.OK,       Outcome.OK,           Outcome.OK),
                arguments("/itinerary",            Outcome.OK,       Outcome.OK,           Outcome.LOGIN),
                arguments("/book-flight",          Outcome.OK,       Outcome.DENIED_HOME,  Outcome.LOGIN),
                arguments("/booked-flights",       Outcome.OK,       Outcome.DENIED_HOME,  Outcome.LOGIN),
                arguments("/booked-flights/abc",   Outcome.OK,       Outcome.DENIED_HOME,  Outcome.LOGIN),
                arguments("/booked-trains/abc",    Outcome.OK,       Outcome.DENIED_HOME,  Outcome.LOGIN),
                arguments("/admin",                Outcome.OK,       Outcome.DENIED_HOME,  Outcome.LOGIN),
                arguments("/actuator/health",      Outcome.OK,       Outcome.OK,           Outcome.OK),
                arguments("/actuator/metrics",     Outcome.OK,       Outcome.DENIED_HOME,  Outcome.LOGIN)
        );
    }

    @ParameterizedTest(name = "{0}: OWNER={1}, FAMILY={2}, ANON={3}")
    @MethodSource("policy")
    void enforcesPolicy(String route, Outcome owner, Outcome family, Outcome anonymous) {
        assertOutcome(route, "OWNER", owner);
        assertOutcome(route, "FAMILY", family);
        assertAnonymousOutcome(route, anonymous);
    }

    private void assertOutcome(String route, String role, Outcome expected) {
        MvcTestResult result = mockMvc.get().uri(route)
                .with(SecurityMockMvcRequestPostProcessors.user("user").roles(role))
                .exchange();
        verify(route, role, result, expected);
    }

    private void assertAnonymousOutcome(String route, Outcome expected) {
        MvcTestResult result = mockMvc.get().uri(route)
                .with(SecurityMockMvcRequestPostProcessors.anonymous())
                .exchange();
        verify(route, "ANONYMOUS", result, expected);
    }

    private static void verify(String route, String who, MvcTestResult result, Outcome expected) {
        switch (expected) {
            case OK -> {
                // Security permitted the request; status may be 200, 404, etc.
                int status = result.getResponse().getStatus();
                if (status == 302) {
                    String location = result.getResponse().getHeader("Location");
                    assertThat(location)
                            .as("%s on %s was redirected to '%s' by security but should be permitted", who, route, location)
                            .isNotEqualTo("/login")
                            .isNotEqualTo("/");
                }
            }
            case LOGIN -> assertThat(result)
                    .as("%s on %s should redirect to login (anonymous)", who, route)
                    .hasStatus3xxRedirection()
                    .hasHeader("Location", "/login");
            case DENIED_HOME -> assertThat(result)
                    .as("%s on %s should redirect home (access denied)", who, route)
                    .hasStatus3xxRedirection()
                    .hasHeader("Location", "/");
        }
    }
}
