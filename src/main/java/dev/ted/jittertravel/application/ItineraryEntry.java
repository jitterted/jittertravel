package dev.ted.jittertravel.application;

import java.time.LocalDateTime;

public sealed interface ItineraryEntry
        permits FlightItineraryEntry, TrainItineraryEntry, HotelItineraryEntry, ConferenceItineraryEntry {
    EntryKind kind();
    LocalDateTime anchorTime();
}
