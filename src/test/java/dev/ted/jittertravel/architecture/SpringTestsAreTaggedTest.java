package dev.ted.jittertravel.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture guard: every test that starts a Spring context carries {@code @Tag("spring")}.
 * <p>
 * PIT mutation testing runs the plain unit tests only (the {@code pitest-maven} config in
 * {@code pom.xml} excludes the {@code spring} group). A Spring slice re-run once per mutant is
 * slow for no gain, and a full context boots a Testcontainers database each time. Nothing else
 * marks a Spring test as one: {@code @WebMvcTest} carries no JUnit tag, and names do not settle it
 * ({@code PlanConferenceControllerTest} is a plain unit test, {@code CancelHotelControllerTest} a
 * slice). So an untagged Spring test does not fail anything. It just makes every PIT run slower,
 * and this test is what catches it.
 * <p>
 * A Spring test is a class meta-annotated with {@code @ExtendWith(SpringExtension.class)}. That
 * is what every Spring test annotation is built on ({@code @SpringBootTest}, {@code @WebMvcTest},
 * {@code @JdbcTest}, {@code @SpringJUnitConfig}), so a slice added later, or a composed annotation
 * of our own, is covered without editing this test. The tag goes on the class itself, even where
 * it would be inherited, so the reader sees it where the test is.
 */
class SpringTestsAreTaggedTest {

    private static final String SPRING_TAG = "spring";

    @Test
    void everySpringTestIsTaggedSpring() {
        classes().that(startASpringContext())
                 .should(beTagged(SPRING_TAG))
                 .because("PIT excludes the \"spring\" group, and an untagged Spring test would run "
                          + "once per mutant")
                 .check(ProjectClasses.TESTS);
    }

    private static DescribedPredicate<JavaClass> startASpringContext() {
        return DescribedPredicate.describe(
                "start a Spring context",
                javaClass -> javaClass.isMetaAnnotatedWith(DescribedPredicate.describe(
                        "@ExtendWith(SpringExtension.class)",
                        SpringTestsAreTaggedTest::extendsWithSpring)));
    }

    private static boolean extendsWithSpring(JavaAnnotation<?> annotation) {
        return annotation.getRawType().isEquivalentTo(ExtendWith.class)
               && Arrays.stream((JavaClass[]) annotation.get("value").orElseThrow())
                        .anyMatch(extension -> extension.isEquivalentTo(SpringExtension.class));
    }

    private static ArchCondition<JavaClass> beTagged(String tag) {
        return new ArchCondition<>("be annotated with @Tag(\"" + tag + "\")") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean tagged = tagsOn(javaClass).anyMatch(tag::equals);
                events.add(new SimpleConditionEvent(
                        javaClass, tagged,
                        javaClass.getName() + " is not annotated with @Tag(\"" + tag + "\")"));
            }
        };
    }

    // A repeatable annotation used more than once is compiled into its container, so a class
    // carrying two tags has @Tags({...}) in its bytecode, not two @Tag.
    private static Stream<String> tagsOn(JavaClass javaClass) {
        Stream<String> single = javaClass.tryGetAnnotationOfType(Tag.class.getName())
                                         .stream()
                                         .map(SpringTestsAreTaggedTest::valueOf);
        Stream<String> repeated = javaClass.tryGetAnnotationOfType(Tags.class.getName())
                                           .stream()
                                           .flatMap(tags -> Arrays.stream(
                                                   (JavaAnnotation<?>[]) tags.get("value").orElseThrow()))
                                           .map(SpringTestsAreTaggedTest::valueOf);
        return Stream.concat(single, repeated);
    }

    private static String valueOf(JavaAnnotation<?> tag) {
        return (String) tag.get("value").orElseThrow();
    }
}
