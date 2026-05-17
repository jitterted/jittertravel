package dev.ted.jittertravel.infrastructure;

import java.util.stream.Stream;

public interface EventStreamConsumer {
    void handle(Stream<StoredEvent> eventStream);
}
