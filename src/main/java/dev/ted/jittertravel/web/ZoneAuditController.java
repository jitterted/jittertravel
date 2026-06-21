package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.LocationAuditProjector;
import dev.ted.jittertravel.application.LocationZoneAudit;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class ZoneAuditController {

    private final LocationAuditProjector locationAuditProjector;
    private final LocationZoneAudit locationZoneAudit;

    ZoneAuditController(LocationAuditProjector locationAuditProjector, LocationZoneAudit locationZoneAudit) {
        this.locationAuditProjector = locationAuditProjector;
        this.locationZoneAudit = locationZoneAudit;
    }

    @GetMapping("/admin/zone-audit")
    public String zoneAudit(Model model) {
        LocationZoneAudit.Report report = locationZoneAudit.report(
                locationAuditProjector.cities(),
                locationAuditProjector.airports());
        model.addAttribute("report", report);
        return "admin-zone-audit";
    }
}
