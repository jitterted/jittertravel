package dev.ted.jittertravel.domain;

public record AirportCode(String code) {
    public AirportCode {
        if (code == null) {
            throw new InvalidAirportCode("Airport code is required");
        }
        if (code.length() != 3) {
            throw new InvalidAirportCode("Airport code must be exactly 3 characters: " + code);
        }
        if (!code.chars().allMatch(Character::isLetter)) {
            throw new InvalidAirportCode("Airport code must contain only letters: " + code);
        }
        code = code.toUpperCase();
    }

    public static AirportCode of(String code) {
        return new AirportCode(code);
    }
}
