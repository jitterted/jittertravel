package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Address;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Form-backing bean for planning a private social event. Same single-day shape as
 * {@link PlanGatheringRequest} (date + start/end time + address + optional zone) minus the
 * public-event fields (no {@code speaking}, no {@code infoUrl}). The wire shape stays date + two
 * times; the boundary turns them into instants in the venue's zone.
 * <p>
 * NOTE: {@code getLocation()} duplicates {@link PlanGatheringRequest#getLocation()}; a shared
 * {@code VenueEventRequest} interface (the {@link HotelStayRequest} analog) is now a real two-user
 * dedup — see docs/archived/PrivateSocialEventPlan.md, deferred pending Ted's call.
 */
public class PlanPrivateEventRequest {
    private String privateEventId;
    private String title;
    private String venueName;
    private String street;
    private String city;
    private String region;
    private String postalCode;
    private String country;
    private String locationForMatching;
    private String zone;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    public String getPrivateEventId() { return privateEventId; }
    public void setPrivateEventId(String privateEventId) { this.privateEventId = privateEventId; }

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

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Address getLocation() {
        return new Address(street, city, region, postalCode, country, locationForMatching);
    }
}
