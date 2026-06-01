package dev.ted.jittertravel.domain;

public record Address(
        String street,
        String city,
        String state,
        String postalCode,
        String country
) {
}
