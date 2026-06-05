package dev.ted.jittertravel.application;

import java.time.LocalDateTime;

public sealed interface ItineraryEntry
        permits FlightItineraryEntry, TrainItineraryEntry, HotelItineraryEntry, ConferenceItineraryEntry, GatheringItineraryEntry {
    EntryKind kind();
    LocalDateTime anchorTime();
}
