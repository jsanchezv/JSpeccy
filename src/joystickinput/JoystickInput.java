/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package joystickinput;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jsanchez
 */
public class JoystickInput implements JoystickRawListener{

    int buttons[] = new int[2];
    int axis[] = new int[2];
    private void doTest() throws InterruptedException {
        JoystickRaw joy1 = null, joy2 = null;

        try {
            joy1 = new JoystickRaw(0);
            System.out.println(String.format("Joystick 1:[%s] found with %d buttons and %d axis",
                    joy1.toString(), joy1.getNumButtons(), joy1.getNumAxis()));
            joy1.addButtonListener(this);
            joy1.addAxisListener(this);
            joy1.start();
            joy2 = new JoystickRaw(1);
            System.out.println(String.format("Joystick 2:[%s] found with %d buttons and %d axis",
                    joy2.toString(), joy2.getNumButtons(), joy2.getNumAxis()));
            joy2.addButtonListener(this);
            joy2.addAxisListener(this);
            joy2.start();
            Thread.sleep(100);
        } catch (IOException ex) {
            Logger.getLogger(JoystickInput.class.getName()).log(Level.SEVERE, null, ex);
            if (joy1 == null && joy2 == null)
                return;
        }

        synchronized (this) {
            wait();
        }

        joy1.stop();
        System.out.println(String.format("Joystick 1 Button events: %d, Axis events: %d", buttons[0], axis[0]));
        if (joy2 != null) {
            joy2.stop();
            System.out.println(String.format("Joystick 2 Button events: %d, Axis events: %d", buttons[1], axis[1]));
        }
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        new JoystickInput().doTest();
    }

    @Override
    public void buttonEvent(int joystickId, int buttonId, boolean state) {
        buttons[joystickId]++;
        if (buttonId == 16) {
            synchronized(this) {
                notify();
            }
        }
        System.out.println(String.format("Joystick %d: Button %d %s.", joystickId, buttonId,
                state ? "pressed" : "released"));
    }

    @Override
    public void axisEvent(int joystickId, int axisId, short value) {
        axis[joystickId]++;
//        if (axisId < 16)
            System.out.println(String.format("Joystick %d: Axis %d pressed. Value = [%d]", joystickId, axisId, value));
    }
}
