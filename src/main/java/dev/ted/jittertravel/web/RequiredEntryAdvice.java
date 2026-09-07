package dev.ted.jittertravel.web;

import org.springframework.core.ResolvableType;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Every date and time bound from a form must be filled in, on every controller in the app. One left
 * blank is a field error under its own input, not an exception.
 *
 * <p><strong>Why this exists.</strong> An empty {@code <input type="datetime-local">} submits an
 * empty string, and Spring binds that to {@code null} rather than to a binding error
 * ({@code FormattingConversionService.ParserConverter} returns null for blank text). The handlers
 * then call {@code ZonedTimestamp.fromLocal(null, zone)} and the page is a 500. No date input in
 * this app carries {@code required}, and per CLAUDE.md none should: the browser bubble blocks the
 * submit, the server never hears about it, and the page comes back unchanged, which reads as "my
 * fix did nothing".
 *
 * <p><strong>Why an advice and not a check in each handler.</strong> Eleven forms carry twenty-odd
 * of these fields, and the next form will carry more. This is the shape {@link TrimTypedTextAdvice}
 * already has, for the same reason: the rule is about everything typed, not about booking a train,
 * so it is stated once and covers the forms nobody has written yet.
 *
 * <p><strong>Required by default</strong>, with {@link OptionalEntry} to opt out — see that
 * annotation for why that direction and not the other.
 *
 * <p>Spring does the work: {@code setRequiredFields} rejects the empty value <em>and</em> drops it,
 * so nothing null reaches the command object. What is left to the controller is not calling its
 * service when binding already failed, which is a {@code bindingResult.hasErrors()} guard at the
 * top of each POST — without it the handler still runs with the nulls this was meant to stop.
 *
 * <p>Nothing at a controller mentions this. {@code RequiredEntryConventionTest} is what keeps it
 * from being invisible, the same arrangement {@code TrimmedTypedTextConventionTest} has.
 */
@ControllerAdvice
public class RequiredEntryAdvice {

    private static final Set<Class<?>> ENTRY_TYPES =
            Set.of(LocalDate.class, LocalTime.class, LocalDateTime.class);

    /** Terse, because it renders as a label under an input whose own label names the field. */
    private static final String MESSAGE = "Required";

    @InitBinder
    void requireEveryBoundDateAndTime(WebDataBinder binder) {
        Class<?> formBean = formBeanType(binder);
        if (formBean == null) {
            return;
        }
        List<String> required = requiredEntryFields(formBean);
        if (required.isEmpty()) {
            return;
        }
        binder.setRequiredFields(required.toArray(String[]::new));
        binder.setBindingErrorProcessor(new DefaultBindingErrorProcessor() {
            @Override
            public void processMissingFieldError(String missingField, BindingResult bindingResult) {
                bindingResult.rejectValue(missingField, MISSING_FIELD_ERROR_CODE, MESSAGE);
            }
        });
    }

    /**
     * The form bean's type. {@code getTarget()} is null here: Spring builds the binder before it
     * constructs the attribute, so the type is the only thing available at {@code @InitBinder}
     * time. Null for a {@code @RequestParam} binder, which has no form bean at all.
     */
    private Class<?> formBeanType(WebDataBinder binder) {
        if (binder.getTarget() != null) {
            return binder.getTarget().getClass();
        }
        ResolvableType targetType = binder.getTargetType();
        return targetType == null ? null : targetType.resolve();
    }

    /** Declared fields only: a form bean is a flat bag of inputs and has no superclass here. */
    private List<String> requiredEntryFields(Class<?> formBean) {
        return Arrays.stream(formBean.getDeclaredFields())
                     .filter(field -> ENTRY_TYPES.contains(field.getType()))
                     .filter(field -> !field.isAnnotationPresent(OptionalEntry.class))
                     .map(Field::getName)
                     .toList();
    }
}
