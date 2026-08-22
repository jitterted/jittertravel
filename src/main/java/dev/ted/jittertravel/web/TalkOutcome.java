package dev.ted.jittertravel.web;

/**
 * What Ted is recording about a talk — the transition, not the state. One value per event on the
 * speaking axis, and it is what the dashboard's action links and the catch-up form both post.
 * <p>
 * Deliberately <em>not</em> {@code SpeakingStatus}, which names the state a conference is in.
 * The two happen to line up one-for-one today because each event moves to the like-named state,
 * but they answer different questions: {@code SpeakingStatus} has a {@code NOT_SPEAKING} value
 * that no event produces, and a future transition that does not simply rename the state (a second
 * submission, say) would have a value here and none there.
 * <p>
 * Lives in {@code web} because it is a form value, alongside {@link RecordTalkRequest}.
 */
public enum TalkOutcome {
    /** Ted submitted a talk to the CFP. */
    SUBMITTED,
    /** The organizers accepted it — which commits attendance on its own. */
    ACCEPTED,
    /** The organizers turned it down. */
    REJECTED,
    /** Ted pulled it. Says nothing about whether he still attends. */
    WITHDRAWN,
    /** The organizers asked him to speak, with no CFP. An offer, so it commits nothing. */
    INVITED
}
