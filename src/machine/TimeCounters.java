/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

/**
 *
 * @author jsanchez
 */
public class TimeCounters {
    private MachineTypes spectrumModel;
    public int tstates;
    private long absTstates, frames;

    public TimeCounters() {
        spectrumModel = MachineTypes.SPECTRUM48K;
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
    
}
