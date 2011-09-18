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
//        System.out.println(String.format("Standard delay %d ms", diff));
        if (diff < 51) {
            spectrum.generateFrame();
            spectrum.drawFrame();
//        System.out.println(String.format("Frame time: %d", System.currentTimeMillis() - now));
//        if (diff > 50)
//            System.out.println(String.format("Frame delayed by %d ms", (diff - 10)));
//        } else {
//            System.out.println(String.format("At %d: Frame delayed %d ms. Skipped.",
//                    now, now - scheduledExecutionTime()));
        }
    }
}
