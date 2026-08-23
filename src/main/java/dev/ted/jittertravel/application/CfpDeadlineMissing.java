package dev.ted.jittertravel.application;

/**
 * Thrown when a form records where a talk is submitted but not when the CFP closes.
 * {@code CfpOpened} is built around its deadline — that is the fact it exists to record, and the
 * one its calendar reminders fire from — so it cannot carry a submission URL on its own.
 * <p>
 * Application-layer rather than domain, and deliberately: the domain never sees this combination,
 * because {@code OpenCfpCommand} cannot be constructed without a deadline. It is a rule about what a
 * form may leave out, so it lives beside {@link ZoneResolutionException}, the other refusal the
 * plan-conference form re-prompts for.
 * <p>
 * Refusing beats dropping the URL silently: Ted typed it, and a value that vanishes without a word
 * is the failure he would not notice.
 */
public class CfpDeadlineMissing extends RuntimeException {
    public CfpDeadlineMissing(String message) {
        super(message);
    }
}
