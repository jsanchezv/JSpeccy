/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;

/**
 *
 * @author jsanchez
 */
public class Clock {
    private MachineTypes spectrumModel;
    private int tstates;
    private long frames;
    private int timeout;
    private final ArrayList<ClockTimeoutListener> clockListeners = new ArrayList<ClockTimeoutListener>();    

    public Clock() {
        spectrumModel = MachineTypes.SPECTRUM48K;
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
    }
    
    private void fireClockTimeout() {
        try {
            for (final ClockTimeoutListener listener : clockListeners) {
                listener.clockTimeout();
            }
        } catch (ConcurrentModificationException excpt) {
            // Optimistic exception handling
            System.out.println("ConcurrentModificationException!");
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
        frames = 0;
    }
    
    public void addTstates(int states) {
        tstates += states;

        if (timeout > 0) {
            timeout -= states;
            if (timeout < 1) {
                fireClockTimeout();
            }
        }
    }
    
    public long getFrames() {
        return frames;
    }
    
    public void endFrame() {
        frames++;
        tstates %= spectrumModel.tstatesFrame;
    }

    public long getAbsTstates() {
        return frames * spectrumModel.tstatesFrame + tstates;
    }

    public void reset() {
        frames = tstates = 0;
    }

    public void setTimeout(int ntstates) {
        if (timeout > 0) {
            System.err.println("Can't set a timeout!");
            return;
        }

        timeout = ntstates > 0 ? ntstates : 1;

//        System.out.println(String.format("timeout %d set for class %s", timeout, target.getClass().getName()));
    } 
}