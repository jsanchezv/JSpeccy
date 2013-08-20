/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jsanchez
 */
public class SleeperThread implements Runnable {
    /*
     * Esto es necesario para conseguir un mejor funcionamiento en Windows.
     * La razón del hack está explicada en:
     * http://blogs.oracle.com/dholmes/entry/inside_the_hotspot_vm_clocks
     * y en
     * http://www.javamex.com/tutorials/threads/sleep.shtml
     * 
     */

    @Override
    public void run() {
        try {
            while (true)
                Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException excpt) {
            Logger.getLogger(SleeperThread.class.getName()).log(Level.SEVERE, null, excpt);
        }
    }
}
