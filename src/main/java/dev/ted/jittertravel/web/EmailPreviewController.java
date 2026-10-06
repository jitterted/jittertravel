package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ConferenceNews;
import dev.ted.jittertravel.application.ViewerTodayZone;
import dev.ted.jittertravel.domain.NotifiedFact;
import dev.ted.jittertravel.infrastructure.BrevoEmailClient;
import dev.ted.jittertravel.infrastructure.EmailTemplates;
import dev.ted.jittertravel.infrastructure.FamilyMessage;
import dev.ted.jittertravel.infrastructure.FamilyNotificationMessages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * The email preview (Ted, 2026-10-05; mockups https://claude.ai/artifact/C7mjK5bUm6yY5nDVX1JgWu and,
 * for the two groups, https://claude.ai/artifact/HSWE6X49TxLtN26B7hKspZ): every family email exactly
 * as it would be sent, from the text files and fixed samples, in two groups. Each group has its own
 * button that sends that group's emails to Ted himself, and its own remembered result.
 * <p>
 * <strong>It can only ever send to {@code TED_REPLY_EMAIL}.</strong> The address is read here and
 * handed to {@link BrevoEmailClient#sendTo}; nothing on this page can name the family address, so a
 * preview cannot reach family by a mistake in configuration or on the form. Like the test email it
 * writes no event and no command row.
 * <p>
 * The words are not here: they are in {@code src/main/resources/email/}, and a change to them needs
 * Ted's approval before it is committed.
 */
@Controller
@RequestMapping("/admin/email-preview")
public class EmailPreviewController {

    private final FamilyNotificationMessages messages;
    private final BrevoEmailClient brevo;
    private final EmailPreviewMemory memory;
    private final Clock clock;
    private final ViewerTodayZone viewerZone;
    private final String baseUrl;
    private final EmailPreviewSamples samples = new EmailPreviewSamples();
    private final EmailPreviewPanels panels = new EmailPreviewPanels();
    private final SendFailureText failureText = new SendFailureText();
    private final EmailTemplates templates = new EmailTemplates();

    public EmailPreviewController(FamilyNotificationMessages messages, BrevoEmailClient brevo,
                                  EmailPreviewMemory memory, Clock clock,
                                  @Value("${jittertravel.base-url:}") String baseUrl,
                                  @Value("${jittertravel.today.fallback-zone:America/Los_Angeles}") String fallbackZone) {
        this.messages = messages;
        this.brevo = brevo;
        this.memory = memory;
        this.clock = clock;
        this.baseUrl = baseUrl == null ? "" : baseUrl.strip();
        this.viewerZone = new ViewerTodayZone(ZoneId.of(fallbackZone));
    }

    @GetMapping("")
    public String page(Model model, @CookieValue(name = ViewerTodayZone.COOKIE_NAME, required = false) String zoneCookie) {
        return render(model, zoneCookie, Map.of());
    }

    /** Sends the flight emails to Ted. */
    @PostMapping("/send")
    public String sendFlights(Model model, RedirectAttributes redirectAttributes,
                              @CookieValue(name = ViewerTodayZone.COOKIE_NAME, required = false) String zoneCookie) {
        return send(EmailGroup.FLIGHTS, model, redirectAttributes, zoneCookie);
    }

    /** Sends the conference emails to Ted. */
    @PostMapping("/send-conferences")
    public String sendConferences(Model model, RedirectAttributes redirectAttributes,
                                  @CookieValue(name = ViewerTodayZone.COOKIE_NAME, required = false) String zoneCookie) {
        return send(EmailGroup.CONFERENCES, model, redirectAttributes, zoneCookie);
    }

    /**
     * Sends one group's emails to Ted, one after another, remembering how far it got. A success
     * redirects, so a reload cannot send again; a failure re-renders this page, so the reason is on a
     * page that can show it.
     */
    private String send(EmailGroup group, Model model, RedirectAttributes redirectAttributes, String zoneCookie) {
        String address = brevo.replyTo();
        if (!brevo.hasKey() || address == null || address.isBlank()) {
            return render(model, zoneCookie, Map.of());
        }
        List<FamilyMessage> emails = emailsToSend(group);
        int sent = 0;
        try {
            for (FamilyMessage message : emails) {
                brevo.sendTo(address, message);
                sent++;
            }
        } catch (RuntimeException failed) {
            memory.remember(group, new EmailPreviewMemory.Result(Instant.now(clock), sent, emails.size(), address,
                    failureText.of(failed)));
            return render(model, zoneCookie, Map.of(group, true));
        }
        memory.remember(group, new EmailPreviewMemory.Result(Instant.now(clock), sent, emails.size(), address, ""));
        redirectAttributes.addFlashAttribute(group.justSentKey(), true);
        return "redirect:/admin/email-preview";
    }

    /** @param justSent the groups whose result is highlighted: the one just sent, or just failed */
    private String render(Model model, String zoneCookie, Map<EmailGroup, Boolean> justSent) {
        ZoneId zone = viewerZone.resolve(zoneCookie);
        model.addAttribute("groups", List.of(group(EmailGroup.FLIGHTS, model, zone, justSent),
                group(EmailGroup.CONFERENCES, model, zone, justSent)));
        model.addAttribute("testEmail", preview("Test email", "test-email.txt",
                templates.render("test-email", Map.of())));
        model.addAttribute("linkNote", baseUrl.isEmpty()
                ? "No JITTERTRAVEL_BASE_URL is set, so these emails have no calendar link."
                : "The calendar link uses the sample's date and your JITTERTRAVEL_BASE_URL (" + baseUrl + ").");
        return "admin-email-preview";
    }

    private PreviewGroup group(EmailGroup group, Model model, ZoneId zone, Map<EmailGroup, Boolean> failedNow) {
        boolean highlight = failedNow.getOrDefault(group, false)
                || Boolean.TRUE.equals(model.asMap().get(group.justSentKey()));
        PreviewPanel panel = panels.panel(group, emailsToSend(group).size(), brevo.hasKey(), brevo.replyTo(),
                memory.last(group), zone, highlight);
        return new PreviewGroup(group.title(), group.sendPath(), panel, previewEmails(group));
    }

    /** What a group's button sends: the same messages the page shows. */
    private List<FamilyMessage> emailsToSend(EmailGroup group) {
        return switch (group) {
            case FLIGHTS -> List.of(
                    messages.messageFor(NotifiedFact.FLIGHT_BOOKED, samples.singleFlight()),
                    messages.messageFor(NotifiedFact.ITINERARY_BOOKED, samples.trip()),
                    messages.messageFor(NotifiedFact.ITINERARY_CANCELLED, samples.trip()));
            case CONFERENCES -> {
                ConferenceNews conference = samples.conference();
                yield List.of(
                        messages.conferenceGoing(conference),
                        messages.conferenceNotGoing(conference, ConferenceNews.Exit.DECLINED),
                        messages.conferenceNotGoing(conference, ConferenceNews.Exit.CANCELLED),
                        messages.conferenceNotGoing(conference, ConferenceNews.Exit.REJECTED));
            }
        };
    }

    /** The cards under a group's panel, in the order the button sends them. */
    private List<PreviewEmail> previewEmails(EmailGroup group) {
        List<FamilyMessage> sent = emailsToSend(group);
        return switch (group) {
            case FLIGHTS -> List.of(
                    preview("Flight booked", "flight-booked.txt", sent.get(0)),
                    preview("Trip booked", "trip-booked.txt", sent.get(1)),
                    preview("Trip cancelled", "trip-cancelled.txt", sent.get(2)));
            case CONFERENCES -> List.of(
                    preview("Conference going", "conference-going.txt", sent.get(0)),
                    preview("Conference declined", "conference-not-going.txt", sent.get(1)),
                    preview("Conference cancelled", "conference-not-going.txt", sent.get(2)),
                    preview("Conference talk rejected", "conference-not-going.txt", sent.get(3)));
        };
    }

    private PreviewEmail preview(String label, String file, FamilyMessage message) {
        return new PreviewEmail(label, "email/" + file, message.subject(), message.textContent());
    }
}
