package dev.ted.jittertravel.web;

public class ClearConflictRequest {
    private String gatheringId;
    private String conferenceId;
    private String reason;

    public String getGatheringId() { return gatheringId; }
    public void setGatheringId(String gatheringId) { this.gatheringId = gatheringId; }

    public String getConferenceId() { return conferenceId; }
    public void setConferenceId(String conferenceId) { this.conferenceId = conferenceId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
