/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton implementation of a ZX Spectrum clock
 *
 * @author jsanchez
 */
@Slf4j
@ToString(onlyExplicitlyIncluded = true, includeFieldNames = true)
public enum SpectrumClock {

    INSTANCE;

    @Getter
    @ToString.Include
    private long frames;

    @Getter
    @ToString.Include
    private int tstates;

    private MachineTypes spectrumModel;
    private int timeout;
    private final CopyOnWriteArrayList<ClockTimeoutListener> clockListeners;

    SpectrumClock() {

        this.spectrumModel = MachineTypes.SPECTRUM48K;
        this.clockListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Adds a new event listener to the list of event listeners.
     *
     * @param listener The new event listener.
     * @throws NullPointerException Thrown if the listener argument is null.
     */
    public void addClockTimeoutListener(final ClockTimeoutListener listener) {
        Objects.requireNonNull(listener, "Error: Listener can't be null");

        // Avoid duplicates
        if (!clockListeners.contains(listener)) {
            clockListeners.add(listener);
        }
    }

    /**
     * Remove a new event listener from the list of event listeners.
     *
     * @param listener The event listener to remove.
     * @throws NullPointerException     Thrown if the listener argument is null.
     * @throws IllegalArgumentException Thrown if the listener wasn't registered.
     */
    public void removeClockTimeoutListener(final ClockTimeoutListener listener) {
        Objects.requireNonNull(listener, "Internal Error: Listener can't be null");

        if (!clockListeners.remove(listener)) {
            throw new IllegalArgumentException("Internal Error: Listener was not listening on object");
        }

        // When don't have listeners, disable any pending timeout
        if (clockListeners.isEmpty()) {
            timeout = 0;
        }
    }

    /**
     * @param spectrumModel the spectrumModel to set
     */
    public void setSpectrumModel(MachineTypes spectrumModel) {
        this.spectrumModel = spectrumModel;
        reset();
    }

    /**
     * @param states the tstates to set
     */
    public void setTstates(final int states) {
        tstates = (states < 0 || states > spectrumModel.tstatesFrame) ? 0 : states;
        frames = timeout = 0;
    }

    public void addTstates(final int states) {
        tstates += states;

        if (timeout > 0) {
            timeout -= states;

            if (timeout <= 0) {
                int res = timeout;
                clockListeners.forEach(ClockTimeoutListener::clockTimeout);

                if (timeout > 0) {
                    log.trace("Timeout: {}, res: {}", timeout, res);
                    timeout += res;
                }
            }
        }
    }

    public void endFrame() {
        frames++;
        tstates -= spectrumModel.tstatesFrame;
    }

    public long getAbsTstates() {
        return frames * spectrumModel.tstatesFrame + tstates;
    }

    public void reset() {
        frames = timeout = tstates = 0;
    }

    public void setTimeout(final int ntstates) {
        if (this.timeout > 0) {
            throw new ConcurrentModificationException("A timeout is in progress. Can't set another timeout!");
        }

        this.timeout = Math.max(ntstates, 10);
    }

}
