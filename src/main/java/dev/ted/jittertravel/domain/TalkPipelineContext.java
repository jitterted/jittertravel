package dev.ted.jittertravel.domain;

/**
 * Decision facts shared by the five commands on the speaking axis ({@link SubmitTalkCommand},
 * {@link AcceptTalkCommand}, {@link RejectTalkCommand}, {@link WithdrawTalkCommand},
 * {@link InviteToSpeakCommand}), folded from the authoritative event stream — never from a read
 * model (R1 in {@code EventSourcingRulesHeuristics.md}).
 *
 * <p><strong>One context for five commands, deliberately.</strong> Elsewhere each command has its
 * own, but these five ask the identical three questions of the identical stream, and five
 * byte-identical records would be five places to keep in step. The five users exist today, so this
 * is not an abstraction built ahead of a second one.
 *
 * <p>No clock. Nothing on this axis is time-gated: recording that a talk was accepted last March
 * is exactly what catching up looks like, and a submission's own deadline lives on
 * {@link CfpOpened}.
 *
 * @param conferenceExists whether a live conference with this id exists — planned at some point
 *                         and neither cancelled by the organizers nor declined by Ted. The same
 *                         fact {@code OpenCfpContext} and {@code ConfirmConferenceAttendanceContext}
 *                         fold, and the first thing every command here refuses on.
 * @param speakingStatus   where the talk stands right now, so a command can refuse a transition
 *                         that would mean nothing — accepting a talk that was never submitted, or
 *                         withdrawing one that is not outstanding.
 * @param format           how the conference forms its program. Only {@link SubmitTalkCommand}
 *                         reads it, to refuse a submission to an {@code OPEN_SPACE} conference,
 *                         which has no CFP to submit to.
 */
public record TalkPipelineContext(
        boolean conferenceExists,
        SpeakingStatus speakingStatus,
        ConferenceFormat format
) implements DecisionContext {
}
