package dev.ted.jittertravel.web;

import org.springframework.validation.BindingResult;

/**
 * What every rejected form says at the top of itself: how many inputs are marked below.
 *
 * <p>This exists as a base class rather than a copy in each form's own error mapper because
 * <strong>the wording is the thing that must not drift</strong>. "2 problems to fix below." on the
 * train forms and something a word different on the hotel forms would be two copies of one
 * sentence, which is the same argument the problem-context banner makes for reading
 * {@code ProblemBand}'s own words rather than restating them. A subclass adds the mapping from
 * <em>this</em> form's domain problems onto <em>this</em> form's input names, which is genuinely
 * per-form; the count is not.
 */
abstract class FormErrors {

    protected final BindingResult bindingResult;

    protected FormErrors(BindingResult bindingResult) {
        this.bindingResult = bindingResult;
    }

    /**
     * The one thing said at the top of the form: how many inputs are marked below. It is a count
     * and not a description on purpose — a reader who can see the marked field does not need it,
     * and a reader who cannot needs to know the submit failed and that there is more than one.
     *
     * <p>Counts field errors only. A whole-form failure (the booking vanished between GET and
     * POST) is already its own global message and is not a problem to fix below — it also means
     * this adds nothing when there are no field errors, so a form that fails wholesale is not
     * given a count of zero.
     */
    void summarize() {
        int count = bindingResult.getFieldErrorCount();
        if (count == 0) {
            return;
        }
        bindingResult.reject("problemCount",
                count == 1 ? "1 problem to fix below." : count + " problems to fix below.");
    }
}
