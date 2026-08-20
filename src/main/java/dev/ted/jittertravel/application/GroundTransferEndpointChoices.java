package dev.ted.jittertravel.application;

import java.util.List;

/**
 * What the ground-transfer form offers at each end.
 * <p>
 * The two ends offer <strong>different</strong> flight legs, and that is the point (Ted,
 * 2026-08-20): you only ever travel <em>from</em> an airport you have landed at and <em>to</em> one
 * you are flying out of. So the "From" select gets {@link #arrivals} and the "To" select gets
 * {@link #departures} — never both. That removes the ambiguity a plain per-airport list had (two
 * trips through SFO collapsed into one unexplained choice) and lets each option carry the one time
 * that matters for it.
 * <p>
 * {@link #hotels} appear in both, unchanged: a hotel is a place at either end of a hop.
 */
public record GroundTransferEndpointChoices(List<TransferEndpointOption> arrivals,
                                            List<TransferEndpointOption> departures,
                                            List<TransferEndpointOption> hotels) {

    public boolean isEmpty() {
        return arrivals.isEmpty() && departures.isEmpty() && hotels.isEmpty();
    }
}
