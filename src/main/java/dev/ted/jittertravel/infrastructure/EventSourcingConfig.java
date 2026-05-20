package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.ConferencePlanning;
import dev.ted.jittertravel.application.TentativeConferenceProjector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventSourcingConfig {

    @Bean
    public EventStore eventStore(MeterRegistry meterRegistry, PostgresPersister persister) {
        return new EventStore(meterRegistry, persister);
    }

    @Bean
    public TentativeConferenceProjector tentativeConferenceProjector(EventStore eventStore) {
        TentativeConferenceProjector projector = new TentativeConferenceProjector();
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }

    @Bean
    public ConferencePlanning conferenceApplicationService(EventStore eventStore, PostgresPersister persister) {
        return new ConferencePlanning(eventStore, persister);
    }
}
