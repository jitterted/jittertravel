package dev.ted.jittertravel.infrastructure;

/**
 * Wires a projector into the {@link EventStore}: subscribe it for future events, then replay the
 * existing stream so it is caught up before it is handed back as a bean.
 * <p>
 * Collapses the three-line {@code new / subscribe / handle(findAll())} ritual that every projector
 * {@code @Bean} in {@link EventSourcingConfig} used to repeat into a single {@code register(...)}
 * call. The order matches the old inline form exactly — subscribe first so nothing appended during
 * replay is missed, then replay history — and {@code register} returns the same instance so the
 * bean method can hand it straight back (and other beans can depend on the concrete type).
 */
public class ProjectorBootstrapper {

    private final EventStore eventStore;

    public ProjectorBootstrapper(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public <P extends EventStreamConsumer> P register(P projector) {
        eventStore.subscribe(projector);
        projector.handle(eventStore.findAll());
        return projector;
    }
}
