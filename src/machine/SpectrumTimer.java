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
    private long now, diff;

    public SpectrumTimer(Spectrum spectrum) {
        this.spectrum = spectrum;
    }

    @Override
    public synchronized void run() {

        now = System.currentTimeMillis();
        diff = now - scheduledExecutionTime();
        if (diff < 51) {
            spectrum.generateFrame();
            spectrum.drawFrame();
        }
    }
}
