/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;
import machine.Spectrum.Joystick;

/**
 *
 * @author jsanchez
 */
public class Keyboard implements KeyListener {
    private int rowKey[] = new int[8];
    private int kempston;
    private Joystick joystick;

    public Keyboard() {
        reset();
    }

    public final void reset() {
        Arrays.fill(rowKey, 0xff);
        kempston = 0;
    }

    public void setJoystick(Joystick model) {
        joystick = model;
        kempston = 0;
    }

    public int readKempstonPort() {
        return kempston;
    }

    public int readKeyboardPort(int port) {
        int keys = 0xff;
        int res = port >>> 8;

//        System.out.println(String.format("readKeyboardPort: %04X", port));
        switch(res) {
            case 0x7f: // SPACE to 'B' row
                return rowKey[7];
            case 0xbf: // ENTER to 'H' row
                return rowKey[6];
            case 0xdf: // 'P' to 'Y' row
                return rowKey[5];
            case 0xef: // '0' to '6' row
                return rowKey[4];
            case 0xf7: // '1' to '5' row
                return rowKey[3];
            case 0xfb: // 'Q' to 'T' row
                return rowKey[2];
            case 0xfd: // 'A' to 'G' row
                return rowKey[1];
            case 0xfe: //  'SHIFT' to 'V' row
                return rowKey[0];
            default:    // reading more than a row
                res = ~res & 0xff;
                for (int row = 0, mask = 0x01; row < 8; row++, mask <<= 1) {
                    if ((res & mask) != 0) {
                        keys &= rowKey[row];
                    }
                }
        }
        return keys;
    }

    public void keyPressed(KeyEvent evt) {

        int key = evt.getKeyCode();

        switch (key) {
            // Fila B - SPACE
            case KeyEvent.VK_SPACE:
                rowKey[7] &= 0xfe;
                break;
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_ALT:
                rowKey[7] &= 0xfd; // Symbol-Shift
                break;
            case KeyEvent.VK_M:
                rowKey[7] &= 0xfb;
                break;
            case KeyEvent.VK_N:
                rowKey[7] &= 0xf7;
                break;
            case KeyEvent.VK_B:
                rowKey[7] &= 0xef;
                break;
            // Fila ENTER - H
            case KeyEvent.VK_ENTER:
                rowKey[6] &= 0xfe;
                break;
            case KeyEvent.VK_L:
                rowKey[6] &= 0xfd;
                break;
            case KeyEvent.VK_K:
                rowKey[6] &= 0xfb;
                break;
            case KeyEvent.VK_J:
                rowKey[6] &= 0xf7;
                break;
            case KeyEvent.VK_H:
                rowKey[6] &= 0xef;
                break;
            // Fila P - Y
            case KeyEvent.VK_P:
                rowKey[5] &= 0xfe;
                break;
            case KeyEvent.VK_O:
                rowKey[5] &= 0xfd;
                break;
            case KeyEvent.VK_I:
                rowKey[5] &= 0xfb;
                break;
            case KeyEvent.VK_U:
                rowKey[5] &= 0xf7;
                break;
            case KeyEvent.VK_Y:
                rowKey[5] &= 0xef;
                break;
            // Fila 0 - 6
            case KeyEvent.VK_0:
                rowKey[4] &= 0xfe;
                break;
            case KeyEvent.VK_9:
                rowKey[4] &= 0xfd;
                break;
            case KeyEvent.VK_8:
                rowKey[4] &= 0xfb;
                break;
            case KeyEvent.VK_7:
                rowKey[4] &= 0xf7;
                break;
            case KeyEvent.VK_6:
                rowKey[4] &= 0xef; // 6
                break;
            // Fila 1 - 5
            case KeyEvent.VK_1:
                rowKey[3] &= 0xfe;
                break;
            case KeyEvent.VK_2:
                rowKey[3] &= 0xfd;
                break;
            case KeyEvent.VK_3:
                rowKey[3] &= 0xfb;
                break;
            case KeyEvent.VK_4:
                rowKey[3] &= 0xf7;
                break;
            case KeyEvent.VK_5:
                rowKey[3] &= 0xef;
                break;
            // Fila Q - T
            case KeyEvent.VK_Q:
                rowKey[2] &= 0xfe;
                break;
            case KeyEvent.VK_W:
                rowKey[2] &= 0xfd;
                break;
            case KeyEvent.VK_E:
                rowKey[2] &= 0xfb;
                break;
            case KeyEvent.VK_R:
                rowKey[2] &= 0xf7;
                break;
            case KeyEvent.VK_T:
                rowKey[2] &= 0xef;
                break;
            // Fila A - G
            case KeyEvent.VK_A:
                rowKey[1] &= 0xfe;
                break;
            case KeyEvent.VK_S:
                rowKey[1] &= 0xfd;
                break;
            case KeyEvent.VK_D:
                rowKey[1] &= 0xfb;
                break;
            case KeyEvent.VK_F:
                rowKey[1] &= 0xf7;
                break;
            case KeyEvent.VK_G:
                rowKey[1] &= 0xef;
                break;
            // Fila CAPS_SHIFT - V
            case KeyEvent.VK_SHIFT:
                rowKey[0] &= 0xfe;
                break;
            case KeyEvent.VK_Z:
                rowKey[0] &= 0xfd;
                break;
            case KeyEvent.VK_X:
                rowKey[0] &= 0xfb;
                break;
            case KeyEvent.VK_C:
                rowKey[0] &= 0xf7;
                break;
            case KeyEvent.VK_V:
                rowKey[0] &= 0xef;
                break;
            // Teclas de conveniencia, para mayor comodidad de uso
            case KeyEvent.VK_BACK_SPACE:
                rowKey[0] &= 0xfe; // CAPS
                rowKey[4] &= 0xfe; // 0
                break;
            case KeyEvent.VK_COMMA:
                rowKey[7] &= 0xf5; // SYMBOL-SHIFT + N
                break;
            case KeyEvent.VK_PERIOD:
                rowKey[7] &= 0xf9; // SYMBOL-SHIFT + M
                break;
            case KeyEvent.VK_MINUS:
                rowKey[7] &= 0xfd; // SYMBOL-SHIFT
                rowKey[6] &= 0xf7; // J
                break;
            case KeyEvent.VK_PLUS:
                rowKey[7] &= 0xfd; // SYMBOL-SHIFT
                rowKey[6] &= 0xfb; // K
                break;
            case KeyEvent.VK_ALT_GRAPH:
                rowKey[0] &= 0xfe; // CAPS
                rowKey[7] &= 0xfd; // SYMBOL-SHIFT -- Extended Mode
                break;
            case KeyEvent.VK_CAPS_LOCK:
                rowKey[0] &= 0xfe; // CAPS
                rowKey[3] &= 0xfd; // 2  -- Caps Lock
                break;
            // Emulación joystick
            case KeyEvent.VK_LEFT:
                switch (joystick) {
                    case NONE:
                        rowKey[0] &= 0xfe; // CAPS
                    case CURSOR:
                        rowKey[3] &= 0xef; // 5  -- Left arrow
                        break;
                    case KEMPSTON:
                        kempston |= 0x02;
                        break;
                    case SINCLAIR1:
                        rowKey[3] &= 0xfe; // 1 -- Left
                        break;
                    case SINCLAIR2:
                        rowKey[4] &= 0xef; // 6  -- Left
                        break;
                }
                break;
            case KeyEvent.VK_DOWN:
                switch (joystick) {
                    case NONE:
                        rowKey[0] &= 0xfe; // CAPS
                    case CURSOR:
                        rowKey[4] &= 0xef; // 6  -- Down arrow
                        break;
                    case KEMPSTON:
                        kempston |= 0x04;
                        break;
                    case SINCLAIR1:
                        rowKey[3] &= 0xfb; // 3 -- Down
                        break;
                    case SINCLAIR2:
                        rowKey[4] &= 0xfb; // 8  -- Down
                        break;
                }
                break;
            case KeyEvent.VK_UP:
                switch (joystick) {
                    case NONE:
                        rowKey[0] &= 0xfe; // CAPS
                    case CURSOR:
                        rowKey[4] &= 0xf7; // 7  -- Up arrow
                        break;
                    case KEMPSTON:
                        kempston |= 0x08;
                        break;
                    case SINCLAIR1:
                        rowKey[3] &= 0xf7; // 4 -- Up
                        break;
                    case SINCLAIR2:
                        rowKey[4] &= 0xfd; // 9 -- Up
                        break;
                }
                break;
            case KeyEvent.VK_RIGHT:
                switch (joystick) {
                    case NONE:
                        rowKey[0] &= 0xfe; // CAPS
                    case CURSOR:
                        rowKey[4] &= 0xfb; // 8  -- Right arrow
                        break;
                    case KEMPSTON:
                        kempston |= 0x01;
                        break;
                    case SINCLAIR1:
                        rowKey[3] &= 0xfd; // 2 -- Right
                        break;
                    case SINCLAIR2:
                        rowKey[4] &= 0xf7; // 7  -- Right
                        break;
                }
                break;
            case KeyEvent.VK_DELETE:
                switch (joystick) {
                    case NONE:
                        break;
                    case CURSOR:
                    case SINCLAIR2:
                        rowKey[4] &= 0xfe; // 0 -- Fire
                        break;
                    case KEMPSTON:
                        kempston |= 0x10;
                        break;
                    case SINCLAIR1:
                        rowKey[3] &= 0xef; // 5 -- Fire
                        break;
                }
                break;
        }
    }

    public void keyReleased(KeyEvent evt) {
        
        int key = evt.getKeyCode();

        switch (key) {
            // Fila SPACE - B
            case KeyEvent.VK_SPACE:
                rowKey[7] |= 0x01; //Spacebar
                break;
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_ALT:
                rowKey[7] |= 0x02; // Symbol-Shift
                break;
            case KeyEvent.VK_M:
                rowKey[7] |= 0x04; // M
                break;
            case KeyEvent.VK_N:
                rowKey[7] |= 0x08; // N
                break;
            case KeyEvent.VK_B:
                rowKey[7] |= 0x10; // B
                break;
            // Fila ENTER - H
            case KeyEvent.VK_ENTER:
                rowKey[6] |= 0x01; // ENTER
                break;
            case KeyEvent.VK_L:
                rowKey[6] |= 0x02; // L
                break;
            case KeyEvent.VK_K:
                rowKey[6] |= 0x04; // K
                break;
            case KeyEvent.VK_J:
                rowKey[6] |= 0x08; // J
                break;
            case KeyEvent.VK_H:
                rowKey[6] |= 0x10; // H
                break;
            // Fila P - Y
            case KeyEvent.VK_P:
                rowKey[5] |= 0x01; // P
                break;
            case KeyEvent.VK_O:
                rowKey[5] |= 0x02; // O
                break;
            case KeyEvent.VK_I:
                rowKey[5] |= 0x04; // I
                break;
            case KeyEvent.VK_U:
                rowKey[5] |= 0x08; // U
                break;
            case KeyEvent.VK_Y:
                rowKey[5] |= 0x10; // Y
                break;
            // Fila 0 - 6
            case KeyEvent.VK_0:
                rowKey[4] |= 0x01; // 0
                break;
            case KeyEvent.VK_9:
                rowKey[4] |= 0x02; // 9
                break;
            case KeyEvent.VK_8:
                rowKey[4] |= 0x04; // 8
                break;
            case KeyEvent.VK_7:
                rowKey[4] |= 0x08; // 7
                break;
            case KeyEvent.VK_6:
                rowKey[4] |= 0x10; // 6
                break;
            // Fila 1 - 5
            case KeyEvent.VK_1:
                rowKey[3] |= 0x01; // 1
                break;
            case KeyEvent.VK_2:
                rowKey[3] |= 0x02; // 2
                break;
            case KeyEvent.VK_3:
                rowKey[3] |= 0x04; // 3
                break;
            case KeyEvent.VK_4:
                rowKey[3] |= 0x08; // 4
                break;
            case KeyEvent.VK_5:
                rowKey[3] |= 0x10; // 5
                break;
            // Fila Q - T
            case KeyEvent.VK_Q:
                rowKey[2] |= 0x01; // Q
                break;
            case KeyEvent.VK_W:
                rowKey[2] |= 0x02; // W
                break;
            case KeyEvent.VK_E:
                rowKey[2] |= 0x04; // E
                break;
            case KeyEvent.VK_R:
                rowKey[2] |= 0x08; // R
                break;
            case KeyEvent.VK_T:
                rowKey[2] |= 0x10; // T
                break;
            // Fila A - G
            case KeyEvent.VK_A:
                rowKey[1] |= 0x01; // A
                break;
            case KeyEvent.VK_S:
                rowKey[1] |= 0x02; // S
                break;
            case KeyEvent.VK_D:
                rowKey[1] |= 0x04; // D
                break;
            case KeyEvent.VK_F:
                rowKey[1] |= 0x08; // F
                break;
            case KeyEvent.VK_G:
                rowKey[1] |= 0x10; // G
                break;
            // Fila CAPS_SHIFT - V
            case KeyEvent.VK_SHIFT:
                rowKey[0] |= 0x01; // CAPS-SHIFT
                break;
            case KeyEvent.VK_Z:
                rowKey[0] |= 0x02; // Z
                break;
            case KeyEvent.VK_X:
                rowKey[0] |= 0x04; // X
                break;
            case KeyEvent.VK_C:
                rowKey[0] |= 0x08; // C
                break;
            case KeyEvent.VK_V:
                rowKey[0] |= 0x10; // V
                break;
            // Teclas de conveniencia
            case KeyEvent.VK_BACK_SPACE:
                rowKey[0] |= 0x01; // CAPS
                rowKey[4] |= 0x01; // 0
                break;
            case KeyEvent.VK_COMMA:
                rowKey[7] |= 0x0A; // SYMBOL-SHIFT + N
                break;
            case KeyEvent.VK_PERIOD:
                rowKey[7] |= 0x06; // SYMBOL-SHIFT + M
                break;
            case KeyEvent.VK_MINUS:
                rowKey[7] |= 0x02; // SYMBOL-SHIFT
                rowKey[6] |= 0x08; // J
                break;
            case KeyEvent.VK_PLUS:
                rowKey[7] |= 0x02; // SYMBOL-SHIFT
                rowKey[6] |= 0x04; // K
                break;
            case KeyEvent.VK_ALT_GRAPH:
                rowKey[0] |= 0x01; // CAPS
                rowKey[7] |= 0x02; // SYMBOL-SHIFT
                break;
            case KeyEvent.VK_CAPS_LOCK:
                rowKey[0] |= 0x01; // CAPS
                rowKey[3] |= 0x02; // 2
                break;
            // Emulación de Joysticks
            case KeyEvent.VK_LEFT:
                switch (joystick) {
                    case NONE:
                        rowKey[0] |= 0x01; // CAPS
                    case CURSOR:
                        rowKey[3] |= 0x10; // 5  -- Left arrow
                        break;
                    case KEMPSTON:
                        kempston &= 0xfd;
                        break;
                    case SINCLAIR1:
                        rowKey[3] |= 0x01; // 1 -- Left
                        break;
                    case SINCLAIR2:
                        rowKey[4] |= 0x10; // 6 -- Left
                        break;
                }
                break;
            case KeyEvent.VK_DOWN:
                switch (joystick) {
                    case NONE:
                        rowKey[0] |= 0x01; // CAPS
                    case CURSOR:
                        rowKey[4] |= 0x10; // 6  -- Down arrow
                        break;
                    case KEMPSTON:
                        kempston &= 0xfb;
                        break;
                    case SINCLAIR1:
                        rowKey[3] |= 0x04; // 3 -- Down
                        break;
                    case SINCLAIR2:
                        rowKey[4] |= 0x04; // 8 -- Down
                        break;
                }
                break;
            case KeyEvent.VK_UP:
                switch (joystick) {
                    case NONE:
                        rowKey[0] |= 0x01; // CAPS
                    case CURSOR:
                        rowKey[4] |= 0x08; // 7  -- Up arrow
                        break;
                    case KEMPSTON:
                        kempston &= 0xf7;
                        break;
                    case SINCLAIR1:
                        rowKey[3] |= 0x08; // 4 -- Up
                        break;
                    case SINCLAIR2:
                        rowKey[4] |= 0x02; // 9 -- Up
                        break;
                }
                break;
            case KeyEvent.VK_RIGHT:
                switch (joystick) {
                    case NONE:
                        rowKey[0] |= 0x01; // CAPS
                    case CURSOR:
                        rowKey[4] |= 0x04; // 8  -- Right arrow
                        break;
                    case KEMPSTON:
                        kempston &= 0xfe;
                        break;
                    case SINCLAIR1:
                        rowKey[3] |= 0x02; // 2 -- Right
                        break;
                    case SINCLAIR2:
                        rowKey[4] |= 0x08; // 7 -- Right
                        break;
                }
                break;
            case KeyEvent.VK_DELETE:
                switch (joystick) {
                    case NONE:
                        break;
                    case CURSOR:
                    case SINCLAIR2:
                        rowKey[4] |= 0x01;   // 0 -- Fire
                        break;
                    case KEMPSTON:
                        kempston &= 0xef;
                        break;
                    case SINCLAIR1:
                        rowKey[3] |= 0x10;  // 5  -- Fire
                        break;
                }
                break;
        }
    }

    public void keyTyped(java.awt.event.KeyEvent evt) {
        // TODO add your handling code here:
    }
}
