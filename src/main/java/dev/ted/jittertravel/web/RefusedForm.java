package dev.ted.jittertravel.web;

import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

/**
 * Re-renders a refused creation form under a fresh entity id, keeping its errors.
 * <p>
 * <strong>Why the id changes:</strong> the entity id is the command id (H4), and a domain refusal
 * has already written a command row under it. A corrected resubmit wearing the same id collides
 * with that row on {@code command_log}'s primary key — the 500 on the iPhone of 2026-10-22. So the
 * attempt that was refused retires its id, and the corrected resubmit is a new command
 * ({@code docs/archived/ValidateBeforeTheWalPlan.md}).
 * <p>
 * <strong>Why it takes four lines and not one.</strong> The form-backing records are immutable, so
 * a fresh id means a new request object on the model. Two Spring details then conspire: the
 * model's {@code BindingAwareModelMap} <em>removes</em> the binding result the moment its attribute
 * is replaced by a different object, and {@code th:field} reads every value through that binding
 * result's target, never the model attribute. Left alone, the re-rendered page would show no
 * errors at all, and a hidden id read any other way breaks on the path where binding itself failed
 * and the model attribute is null. So the errors are carried onto a new binding result whose target
 * is the fresh request, and put back under the key Spring looks them up by: {@code th:field} then
 * renders the new id and every error, with the templates untouched.
 * <p>
 * The flight form is the one mutable request bean and sets its id in place instead.
 */
final class RefusedForm {

    private RefusedForm() {
    }

    static void reRenderUnderFreshId(Model model, String attributeName,
                                     Object requestUnderFreshId, BindingResult refused) {
        model.addAttribute(attributeName, requestUnderFreshId);
        BeanPropertyBindingResult carriedOver =
                new BeanPropertyBindingResult(requestUnderFreshId, attributeName);
        carriedOver.addAllErrors(refused);
        model.addAttribute(BindingResult.MODEL_KEY_PREFIX + attributeName, carriedOver);
    }
}
