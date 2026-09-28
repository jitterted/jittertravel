package dev.ted.jittertravel.application;

/**
 * One conference on the dashboard, with what the dashboard worked out about it at {@code now}.
 * <p>
 * {@code cfpOpen} is true only where a deadline is recorded and has not yet passed. It sits here
 * rather than on {@link ConferenceView} because it is not a fact from events: the same conference
 * flips from open to closed with no event and no write, just a later {@code now}. And it is decided
 * by {@link ConferenceDashboard}, beside the grouping that asks the same question, rather than by
 * the renderer, so the "CFP closes soon" heading and the Submit link under it cannot disagree about
 * whether the deadline has passed. The rule itself is {@link CfpDeadlineView#cfpOpenAt}, which the
 * conference's detail page asks too.
 */
public record DashboardRow(
        ConferenceView conference,
        boolean cfpOpen
) {
}
