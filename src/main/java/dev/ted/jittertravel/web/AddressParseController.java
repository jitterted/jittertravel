package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.AddressParseService;
import dev.ted.jittertravel.infrastructure.AddressParseService.ParsedAddress;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AddressParseController {

    private final AddressParseService parseService;

    public AddressParseController(AddressParseService parseService) {
        this.parseService = parseService;
    }

    // A geocoding lookup is read-only, so it is modelled as a GET: this keeps it out of
    // Spring Security's CSRF scope (which only guards state-changing methods) and avoids
    // the form having to thread a CSRF token through the fetch.
    @GetMapping("/api/parse-address")
    public ResponseEntity<ParsedAddress> parseAddress(@RequestParam("q") String q) {
        return parseService.parse(q)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.unprocessableEntity().build());
    }
}
