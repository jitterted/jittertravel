package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.TentativeConferenceProjector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventSourcingConfig {

    @Bean
    public InMemoryEventStore eventStore(MeterRegistry meterRegistry) {
        return new InMemoryEventStore(meterRegistry);
    }

    @Bean
    public TentativeConferenceProjector tentativeConferenceProjector(InMemoryEventStore eventStore) {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }
}
