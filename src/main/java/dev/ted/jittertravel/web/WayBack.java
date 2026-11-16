package dev.ted.jittertravel.web;

/**
 * Where a page's way out leads, and how to name it in a sentence: {@code place} completes "Keep it
 * — back to …" ({@code "the calendar"}, {@code "the list"}). Resolved once by
 * {@link LinkOrigin#wayBack} so a cancel page's link and its redirect are the same value.
 */
public record WayBack(String href, String place) {
}
