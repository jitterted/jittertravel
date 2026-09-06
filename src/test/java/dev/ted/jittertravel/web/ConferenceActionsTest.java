package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import j2html.tags.DomContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The conference state machine, tested where it lives rather than only through the two pages that
 * render it — a self-contained renderer gets its own direct test (CLAUDE.md). The dashboard's own
 * tests still cover how a row lays these out; this one is about which moves exist.
 */
class ConferenceActionsTest {

    private static final ConferenceId CONFERENCE_ID =
            ConferenceId.of(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    private static final String BASE = "/conferences/11111111-2222-3333-4444-555555555555";

    /**
     * Every state's whole set of moves, in order, in one table — which is the point of the table:
     * a change to the state machine shows up here as a changed line rather than as a new test
     * somewhere else.
     */
    static List<Arguments> everyState() {
        return List.of(
                arguments("nothing submitted", AttendanceCommitment.WATCHING,
                          SpeakingStatus.NOT_SPEAKING, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/talk?outcome=SUBMITTED",
                                  BASE + "/confirm?basis=TICKET_PURCHASED",
                                  BASE + "/decline")),
                // An open space has nothing to submit to, so the same state offers one move fewer.
                arguments("open space", AttendanceCommitment.WATCHING,
                          SpeakingStatus.NOT_SPEAKING, ConferenceFormat.OPEN_SPACE,
                          List.of(BASE + "/confirm?basis=TICKET_PURCHASED", BASE + "/decline")),
                arguments("submitted", AttendanceCommitment.WATCHING,
                          SpeakingStatus.SUBMITTED, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/talk?outcome=ACCEPTED",
                                  BASE + "/talk?outcome=REJECTED",
                                  BASE + "/talk?outcome=WITHDRAWN")),
                arguments("invited", AttendanceCommitment.WATCHING,
                          SpeakingStatus.INVITED, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/confirm?basis=SPEAKING_INVITED", BASE + "/decline")),
                arguments("rejected but still watching", AttendanceCommitment.WATCHING,
                          SpeakingStatus.REJECTED, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/confirm?basis=TICKET_PURCHASED", BASE + "/decline")),
                // Pulling a talk puts submitting back on the table.
                arguments("withdrawn", AttendanceCommitment.WATCHING,
                          SpeakingStatus.WITHDRAWN, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/talk?outcome=SUBMITTED",
                                  BASE + "/confirm?basis=TICKET_PURCHASED",
                                  BASE + "/decline")),
                // Going on an early-bird ticket, nothing submitted. Submitting is still on the
                // table — the ticket says nothing about the talk — and Ticket Bought is the only
                // move a commitment takes away.
                arguments("going on a ticket", AttendanceCommitment.GOING,
                          SpeakingStatus.NOT_SPEAKING, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/talk?outcome=SUBMITTED", BASE + "/decline")),
                arguments("going to an open space", AttendanceCommitment.GOING,
                          SpeakingStatus.NOT_SPEAKING, ConferenceFormat.OPEN_SPACE,
                          List.of(BASE + "/decline")),
                // The dead end this table is written to catch: a ticket bought, then a talk
                // submitted. Before 2026-09-06 this offered Decline alone, so the acceptance could
                // not be recorded anywhere and the speaking badge could never be earned.
                arguments("going with a talk out", AttendanceCommitment.GOING,
                          SpeakingStatus.SUBMITTED, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/talk?outcome=ACCEPTED",
                                  BASE + "/talk?outcome=REJECTED",
                                  BASE + "/talk?outcome=WITHDRAWN")),
                // Asked to keynote something he already had a ticket for. Saying yes is what turns
                // an attendee into a speaker, so the move has to be here.
                arguments("going and then invited", AttendanceCommitment.GOING,
                          SpeakingStatus.INVITED, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/confirm?basis=SPEAKING_INVITED", BASE + "/decline")),
                // He is already going, so buying a ticket "after all" is not a move here.
                arguments("going after a rejection", AttendanceCommitment.GOING,
                          SpeakingStatus.REJECTED, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/decline")),
                arguments("going after pulling a talk", AttendanceCommitment.GOING,
                          SpeakingStatus.WITHDRAWN, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/talk?outcome=SUBMITTED", BASE + "/decline")),
                // The one talk-side move left once a talk is in the program; it says nothing about
                // attending, which is why Decline is still beside it.
                arguments("going with a talk accepted", AttendanceCommitment.GOING,
                          SpeakingStatus.ACCEPTED, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of(BASE + "/talk?outcome=WITHDRAWN", BASE + "/decline")),
                arguments("dropped", AttendanceCommitment.NOT_GOING,
                          SpeakingStatus.NOT_SPEAKING, ConferenceFormat.CALL_FOR_PAPERS,
                          List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyState")
    void eachStateOffersExactlyItsLegalMoves(String state, AttendanceCommitment commitment,
                                             SpeakingStatus speakingStatus, ConferenceFormat format,
                                             List<String> expectedHrefs) {
        List<DomContent> links = ConferenceActions.links(
                CONFERENCE_ID, commitment, speakingStatus, format);

        assertThat(links)
                .as("moves offered on a '%s' conference", state)
                .extracting(ConferenceActionsTest::hrefOf)
                .containsExactlyElementsOf(expectedHrefs);
    }

    /**
     * Every combination of the three enums, built from their own {@code values()} so a new constant
     * on any of them becomes new cases with nobody adding one — the {@code CalendarDayMenuTest}
     * mechanism, and for the same reason: the table above is hand-written, and a hand-written table
     * is exactly what a change forgets to extend.
     * <p>
     * A few of these are unreachable in the domain ({@code WATCHING} with an accepted talk, since
     * accepting commits attendance on its own). They are included anyway: the invariants below hold
     * of them too, and leaving them out would mean deciding reachability in a test that has no way
     * to check it.
     */
    static List<Arguments> everyCombination() {
        List<Arguments> combinations = new ArrayList<>();
        for (AttendanceCommitment commitment : AttendanceCommitment.values()) {
            for (SpeakingStatus status : SpeakingStatus.values()) {
                for (ConferenceFormat format : ConferenceFormat.values()) {
                    combinations.add(arguments(
                            commitment + " / " + status + " / " + format,
                            commitment, status, format));
                }
            }
        }
        return List.copyOf(combinations);
    }

    /**
     * The budget the whole arrangement rests on: three fit the dashboard's fixed 240px column on
     * one line, and up to three is where CLAUDE.md says links beat a menu. A fourth breaks both.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCombination")
    void noStateOffersMoreThanThreeMoves(String state, AttendanceCommitment commitment,
                                         SpeakingStatus speakingStatus, ConferenceFormat format) {
        assertThat(ConferenceActions.links(CONFERENCE_ID, commitment, speakingStatus, format))
                .as("moves offered on a '%s' conference", state)
                .hasSizeLessThanOrEqualTo(3);
    }

    /**
     * <strong>No live conference is a dead end.</strong> Only a dropped one offers nothing, and it
     * offers nothing deliberately. Anything else with an empty action set is a conference Ted can
     * see and cannot move, which is how the {@code GOING} + {@code SUBMITTED} bug read on the page:
     * a Talk panel saying the organizers were holding his talk, beside a band whose only move was
     * to abandon the whole conference.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCombination")
    void onlyADroppedConferenceOffersNoMoveAtAll(String state, AttendanceCommitment commitment,
                                                 SpeakingStatus speakingStatus,
                                                 ConferenceFormat format) {
        List<DomContent> links = ConferenceActions.links(
                CONFERENCE_ID, commitment, speakingStatus, format);

        if (commitment == AttendanceCommitment.NOT_GOING) {
            assertThat(links).as("a dropped conference has no live action").isEmpty();
        } else {
            assertThat(links).as("moves offered on a '%s' conference", state).isNotEmpty();
        }
    }

    /**
     * <strong>The talk axis does not close when the attendance axis commits.</strong> Both moves
     * independently — {@code ConferenceProgress.submitted()} and {@code invited()} carry the
     * commitment through — so wherever the organizers are holding something, this must offer the
     * way to record what they say, whether or not Ted already has a ticket.
     * <p>
     * This is the invariant the 2026-09-06 bug broke, and it is stated over the whole cross-product
     * rather than as another row in the table above, so the next state that couples the two axes
     * cannot be added without it being checked.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCombination")
    void aTalkTheOrganizersHoldCanBeResolvedWhateverTheCommitment(String state,
                                                                  AttendanceCommitment commitment,
                                                                  SpeakingStatus speakingStatus,
                                                                  ConferenceFormat format) {
        if (commitment == AttendanceCommitment.NOT_GOING) {
            return;
        }
        List<String> hrefs = ConferenceActions.links(CONFERENCE_ID, commitment, speakingStatus, format)
                                              .stream()
                                              .map(ConferenceActionsTest::hrefOf)
                                              .toList();

        switch (speakingStatus) {
            case SUBMITTED -> assertThat(hrefs)
                    .as("a submitted talk on a '%s' conference has all three answers", state)
                    .contains(BASE + "/talk?outcome=ACCEPTED",
                              BASE + "/talk?outcome=REJECTED",
                              BASE + "/talk?outcome=WITHDRAWN");
            case INVITED -> assertThat(hrefs)
                    .as("an open invitation on a '%s' conference can be taken up", state)
                    .contains(BASE + "/confirm?basis=SPEAKING_INVITED");
            // Nothing is outstanding with the organizers in the other states.
            case NOT_SPEAKING, ACCEPTED, REJECTED, WITHDRAWN -> {
            }
        }
    }

    /**
     * <strong>The split is by the command a move posts, not by what it is about.</strong> The detail
     * page puts each move beside the state it changes, so "Invitation Accepted" belongs to
     * attendance despite being talk-shaped: it writes {@code ConferenceAttendanceConfirmed}, and it
     * stays beside the Decline that answers the same offer the other way.
     * <p>
     * Asserted over the whole cross-product on the path itself rather than on a hand-listed table,
     * so the classification cannot drift for a state nobody thought to write down.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCombination")
    void everyMoveIsFiledUnderTheAxisItsCommandChanges(String state, AttendanceCommitment commitment,
                                                       SpeakingStatus speakingStatus,
                                                       ConferenceFormat format) {
        ConferenceActions.Moves moves = ConferenceActions.movesByAxis(
                CONFERENCE_ID, commitment, speakingStatus, format);

        assertThat(moves.talk())
                .as("talk-axis moves on a '%s' conference", state)
                .allSatisfy(move -> assertThat(hrefOf(move)).startsWith(BASE + "/talk?"));
        assertThat(moves.attendance())
                .as("attendance-axis moves on a '%s' conference", state)
                .allSatisfy(move -> assertThat(hrefOf(move))
                        .matches(href -> href.startsWith(BASE + "/confirm?")
                                         || href.equals(BASE + "/decline"),
                                 "a /confirm or /decline path"));
    }

    /**
     * The split is a view of the same decision, never a second one. {@code links} is composed from
     * {@code movesByAxis}, and this is what keeps that true: the dashboard's flat row and the detail
     * page's three tracks offer exactly the same moves, in the same order — talk first, then what
     * Ted decides.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCombination")
    void theFlatListIsExactlyTheTwoAxesInOrder(String state, AttendanceCommitment commitment,
                                               SpeakingStatus speakingStatus,
                                               ConferenceFormat format) {
        ConferenceActions.Moves moves = ConferenceActions.movesByAxis(
                CONFERENCE_ID, commitment, speakingStatus, format);
        List<String> expected = Stream.concat(moves.talk().stream(), moves.attendance().stream())
                                      .map(ConferenceActionsTest::hrefOf)
                                      .toList();

        assertThat(ConferenceActions.links(CONFERENCE_ID, commitment, speakingStatus, format))
                .as("the flat list on a '%s' conference", state)
                .extracting(ConferenceActionsTest::hrefOf)
                .containsExactlyElementsOf(expected);
    }

    /**
     * Decline is the one move that reads as a warning rather than as a record, and it wears its own
     * class on both surfaces. The rest are ordinary accent links.
     */
    @Test
    void decliningIsTheOnlyMoveStyledAsARetreat() {
        List<DomContent> links = ConferenceActions.links(
                CONFERENCE_ID, AttendanceCommitment.WATCHING,
                SpeakingStatus.NOT_SPEAKING, ConferenceFormat.CALL_FOR_PAPERS);

        assertThat(links)
                .extracting(DomContent::render)
                .filteredOn(markup -> markup.contains("class=\"conf-decline\""))
                .singleElement()
                .asString()
                .contains("href=\"" + BASE + "/decline\"");
    }

    private static String hrefOf(DomContent link) {
        String markup = link.render();
        int start = markup.indexOf("href=\"") + "href=\"".length();
        return markup.substring(start, markup.indexOf('"', start));
    }
}
