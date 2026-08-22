package dev.ted.jittertravel.application;

/**
 * One choice in a ground-transfer endpoint {@code <select>}: the {@code token} is the submitted
 * value ({@code airport:DEN}, {@code hotel:<bookingId>}), the {@code label} is what Ted reads.
 * <p>
 * {@code city} is never displayed on its own — the label already names it. It is the place the
 * <em>schedule</em> reasons about this endpoint in (an airport's city, a stay's
 * {@code locationForMatching}), so a missing-travel gap can be matched against the options and the
 * form can preselect the two ends it is about. Matching on a scraped label would tie that to
 * punctuation.
 * <p>
 * {@code prefillDate} / {@code prefillTime} are the endpoint's own moment, carried onto the option
 * so the form can fill the date and time in when it is chosen — otherwise Ted has to remember when
 * the flight lands, or open another tab. They are already in the shapes the inputs want
 * ({@code 2026-09-14}, {@code 11:30}), so nothing formats them again downstream.
 * <p>
 * A hotel carries one too, as of 2026-08-21: leaving one, the moment is its <em>check-out</em>;
 * arriving at one, its <em>check-in</em>. That is a guess in a way a flight's moment is not — a
 * stay is a range, and the ride to a gathering mid-stay happens on neither of those days — so the
 * label always says which moment it is filling in.
 */
public record TransferEndpointOption(String token, String label, String city,
                                     String prefillDate, String prefillTime) {

    public TransferEndpointOption {
        city = blankWhenNull(city);
        prefillDate = blankWhenNull(prefillDate);
        prefillTime = blankWhenNull(prefillTime);
    }

    private static String blankWhenNull(String value) {
        return value == null ? "" : value;
    }
}
