package dev.ted.jittertravel.domain;

public record TrainStationAddress(
        String name,
        String city,
        String country,
        String mapsUrl
) {
    public TrainStationAddress {
        mapsUrl = mapsUrl != null ? mapsUrl : "";
    }
}
