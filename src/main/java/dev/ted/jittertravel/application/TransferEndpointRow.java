package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Place;
import dev.ted.jittertravel.domain.ZonedTimestamp;

/**
 * One endpoint the ground-transfer form could offer, as {@link TransferEndpointProjector} reads it
 * out of the events — before {@code now} has been anywhere near it.
 * <p>
 * <strong>{@link #city} and {@link #place} look like duplication and are not.</strong> For a flight
 * they hold the same value. For a hotel they deliberately differ: the label says the address's city
 * ("SeminarZentrum Rückersbach — Rückersbach", the words on the building), while the schedule
 * matches on {@code locationForMatching} ("Johannesberg", the town everything else names). Collapse
 * them and one of the two breaks — either the label reads wrong, or a gap stops preselecting the
 * stay that would close it, which is the silent half.
 * <p>
 * <strong>{@link #moment} and {@link #offeredUntil} likewise.</strong> {@code moment} is what this
 * end is <em>about</em>: it fills the form's date and time, and the label names it. {@code
 * offeredUntil} is the moment whose own local day decides whether the endpoint is still worth
 * showing. A flight's are the same. A hotel's are not, and that is load-bearing: both ends of a
 * stay are filtered on <em>check-out</em>, so the hotel you are riding toward does not vanish from
 * the "To" list the instant you arrive — which is exactly when the ride gets written down.
 * <p>
 * {@link #detail} is the parenthesis a flight leg carries ({@code Airline F1}) and a stay does not;
 * blank means the label ends after the moment.
 *
 * @param end          which of the form's four lists this belongs in, and the verb its label uses
 * @param token        the submitted value — {@code airport:DEN}, {@code hotel:<bookingId>}. Two
 *                     arrivals into DEN are two rows sharing one token, because a transfer is
 *                     between places and not between flights (D3)
 * @param name         the label's first part: an airport code, or the hotel's name
 * @param city         the label's second part — display only
 * @param place        what the schedule reasons about this endpoint in, for matching a gap
 * @param moment       this end's own moment: the prefill, and what the label names
 * @param offeredUntil the moment whose local day decides whether this is still offered
 * @param detail       the label's trailing parenthesis, or blank
 */
public record TransferEndpointRow(
        TransferEnd end,
        String token,
        String name,
        String city,
        Place place,
        ZonedTimestamp moment,
        ZonedTimestamp offeredUntil,
        String detail
) {

    public TransferEndpointRow {
        if (detail == null) {
            detail = "";
        }
    }
}
