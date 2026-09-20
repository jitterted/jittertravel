package dev.ted.jittertravel.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * The two ends of a train trip, checked as a pair so one submit reports both.
 *
 * <p>{@link EnteredLocation} answers for one station at a time; this is where the trip's answer is
 * assembled. Each end contributes every problem it has — at most one per field, so up to two — and
 * every end at fault is named, rather than the first.
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
                Stream.of(EnteredLocation.of(departure).problems(LocationRole.DEPARTURE),
                          EnteredLocation.of(arrival).problems(LocationRole.ARRIVAL))
                      .flatMap(List::stream)
                      .toList();
        if (!problems.isEmpty()) {
            throw new InvalidTrainEntry(problems, List.of());
        }
    }
}
