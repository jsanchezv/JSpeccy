/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gui;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;

/**
 *
 * @author jsanchez
 */
public class GuiComponents {
    public JRadioButtonMenuItem hardwareMenu16k, hardwareMenu48k, hardwareMenu128k,
            hardwareMenuPlus2, hardwareMenuPlus2A, hardwareMenuPlus3;
    public JRadioButtonMenuItem joystickNone, joystickKempston,
            joystickSinclair1, joystickSinclair2, joystickCursor, joystickFuller;
    public JMenuItem insertIF2RomMenu, ejectIF2RomMenu, playTapeMenu;
    public javax.swing.JLabel modelLabel, speedLabel, tapeFilename, tapeIcon;

    public void setInfoLabels(JLabel nameComponent, JLabel speedComponent, JLabel tapeComponent) {
        modelLabel = nameComponent;
        speedLabel = speedComponent;
        tapeFilename = tapeComponent;
    }

    public void setHardwareMenuItems(JRadioButtonMenuItem hw16k, JRadioButtonMenuItem hw48k,
            JRadioButtonMenuItem hw128k, JRadioButtonMenuItem hwPlus2,
            JRadioButtonMenuItem hwPlus2A, JRadioButtonMenuItem hwPlus3) {
        hardwareMenu16k = hw16k;
        hardwareMenu48k = hw48k;
        hardwareMenu128k = hw128k;
        hardwareMenuPlus2 = hwPlus2;
        hardwareMenuPlus2A = hwPlus2A;
        hardwareMenuPlus3 = hwPlus3;
    }

    public void setMenuItems(JMenuItem insert, JMenuItem eject, JMenuItem play) {
        insertIF2RomMenu = insert;
        ejectIF2RomMenu = eject;
        playTapeMenu = play;
    }

    public void setJoystickMenuItems(JRadioButtonMenuItem jNone, JRadioButtonMenuItem jKempston,
            JRadioButtonMenuItem jSinclair1, JRadioButtonMenuItem jSinclair2,
            JRadioButtonMenuItem jCursor, JRadioButtonMenuItem jFuller) {
        joystickNone = jNone;
        joystickKempston = jKempston;
        joystickSinclair1 = jSinclair1;
        joystickSinclair2 = jSinclair2;
        joystickCursor = jCursor;
        joystickFuller = jFuller;
    }

    public void setTapeIcon(javax.swing.JLabel tapeLabel) {
        tapeIcon = tapeLabel;
    }
}
