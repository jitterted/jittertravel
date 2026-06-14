package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.ChangeFlightCommand;
import dev.ted.jittertravel.domain.Event;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

public class ChangeFlightRequest implements ImportableCommand {
    private String flightId;
    private String airline;
    private String flightNumber;
    private String departureAirport;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime departureDateTime;
    private String arrivalAirport;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime arrivalDateTime;
    private String reason;

    public ChangeFlightRequest() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getFlightId() {
        return flightId;
    }

    public void setFlightId(String flightId) {
        this.flightId = flightId;
    }

    public String getAirline() {
        return airline;
    }

    public void setAirline(String airline) {
        this.airline = airline;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getDepartureAirport() {
        return departureAirport;
    }

    public void setDepartureAirport(String departureAirport) {
        this.departureAirport = departureAirport;
    }

    public LocalDateTime getDepartureDateTime() {
        return departureDateTime;
    }

    public void setDepartureDateTime(LocalDateTime departureDateTime) {
        this.departureDateTime = departureDateTime;
    }

    public String getArrivalAirport() {
        return arrivalAirport;
    }

    public void setArrivalAirport(String arrivalAirport) {
        this.arrivalAirport = arrivalAirport;
    }

    public LocalDateTime getArrivalDateTime() {
        return arrivalDateTime;
    }

    public void setArrivalDateTime(LocalDateTime arrivalDateTime) {
        this.arrivalDateTime = arrivalDateTime;
    }

    @Override
    public UUID commandId() {
        // A flight may be changed many times; each change is a distinct command, so the id is not
        // derived from flightId (import keeps it random, matching the prior behavior).
        return UUID.randomUUID();
    }

    @Override
    public Stream<? extends Event> events() {
        // On import the flight is assumed to already exist (its booking imported earlier).
        return new ChangeFlightCommand().execute(this, true, IMPORT_BYPASS_NOW);
    }

    @Override
    public String toString() {
        return "ChangeFlightRequest {" +
                "flightId='" + flightId + '\'' +
                ", airline='" + airline + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", departureAirport='" + departureAirport + '\'' +
                ", departureDateTime=" + departureDateTime +
                ", arrivalAirport='" + arrivalAirport + '\'' +
                ", arrivalDateTime=" + arrivalDateTime +
                ", reason='" + reason + '\'' +
                '}';
    }
}
