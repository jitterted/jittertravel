package dev.ted.jittertravel.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.ted.jittertravel.infrastructure.EventSourcingConfig;
import dev.ted.jittertravel.infrastructure.EventStreamConsumer;
import dev.ted.jittertravel.infrastructure.ProjectorBootstrapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard: a projector that is built but never registered is invisible.
 * <p>
 * {@code ProjectorBootstrapper.register} does two things a bare constructor does not — subscribe the
 * projector to the {@link dev.ted.jittertravel.infrastructure.EventStore} for future appends, and
 * replay the existing stream so it is caught up before it becomes a bean. Drop the call and the
 * projector still wires, still injects, still answers every query: with an empty read model, for
 * ever. Nothing throws.
 * <p>
 * That is untestable from the outside in the usual way, because every test that renders a page
 * supplies its projectors as mocks or stubs — so the registration line is covered by nothing at all.
 * It matters most for {@code PublicCalendarProjector}, which since the S2 refactor is the sole
 * source of the anonymous {@code /calendar}: unregistered, every visitor gets a permanently empty
 * calendar and the whole suite stays green.
 * <p>
 * The set of beans checked is derived from the compiled {@link EventSourcingConfig}, not listed
 * here, so a new projector bean is covered the day it is written — there is no fixture to forget.
 * Whether each one calls {@code register} is read from its compiled calls, so renaming the
 * {@code bootstrapper} parameter changes nothing.
 */
class EveryProjectorBeanIsRegisteredTest {

    @Test
    void everyBeanThatConsumesTheEventStreamIsRegisteredWithTheBootstrapper() {
        methods()
                .that(areProjectorBeans())
                .should(callProjectorBootstrapperRegister())
                .because("an unregistered projector is neither subscribed to future events nor "
                         + "replayed over past ones, so it answers every query with an empty read "
                         + "model and never fails — for PublicCalendarProjector that is a blank "
                         + "/calendar for every anonymous visitor")
                .check(ProjectClasses.PRODUCTION);
    }

    /**
     * The guard is only worth having while it is actually looking at something: a moved config
     * class, or a bean whose declared return type stops being an {@code EventStreamConsumer}, would
     * leave it passing over fewer beans than exist. Pin the count and the one that matters most.
     */
    @Test
    void theGuardIsLookingAtRealProjectorBeans() {
        List<String> returnTypes = ProjectClasses.PRODUCTION.get(EventSourcingConfig.class)
                                                            .getMethods()
                                                            .stream()
                                                            .filter(areProjectorBeans())
                                                            .map(method -> method.getRawReturnType().getSimpleName())
                                                            .toList();

        assertThat(returnTypes)
                .as("EventSourcingConfig must still declare the projector beans this guard checks")
                .hasSizeGreaterThanOrEqualTo(20)
                .contains("PublicCalendarProjector");
    }

    private static DescribedPredicate<JavaMethod> areProjectorBeans() {
        return DescribedPredicate.describe(
                "are @Bean methods in EventSourcingConfig returning an EventStreamConsumer",
                method -> method.getOwner().isEquivalentTo(EventSourcingConfig.class)
                          && method.isAnnotatedWith(Bean.class)
                          && method.getRawReturnType().isAssignableTo(EventStreamConsumer.class));
    }

    private static ArchCondition<JavaMethod> callProjectorBootstrapperRegister() {
        return new ArchCondition<>("call ProjectorBootstrapper.register(...)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean registers = method.getMethodCallsFromSelf()
                                          .stream()
                                          .anyMatch(call -> call.getTargetOwner().isEquivalentTo(ProjectorBootstrapper.class)
                                                            && call.getTarget().getName().equals("register"));
                events.add(new SimpleConditionEvent(
                        method, registers,
                        method.getFullName() + " never calls ProjectorBootstrapper.register(...)"));
            }
        };
    }
}
