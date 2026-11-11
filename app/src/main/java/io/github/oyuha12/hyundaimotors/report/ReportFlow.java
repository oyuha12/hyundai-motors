package io.github.oyuha12.hyundaimotors.report;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.guidance.ReportIntentClassifier;

/**
 * Reporting with one touch or one phrase. The SOS button reports at any time. After the last
 * guidance step the app offers a report by voice, then listens, never while it is speaking.
 * Every report goes to {@link MockEmergencyReporter} only; nothing leaves the app.
 *
 * <p>Plain Java with no timers, speech or recording, so the flow can be checked in unit tests.
 * The caller speaks, listens and records, and says when each part is done.
 */
public final class ReportFlow {
    public enum Trigger {
        /** The SOS button. */
        BUTTON,
        /** A recognized phrase such as "신고해 줘". */
        VOICE,
        /** Video mode stand-in for VOICE when the emulator microphone does not work. */
        STAGED_VOICE
    }

    public interface Listener {
        /** Read the offer ("신고가 필요하면 …"), then call {@link #onPromptSpoken()}. */
        void onPrompt();

        /** Start listening for a reply. Called only after the offer has been read. */
        void onListen();

        /**
         * Record the report, show the receipt and announce it, then call {@link #onAnnounced()}.
         *
         * @param heard the recognized phrase for {@link Trigger#VOICE}, otherwise null
         */
        void onReport(Trigger trigger, @Nullable String heard);

        /**
         * Show the evacuation and dispatch status: right after the announcement, or for a report
         * made without one, once the guidance has been read to the end.
         */
        void onReportFinished();
    }

    private enum Offer { NONE, SPEAKING, LISTENING }

    private final Listener listener;
    private Offer offer = Offer.NONE;
    private boolean guidanceFinished;
    private boolean reported;
    private boolean announced;
    /** Reported without an announcement, so the guidance is not cut short. */
    private boolean silent;
    private boolean finishedSent;

    public ReportFlow(Listener listener) {
        this.listener = listener;
    }

    /** A new situation, or none. */
    public void reset() {
        offer = Offer.NONE;
        guidanceFinished = false;
        reported = false;
        announced = false;
        silent = false;
        finishedSent = false;
    }

    /** Reported without this flow and without an announcement, for example on confirmation. */
    public void markReported() {
        offer = Offer.NONE;
        reported = true;
        announced = true;
        silent = true;
    }

    /** The last guidance step has been read. Offers a report unless one was made. */
    public void onGuidanceFinished() {
        guidanceFinished = true;
        if (reported) {
            sendFinishedIfDone();
            return;
        }
        if (offer != Offer.NONE) return;
        offer = Offer.SPEAKING;
        listener.onPrompt();
    }

    /** The guidance is read again from the top: the offer comes again at its end. */
    public void onGuidanceRestarted() {
        guidanceFinished = false;
        offer = Offer.NONE;
        // An announcement cut off by the restart counts as made.
        if (reported) announced = true;
    }

    /** The offer has been read; listening may start. */
    public void onPromptSpoken() {
        if (offer != Offer.SPEAKING || reported) return;
        offer = Offer.LISTENING;
        listener.onListen();
    }

    /** Waiting for a spoken request: the offer has been read and nothing is reported yet. */
    public boolean isAwaiting() {
        return offer == Offer.LISTENING && !reported;
    }

    public boolean isReported() {
        return reported;
    }

    /** A recognized reply. Reports when it asks for one. */
    public boolean onHeard(@Nullable String text) {
        if (!isAwaiting() || !ReportIntentClassifier.isReport(text)) return false;
        report(Trigger.VOICE, text);
        return true;
    }

    /** Video mode stand-in for a spoken request. Works only while awaiting one. */
    public boolean onStagedVoice() {
        if (!isAwaiting()) return false;
        report(Trigger.STAGED_VOICE, null);
        return true;
    }

    /** The SOS button: reports at once, without asking again. */
    public boolean onButton() {
        if (reported) return false;
        report(Trigger.BUTTON, null);
        return true;
    }

    /** The report announcement has been read. */
    public void onAnnounced() {
        if (!reported || announced) return;
        announced = true;
        sendFinishedIfDone();
    }

    private void report(Trigger trigger, @Nullable String heard) {
        reported = true;
        offer = Offer.NONE;
        listener.onReport(trigger, heard);
    }

    private void sendFinishedIfDone() {
        if (finishedSent || !announced || (silent && !guidanceFinished)) return;
        finishedSent = true;
        listener.onReportFinished();
    }
}
