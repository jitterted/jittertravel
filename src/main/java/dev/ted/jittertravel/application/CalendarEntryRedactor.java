package dev.ted.jittertravel.application;

public class CalendarEntryRedactor {

    public CalendarEntry redact(CalendarEntry entry) {
        return switch (entry.kind()) {
            case LODGING -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    "Hotel", entry.subTitle(),
                    "Hotel cont'd", entry.continuationSubTitle(),
                    null
            );
            case FLIGHT -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), null,
                    entry.continuationTitle(), null,
                    entry.mapsUrl()
            );
            case TRAIN -> new CalendarEntry(
                    entry.kind(), entry.start(), entry.end(),
                    entry.mainTitle(), null,
                    entry.continuationTitle(), null,
                    null
            );
            case CONFERENCE, GATHERING -> entry;
        };
    }
}
