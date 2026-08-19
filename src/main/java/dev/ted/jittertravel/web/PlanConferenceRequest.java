package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Address;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class PlanConferenceRequest {
    private String conferenceId;
    private String name;
    // The @DateTimeFormat for start and end dates are required to match browser's <input type="datetime-local" /> format
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startDate;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endDate;
    private String venueName;
    private String venueStreet;
    private String venueCity;
    private String venueState;
    private String venueCountry;
    private String venuePostalCode;
    // Optional CommonZone pick. Absent (older backups have no such field) means "derive the zone
    // from the venue address" — which is what keeps pre-migration backups importable unchanged.
    private String zone;
    // ConferenceFormat enum name, chosen via radio buttons on the form. Defaults to the safe
    // CALL_FOR_PAPERS so a submit that somehow omits it still binds; the handler re-derives the
    // default too (ConferenceFormat.fromParam).
    private String format = "CALL_FOR_PAPERS";

    public PlanConferenceRequest() {
    }

    public String getConferenceId() {
        return conferenceId;
    }

    public void setConferenceId(String conferenceId) {
        this.conferenceId = conferenceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getVenueStreet() {
        return venueStreet;
    }

    public void setVenueStreet(String venueStreet) {
        this.venueStreet = venueStreet;
    }

    public String getVenueCity() {
        return venueCity;
    }

    public void setVenueCity(String venueCity) {
        this.venueCity = venueCity;
    }

    public String getVenueState() {
        return venueState;
    }

    public void setVenueState(String venueState) {
        this.venueState = venueState;
    }

    public String getVenueCountry() {
        return venueCountry;
    }

    public void setVenueCountry(String venueCountry) {
        this.venueCountry = venueCountry;
    }

    public String getVenuePostalCode() {
        return venuePostalCode;
    }

    public void setVenuePostalCode(String venuePostalCode) {
        this.venuePostalCode = venuePostalCode;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Address getVenueAddress() {
        return new Address(venueStreet, venueCity, venueState, venuePostalCode, venueCountry, null);
    }

    @Override
    public String toString() {
        return "PlanConferenceRequest {" +
                "conferenceId='" + conferenceId + '\'' +
                ", name='" + name + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", venueName='" + venueName + '\'' +
                ", venueStreet='" + venueStreet + '\'' +
                ", venueCity='" + venueCity + '\'' +
                ", venueState='" + venueState + '\'' +
                ", venueCountry='" + venueCountry + '\'' +
                ", venuePostalCode='" + venuePostalCode + '\'' +
                ", zone='" + zone + '\'' +
                ", format='" + format + '\'' +
                '}';
    }
}
