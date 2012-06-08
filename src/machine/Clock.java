/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

/**
 *
 * @author jsanchez
 */
public class Clock {
    private MachineTypes spectrumModel;
    private int tstates;
    private long frames;
    private ClockTimeoutListener clockListener;
    private int timeout;

    public Clock() {
        spectrumModel = MachineTypes.SPECTRUM48K;
        clockListener = null;
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

        if (clockListener == null) {
//            System.out.println("timeout = " + timeout);
            return;
        }

        if (timeout > 0) {
            timeout -= states;
            if (timeout < 1) {
//                System.out.println("timeout fired!");
                clockListener.clockTimeout();
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

    public void setTimeoutListener(ClockTimeoutListener dest) {
        if (dest != null && clockListener != null) {
            System.err.println("Can't set a listener for timeouts!");
            return;
        }

        clockListener = dest;
        timeout = 0;
    }

    public void setTimeout(int tstates) {
        if (clockListener == null || timeout > 0) {
            System.err.println("Can't set a timeout!");
            return;
        }

        timeout = tstates > 0 ? tstates : 1;

//        System.out.println(String.format("timeout %d set for class %s", timeout, target.getClass().getName()));
    } 
}
