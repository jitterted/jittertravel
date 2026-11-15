package dev.ted.jittertravel.application;

/**
 * A list row that stays on its list after it is cancelled, as a record rather than as travel to
 * take — what {@link CancelledView} filters on {@code /booked-flights} and {@code /booked-hotels}.
 */
public interface CancellableView {

    boolean cancelled();
}
