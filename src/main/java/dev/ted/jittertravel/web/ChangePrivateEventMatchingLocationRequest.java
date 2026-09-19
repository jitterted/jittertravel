package dev.ted.jittertravel.web;

/**
 * Form-backing record for the "match location" page.
 * <p>
 * A <strong>record</strong>, despite being bound by Thymeleaf with {@code th:object}/{@code th:field}.
 * That combination was long believed to need a mutable bean with a no-arg constructor and setters.
 * It does not, and this was settled empirically on Spring Framework 7.0.8 / Thymeleaf 3.1.5 rather
 * than argued: constructor binding populates the record, {@code th:field} reads it back, a rejected
 * POST re-renders carrying every other submitted value, and an unparseable value keeps its rejected
 * text in the input. Both binder advices still fire — {@code TrimTypedTextAdvice}'s
 * {@code StringTrimmerEditor} trims components, and {@code RequiredEntryAdvice} still finds its date
 * fields, because its {@code getTargetType()} fallback covers the no-instance-yet case and
 * {@code @OptionalEntry} propagates to a record component's backing field. The only observable
 * difference from a mutable bean is the wording of Spring's default type-mismatch message, which no
 * form here displays.
 * <p>
 * <strong>The id is not here.</strong> Which evening is re-matched is path data, not form data —
 * nothing on the page submits it, and a hidden field holding it would let a submit re-target a
 * different evening. The controller reads it from the path and hands it to the application service
 * alongside this request, so the re-target protection is structural rather than remembered.
 * <p>
 * That the command log still names the evening is {@code CommandExecutor}'s doing, not this
 * record's: the logged payload is the {@link dev.ted.jittertravel.domain.DomainCommand}, which
 * carries the resolved id. Serializing a form bean instead is what shipped the 2026-09-18 defect
 * where a FAILED row named no target at all.
 * <p>
 * {@code locationForMatching} arrives already trimmed — {@code TrimTypedTextAdvice} registers a
 * {@code StringTrimmerEditor} for every bound String — so {@code " "} reaches the command as
 * {@code ""} and is refused as blank rather than becoming a place name made of whitespace.
 */
public record ChangePrivateEventMatchingLocationRequest(
        String locationForMatching
) {
}
