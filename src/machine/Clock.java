/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import gui.JSpeccyScreen;
import java.util.TimerTask;

/**
 *
 * @author jsanchez
 */
public class Clock extends TimerTask {
    private JSpeccyScreen jscr;

    public Clock(JSpeccyScreen jscr) {
        this.jscr = jscr;
    }

    public void run() {
        System.out.println(System.currentTimeMillis());
        jscr.generateFrame();
    }

}
