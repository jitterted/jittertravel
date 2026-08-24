package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Address;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The mapper's read-side behaviour, asserted through the <em>production</em> configuration rather
 * than the strict one {@code GoldenEventDeserializationTest} uses.
 * <p>
 * Leniency is the load-bearing fact: production ignores a property it does not know, so a field
 * that disappears from an event does not break replay. The second test records what that costs
 * after the {@code "state"} → {@code region} alias was <strong>retired</strong> on 2026-08-23 — an
 * artifact predating the rename now reads back with an <em>empty</em> region rather than failing.
 * That is deliberate: no backup in rotation and no production row carries the old spelling (all
 * five event-format backups checked, zero occurrences), and the only files that do are the
 * June-2026 command exports, a format restore cannot read at all.
 */
class EventJsonMapperFactoryTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();

    @Test
    void unknownPropertyIsIgnoredRatherThanRejected() {
        String json = """
                {"street": "1 Main St", "city": "Springfield", "region": "IL",
                 "postalCode": "62701", "country": "US", "notAFieldAnyMore": "x"}
                """;

        assertThatCode(() -> mapper.readValue(json, Address.class))
                .as("production reads stored events leniently — a dropped field must not break replay")
                .doesNotThrowAnyException();
    }

    @Test
    void preRenameStateFieldIsNoLongerReadIntoRegion() {
        String json = """
                {"street": "1 Main St", "city": "Springfield", "state": "IL",
                 "postalCode": "62701", "country": "US"}
                """;

        assertThat(mapper.readValue(json, Address.class).region())
                .as("the 'state' alias is retired — an artifact predating the rename loses its "
                    + "region silently, and nothing in rotation is such an artifact")
                .isEmpty();
    }
}
