package dev.ted.jittertravel.domain;

/** One country: its ISO 3166-1 alpha-2 code, which is what an event stores, and its English name. */
public record Country(String code, String name) {
}
