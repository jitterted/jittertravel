package dev.ted.jittertravel.domain;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The two ends of a train trip, checked as a pair so one submit reports both.
 *
 * <p>{@link EnteredLocation} answers for one station at a time; this is where the trip's answer is
 * assembled. Each end contributes at most one problem — its earliest broken rule — and every end at
 * fault is named, rather than the first.
 *
 * <p>This is the command's gate, and it sees locations only. The boundary asks a wider question
 * (locations <em>and</em> zones, per end) before building a command at all; see
 * {@code TrainEndpoints}. Both report through {@link InvalidTrainEntry}, so a controller has one
 * thing to catch.
 */
public record TrainStations(TrainStationAddress departure, TrainStationAddress arrival) {

    /**
     * @throws InvalidTrainEntry naming every end whose location cannot be a place.
     */
    public void check() {
        List<InvalidLocationEntry> problems =
                Stream.of(EnteredLocation.of(departure).problem(LocationRole.DEPARTURE),
                          EnteredLocation.of(arrival).problem(LocationRole.ARRIVAL))
                      .flatMap(Optional::stream)
                      .toList();
        if (!problems.isEmpty()) {
            throw new InvalidTrainEntry(problems, List.of());
        }
    }
}
