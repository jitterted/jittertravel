package dev.ted.jittertravel.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import org.junit.jupiter.api.Test;
import org.springframework.validation.Errors;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture guard: a test never contains a copy of production code — see CLAUDE.md, "A test never
 * contains a copy of production code".
 * <p>
 * The shape this catches is a controller's {@code catch} block re-typed in a test, deciding for
 * itself which input an error lands on. Production code writes a {@code BindingResult} and a test
 * only reads one, so a call to {@code rejectValue(...)} or {@code reject(...)} on any
 * {@link Errors} — a {@code BindingResult} is one — from a test class is that copy, whatever the
 * variable holding it is called. Which input a refusal lands on is a plain controller test with a
 * stub service programmed to throw (see CLAUDE.md, "Testing a form's validation").
 * <p>
 * Five {@code *ControllerValidationTest} classes carried the copy when this arrived (2026-09-22);
 * all five were replaced, so there is no exemption list.
 * <p>
 * <strong>This is narrower than the rule.</strong> It cannot see a service method re-typed in an
 * anonymous subclass, or a copy of anything else; it is the floor, not the rule.
 */
class TestsNeverWriteToABindingResultTest {

    private static final Set<String> WRITES = Set.of("rejectValue", "reject");

    @Test
    void noTestWritesToABindingResult() {
        noClasses()
                .should().accessTargetWhere(writeToABindingResult())
                .because("a test that writes to a BindingResult re-types a controller's catch "
                         + "block and decides for itself which field an error lands on. Call the "
                         + "real controller with a stub service programmed to throw instead")
                .check(ProjectClasses.TESTS);
    }

    // An access rather than a call, so a method reference to rejectValue is caught too.
    private static DescribedPredicate<JavaAccess<?>> writeToABindingResult() {
        return DescribedPredicate.describe(
                "write to a BindingResult",
                access -> access.getTargetOwner().isAssignableTo(Errors.class)
                          && WRITES.contains(access.getTarget().getName()));
    }
}
