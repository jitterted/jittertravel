package dev.ted.jittertravel.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Thymeleaf templates as files on disk, for tests that check what the shipped markup says.
 * <p>
 * Dot-directories are skipped: a git worktree checked out under {@code templates/} would
 * otherwise contribute a stale copy of every page and quietly fail the conventions.
 */
class TemplateSources {

    static final Path ROOT = Path.of("src/main/resources/templates");

    /** Every shipped template whose source contains {@code snippet}, in stable order. */
    List<Path> containing(String snippet) {
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files.filter(Files::isRegularFile)
                        .filter(this::isShippedTemplate)
                        .filter(template -> read(template).contains(snippet))
                        .sorted()
                        .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String read(Path template) {
        try {
            return Files.readString(template, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isShippedTemplate(Path path) {
        if (!path.toString().endsWith(".html")) {
            return false;
        }
        for (Path part : path) {
            if (part.toString().startsWith(".")) {
                return false;
            }
        }
        return true;
    }
}
