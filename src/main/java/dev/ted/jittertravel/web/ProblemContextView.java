package dev.ted.jittertravel.web;

import java.util.List;

/**
 * The "why you are here" banner, display-ready: what the fix link was about, and what the schedule
 * holds around it.
 * <p>
 * Every field is a finished string. The banner renders inside a Thymeleaf form page, and a form
 * page is not the place to start formatting dates — {@link ProblemContextLookup} builds this from
 * {@link ProblemBand} and {@link ContextBand}, which already own the report's wording.
 *
 * @param markerModifier the problem's kind, as the CSS modifier the problem calendar uses, so the
 *                       banner's left edge carries the same hue as the band Ted clicked. The fill
 *                       stays amber whatever the kind is.
 */
public record ProblemContextView(String markerModifier,
                                 String title,
                                 String detail,
                                 List<String> contextLines,
                                 String backLabel,
                                 String backHref) {

    public ProblemContextView {
        contextLines = List.copyOf(contextLines);
    }
}
