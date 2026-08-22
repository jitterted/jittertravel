package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ConferenceFormat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sorts the conference list into "what needs doing next" — the dashboard that replaced one flat
 * table sorted by start date.
 * <p>
 * A flat list could not answer the question the page exists for. Everything looked alike whether its
 * CFP closed on Friday, closed last month, or never had one, and the answer to "what am I committed
 * to?" was scattered down a column. Grouping puts the conferences with someone else's clock running
 * at the top and the ones needing nothing at the bottom.
 * <p>
 * <strong>Derived per request from {@code now}, which is passed in.</strong> A conference crosses
 * from {@link DashboardGroup#CFP_CLOSES_SOON} to {@link DashboardGroup#DECIDE} the moment its deadline
 * passes, with no event and no write — so this reads a clock it is handed and never one of its own
 * (CLAUDE.md, "Time comes from the injected Clock").
 * <p>
 * Empty groups are left out: a heading over nothing is noise on a page whose job is to be scanned.
 */
public class ConferenceDashboard {

    /**
     * Group the conferences, in {@link DashboardGroup} declaration order, dropping empty groups.
     *
     * @param conferences already filtered by the FUTURE/ALL toggle — the dashboard groups what it is
     *                    given and does not second-guess that filter.
     * @param now         captured at the boundary; decides only whether a CFP deadline has passed.
     */
    public List<DashboardSection> sections(List<ConferenceView> conferences, Instant now) {
        Map<DashboardGroup, List<ConferenceView>> byGroup = new EnumMap<>(DashboardGroup.class);
        for (ConferenceView conference : conferences) {
            byGroup.computeIfAbsent(groupFor(conference, now), group -> new ArrayList<>())
                   .add(conference);
        }

        List<DashboardSection> sections = new ArrayList<>();
        for (DashboardGroup group : DashboardGroup.values()) {
            List<ConferenceView> members = byGroup.get(group);
            if (members != null && !members.isEmpty()) {
                sections.add(new DashboardSection(group, sorted(group, members)));
            }
        }
        return List.copyOf(sections);
    }

    /**
     * <strong>Commitment is asked first, and that ordering is the rule.</strong> A conference Ted is
     * going to needs nothing from him whatever its CFP is doing — he has already decided, and
     * showing it under "CFP closes soon" would be nagging about a question he answered. Everything
     * below this line is therefore about a conference still merely watched.
     */
    private DashboardGroup groupFor(ConferenceView conference, Instant now) {
        if (conference.commitment() == AttendanceCommitment.GOING) {
            return DashboardGroup.GOING;
        }
        if (conference.format() == ConferenceFormat.OPEN_SPACE) {
            return DashboardGroup.NOTHING_TO_SUBMIT;
        }
        if (conference.cfpClosesOn() == null) {
            return DashboardGroup.CFP_DATE_UNKNOWN;
        }
        return conference.cfpClosesOn().utc().isAfter(now)
                ? DashboardGroup.CFP_CLOSES_SOON
                : DashboardGroup.DECIDE;
    }

    /**
     * The closing group sorts by its deadline — the soonest is the most urgent, and the whole point
     * of the group. Every other group keeps the list's start-date order, which is how a calendar
     * reads.
     */
    private List<ConferenceView> sorted(DashboardGroup group, List<ConferenceView> members) {
        Comparator<ConferenceView> order = group == DashboardGroup.CFP_CLOSES_SOON
                ? Comparator.comparing(view -> view.cfpClosesOn().utc())
                : Comparator.comparing(view -> view.startDate().utc());
        return members.stream().sorted(order).toList();
    }
}
