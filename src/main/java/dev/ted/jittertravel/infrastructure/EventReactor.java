package dev.ted.jittertravel.infrastructure;

import java.util.List;

/**
 * A consumer that reacts to appended events <strong>asynchronously</strong>, off the append thread,
 * and may do I/O or append events of its own. The Translator/Processor pattern:
 * {@code docs/FamilyEmailNotificationsPlan.md} §4.1.
 *
 * <p><strong>Deliberately not an {@link EventStreamConsumer}, and the separation is the point.</strong>
 * Two things follow from it that a shared interface would give away:
 *
 * <ul>
 *   <li><strong>A projector cannot be subscribed asynchronously.</strong> If async delivery were
 *       available to any {@code EventStreamConsumer}, someone eventually wires a projector that way
 *       and {@code /calendar} starts rendering stale immediately after a POST. The two populations
 *       are separated by type rather than by a comment asking nobody to do it.</li>
 *   <li><strong>A reactor can never be replayed into.</strong> {@link ProjectorBootstrapper#register}
 *       is {@code <P extends EventStreamConsumer>}, so a reactor does not fit it, and
 *       {@link EventStore} has no other path that hands history to one. That is a compile error
 *       rather than a rule to remember — which matters because the first real reactor sends email,
 *       and replaying history into it at boot would mail years of bookings at once.</li>
 * </ul>
 *
 * <p>The guard is "a reactor cannot be replayed into", not "a class cannot be both": nothing stops a
 * class implementing both interfaces and being registered twice. That takes two deliberate
 * registrations and the differing parameter types make it visible in the class declaration.
 *
 * <p><strong>{@code List}, not {@code Stream}, and it avoids a real bug.</strong> The batch is handed
 * across a thread boundary and consumed later, on the worker. A {@code Stream} is
 * single-consumption, so building one at dispatch and capturing it in the task would hand the second
 * reactor an already-consumed stream. {@code EventStreamConsumer} keeps its {@code Stream} because it
 * is consumed synchronously, in the same stack frame.
 */
public interface EventReactor {
    void react(List<StoredEvent> events);
}
