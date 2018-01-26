/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author jsanchez
 */
public class Clock {
    private static final Clock instance = new Clock();
    private MachineTypes spectrumModel = MachineTypes.SPECTRUM48K;
    private int tstates;
    private long frames;
    private int timeout;
    private final CopyOnWriteArrayList<ClockTimeoutListener> clockListeners;
    private final boolean activeINT[] = new boolean[71000];

    // Clock class implements a Singleton pattern.
    private Clock() {
        this.clockListeners = new CopyOnWriteArrayList<>();
    }

    public static Clock getInstance() {
        return instance;
    }
    /**
     * Adds a new event listener to the list of event listeners.
     *
     * @param listener The new event listener.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     */
    public void addClockTimeoutListener(final ClockTimeoutListener listener) {

        if (listener == null) {
            throw new NullPointerException("Error: Listener can't be null");
        }

        // Avoid duplicates
        if (!clockListeners.contains(listener)) {
            clockListeners.add(listener);
        }
    }

    /**
     * Remove a new event listener from the list of event listeners.
     *
     * @param listener The event listener to remove.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     * @throws IllegalArgumentException Thrown if the listener wasn't registered.
     */
    public void removeClockTimeoutListener(final ClockTimeoutListener listener) {

        if (listener == null) {
            throw new NullPointerException("Internal Error: Listener can't be null");
        }

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
        Arrays.fill(activeINT, false);
        Arrays.fill(activeINT, 0, spectrumModel.lengthINT, true);
        Arrays.fill(activeINT, spectrumModel.tstatesFrame, spectrumModel.tstatesFrame + spectrumModel.lengthINT, true);
        reset();
    }

    /**
     * @return the tstates
     */
    public int getTstates() {
        return tstates;
    }

    /**
     * @param states the tstates to set
     */
    public void setTstates(int states) {
        tstates = states;
        frames = timeout = 0;
    }

    public void addTstates(int states) {
        tstates += states;

        if (timeout > 0) {
            timeout -= states;

            if (timeout <= 0) {
                int res = timeout;
                for (final ClockTimeoutListener listener : clockListeners) {
                   listener.clockTimeout();
                }
                
                if (timeout > 0) {
//                    System.out.println("Timeout: " + timeout + " res: " + res);
                    timeout += res;
                }
            }
        }
    }

    public long getFrames() {
        return frames;
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

    public void setTimeout(int ntstates) {
        if (timeout > 0) {
            throw new ConcurrentModificationException("A timeout is in progress. Can't set another timeout!");
        }

        timeout = ntstates > 10 ? ntstates : 10;
    } 

    public boolean isINTtime() {
        return activeINT[tstates];
    }

    @Override
    public String toString() {
        return String.format("Frame: %d, t-states: %d", frames, tstates);
    }
}
