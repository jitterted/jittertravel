package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferenceProjector;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.ReadOnlyModeException;
import dev.ted.jittertravel.application.TalkTracking;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceHasNoCfp;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceNotFound;
import dev.ted.jittertravel.domain.NoTalkToDecide;
import dev.ted.jittertravel.domain.NoTalkToWithdraw;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.TalkAlreadyAccepted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Records where a talk stands with one conference, on its own page: GET renders the form, POST
 * performs it.
 * <p>
 * <strong>Two ways in, one page.</strong> Reached from the dashboard's per-row actions with the
 * move already chosen ({@code ?outcome=ACCEPTED}), so the second click is a confirmation rather
 * than a decision; and reached bare, as the catch-up page, offering every move that is legal from
 * where the conference stands now. That second use is what Ted asked for: entering a conference's
 * history after the fact is a different job from recording today's news, and it is the only place
 * an out-of-the-blue invitation can be recorded.
 * <p>
 * The choices offered are the state machine's, so an illegal move cannot be picked — but the
 * domain refuses it anyway, because a stale page in another tab can still post one. Those
 * refusals re-render <em>this</em> page with the reason, per the project rule that a failed submit
 * shows its error on the page hosting the form; {@code /conferences} is a j2html view that cannot
 * render a flash.
 */
@Controller
public class RecordTalkController {

    private static final Logger log = LoggerFactory.getLogger(RecordTalkController.class);

    private final TalkTracking applicationService;
    private final ConferenceProjector projector;
    private final Clock clock;

    public RecordTalkController(TalkTracking applicationService,
                                ConferenceProjector projector,
                                Clock clock) {
        this.applicationService = applicationService;
        this.projector = projector;
        this.clock = clock;
    }

    @GetMapping("/conferences/{conferenceId}/talk")
    public String recordTalkForm(@PathVariable("conferenceId") String conferenceIdString,
                                 @RequestParam(value = "outcome", required = false) String outcomeParam,
                                 Model model) {
        Optional<ConferenceView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            // Stale link for a conference that is already gone: the view-only list cannot render a
            // flash, so navigate there silently rather than attach a message that gets dropped.
            return "redirect:/conferences";
        }
        return renderForm(model, maybe.get(), parseOutcome(outcomeParam).orElse(null), null);
    }

    @PostMapping("/conferences/{conferenceId}/talk")
    public String recordTalk(@PathVariable("conferenceId") String conferenceIdString,
                             @RequestParam(value = "outcome", required = false) String outcomeParam,
                             Model model) {
        Optional<ConferenceView> maybe = lookup(conferenceIdString);
        if (maybe.isEmpty()) {
            return "redirect:/conferences";
        }
        ConferenceView conference = maybe.get();

        Optional<TalkOutcome> outcome = parseOutcome(outcomeParam);
        if (outcome.isEmpty()) {
            return renderForm(model, conference, null, "Choose what happened with the talk.");
        }

        try {
            // commandId and the timestamp — the nondeterministic inputs — are captured here at the
            // boundary. The timestamp is when Ted recorded this, never when the organizers decided:
            // the app cannot know that, and does not pretend to.
            applicationService.record(UUID.randomUUID(),
                    new RecordTalkRequest(conference.conferenceId().id(), outcome.get()),
                    Instant.now(clock));
        } catch (ConferenceNotFound e) {
            // Cancelled or declined in another tab between the lookup above and the write.
            return "redirect:/conferences";
        } catch (ConferenceHasNoCfp | NoTalkToDecide | NoTalkToWithdraw | TalkAlreadyAccepted e) {
            // A move that is not legal from where this conference stands — a stale page, or a
            // hand-edited parameter. The reason belongs on the page hosting the form.
            return renderForm(model, conference, outcome.get(), e.getMessage());
        } catch (ReadOnlyModeException e) {
            log.warn("Attempted to record a talk outcome while in read-only mode", e);
            return "redirect:/read-only";
        }

        return "redirect:/conferences";
    }

    private String renderForm(Model model, ConferenceView conference, TalkOutcome chosen, String error) {
        model.addAttribute("conference", conference);
        model.addAttribute("chosen", chosen);
        model.addAttribute("outcomes", legalOutcomes(conference));
        model.addAttribute("error", error);
        return "record-talk";
    }

    /**
     * The moves that are legal from where this conference stands — the same state machine the
     * dashboard's row actions read, asked here for the whole set rather than one row's worth.
     * <p>
     * It answers with what the <em>domain</em> would accept, which is deliberately wider than what
     * a dashboard row offers: the row shows the expected next step, while this page is also for
     * catching up, where the expected step is often not the one that happened.
     */
    private List<TalkOutcome> legalOutcomes(ConferenceView conference) {
        SpeakingStatus status = conference.speakingStatus();
        List<TalkOutcome> outcomes = new ArrayList<>();
        if (status != SpeakingStatus.ACCEPTED && conference.format() != ConferenceFormat.OPEN_SPACE) {
            outcomes.add(TalkOutcome.SUBMITTED);
        }
        if (status != SpeakingStatus.NOT_SPEAKING && status != SpeakingStatus.INVITED) {
            outcomes.add(TalkOutcome.ACCEPTED);
            outcomes.add(TalkOutcome.REJECTED);
        }
        if (status == SpeakingStatus.SUBMITTED || status == SpeakingStatus.ACCEPTED) {
            outcomes.add(TalkOutcome.WITHDRAWN);
        }
        // An invitation can arrive at any moment, for any format — organizers of an open-space
        // conference can still ask for a keynote.
        outcomes.add(TalkOutcome.INVITED);
        return List.copyOf(outcomes);
    }

    private Optional<TalkOutcome> parseOutcome(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TalkOutcome.valueOf(value.trim().toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException unknownValue) {
            return Optional.empty();
        }
    }

    private Optional<ConferenceView> lookup(String conferenceIdString) {
        try {
            return projector.findById(ConferenceId.of(UUID.fromString(conferenceIdString)));
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }
}
