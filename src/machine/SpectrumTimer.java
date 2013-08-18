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
    private long lastTick;

    public SpectrumTimer(Spectrum spectrum) {
        this.spectrum = spectrum;
    }

    @Override
    public synchronized void run() {

        long now = System.currentTimeMillis();
        // Ejecutar el frame siempre que hayan transcurrido al menos 17 ms desde el
        // anterior. El resto, se ignoran.
        if (now - lastTick > 17) {
            spectrum.clockTick();
            lastTick = now;
//        } else {
//            System.out.println(String.format("Tick skipped. Now: %d, last: %d, diff: %d",
//                    now, lastTick, now - lastTick));
        }
    }
}
