package dev.ted.jittertravel.domain;

/**
 * Which location of a booking an {@link EnteredLocation} check is about — a trip has two, a stay
 * has one. It travels on {@link InvalidLocationEntry} so the form that submitted the booking knows
 * which of its fields to put the error under; the domain itself never names a form field.
 */
public enum LocationRole {
    DEPARTURE,
    ARRIVAL,
    STAY
}
