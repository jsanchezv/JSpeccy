/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package joystickinput;

/**
 *
 * @author jsanchez
 */
public interface JoystickRawListener {
    public void buttonEvent(int joystickId, int buttonId, boolean state);
    public void axisEvent(int joystickId, int axisId, short value);
}
