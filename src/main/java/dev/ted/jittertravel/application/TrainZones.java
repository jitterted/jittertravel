package dev.ted.jittertravel.application;

import java.time.ZoneId;

/**
 * The zone at each end of a train trip. A trip may span two, so they are separate values and never
 * one — see {@link TrainEndpoints}, which is what produces this.
 */
public record TrainZones(ZoneId departure, ZoneId arrival) {
}
