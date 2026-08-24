package dev.ted.jittertravel.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form-backing bean for planning a ground transfer — the taxi from the airport to the hotel, the
 * subway back.
 * <p>
 * Notice what is <em>not</em> here: no address fields. Each end is an endpoint <em>token</em>
 * ({@code airport:DEN}, {@code hotel:<bookingId>}) picked from a {@code <select>} of places the app
 * already knows, and the server resolves it at submit time. Ted never types an address (D3), and
 * there is no free-text fallback (D12).
 * <p>
 * One date and two times: a transfer that crosses midnight, like one that crosses a zone boundary,
 * is out of scope for this slice.
 * <p>
 * {@code mode} is the one free-text field, and it is optional — see
 * {@code GroundTransferPlanned} for why it is text rather than a choice, and why it is not a
 * reopening of D12.
 */
public class PlanGroundTransferRequest {
    private String groundTransferId;
    private String origin;
    private String destination;
    private String mode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime departureTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime arrivalTime;

    public String getGroundTransferId() { return groundTransferId; }
    public void setGroundTransferId(String groundTransferId) { this.groundTransferId = groundTransferId; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalTime departureTime) { this.departureTime = departureTime; }

    public LocalTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(LocalTime arrivalTime) { this.arrivalTime = arrivalTime; }
}
