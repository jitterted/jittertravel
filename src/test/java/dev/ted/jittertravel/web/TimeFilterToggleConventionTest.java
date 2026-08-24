package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.DroppedView;
import dev.ted.jittertravel.application.TimeView;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convention guard: every list-view page renderer must surface the shared
 * FUTURE/ALL toggle, wired to the active filter.
 * <p>
 * "List-view page renderer" is recognised structurally — any {@code public
 * static render(...)} method in the {@code web} package that accepts both a
 * {@link List} of rows and a {@link TimeView}. A new view that follows that
 * signature is covered automatically; one that forgets
 * {@link TimeFilterToggle} fails here. See CLAUDE.md "Architecture Rules".
 */
class TimeFilterToggleConventionTest {

    private static final String WEB_PACKAGE = "dev/ted/jittertravel/web";

    @TestFactory
    Stream<DynamicTest> everyListViewRendererEmitsTheSharedToggle() throws Exception {
        List<Method> renderers = listViewRenderMethods();

        assertThat(renderers)
                .as("should discover the known list-view renderers; "
                    + "if this drops, the discovery scan is broken")
                .extracting(m -> m.getDeclaringClass().getSimpleName())
                .contains("BookedTrainsRenderer", "BookedFlightsRenderer",
                        "BookedHotelsRenderer", "PlannedGatheringsRenderer",
                        "PlannedPrivateEventsRenderer", "ConferencesRenderer");

        return renderers.stream().map(renderer -> DynamicTest.dynamicTest(
                renderer.getDeclaringClass().getSimpleName(),
                () -> assertRendersToggle(renderer)));
    }

    private static void assertRendersToggle(Method renderer) throws Exception {
        String futureHtml = render(renderer, TimeView.FUTURE);
        assertThat(futureHtml)
                .as("%s must render the shared .time-toggle", renderer.getDeclaringClass().getSimpleName())
                .contains("class=\"time-toggle\"")
                .contains("?filter=future")
                .contains("?filter=all")
                .containsPattern("\\?filter=future\" class=\"active\"");

        String allHtml = render(renderer, TimeView.ALL);
        assertThat(allHtml)
                .as("%s must mark the All link active under TimeView.ALL", renderer.getDeclaringClass().getSimpleName())
                .containsPattern("\\?filter=all\" class=\"active\"");
    }

    /**
     * Every discovered renderer is invoked with an empty row list and the filter under test. A view
     * with a second, independent filter of its own gets that filter's default here — this test is
     * about the shared FUTURE/ALL toggle, and the other filter's own behaviour belongs to the
     * view's own test.
     */
    private static String render(Method renderer, TimeView activeFilter) throws Exception {
        Object[] args = Stream.of(renderer.getParameterTypes())
                .map(type -> defaultArgumentFor(type, activeFilter))
                .toArray();
        return (String) renderer.invoke(null, args);
    }

    private static Object defaultArgumentFor(Class<?> type, TimeView activeFilter) {
        if (type == TimeView.class) {
            return activeFilter;
        }
        if (type == DroppedView.class) {
            return DroppedView.HIDE;
        }
        // A count a view carries about its own second filter — zero is the empty-list equivalent,
        // and reflective invoke rejects a List where an int is declared.
        if (type == int.class) {
            return 0;
        }
        return List.of();
    }

    private static List<Method> listViewRenderMethods() throws Exception {
        Path classesRoot = Path.of(TimeFilterToggle.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path webDir = classesRoot.resolve(WEB_PACKAGE);
        try (Stream<Path> files = Files.walk(webDir, 1)) {
            return files
                    .filter(path -> path.toString().endsWith(".class"))
                    .map(path -> classNameOf(classesRoot, path))
                    .map(TimeFilterToggleConventionTest::loadClass)
                    .flatMap(clazz -> Stream.of(clazz.getDeclaredMethods()))
                    .filter(TimeFilterToggleConventionTest::isListViewRender)
                    .toList();
        }
    }

    private static boolean isListViewRender(Method method) {
        if (!method.getName().equals("render") || !Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        List<Class<?>> params = List.of(method.getParameterTypes());
        return params.contains(TimeView.class)
               && params.stream().anyMatch(List.class::isAssignableFrom);
    }

    private static String classNameOf(Path classesRoot, Path classFile) {
        String relative = classesRoot.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace(classFile.getFileSystem().getSeparator(), ".");
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load " + className, e);
        }
    }
}