package dev.ted.jittertravel.web;

/**
 * Translates a display page number into the LIMIT/OFFSET window to fetch from the
 * command log, which is always queried oldest-first (its divergence detection scans
 * forward through ascending event sequence numbers).
 * <p>
 * When the display is newest-first, display page 0 must therefore be the *last*
 * ascending window, so that the most recent commands show up on the first page. The
 * oldest page is then the partial one.
 */
class PageWindow {

    private final int offset;
    private final int limit;

    PageWindow(int totalCommands, int pageSize, int page, boolean newestFirst) {
        if (!newestFirst) {
            offset = page * pageSize;
            limit = pageSize;
            return;
        }

        // Count back from the end: page 0 is the final pageSize rows, page 1 the ones
        // before those, and so on. A negative start means we ran past the oldest row,
        // so the window shrinks to whatever remains.
        int startOfWindow = totalCommands - (page + 1) * pageSize;
        if (startOfWindow < 0) {
            offset = 0;
            limit = Math.max(0, pageSize + startOfWindow);
        } else {
            offset = startOfWindow;
            limit = pageSize;
        }
    }

    int offset() {
        return offset;
    }

    int limit() {
        return limit;
    }
}
