package io.github.oyuha12.hyundaimotors.guidance;

import androidx.annotation.Nullable;
import io.github.oyuha12.hyundaimotors.diagnosis.GuidanceStep;
import java.util.Collections;
import java.util.List;

/**
 * Moves through guidance steps. Each step follows the previous one once it has been read, except
 * after a step that {@link GuidanceStep#waitsForStop waits for the car to stop}: then the next
 * step starts only when the speed falls below {@link #STOPPED_BELOW_KPH}.
 *
 * <p>Plain Java with no timers, so the whole flow can be checked in unit tests. The caller says
 * when a step has been read and passes on every speed reading. While paused, for example during
 * a report announcement, nothing new starts.
 */
public final class GuidanceSequencer {
    /** Below this speed the car counts as stopped. */
    public static final float STOPPED_BELOW_KPH = 1f;

    public interface Listener {
        /** A step begins: show it and read it. */
        void onStep(int index, GuidanceStep step);

        /** Waiting for the car to stop before the next step starts, or no longer. */
        void onWaitingForStop(boolean waiting);

        /** The last step has been read. Once per start. */
        void onFinished();
    }

    private final Listener listener;
    private List<GuidanceStep> steps = Collections.emptyList();
    private int index = -1;
    private boolean waiting;
    private boolean finished;
    private boolean paused;
    @Nullable private Float speedKph;

    public GuidanceSequencer(Listener listener) {
        this.listener = listener;
    }

    /** Starts at the first step, also to read the steps again from the top. */
    public void start(List<GuidanceStep> steps) {
        this.steps = steps;
        setWaiting(false);
        finished = false;
        paused = false;
        index = -1;
        if (!steps.isEmpty()) next();
    }

    /** Ends the guidance. Nothing more starts until {@link #start}. */
    public void stop() {
        steps = Collections.emptyList();
        index = -1;
        finished = false;
        paused = false;
        setWaiting(false);
    }

    /** Holds the guidance, for example while a report is announced. */
    public void pause() {
        if (index >= 0) paused = true;
    }

    /**
     * Carries on after {@link #pause()}: the next step if the car stopped meanwhile, otherwise
     * the interrupted step read again. Nothing after the last step.
     */
    public void resume() {
        if (!paused) return;
        paused = false;
        if (finished) return;
        if (waiting) {
            onSpeed(speedKph);
            return;
        }
        listener.onStep(index, steps.get(index));
    }

    /** The current step has been read, or its time ran out. */
    public void onStepSpoken() {
        if (index < 0 || waiting || paused || finished) return;
        if (index + 1 >= steps.size()) {
            finished = true;
            listener.onFinished();
            return;
        }
        if (steps.get(index).waitsForStop && !isStopped(speedKph)) {
            setWaiting(true);
            return;
        }
        next();
    }

    /** Every speed reading, in km/h. */
    public void onSpeed(@Nullable Float kph) {
        speedKph = kph;
        if (!paused && waiting && isStopped(kph)) {
            setWaiting(false);
            next();
        }
    }

    /** The current step, or -1 before the first and after {@link #stop}. */
    public int index() {
        return index;
    }

    public boolean isWaiting() {
        return waiting;
    }

    /** The last step has been read. */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Unknown speed counts as stopped, so guidance never stalls in a car that reports no speed.
     */
    public static boolean isStopped(@Nullable Float kph) {
        return kph == null || kph < STOPPED_BELOW_KPH;
    }

    private void next() {
        index++;
        listener.onStep(index, steps.get(index));
    }

    private void setWaiting(boolean value) {
        if (waiting == value) return;
        waiting = value;
        listener.onWaitingForStop(value);
    }
}
