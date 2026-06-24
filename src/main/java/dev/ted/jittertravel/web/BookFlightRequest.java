package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.application.BookFlightHandler;
import dev.ted.jittertravel.domain.BookFlightContext;
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
    // Optional explicit time-zone pick (a CommonZone enum name or raw IANA zone ID). Empty/absent
    // means "derive from the airport code via AirportZoneResolver". Departure and arrival are independent.
    private String departureZone;
    // The @DateTimeFormat for departure and arrival times are required to match browser's <input type="datetime-local" /> format
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime departureDateTime;
    private String arrivalAirport;
    private String arrivalZone;
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

    public String getDepartureZone() {
        return departureZone;
    }

    public void setDepartureZone(String departureZone) {
        this.departureZone = departureZone;
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

    public String getArrivalZone() {
        return arrivalZone;
    }

    public void setArrivalZone(String arrivalZone) {
        this.arrivalZone = arrivalZone;
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
        return new BookFlightHandler(new AirportZoneResolver()).handle(this)
                .execute(new BookFlightContext(IMPORT_BYPASS_INSTANT));
    }

    @Override
    public String toString() {
        return "BookFlightRequest {" +
                "flightId='" + flightId + '\'' +
                ", airline='" + airline + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", departureAirport='" + departureAirport + '\'' +
                ", departureZone='" + departureZone + '\'' +
                ", departureDateTime=" + departureDateTime +
                ", arrivalAirport='" + arrivalAirport + '\'' +
                ", arrivalZone='" + arrivalZone + '\'' +
                ", arrivalDateTime=" + arrivalDateTime +
                '}';
    }
}
