package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Address;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Form-backing record for planning a conference.
 * <p>
 * {@code conferenceId} stays a component: it is minted for a new conference and carried in a hidden
 * field, so it is form data rather than something the path already says.
 * <p>
 * The generated {@code toString} replaces a hand-written one that had fallen behind its own fields —
 * it named thirteen of them and omitted {@code cfpClosesOn} and {@code cfpSubmissionUrl}. It is read
 * by {@code CommandExecutor.refuseWhenReadOnly}, so a refusal now names the whole submitted form.
 */
public record PlanConferenceRequest(
        String conferenceId,
        String name,
        // The @DateTimeFormat for start and end dates are required to match browser's
        // <input type="datetime-local" /> format
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDate,
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endDate,
        String venueName,
        String venueStreet,
        String venueCity,
        String venueState,
        String venueCountry,
        String venuePostalCode,
        String zone,
        String format,
        String infoUrl,
        @OptionalEntry @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime cfpClosesOn,
        String cfpSubmissionUrl
) {

    /**
     * What the field initializer used to say. A record has no initializers, so the default lands
     * here — and only for an <em>absent</em> value, which is the case the initializer covered: a
     * submitted-but-empty {@code format} stayed {@code ""} before and still does.
     */
    private static final String DEFAULT_FORMAT = "CALL_FOR_PAPERS";

    public PlanConferenceRequest {
        format = format == null ? DEFAULT_FORMAT : format;
    }

    public Address venueAddress() {
        return new Address(venueStreet, venueCity, venueState, venuePostalCode, venueCountry, null);
    }
}
