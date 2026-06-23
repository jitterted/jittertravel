package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.AddressParseService;
import dev.ted.jittertravel.infrastructure.AddressParseService.ParsedAddress;
import dev.ted.jittertravel.infrastructure.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Exercises the real {@link SecurityConfig} chain (not the @WebMvcTest default) to verify that
 * an authenticated-but-forbidden request to an {@code /api/**} endpoint gets a real 403, rather
 * than the 302-redirect-to-home that other pages get. A redirect reads as a 200 to a fetch()
 * caller and masks the failure.
 */
@WebMvcTest(AddressParseController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"TED_PASSWORD=testpass", "FAMILY_PASSWORD=testpass"})
class ApiAccessDeniedSecurityTest {

    @Autowired
    MockMvcTester mockMvc;

    @MockitoBean
    AddressParseService parseService;

    @Test
    @WithMockUser(roles = "FAMILY")
    void forbiddenApiRequestReturns403NotRedirect() {
        assertThat(mockMvc.get().uri("/api/parse-address?q={q}", "Berlin"))
                .hasStatus(403);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void ownerApiRequestIsAllowed() {
        when(parseService.parse(anyString()))
                .thenReturn(Optional.of(new ParsedAddress("", "", "", "", "", "")));
        assertThat(mockMvc.get().uri("/api/parse-address?q={q}", "Berlin"))
                .hasStatusOk();
    }
}
