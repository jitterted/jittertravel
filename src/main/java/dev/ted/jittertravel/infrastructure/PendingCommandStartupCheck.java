package dev.ted.jittertravel.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, detects commands left in PENDING status (e.g. after a crash between
 * persisting the command and writing its events). Because event appending is atomic,
 * a PENDING command means no events were durably written — there is nothing to repair,
 * but it should be surfaced for a human to abandon or keep.
 */
@Component
class PendingCommandStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandStartupCheck.class);

    private final PostgresPersister persister;

    PendingCommandStartupCheck(PostgresPersister persister) {
        this.persister = persister;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnAboutPendingCommands() {
        int pending = persister.countPendingCommands();
        if (pending > 0) {
            log.warn("{} command(s) are still PENDING at startup — they did not complete and no events were written. "
                    + "Review and resolve them at /admin/pending-commands", pending);
        }
    }
}
