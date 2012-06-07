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
    private long absTstates, frames;
    private ClockInterface clockListener;
    private ClockScreen screenListener;
    private int timeout;
    private int screenTable[];
    private int step;

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
        absTstates = frames = step = 0;
        while(step < screenTable.length && states > screenTable[step])
                step++;
    }
    
    public void addTstates(int states){
        tstates += states;

        if (screenListener != null && step < screenTable.length) {
//            System.out.println(String.format("updScr. step = %d, table = %d, tstates = %d", step, tstatesTable[step], tstates));
           do {
                screenListener.updateScreen(screenTable[step++]);
            } while(step < screenTable.length && tstates > screenTable[step]);
        }

        if (clockListener == null) {
//            System.out.println("timeout = " + timeout);
            return;
        }
        
        timeout -= states;
        if (timeout > 0) {
            return;
        }

//        System.out.println("timeout fired!");
        clockListener.clockTimeout();
    }

    /**
     * @return the abststates
     */
    public long getAbsTstates() {
        return absTstates + tstates;
    }
    
    public long getFrames() {
        return frames;
    }

    /**
     * @param abststates the abststates to set
     */
    public void setAbststates(long abststates) {
        this.absTstates = abststates;
    }
    
    public void reset() {
        tstates = step = 0;
        absTstates = frames = 0;
    }
    
    public void endFrame() {
        absTstates += spectrumModel.tstatesFrame;
        frames++;
        tstates %= spectrumModel.tstatesFrame;
        step = 0;
    }

    public void setTimeoutListener(ClockInterface dest) {
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
    
    public void setUpdateScreen(ClockScreen scr, int[] tst) {
        screenListener = scr;
        screenTable = tst;
    }   
}
