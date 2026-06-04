package dev.ted.jittertravel.web;

import dev.ted.jittertravel.infrastructure.AddressParseService;
import dev.ted.jittertravel.infrastructure.AddressParseService.ParsedAddress;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AddressParseController {

    private final AddressParseService parseService;

    public AddressParseController(AddressParseService parseService) {
        this.parseService = parseService;
    }

    public record ParseAddressRequest(String rawAddress) {}

    @PostMapping("/api/parse-address")
    public ResponseEntity<ParsedAddress> parseAddress(@RequestBody ParseAddressRequest request) {
        return parseService.parse(request.rawAddress())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.unprocessableEntity().build());
    }
}
