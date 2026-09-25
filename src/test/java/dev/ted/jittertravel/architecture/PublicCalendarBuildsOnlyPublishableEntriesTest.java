package dev.ted.jittertravel.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.ted.jittertravel.application.CalendarEntry;
import dev.ted.jittertravel.application.EntryDetails;
import dev.ted.jittertravel.application.PublicCalendarProjector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture guard on the one page anonymous visitors can see.
 * <p>
 * {@code PublicCalendarProjector} builds every entry through its private {@code entry(...)}
 * helper, whose last argument is an {@code EntryDetails.Publishable} — the details records that
 * have no slot for an edit path, a cancel path, a maps URL or a hotel name. That is what makes the
 * public calendar an allow-list the compiler checks.
 * <p>
 * <strong>But only while the helper is the only way in.</strong> {@code CalendarEntry}'s canonical
 * constructor is public and accepts any {@code EntryDetails}, so a branch that calls
 * {@code new CalendarEntry(...)} directly — the obvious thing to do when copying a branch across
 * from an owner projector — would compile happily with {@code new EntryDetails.Gathering(infoUrl,
 * speaking, editPath)} in it, and publish Ted's edit path to the anonymous calendar. Nothing else
 * would fail: the runtime invariant in {@code PublicCalendarProjectorTest} only sees the kinds its
 * fixture feeds it.
 * <p>
 * So this test is what the javadoc's "compiler check rather than a convention" actually rests on,
 * and it exists because the guarantee is worth more than the constructor call it forbids. It reads
 * the compiled calls: a construction is allowed only when the code unit making it is a method named
 * {@code entry}. A construction inside a lambda anywhere else compiles to a synthetic
 * {@code lambda$…} method and is caught as well.
 */
class PublicCalendarBuildsOnlyPublishableEntriesTest {

    private static final String ENTRY_HELPER = "entry";

    @Test
    void thePublicProjectorNeverCallsTheCalendarEntryConstructorOutsideItsPublishableHelper() {
        noClasses()
                .that().belongToAnyOf(PublicCalendarProjector.class)
                .should().accessTargetWhere(constructACalendarEntryOutsideTheHelper())
                .because("PublicCalendarProjector must build entries through its entry(...) helper, "
                         + "whose last argument is an EntryDetails.Publishable. Calling the "
                         + "CalendarEntry constructor directly accepts any EntryDetails — including "
                         + "the owner records that carry edit paths and maps URLs — and would publish "
                         + "them to the anonymous calendar. See CLAUDE.md, \"Redaction: anonymous "
                         + "viewers are a first-class threat model\", rule 1")
                .check(ProjectClasses.PRODUCTION);
    }

    /**
     * The helper's signature is the compiler check, so the rule above guards nothing if it is
     * renamed or loses its {@code Publishable} parameter. An empty match fails here too: ArchUnit
     * refuses a rule whose {@code that()} selects nothing.
     */
    @Test
    void thePublishableOnlyHelpersStillExist() {
        methods()
                .that().areDeclaredIn(PublicCalendarProjector.class)
                .and().haveName(ENTRY_HELPER)
                .should(takeAPublishableLast())
                .check(ProjectClasses.PRODUCTION);
    }

    // An access rather than a call: .map(CalendarEntry::new) builds an entry just as surely as
    // new CalendarEntry(...), and is the likelier shape in a stream-heavy projector.
    private static DescribedPredicate<JavaAccess<?>> constructACalendarEntryOutsideTheHelper() {
        return DescribedPredicate.describe(
                "construct a CalendarEntry outside entry(...)",
                access -> access.getTarget().getName().equals(JavaConstructor.CONSTRUCTOR_NAME)
                          && access.getTargetOwner().isEquivalentTo(CalendarEntry.class)
                          && !access.getOrigin().getName().equals(ENTRY_HELPER));
    }

    private static ArchCondition<JavaMethod> takeAPublishableLast() {
        return new ArchCondition<>("take an EntryDetails.Publishable as the last argument") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                List<JavaClass> parameters = method.getRawParameterTypes();
                boolean publishableLast = !parameters.isEmpty()
                                          && parameters.getLast().isEquivalentTo(EntryDetails.Publishable.class);
                events.add(new SimpleConditionEvent(
                        method, publishableLast,
                        method.getFullName() + " does not take an EntryDetails.Publishable last"));
            }
        };
    }
}
