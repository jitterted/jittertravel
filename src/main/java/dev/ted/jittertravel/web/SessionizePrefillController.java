package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.SessionizePrefillService;
import dev.ted.jittertravel.infrastructure.SessionizePrefillService.SessionizePrefill;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionizePrefillController {

    private final SessionizePrefillService prefillService;

    public SessionizePrefillController(SessionizePrefillService prefillService) {
        this.prefillService = prefillService;
    }

    // Reading a public Sessionize page is read-only, so it is modelled as a GET — the same
    // reasoning AddressParseController records: it keeps the endpoint outside Spring Security's
    // CSRF scope (which only guards state-changing methods) so the widget's fetch needn't thread
    // a token. The route is OWNER-only regardless: everything it returns is already on the open
    // web, but *that Ted is looking at this CFP* is not.
    @GetMapping("/api/sessionize-prefill")
    public ResponseEntity<SessionizePrefill> prefill(@RequestParam("url") String url) {
        return prefillService.prefill(url)
                             .map(ResponseEntity::ok)
                             .orElse(ResponseEntity.unprocessableEntity().build());
    }
}
