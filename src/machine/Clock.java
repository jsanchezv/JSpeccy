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
    ClockInterface clockListener;
    ClockScreen screenListener;
    int timeout;
    int tstatesTable[];
    int step;
    // Constante que indica que no hay un evento próximo
    // El valor de la constante debe ser mayor que cualquier spectrumModel.tstatesframe
    private final int NO_EVENT = 0x1234567;
    // t-states del próximo evento
    int nextEvent = NO_EVENT;

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
     * @param tstates the tstates to set
     */
    public void setTstates(int tstates) {
        this.tstates = tstates;
        absTstates = frames = step = 0;
        nextEvent = tstatesTable[0];
    }
    
    public void addTstates(int tsadd){
        tstates += tsadd;

        if (screenListener != null && tstates >= nextEvent) {
//            System.out.println(String.format("updScr. step = %d, table = %d, tstates = %d", step, tstatesTable[step], tstates));
            screenListener.updateScreen(tstates);
            step++;
            while(step < tstatesTable.length && tstates > tstatesTable[step])
                step++;
            nextEvent = step < tstatesTable.length ? tstatesTable[step] : NO_EVENT;
        }

        if (timeout <= 0)
            return;
        
        timeout -= tsadd;
        if (timeout > 0) {
            return;
        }

//        System.out.println("timeout fired for class " + target.getClass().getName());
        if (clockListener != null)
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
        nextEvent = tstatesTable[0];
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
        tstatesTable = tst;
    }   
}
