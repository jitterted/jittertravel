package dev.ted.jittertravel.web;

import j2html.tags.DomContent;
import j2html.tags.specialized.DetailsTag;

import java.util.List;

import static j2html.TagCreator.a;
import static j2html.TagCreator.details;
import static j2html.TagCreator.div;
import static j2html.TagCreator.span;
import static j2html.TagCreator.summary;

/**
 * A tap-to-open popup menu, built on native {@code <details>}/{@code <summary>} so a tap toggles it
 * with no hover dependency — which is what makes it work on the iPad. The list overlays whatever is
 * beside it (absolute + z-index) rather than reflowing the layout when it opens.
 * <p>
 * Two users: the calendar's per-day "Add …" menu ({@code CalendarViewBuilder}) and the fix menu on
 * every schedule-problem card and band. The second is what justifies lifting the CSS and the
 * dismissal script out of {@code CalendarRenderer} — copying a dismissal script is how two menus
 * end up behaving differently, and the behaviour here is subtle enough to be worth having once
 * (outside-click, Escape, and opening one closes the others).
 * <p>
 * Callers supply their own summary content and style it themselves; the {@link #CSS} here owns only
 * the popup mechanics and the item vocabulary.
 */
public class DisclosureMenu {

    static final String MENU_CLASS = "disclosure-menu";
    static final String LIST_CLASS = "disclosure-menu-list";
    static final String ITEM_CLASS = "disclosure-menu-item";

    /**
     * Uses the calendar's colour variables, which are defined on {@code :root} by every page that
     * renders a menu. A page adopting this without them gets an unstyled but working menu.
     */
    public static final String CSS = """
            .disclosure-menu { position: relative; }
            .disclosure-menu > summary { display: block; list-style: none; cursor: pointer; }
            .disclosure-menu > summary::-webkit-details-marker { display: none; }
            .disclosure-menu-list {
                position: absolute; top: 100%; left: 0; z-index: 50;
                min-width: 160px; margin-top: 2px; padding: 4px;
                display: flex; flex-direction: column;
                background-color: var(--calendar-surface, #ffffff);
                border: 1px solid var(--calendar-border-strong, darkgray);
                border-radius: 8px; box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
            }
            .disclosure-menu-item {
                padding: 8px 10px; border-radius: 6px;
                font-size: 0.85rem; font-weight: 500;
                color: var(--calendar-text-secondary, #495057);
                text-decoration: none; white-space: nowrap;
            }
            .disclosure-menu-item:hover { background-color: var(--calendar-header-bg, #f8f9fa); }
            /* An item that cannot be triggered right now is shown, not removed — greyed and inert,
               with a title saying why (CLAUDE.md, action affordances). */
            .disclosure-menu-item--disabled {
                color: var(--muted-text, #6b7280); opacity: 0.7; cursor: default;
            }
            .disclosure-menu-item--disabled:hover { background-color: transparent; }
            """;

    /**
     * Queried once at load, so every menu on the page shares one set of listeners. Deliberately not
     * scoped to a container: outside-click and Escape are page-wide questions.
     */
    public static final String SCRIPT = """
            var disclosureMenus = document.querySelectorAll('.disclosure-menu');
            function closeDisclosureMenus(except) {
                disclosureMenus.forEach(function (menu) {
                    if (menu !== except) {
                        menu.open = false;
                    }
                });
            }
            disclosureMenus.forEach(function (menu) {
                menu.addEventListener('toggle', function () {
                    if (menu.open) {
                        closeDisclosureMenus(menu);  // opening one closes the rest — no stacking
                    }
                });
            });
            document.addEventListener('click', function (event) {
                if (!event.target.closest('.disclosure-menu')) {
                    closeDisclosureMenus(null);
                }
            });
            document.addEventListener('keydown', function (event) {
                if (event.key === 'Escape') {
                    closeDisclosureMenus(null);
                }
            });
            """;

    /** A menu whose {@code <summary>} is {@code summaryContent}, opening onto {@code items}. */
    public static DetailsTag render(DomContent summaryContent, String summaryClass,
                                    List<DomContent> items) {
        return details().withClass(MENU_CLASS).with(
                summary(summaryContent).withClass(summaryClass),
                div().withClass(LIST_CLASS).with(items)
        );
    }

    public static DomContent item(String label, String href) {
        return a(label).withHref(href).withClass(ITEM_CLASS);
    }

    /**
     * An item that exists but cannot be used, with the reason on it. A {@code span}, never a
     * disabled {@code <a>}: the point is that it is visibly not a link.
     */
    public static DomContent disabledItem(String label, String reason) {
        return span(label).withClass(ITEM_CLASS + " " + ITEM_CLASS + "--disabled").withTitle(reason);
    }
}
