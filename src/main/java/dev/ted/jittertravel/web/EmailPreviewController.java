package dev.ted.jittertravel.web;

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
 * The email preview (Ted, 2026-10-05; mockup https://claude.ai/artifact/C7mjK5bUm6yY5nDVX1JgWu):
 * every family email exactly as it would be sent, from the text files and fixed sample flights, and a
 * button that sends all three to Ted himself.
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

    private static final String JUST_SENT = "justSent";
    private static final int EMAILS_SENT = 3;

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
    public String page(Model model,
                       @CookieValue(name = ViewerTodayZone.COOKIE_NAME, required = false) String zoneCookie) {
        return render(model, zoneCookie, Boolean.TRUE.equals(model.asMap().get(JUST_SENT)));
    }

    /**
     * Sends the three sample emails to Ted, one after another, remembering how far it got. A success
     * redirects, so a reload cannot send again; a failure re-renders this page, so the reason is on a
     * page that can show it.
     */
    @PostMapping("/send")
    public String send(Model model, RedirectAttributes redirectAttributes,
                       @CookieValue(name = ViewerTodayZone.COOKIE_NAME, required = false) String zoneCookie) {
        String address = brevo.replyTo();
        if (!brevo.hasKey() || address == null || address.isBlank()) {
            return render(model, zoneCookie, false);
        }
        int sent = 0;
        try {
            for (FamilyMessage message : emailsToSend()) {
                brevo.sendTo(address, message);
                sent++;
            }
        } catch (RuntimeException failed) {
            memory.remember(new EmailPreviewMemory.Result(Instant.now(clock), sent, EMAILS_SENT, address,
                    failureText.of(failed)));
            return render(model, zoneCookie, true);
        }
        memory.remember(new EmailPreviewMemory.Result(Instant.now(clock), sent, EMAILS_SENT, address, ""));
        redirectAttributes.addFlashAttribute(JUST_SENT, true);
        return "redirect:/admin/email-preview";
    }

    private String render(Model model, String zoneCookie, boolean justSent) {
        model.addAttribute("panel", panels.panel(brevo.hasKey(), brevo.replyTo(), memory.last(),
                viewerZone.resolve(zoneCookie), justSent));
        model.addAttribute("emails", previewEmails());
        model.addAttribute("linkNote", baseUrl.isEmpty()
                ? "No JITTERTRAVEL_BASE_URL is set, so these emails have no calendar link."
                : "The calendar link uses the sample's date and your JITTERTRAVEL_BASE_URL (" + baseUrl + ").");
        return "admin-email-preview";
    }

    /** The three that are sent: a single flight, a booked trip, a cancelled trip. */
    private List<FamilyMessage> emailsToSend() {
        return List.of(
                messages.messageFor(NotifiedFact.FLIGHT_BOOKED, samples.singleFlight()),
                messages.messageFor(NotifiedFact.ITINERARY_BOOKED, samples.trip()),
                messages.messageFor(NotifiedFact.ITINERARY_CANCELLED, samples.trip()));
    }

    /** What the page shows: those three, then the test email, so every text is in one place. */
    private List<PreviewEmail> previewEmails() {
        List<FamilyMessage> three = emailsToSend();
        FamilyMessage test = templates.render("test-email", Map.of());
        return List.of(
                preview("Flight booked", "flight-booked.txt", three.get(0)),
                preview("Trip booked", "trip-booked.txt", three.get(1)),
                preview("Trip cancelled", "trip-cancelled.txt", three.get(2)),
                preview("Test email", "test-email.txt", test));
    }

    private PreviewEmail preview(String label, String file, FamilyMessage message) {
        return new PreviewEmail(label, "email/" + file, message.subject(), message.textContent());
    }
}
