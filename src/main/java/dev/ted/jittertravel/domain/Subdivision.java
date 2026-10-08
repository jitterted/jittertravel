package dev.ted.jittertravel.domain;

/** A state, province or territory: its postal code, which is what an address stores, and its name. */
public record Subdivision(String code, String name) {
}
