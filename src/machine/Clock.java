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
    public int tstates;
    private long absTstates, frames;
    ClockInterface target;
    int timeout;

    public Clock() {
        spectrumModel = MachineTypes.SPECTRUM48K;
        target = null;
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
     * @param tstates the tstates to set
     */
    public void setTstates(int tstates) {
        this.tstates = tstates;
        absTstates = frames = 0;
    }
    
    public void addTstates(int tstates) {
        this.tstates += tstates;

        if (timeout <= 0)
            return;
        
        timeout -= tstates;
        if (timeout > 0) {
            return;
        }

//        System.out.println("timeout fired for class " + target.getClass().getName());
        target.clockTimeout();
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
        tstates = 0;
        absTstates = frames = 0;
    }
    
    public void endFrame() {
        absTstates += spectrumModel.tstatesFrame;
        frames++;
        tstates %= spectrumModel.tstatesFrame;
    }
    
    public boolean setTimeout(ClockInterface dest, int tstates) {
        if (dest == null || timeout > 0) {
            System.err.println("Can't set a timeout!");
            return false;
        }

        if (timeout > 0 && target != dest) {
            System.out.println("A timeout was in use!");
            return false;
        }

        target = dest;
        timeout = tstates > 0 ? tstates : 1;

//        System.out.println(String.format("timeout %d set for class %s", timeout, target.getClass().getName()));

        return true;
    }
}
