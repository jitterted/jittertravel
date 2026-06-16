package dev.ted.jittertravel.contract;

import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.infrastructure.EventJsonMapperFactory;
import io.eventdriven.strictland.MessageContract;
import io.eventdriven.strictland.SpecificationOptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPIKE: evaluates Strictland for event-serialization contract tests against our Jackson-3 mapper.
 * See docs/Event_Serialization_Contract_Tests.md.
 * <p>
 * Uses the pinned {@link EventJsonMapperFactory} — the same config the production {@code JsonMapper}
 * bean is built from — so the approved snapshot reflects the exact bytes we'd persist. The approved
 * snapshot file is written next to this test on first run and committed.
 */
class ConferenceCancelledContractTest {

    private static final JsonMapper MAPPER = EventJsonMapperFactory.create();

    private static final SpecificationOptions OPTIONS =
            SpecificationOptions.serializer(new JsonMapperMessageSerializer(MAPPER));

    @Test
    void conferenceCancelledSerializationContractIsUnchanged() {
        ConferenceCancelled event = new ConferenceCancelled(
                new ConferenceId(UUID.fromString( "22222222-2222-2222-2222-222222222222")),
                "Venue double-booked");

        MessageContract.specification(OPTIONS)
                .given(event)
                .whenSerialized()
                .thenContractIsUnchanged();
    }

    @Test
    void conferenceCancelledRoundTripsThroughTheRealMapper() {
        ConferenceCancelled original = new ConferenceCancelled(
                new ConferenceId(UUID.fromString( "22222222-2222-2222-2222-222222222222")),
                "Venue double-booked");

        MessageContract.specification(OPTIONS)
                .given(original)
                .whenDeserializedAs(ConferenceCancelled.class)
                .thenBackwardCompatible(roundTripped ->
                        assertThat(roundTripped).isEqualTo(original));
    }
}