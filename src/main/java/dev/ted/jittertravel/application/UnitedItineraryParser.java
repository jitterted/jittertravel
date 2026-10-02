package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the text of a United confirmation email, as copied out of Mail. Plain pattern matching; no
 * LLM (a standing rule for application logic).
 * <p>
 * <strong>An allow-list, like {@code PublicCalendarProjector}.</strong> It reads the confirmation
 * code and, per leg, the flight number, two dates, two times and two airport codes — and nothing
 * else. Cabin class, "Operated by", seats, the eTicket number, the frequent-flyer number and the
 * traveller's name are never read, so none of them can reach a command, an event or a log. The
 * pasted text itself is never stored either.
 * <p>
 * <strong>Layout-tolerant within a leg.</strong> Each "Flight N of M" header opens a block that runs
 * to the next header (or "Traveler Details"), and the block must hold exactly two dates, two times
 * and two bracketed airport codes, in departure-then-arrival order. Whether a copy lays the two
 * columns side by side on one line or one under the other does not matter, which is what makes the
 * same parser work on text copied on a Mac and on an iPad.
 * <p>
 * Both dates are read, never inferred: a red-eye prints its own arrival date.
 * <p>
 * <strong>Every problem, in one result.</strong> "N of M" is a completeness check, so a paste that
 * lost a leg is refused rather than booked short.
 */
public class UnitedItineraryParser {

    /** What a parse produced: an itinerary, or every reason there is not one. */
    public sealed interface Result {
    }

    public record Parsed(PastedItinerary itinerary) implements Result {
    }

    public record Unparseable(List<String> problems) implements Result {
        public Unparseable {
            problems = List.copyOf(problems);
        }
    }

    private static final Pattern CONFIRMATION_LABEL =
            Pattern.compile("Confirmation Number:\\s*([A-Z0-9]{6})?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIRMATION_CODE = Pattern.compile("^[A-Z0-9]{6}$");
    private static final Pattern FLIGHT_HEADER =
            Pattern.compile("^Flight\\s+(\\d+)\\s+of\\s+(\\d+)\\s+([A-Z0-9]{2})\\s*(\\d{1,4})\\b");
    private static final Pattern SECTION_END = Pattern.compile("^Traveler Details");
    private static final Pattern DATE = Pattern.compile(
            "(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun), (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \\d{1,2}, \\d{4}");
    private static final Pattern TIME = Pattern.compile("\\b\\d{1,2}:\\d{2} [AP]M\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AIRPORT = Pattern.compile("\\(([A-Z]{3})\\)");

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("h:mm a").toFormatter(Locale.ENGLISH);

    /** Carrier codes this parser names; any other prefix is kept as the airline. */
    private static final Map<String, String> AIRLINES = Map.of("UA", "United Airlines");

    public Result parse(String pasted) {
        List<String> lines = lines(pasted == null ? "" : pasted);
        List<String> problems = new ArrayList<>();

        String confirmationCode = confirmationCode(lines);
        if (confirmationCode.isEmpty()) {
            problems.add("No confirmation number found");
        }

        Map<Integer, PastedItinerary.Leg> legs = new TreeMap<>();
        int declaredTotal = legs(lines, legs, problems);

        if (declaredTotal == 0) {
            problems.add("No flights found. Paste the text of a United confirmation email.");
        } else {
            for (int number = 1; number <= declaredTotal; number++) {
                if (!legs.containsKey(number) && !mentionedWithAProblem(problems, number)) {
                    problems.add("Flight " + number + " of " + declaredTotal + " is missing from the paste");
                }
            }
        }

        return problems.isEmpty()
                ? new Parsed(new PastedItinerary(confirmationCode, List.copyOf(legs.values())))
                : new Unparseable(problems);
    }

    /**
     * Trimmed, non-blank lines, with the two spaces a copy out of Mail can carry normalized to
     * plain ones: a no-break space, and the narrow no-break space Apple puts before AM/PM.
     */
    private static List<String> lines(String pasted) {
        return pasted.replace(' ', ' ').replace(' ', ' ')
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static String confirmationCode(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher label = CONFIRMATION_LABEL.matcher(lines.get(i));
            if (label.find()) {
                if (label.group(1) != null) {
                    return label.group(1);
                }
                if (i + 1 < lines.size() && CONFIRMATION_CODE.matcher(lines.get(i + 1)).matches()) {
                    return lines.get(i + 1);
                }
            }
        }
        return "";
    }

    /** Fills {@code legs}, adds any problems, and returns the "of M" the headers declared. */
    private static int legs(List<String> lines, Map<Integer, PastedItinerary.Leg> legs, List<String> problems) {
        int declaredTotal = 0;
        for (int i = 0; i < lines.size(); i++) {
            Matcher header = FLIGHT_HEADER.matcher(lines.get(i));
            if (!header.find()) {
                continue;
            }
            int number = Integer.parseInt(header.group(1));
            int total = Integer.parseInt(header.group(2));
            if (declaredTotal != 0 && total != declaredTotal) {
                problems.add("Flight " + number + " says \"of " + total + "\", but an earlier one said \"of "
                             + declaredTotal + "\"");
            }
            declaredTotal = Math.max(declaredTotal, total);

            StringBuilder block = new StringBuilder();
            int next = i + 1;
            while (next < lines.size()
                   && !FLIGHT_HEADER.matcher(lines.get(next)).find()
                   && !SECTION_END.matcher(lines.get(next)).find()) {
                block.append(lines.get(next)).append('\n');
                next++;
            }
            leg(number, header.group(3), header.group(4), block.toString(), problems)
                    .ifPresent(leg -> legs.put(number, leg));
        }
        return declaredTotal;
    }

    private static Optional<PastedItinerary.Leg> leg(int number, String carrier, String digits,
                                                              String block, List<String> problems) {
        List<String> dates = all(DATE, block);
        List<String> times = all(TIME, block);
        List<String> airports = allGroup(AIRPORT, block);
        List<String> missing = new ArrayList<>();
        if (dates.size() != 2) {
            missing.add("two dates");
        }
        if (times.size() != 2) {
            missing.add("two times");
        }
        if (airports.size() != 2) {
            missing.add("two airport codes");
        }
        if (!missing.isEmpty()) {
            problems.add("Flight " + number + ": expected " + String.join(", ", missing));
            return Optional.empty();
        }
        try {
            return Optional.of(new PastedItinerary.Leg(
                    number,
                    AIRLINES.getOrDefault(carrier, carrier),
                    carrier + digits,
                    AirportCode.of(airports.get(0)),
                    LocalDateTime.of(LocalDate.parse(dates.get(0), DATE_FORMAT),
                                     LocalTime.parse(times.get(0), TIME_FORMAT)),
                    AirportCode.of(airports.get(1)),
                    LocalDateTime.of(LocalDate.parse(dates.get(1), DATE_FORMAT),
                                     LocalTime.parse(times.get(1), TIME_FORMAT))));
        } catch (DateTimeParseException e) {
            // Most often a weekday that does not match its date — a typo'd paste, not a real email.
            problems.add("Flight " + number + ": could not read a date or time (" + e.getParsedString() + ")");
            return Optional.empty();
        }
    }

    private static boolean mentionedWithAProblem(List<String> problems, int number) {
        return problems.stream().anyMatch(problem -> problem.startsWith("Flight " + number + ":"));
    }

    private static List<String> all(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(MatchResult::group).toList();
    }

    private static List<String> allGroup(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(result -> result.group(1)).toList();
    }
}
