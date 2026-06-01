package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Address;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class PlanTentativeConferenceRequest {
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

    public PlanTentativeConferenceRequest() {
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

    public Address getVenueAddress() {
        return new Address(venueStreet, venueCity, venueState, venuePostalCode, venueCountry);
    }

    @Override
    public String toString() {
        return "PlanTentativeConferenceRequest {" +
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
                '}';
    }
}
