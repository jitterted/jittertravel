package dev.ted.jittertravel.web;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * The conflict summary fields (names, cities, date) are display-only, but they ride the form as
 * hidden inputs rather than as loose model attributes: a rejected POST re-renders this same page,
 * and without them the summary would come back blank.
 */
public class ClearConflictRequest {
    private String gatheringId;
    private String conferenceId;
    private String reason;
    private String gatheringName;
    private String gatheringCity;
    private String conferenceName;
    private String conferenceCity;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    public String getGatheringId() { return gatheringId; }
    public void setGatheringId(String gatheringId) { this.gatheringId = gatheringId; }

    public String getConferenceId() { return conferenceId; }
    public void setConferenceId(String conferenceId) { this.conferenceId = conferenceId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getGatheringName() { return gatheringName; }
    public void setGatheringName(String gatheringName) { this.gatheringName = gatheringName; }

    public String getGatheringCity() { return gatheringCity; }
    public void setGatheringCity(String gatheringCity) { this.gatheringCity = gatheringCity; }

    public String getConferenceName() { return conferenceName; }
    public void setConferenceName(String conferenceName) { this.conferenceName = conferenceName; }

    public String getConferenceCity() { return conferenceCity; }
    public void setConferenceCity(String conferenceCity) { this.conferenceCity = conferenceCity; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
