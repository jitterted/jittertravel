package dev.ted.jittertravel.infrastructure;

import tools.jackson.databind.json.JsonMapper;

/**
 * Single source of truth for the {@link JsonMapper} used to (de)serialize persisted events and
 * commands. Both the production {@code JsonMapper} bean (see {@link EventSourcingConfig}) and the
 * serialization tests build their mapper here, so a snapshot/contract test can never silently
 * drift from what production actually writes to the {@code event_log} / {@code command_log}.
 * <p>
 * For an event-sourced store the on-the-wire format is a long-lived contract: stored events and
 * exported backups must stay readable across upgrades. Pinning the config here (rather than
 * relying on Spring Boot's auto-configured mapper) keeps that contract under version control and
 * out of the reach of a framework-default change. {@code EventJsonMapperEquivalenceTest} proves
 * this config serializes byte-for-byte identically to the mapper Spring currently auto-configures.
 */
public final class EventJsonMapperFactory {

    private EventJsonMapperFactory() {
    }

    public static JsonMapper create() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}