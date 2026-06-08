package dev.ted.jittertravel.domain;

import com.fasterxml.jackson.annotation.JsonAlias;

public record Address(
        String street,
        String city,
        @JsonAlias("state") String region,
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
