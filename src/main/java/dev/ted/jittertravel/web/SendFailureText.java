package dev.ted.jittertravel.web;

/**
 * Words a failed send for a narrow column: "The send failed: " and the reason, cut to a length that
 * fits. A Brevo or network message can run long.
 */
final class SendFailureText {

    private static final int LIMIT = 140;

    String of(RuntimeException failed) {
        String message = failed.getMessage();
        String text = message == null || message.isBlank() ? "no reason given" : message.strip();
        return "The send failed: " + (text.length() <= LIMIT ? text : text.substring(0, LIMIT) + "…");
    }
}
