package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One {@link ScheduleContext} entry placed on the problem calendar as the grey backdrop behind the
 * problem bands: the run of days it occupies, and a label naming it.
 * <p>
 * All context is one grey, whatever kind it is. Shades per kind would rebuild the entry-colour
 * vocabulary of the public calendar on a page that is not about that — the kind is in the label.
 */
public record ContextBand(String label, LocalDate firstDay, LocalDate lastDay) {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d", Locale.ENGLISH);

    /**
     * Exhaustive over the sealed {@link ScheduleContext}: a new kind of context cannot be added
     * without deciding how it reads on the calendar.
     */
    public static ContextBand from(ScheduleContext context) {
        return switch (context) {
            case ScheduleContext.Conference(String name, String city, LocalDate first, LocalDate last) ->
                    new ContextBand(named(name, city, first, last), first, last);
            case ScheduleContext.Gathering(String name, String city, LocalDate first, LocalDate last) ->
                    new ContextBand(named(name, city, first, last), first, last);
            case ScheduleContext.Travel(String fromCity, String toCity, LocalDate first, LocalDate last) ->
                    new ContextBand(fromCity + " → " + toCity + " · " + dayRange(first, last), first, last);
            case ScheduleContext.Stay(String city, LocalDate first, LocalDate last) ->
                    new ContextBand("Hotel, " + city + " · " + dayRange(first, last), first, last);
        };
    }

    private static String named(String name, String city, LocalDate first, LocalDate last) {
        return name + ", " + city + " · " + dayRange(first, last);
    }

    /**
     * The dates the band covers, said once: {@code Sep 14–18} within a month, {@code Sep 30 – Oct 2}
     * across one, and a bare {@code Sep 14} for a single day. The band's own extent already shows
     * the span; the label is what makes it exact.
     */
    private static String dayRange(LocalDate firstDay, LocalDate lastDay) {
        if (firstDay.equals(lastDay)) {
            return firstDay.format(MONTH_DAY);
        }
        if (firstDay.getMonth() == lastDay.getMonth() && firstDay.getYear() == lastDay.getYear()) {
            return firstDay.format(MONTH_DAY) + "–" + lastDay.format(DAY);
        }
        return firstDay.format(MONTH_DAY) + " – " + lastDay.format(MONTH_DAY);
    }
}
