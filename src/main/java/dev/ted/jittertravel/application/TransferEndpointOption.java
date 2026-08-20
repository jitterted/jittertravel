package dev.ted.jittertravel.application;

/**
 * One choice in a ground-transfer endpoint {@code <select>}: the {@code token} is the submitted
 * value ({@code airport:DEN}, {@code hotel:<bookingId>}), the {@code label} is what Ted reads.
 * <p>
 * {@code prefillDate} / {@code prefillTime} are the flight's own moment, carried onto the option so
 * the form can fill the date and time in when it is chosen — otherwise Ted has to remember when the
 * flight lands, or open another tab. They are already in the shapes the inputs want
 * ({@code 2026-09-14}, {@code 11:30}), so nothing formats them again downstream. Both are blank on
 * a hotel: a check-in time is not when a taxi runs.
 */
public record TransferEndpointOption(String token, String label,
                                     String prefillDate, String prefillTime) {

    public TransferEndpointOption {
        prefillDate = blankWhenNull(prefillDate);
        prefillTime = blankWhenNull(prefillTime);
    }

    /** An option with no moment behind it — a hotel. */
    public TransferEndpointOption(String token, String label) {
        this(token, label, "", "");
    }

    private static String blankWhenNull(String value) {
        return value == null ? "" : value;
    }
}
