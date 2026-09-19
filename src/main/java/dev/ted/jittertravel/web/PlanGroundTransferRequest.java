package dev.ted.jittertravel.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form-backing record for planning a ground transfer — the taxi from the airport to the hotel, the
 * subway back.
 * <p>
 * Notice what is <em>not</em> here: no address fields. Each end is an endpoint <em>token</em>
 * ({@code airport:DEN:<flightId>}, {@code hotel:<bookingId>}) picked from a {@code <select>} of
 * places the app already knows, and the server resolves it at submit time. Ted never types an
 * address (D3), and there is no free-text fallback (D12).
 * <p>
 * {@code groundTransferId} stays a component, unlike the ids on the {@code Change*} forms: it is
 * minted for a new transfer and carried in a hidden field, so it genuinely is form data rather than
 * something the path already says.
 * <p>
 * One date and two times: a transfer that crosses midnight, like one that crosses a zone boundary,
 * is out of scope for this slice.
 * <p>
 * {@code mode} is the one free-text field, and it is optional — see
 * {@code GroundTransferPlanned} for why it is text rather than a choice, and why it is not a
 * reopening of D12.
 */
public record PlanGroundTransferRequest(
        String groundTransferId,
        String origin,
        String destination,
        String mode,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @DateTimeFormat(pattern = "HH:mm") LocalTime departureTime,
        @DateTimeFormat(pattern = "HH:mm") LocalTime arrivalTime
) {
}
