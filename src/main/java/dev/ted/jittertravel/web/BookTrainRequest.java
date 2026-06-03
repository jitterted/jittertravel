package dev.ted.jittertravel.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class BookTrainRequest {
    private String trainTripId;
    private String serviceId;

    private String departureStationName;
    private String departureCityName;
    private String departureCountry;
    private String departureMapsUrl;
    // @DateTimeFormat required to match browser's <input type="datetime-local" /> format
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime departureDateTime;

    private String arrivalStationName;
    private String arrivalCityName;
    private String arrivalCountry;
    private String arrivalMapsUrl;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime arrivalDateTime;

    public BookTrainRequest() {
    }

    public String getTrainTripId() { return trainTripId; }
    public void setTrainTripId(String trainTripId) { this.trainTripId = trainTripId; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getDepartureStationName() { return departureStationName; }
    public void setDepartureStationName(String departureStationName) { this.departureStationName = departureStationName; }

    public String getDepartureCityName() { return departureCityName; }
    public void setDepartureCityName(String departureCityName) { this.departureCityName = departureCityName; }

    public String getDepartureCountry() { return departureCountry; }
    public void setDepartureCountry(String departureCountry) { this.departureCountry = departureCountry; }

    public String getDepartureMapsUrl() { return departureMapsUrl; }
    public void setDepartureMapsUrl(String departureMapsUrl) { this.departureMapsUrl = departureMapsUrl; }

    public LocalDateTime getDepartureDateTime() { return departureDateTime; }
    public void setDepartureDateTime(LocalDateTime departureDateTime) { this.departureDateTime = departureDateTime; }

    public String getArrivalStationName() { return arrivalStationName; }
    public void setArrivalStationName(String arrivalStationName) { this.arrivalStationName = arrivalStationName; }

    public String getArrivalCityName() { return arrivalCityName; }
    public void setArrivalCityName(String arrivalCityName) { this.arrivalCityName = arrivalCityName; }

    public String getArrivalCountry() { return arrivalCountry; }
    public void setArrivalCountry(String arrivalCountry) { this.arrivalCountry = arrivalCountry; }

    public String getArrivalMapsUrl() { return arrivalMapsUrl; }
    public void setArrivalMapsUrl(String arrivalMapsUrl) { this.arrivalMapsUrl = arrivalMapsUrl; }

    public LocalDateTime getArrivalDateTime() { return arrivalDateTime; }
    public void setArrivalDateTime(LocalDateTime arrivalDateTime) { this.arrivalDateTime = arrivalDateTime; }
}
