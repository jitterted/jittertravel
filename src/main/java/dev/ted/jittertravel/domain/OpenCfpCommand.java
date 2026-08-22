package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

/**
 * Records that a conference's call for papers is open and closes at a given moment, emitting
 * {@link CfpOpened}.
 * <p>
 * <strong>On the name:</strong> commands here are the imperative of the event they produce
 * ({@code PlanConference} → {@code ConferencePlanned}, {@code CancelHotel} →
 * {@code HotelBookingCancelled}), so this is {@code OpenCfp}. The organizers are the ones who
 * actually opened it — Ted is recording a fact from the world — and the "named for what happened"
 * rule that shaped {@link CfpOpened} is about events, not about who pressed the button.
 * <p>
 * The only refusal is a conference that does not exist — never planned, or since cancelled by the
 * organizers, or declined. There is deliberately <strong>no time gate</strong>: a CFP whose deadline
 * has already passed is still worth recording, because "this closed and I did not submit" is exactly
 * the state the dashboard wants to show, and because backfilling an old conference is a legitimate use.
 * <p>
 * <strong>Recording twice is allowed</strong>, and is how a deadline extension gets in — organizers
 * move CFP dates routinely. The fold takes the last one, so there is no prior state to consult and
 * nothing to protect against; contrast {@link DeclineConferenceCommand}, where a second decline
 * would repeat a decision that already removed the conference from every read model.
 */
public record OpenCfpCommand(
        ConferenceId conferenceId,
        ZonedTimestamp closesOn
) implements DomainCommand<OpenCfpContext> {

    @Override
    public Stream<CfpOpened> execute(OpenCfpContext context) {
        if (!context.conferenceExists()) {
            throw new ConferenceNotFound("No conference found to record a CFP for: " + conferenceId);
        }
        return Stream.of(new CfpOpened(conferenceId, closesOn));
    }
}
