package dev.ted.jittertravel.infrastructure;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;
import java.util.Map;

/**
 * Renders the text of every email the app sends from a plain text file in
 * {@code src/main/resources/email/}, so what family read is a file you can open and edit, not
 * strings assembled in code. Thymeleaf is already a dependency; this uses its plain-text mode and
 * nothing else.
 * <p>
 * <strong>The file is the email.</strong> Its first line is {@code Subject: ...}, then a blank line,
 * then the body, exactly as it will be sent. Values go in as {@code [[${name}]]}, and a repeated part
 * is {@code [# th:each="leg : ${legs}"] ... [/]}. Surrounding blank lines are trimmed, and a run of
 * blank lines is sent as one.
 * <p>
 * <strong>Any wording change needs Ted's approval before it is committed</strong> (2026-10-05): this
 * folder is the one place all outbound text lives, so that is where he reviews it.
 */
public class EmailTemplates {

    private static final String SUBJECT_PREFIX = "Subject: ";

    private final TemplateEngine engine;

    public EmailTemplates() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("email/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    /** Renders {@code email/<name>.txt} with the given values into a subject and a body. */
    public FamilyMessage render(String name, Map<String, Object> values) {
        // Never more than one blank line in a row: the engine's own line breaks around a loop add to the
        // blank lines written in the file, so what is typed and what is sent would otherwise differ.
        String text = engine.process(name, new Context(Locale.US, values)).strip().replaceAll("\n{3,}", "\n\n");
        int firstLineEnd = text.indexOf('\n');
        String firstLine = firstLineEnd < 0 ? text : text.substring(0, firstLineEnd);
        if (!firstLine.startsWith(SUBJECT_PREFIX)) {
            throw new IllegalStateException("email/" + name + ".txt must start with \"" + SUBJECT_PREFIX + "\"");
        }
        String body = firstLineEnd < 0 ? "" : text.substring(firstLineEnd).strip();
        return new FamilyMessage(firstLine.substring(SUBJECT_PREFIX.length()).strip(), body);
    }
}
