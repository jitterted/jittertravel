package dev.ted.jittertravel.infrastructure;

import tools.jackson.databind.node.ObjectNode;

/**
 * One rung of one event type's schema ladder: it advances a payload from a single specific version
 * to the next. {@link EventPayloadUpcaster} composes the registered rungs into the full climb, so an
 * upcaster is deliberately narrow — it knows one event type (or a small family that shares a shape)
 * and one version step, nothing about the types or steps on either side of it.
 *
 * <p>An upcaster {@link #canHandle advertises} the {@code (logical type, version)} it consumes, so
 * the composite never has to sniff payload shape to decide which rung applies: it drives the climb
 * from the row's stored {@code schema_version}. Exactly one registered upcaster may claim any given
 * {@code (type, version)} pair — the composite enforces that.
 *
 * <p>{@link #upcast} mutates the payload in place. It is only ever called after {@code canHandle}
 * returned {@code true} for this payload's type and current version, so an implementation may assume
 * it holds the right type at the right rung; it still keeps its edit idempotent (an absence/shape
 * check) so a legacy row that was never stamped — and so is climbed from version 1 even though its
 * payload is already in a later shape — passes through the rung untouched rather than double-applied.
 */
interface EventUpcaster {

    /** True when this upcaster advances the given logical event type from exactly {@code eventVersion}. */
    boolean canHandle(String eventLogicalType, int eventVersion);

    /** Advance the payload one version, in place. Called only when {@link #canHandle} matched. */
    void upcast(ObjectNode payload, String eventLogicalType);
}
