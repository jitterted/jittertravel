package dev.ted.jittertravel.domain;

/**
 * The typed date does not fit the two places the transfer joins: they are more than a day apart, or
 * the date is the day of neither.
 */
public class InvalidGroundTransferDate extends RuntimeException {
    public InvalidGroundTransferDate(String message) {
        super(message);
    }
}
