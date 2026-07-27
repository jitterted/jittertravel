package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ChangeGatheringHandler;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.application.VenueZone;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ChangeGatheringContext;
import dev.ted.jittertravel.domain.Event;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.Stream;

public class ChangeGatheringRequest implements ImportableCommand {
    private String gatheringId;
    private String title;
    private String venueName;
    private String street;
    private String city;
    private String region;
    private String postalCode;
    private String country;
    private String locationForMatching;
    // See PlanGatheringRequest: optional CommonZone pick, wins over location-derived resolution.
    private String zone;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private boolean speaking;
    private String infoUrl;

    public String getGatheringId() { return gatheringId; }
    public void setGatheringId(String gatheringId) { this.gatheringId = gatheringId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getLocationForMatching() { return locationForMatching; }
    public void setLocationForMatching(String locationForMatching) { this.locationForMatching = locationForMatching; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public boolean isSpeaking() { return speaking; }
    public void setSpeaking(boolean speaking) { this.speaking = speaking; }

    public String getInfoUrl() { return infoUrl; }
    public void setInfoUrl(String infoUrl) { this.infoUrl = infoUrl; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public Address getLocation() {
        return new Address(street, city, region, postalCode, country, locationForMatching);
    }

    @Override
    public UUID commandId() {
        // A gathering may be changed many times; each change is a distinct command, so the id is
        // not derived from gatheringId (the aggregate id). Import keeps it random, matching live
        // behavior.
        return UUID.randomUUID();
    }

    @Override
    public Stream<? extends Event> events() {
        // On import the gathering is assumed to already exist (its planning imported earlier).
        return new ChangeGatheringHandler(new LocationZoneResolver()).handle(this)
                .execute(new ChangeGatheringContext(true, IMPORT_BYPASS_INSTANT));
    }
}
