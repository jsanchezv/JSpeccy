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

    public void run() {
        spectrum.generateFrame();
    }
}
