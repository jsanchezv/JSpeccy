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
    private static Clock instance = new Clock();
    private MachineTypes spectrumModel = MachineTypes.SPECTRUM48K;
    private int tstates;
    private long frames, absTstates;

    // Clock class implements a Singleton pattern.
    private Clock() {
    }

    public static Clock getInstance() {
        return instance;
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
        frames = absTstates = 0;
    }

    public void addTstates(int states) {
        tstates += states;
    }

    public long getFrames() {
        return frames;
    }

    public void endFrame() {
        frames++;
        tstates %= spectrumModel.tstatesFrame;
        absTstates += spectrumModel.tstatesFrame;
    }

    public long getAbsTstates() {
        return absTstates + tstates;
    }

    public void reset() {
        frames = absTstates = tstates = 0;
    }
}
