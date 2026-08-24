package dev.ted.jittertravel.domain;

public record Address(
        String street,
        String city,
        String region,
        String postalCode,
        String country,
        String locationForMatching
) {
    public Address {
        if (region == null) region = "";
        locationForMatching = (locationForMatching == null || locationForMatching.isBlank())
                ? city : locationForMatching;
    }
}
