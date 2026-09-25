package dev.ted.jittertravel.architecture;

import dev.ted.jittertravel.application.CommandExecutor;
import dev.ted.jittertravel.infrastructure.EventStore;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.anyElementThat;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noConstructors;

/**
 * Architecture guard: no class in the {@code application} package may take an {@link EventStore} as
 * a constructor dependency. Appending events goes through {@code CommandExecutor}, which persists
 * the command row <em>before</em> the events — {@code EventStore.append} requires the command to
 * already exist in {@code command_log} (foreign key), so bypassing the executor causes FK
 * violations and partial writes where some events land and others don't. The executor also refuses
 * to write at all in read-only mode.
 * <p>
 * {@code ConferencePlanning} was the last service injecting {@code EventStore} directly; this test
 * exists so that never comes back.
 */
class ApplicationServicesUseCommandExecutorTest {

    @Test
    void noApplicationClassTakesAnEventStoreConstructorDependency() {
        noConstructors()
                .that().areDeclaredInClassesThat().resideInAPackage("dev.ted.jittertravel.application..")
                // the one authorized holder — it *is* the enforced route
                .and().areDeclaredInClassesThat().doNotBelongToAnyOf(CommandExecutor.class)
                .should().haveRawParameterTypes(anyElementThat(equivalentTo(EventStore.class)))
                .because("application services must append events via CommandExecutor, never "
                         + "EventStore directly")
                .check(ProjectClasses.PRODUCTION);
    }
}
