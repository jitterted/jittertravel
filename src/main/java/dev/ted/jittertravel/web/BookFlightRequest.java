package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.BookFlightCommand;
import dev.ted.jittertravel.domain.Event;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

public class BookFlightRequest implements ImportableCommand {
    private String flightId;
    private String airline;
    private String flightNumber;
    private String departureAirport;
    // The @DateTimeFormat for departure and arrival times are required to match browser's <input type="datetime-local" /> format
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime departureDateTime;
    private String arrivalAirport;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime arrivalDateTime;

    public BookFlightRequest() {
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
        return UUID.fromString(flightId);
    }

    @Override
    public Stream<? extends Event> events() {
        return new BookFlightCommand().execute(this, IMPORT_BYPASS_NOW);
    }

    @Override
    public String toString() {
        return "BookFlightRequest {" +
                "flightId='" + flightId + '\'' +
                ", airline='" + airline + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", departureAirport='" + departureAirport + '\'' +
                ", departureDateTime=" + departureDateTime +
                ", arrivalAirport='" + arrivalAirport + '\'' +
                ", arrivalDateTime=" + arrivalDateTime +
                '}';
    }
}
