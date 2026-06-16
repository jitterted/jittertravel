package dev.ted.jittertravel.contract;

import io.eventdriven.strictland.MessageSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adapts our Jackson-3 ({@code tools.jackson}) {@link JsonMapper} to Strictland's
 * {@link MessageSerializer}.
 * <p>
 * Strictland 0.3.0 ships its own Jackson-<b>2</b> ({@code com.fasterxml.jackson}) integration
 * via {@code Json.Jackson.of(ObjectMapper)} — incompatible with the Jackson-3 mapper our app
 * actually serializes events with. This adapter routes Strictland through the real mapper so the
 * snapshot reflects the exact bytes we persist. Jackson-3 throws unchecked, so no wrapping needed.
 */
public final class JsonMapperMessageSerializer implements MessageSerializer {

    private final JsonMapper jsonMapper;

    public JsonMapperMessageSerializer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public byte[] serialize(Object value) {
        return jsonMapper.writeValueAsBytes(value);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) {
        return jsonMapper.readValue(bytes, type);
    }
}