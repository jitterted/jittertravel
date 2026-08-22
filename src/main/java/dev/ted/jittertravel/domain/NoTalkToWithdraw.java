package dev.ted.jittertravel.domain;

/**
 * Thrown when {@link WithdrawTalkCommand} is recorded against a conference with nothing
 * outstanding to withdraw — nothing submitted, already withdrawn, or already turned down.
 * <p>
 * Withdrawing from an <em>accepted</em> talk is legal and is the case the command exists for: a
 * schedule clash after the good news. What it refuses is a withdrawal that names no talk.
 */
public class NoTalkToWithdraw extends RuntimeException {
    public NoTalkToWithdraw(String message) {
        super(message);
    }
}
