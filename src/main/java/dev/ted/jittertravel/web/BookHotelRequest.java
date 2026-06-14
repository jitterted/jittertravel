package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookHotelHandler;
import dev.ted.jittertravel.domain.BookHotelContext;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

public class BookHotelRequest implements ImportableCommand {
    private String hotelBookingId;
    private String hotelName;
    private String street;
    private String city;
    private String region;
    private String country;
    private String postalCode;
    private String locationForMatching;
    private String mapsUrl;
    // @DateTimeFormat required to match browser's <input type="datetime-local" /> format
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime checkIn;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime checkOut;
    private BookingIntent bookingIntent;

    public String getHotelBookingId() { return hotelBookingId; }
    public void setHotelBookingId(String hotelBookingId) { this.hotelBookingId = hotelBookingId; }

    public String getHotelName() { return hotelName; }
    public void setHotelName(String hotelName) { this.hotelName = hotelName; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public void setState(String state) { this.region = state; } // backward compat for old exports

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getLocationForMatching() { return locationForMatching; }
    public void setLocationForMatching(String locationForMatching) { this.locationForMatching = locationForMatching; }

    public String getMapsUrl() { return mapsUrl; }
    public void setMapsUrl(String mapsUrl) { this.mapsUrl = mapsUrl; }

    public LocalDateTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDateTime checkIn) { this.checkIn = checkIn; }

    public LocalDateTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDateTime checkOut) { this.checkOut = checkOut; }

    public BookingIntent getBookingIntent() { return bookingIntent; }
    public void setBookingIntent(BookingIntent bookingIntent) { this.bookingIntent = bookingIntent; }

    @Override
    public UUID commandId() {
        return UUID.fromString(hotelBookingId);
    }

    @Override
    public Stream<? extends Event> events() {
        return new BookHotelHandler().handle(this).execute(new BookHotelContext(IMPORT_BYPASS_NOW));
    }
}
