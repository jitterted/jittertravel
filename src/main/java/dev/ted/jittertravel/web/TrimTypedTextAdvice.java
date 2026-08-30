package dev.ted.jittertravel.web;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Every {@code String} that arrives from a form or a query parameter is trimmed, on every
 * controller in the app.
 *
 * <p><strong>Why this exists.</strong> An iPhone keyboard puts a space on the end of a word when
 * the space bar commits an autocorrect suggestion, and {@code <input type="text">} submits its
 * value verbatim — HTML only strips whitespace for {@code type="email"} and {@code type="url"}. In
 * August 2026 that wrote {@code "Hamburg "} into a private event's city and {@code
 * /schedule-problems} reported a journey from Hamburg to Hamburg, because the space is invisible in
 * the markup but not to a comparison. See the CLAUDE.md section "Typed text is normalized where it
 * lands, not where it is typed".
 *
 * <p><strong>Why here and not on each field.</strong> The fields damaged that day were repaired in
 * {@code Address} and {@code TrainStationAddress}, which was the right place for them: dirt was
 * already stored, and normalizing in a record's compact constructor repairs it on every read
 * without rewriting a row. This advice answers the other half of the question — the free text
 * nothing compares *yet*. A hotel name, a venue name, a talk title: none of them decides anything
 * today, so per-field normalization would be ceremony in a dozen event records, and it would still
 * leave the next new form uncovered. Trimming at the boundary covers every field on every form,
 * including the ones nobody has written, with nothing to remember.
 *
 * <p><strong>The split to keep in mind:</strong> the boundary trims everything <em>typed</em>; a
 * record normalizes everything <em>compared</em>. Both are needed, because a restore binds stored
 * JSON through Jackson and never passes this way.
 *
 * <p>{@code StringTrimmerEditor(false)} trims without converting {@code ""} to null, so the house
 * rule that a domain string is never null survives untouched.
 *
 * <p><strong>It reaches {@code @RequestParam} too</strong>, which includes the typed confirmation
 * words on {@code /admin/database} and {@code /admin/migrate-legacy-events}: {@code " DELETE "} now
 * opens the Danger Zone. Agreed deliberately (Ted, 2026-08-30) — the gate is there to prove intent,
 * and refusing a correctly typed word because a phone appended a space is the gate failing at its
 * own job.
 *
 * <p>Login is unaffected: Spring Security reads its parameters straight off the request rather than
 * through a {@link WebDataBinder}.
 *
 * <p>Nothing at a controller mentions this — the cost of an advice, and the same cost {@code
 * ProblemContextAdvice} carries. {@code TrimmedTypedTextConventionTest} is what keeps it from being
 * invisible: it posts padded text through two real controllers and fails if either receives it
 * untrimmed.
 */
@ControllerAdvice
public class TrimTypedTextAdvice {

    @InitBinder
    void trimEveryBoundString(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }
}
