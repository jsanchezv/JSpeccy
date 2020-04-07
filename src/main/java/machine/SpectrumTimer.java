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

    private final Spectrum spectrum;

    public SpectrumTimer(Spectrum spectrum) {
        this.spectrum = spectrum;
    }

    @Override
    public void run() {

        // El timer solo nos sirve de metrónomo, pero es indiferente lo que se
        // retrase la señal, ya que el tiempo lo marca la tarjeta de sonido.
        // Lo que sí hay que hacer es purgar todos los eventos demasiado
        // retrasados para que no se acumulen en el sistema.
//        long now = System.currentTimeMillis();
//        System.out.println("Tick delayed: " + (now - scheduledExecutionTime()) + " at frame " + Clock.getInstance().getFrames());
        if (System.currentTimeMillis() - scheduledExecutionTime() < 100)
            synchronized(spectrum) {
                spectrum.notify();
            }
    }
}
