/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import java.util.TimerTask;

/**
 *
 * @author jsanchez
 */
public class SpectrumTimer extends TimerTask {

    private Spectrum spectrum;

    public SpectrumTimer(Spectrum spectrum) {
        this.spectrum = spectrum;
    }

    @Override
    public synchronized void run() {

        if (!spectrum.isFrameInProgress()) {
            spectrum.generateFrame();
            spectrum.drawFrame();
        } else {
            notifyAll();
        }

    }
}
