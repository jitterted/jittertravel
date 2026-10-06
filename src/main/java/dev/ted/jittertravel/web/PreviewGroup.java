package dev.ted.jittertravel.web;

import java.util.List;

/** One group on the preview page: its heading, its send panel and the emails the panel sends. */
public record PreviewGroup(String title, String sendPath, PreviewPanel panel, List<PreviewEmail> emails) {
}
