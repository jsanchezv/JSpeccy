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
    private int kempston, fuller;
    private Joystick joystick;

    /*
     * Spectrum Keyboard Map
     *
     * PORT  |  BIT 4   3   2   1   0
     * -----------------------------------------
     * 254 (FEh)    V   C   X   Z   CAPS
     * -----------------------------------------
     * 253 (FDh)    G   F   D   S   A
     * -----------------------------------------
     * 251 (FBh)    T   R   E   W   Q
     * -----------------------------------------
     * 247 (F7h)    5   4   3   2   1
     * -----------------------------------------
     * 239 (EFh)    6   7   8   9   0
     * -----------------------------------------
     * 223 (FDh)    Y   U   I   O   P
     * -----------------------------------------
     * 191 (BFh)    H   J   K   L   ENTER
     * -----------------------------------------
     * 127 (7Fh)    B   N   M   SYM BREAK/SPACE
     * -----------------------------------------
     * 
     */
    private static final int KEY_PRESSED_BIT0 = 0xfe;
    private static final int KEY_PRESSED_BIT1 = 0xfd;
    private static final int KEY_PRESSED_BIT2 = 0xfb;
    private static final int KEY_PRESSED_BIT3 = 0xf7;
    private static final int KEY_PRESSED_BIT4 = 0xef;

    private static final int KEY_RELEASED_BIT0 = 0x01;
    private static final int KEY_RELEASED_BIT1 = 0x02;
    private static final int KEY_RELEASED_BIT2 = 0x04;
    private static final int KEY_RELEASED_BIT3 = 0x08;
    private static final int KEY_RELEASED_BIT4 = 0x10;

    public Keyboard() {
        reset();
    }

    public final void reset() {
        Arrays.fill(rowKey, 0xff);
        kempston = 0;
        fuller = 0xff;
    }

    public void setJoystick(Joystick model) {
        joystick = model;
        kempston = 0;
        fuller = 0xff;
    }

    public int readKempstonPort() {
        return kempston;
    }

    public int readFullerPort() {
        return fuller;
    }

    public int readKeyboardPort(int port) {
        int keys = 0xff;
        int res = port >>> 8;

//        System.out.println(String.format("readKeyboardPort: %04X", port));
        switch (res) {
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

    @Override
    public void keyPressed(KeyEvent evt) {

//        char keychar = evt.getKeyChar();
//        boolean done = false;
//
//        if (!evt.isAltDown() && !evt.isControlDown() && keychar != KeyEvent.CHAR_UNDEFINED) {
//            System.out.println("pressed " + keychar);
//            switch (keychar) {
//                case ' ':
//                    rowKey[7] &= PRESS_KEY_BIT_0;
//                    done = true;
//                    break;
//                case '!':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[3] &= PRESS_KEY_BIT_0; // 1
//                    done = true;
//                    break;
//                case '\"':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[5] &= PRESS_KEY_BIT_0; // P
//                    done = true;
//                    break;
//                case '#':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[3] &= 0xfb; // 3
//                    done = true;
//                    break;
//                case '$':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[3] &= 0xf7; // 4
//                    done = true;
//                    break;
//                case '%':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[3] &= 0xef; // 5
//                    done = true;
//                    break;
//                case '&':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[4] &= 0xef; // 6
//                    done = true;
//                    break;
//                case '\'':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[4] &= 0xf7; // 7
//                    done = true;
//                    break;
//                case '(':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[4] &= 0xfb; // 8
//                    done = true;
//                    break;
//                case ')':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[4] &= 0xfd; // 9
//                    done = true;
//                    break;
//                case '*':
//                    rowKey[7] &= 0xed; // Symbol-Shift + B
//                    done = true;
//                    break;
//                case '+':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[6] &= 0xfb; // K
//                    done = true;
//                    break;
//                case ',':
//                    rowKey[7] &= 0xf5; // SYMBOL-SHIFT + N
//                    done = true;
//                    break;
//                 case '-':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[6] &= 0xf7; // J
//                    done = true;
//                    break;
//                 case '.':
//                    rowKey[7] &= 0xf9; // SYMBOL-SHIFT + M
//                    done = true;
//                    break;
//                 case '/':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[0] &= 0xef; // V
//                    done = true;
//                    break;
//                case '0':
//                    rowKey[4] &= PRESS_KEY_BIT_0; // 0
//                    done = true;
//                    break;
//                case '1':
//                    rowKey[3] &= PRESS_KEY_BIT_0; // 1
//                    done = true;
//                    break;
//                case '2':
//                    rowKey[3] &= 0xfd; // 2
//                    done = true;
//                    break;
//                case '3':
//                    rowKey[3] &= 0xfb; // 3
//                    done = true;
//                    break;
//                case '4':
//                    rowKey[3] &= 0xf7; // 4
//                    done = true;
//                    break;
//                case '5':
//                    rowKey[3] &= 0xef; // 5
//                    done = true;
//                    break;
//                case '6':
//                    rowKey[4] &= 0xef; // 6
//                    done = true;
//                    break;
//                case '7':
//                    rowKey[4] &= 0xf7; // 7
//                    done = true;
//                    break;
//                case '8':
//                    rowKey[4] &= 0xfb; // 8
//                    done = true;
//                    break;
//                case '9':
//                    rowKey[4] &= 0xfd; // 9
//                    done = true;
//                    break;
//                case ':':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[0] &= 0xfd; // Z
//                    done = true;
//                    break;
//                case ';':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[5] &= 0xfd; // O
//                    done = true;
//                    break;
//                case '<':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[2] &= 0xf7; // R
//                    done = true;
//                    break;
//                case '=':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[6] &= 0xfd; // L
//                    done = true;
//                    break;
//                case '>':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[2] &= 0xef; // T
//                    done = true;
//                    break;
//                case '?':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[0] &= 0xf7; // C
//                    done = true;
//                    break;
//                case '@':
//                    rowKey[7] &= 0xfd; // SYMBOL-SHIFT
//                    rowKey[3] &= 0xfd; // 2
//                    done = true;
//                    break;
//                case 'A':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[1] &= PRESS_KEY_BIT_0; // A
//                    done = true;
//                    break;
//                case 'B':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[7] &= 0xef; // B
//                    done = true;
//                    break;
//                case 'C':
//                    rowKey[0] &= 0xf6; // Caps Shift + C
//                    done = true;
//                    break;
//                case 'D':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[1] &= 0xfb; // D
//                    done = true;
//                    break;
//                case 'E':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[2] &= 0xfb; // E
//                    done = true;
//                    break;
//                case 'F':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[1] &= 0xf7; // F
//                    done = true;
//                    break;
//                case 'G':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[1] &= 0xef; // G
//                    done = true;
//                    break;
//                case 'H':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[6] &= 0xef; // H
//                    done = true;
//                    break;
//                case 'I':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[5] &= 0xfb; // I
//                    done = true;
//                    break;
//                case 'J':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[6] &= 0xf7; // J
//                    done = true;
//                    break;
//                case 'K':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[6] &= 0xfb; // K
//                    done = true;
//                    break;
//                case 'L':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[6] &= 0xfd; // L
//                    done = true;
//                    break;
//                case 'M':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[7] &= 0xfb; // M
//                    done = true;
//                    break;
//                case 'N':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[7] &= 0xf7; // N
//                    done = true;
//                    break;
//                case 'O':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[5] &= 0xfd; // O
//                    done = true;
//                    break;
//                case 'P':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[5] &= PRESS_KEY_BIT_0; // P
//                    done = true;
//                    break;
//                case 'Q':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[2] &= PRESS_KEY_BIT_0; // Q
//                    done = true;
//                    break;
//                case 'R':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[2] &= 0xf7; // R
//                    done = true;
//                    break;
//                case 'S':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[1] &= 0xfd; // S
//                    done = true;
//                    break;
//                case 'T':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[2] &= 0xef; // T
//                    done = true;
//                    break;
//                case 'U':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[5] &= 0xf7; // U
//                    done = true;
//                    break;
//                case 'V':
//                    rowKey[0] &= 0xee; // Caps Shift + V
//                    done = true;
//                    break;
//                case 'W':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[2] &= 0xfd; // W
//                    done = true;
//                    break;
//                case 'X':
//                    rowKey[0] &= 0xfa; // Caps Shift + X
//                    done = true;
//                    break;
//                case 'Y':
//                    rowKey[0] &= PRESS_KEY_BIT_0; // Caps Shift
//                    rowKey[5] &= 0xef; // Y
//                    done = true;
//                    break;
//                case 'Z':
//                    rowKey[0] &= 0xfc; // Caps Shift + Z
//                    done = true;
//                    break;
//                case '[':
//                case '\\':
//                case ']':
//                case '^':
//                    // Aren't mappeable
//                    break;
//                case '_':
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[4] &= PRESS_KEY_BIT_0; // 0
//                    done = true;
//                    break;
//                case '`':
//                    // Isn't mappeable
//                    break;
//                case 'a':
//                    rowKey[1] &= PRESS_KEY_BIT_0; // A
//                    done = true;
//                    break;
//                case 'b':
//                    rowKey[7] &= 0xef; // B
//                    done = true;
//                    break;
//                case 'c':
//                    rowKey[0] &= 0xf7; // C
//                    done = true;
//                    break;
//                case 'd':
//                    rowKey[1] &= 0xfb; // D
//                    done = true;
//                    break;
//                case 'e':
//                    rowKey[2] &= 0xfb; // E
//                    done = true;
//                    break;
//                case 'f':
//                    rowKey[1] &= 0xf7; // F
//                    done = true;
//                    break;
//                case 'g':
//                    rowKey[1] &= 0xef; // G
//                    done = true;
//                    break;
//                case 'h':
//                    rowKey[6] &= 0xef; // H
//                    done = true;
//                    break;
//                case 'i':
//                    rowKey[5] &= 0xfb; // I
//                    done = true;
//                    break;
//                case 'j':
//                    rowKey[6] &= 0xf7; // J
//                    done = true;
//                    break;
//                case 'k':
//                    rowKey[6] &= 0xfb; // K
//                    done = true;
//                    break;
//                case 'l':
//                    rowKey[6] &= 0xfd; // L
//                    done = true;
//                    break;
//                case 'm':
//                    rowKey[7] &= 0xfb; // M
//                    done = true;
//                    break;
//                case 'n':
//                    rowKey[7] &= 0xf7; // N
//                    done = true;
//                    break;
//                case 'o':
//                    rowKey[5] &= 0xfd; // O
//                    done = true;
//                    break;
//                case 'p':
//                    rowKey[5] &= PRESS_KEY_BIT_0; // P
//                    done = true;
//                    break;
//                case 'q':
//                    rowKey[2] &= PRESS_KEY_BIT_0; // Q
//                    done = true;
//                    break;
//                case 'r':
//                    rowKey[2] &= 0xf7; // R
//                    done = true;
//                    break;
//                case 's':
//                    rowKey[1] &= 0xfd; // S
//                    done = true;
//                    break;
//                case 't':
//                    rowKey[2] &= 0xef; // T
//                    done = true;
//                    break;
//                case 'u':
//                    rowKey[5] &= 0xf7; // U
//                    done = true;
//                    break;
//                case 'v':
//                    rowKey[0] &= 0xef; // V
//                    done = true;
//                    break;
//                case 'w':
//                    rowKey[2] &= 0xfd; // W
//                    done = true;
//                    break;
//                case 'x':
//                    rowKey[0] &= 0xfb; // X
//                    done = true;
//                    break;
//                case 'y':
//                    rowKey[5] &= 0xef; // Y
//                    done = true;
//                    break;
//                case 'z':
//                    rowKey[0] &= 0xfd; // Z
//                    done = true;
//                    break;
//                case 0xA3:  // Pound sign
//                    rowKey[7] &= 0xfd; // Symbol-Shift
//                    rowKey[0] &= 0xfb; // X
//                    done = true;
//                    break;
//            }
//        }
//
//        if (done)
//            return;

        int key = evt.getKeyCode();
        switch (key) {
            // Row B - Break/Space
            case KeyEvent.VK_SPACE:
                rowKey[7] &= KEY_PRESSED_BIT0; // Break/Space
                break;
            case KeyEvent.VK_ALT:
                rowKey[7] &= KEY_PRESSED_BIT1; // Symbol-Shift
                break;
            case KeyEvent.VK_M:
                rowKey[7] &= KEY_PRESSED_BIT2; // M
                break;
            case KeyEvent.VK_N:
                rowKey[7] &= KEY_PRESSED_BIT3; // N
                break;
            case KeyEvent.VK_B:
                rowKey[7] &= KEY_PRESSED_BIT4; // B
                break;
            // Row ENTER - H
            case KeyEvent.VK_ENTER:
                rowKey[6] &= KEY_PRESSED_BIT0; // ENTER
                break;
            case KeyEvent.VK_L:
                rowKey[6] &= KEY_PRESSED_BIT1; // L
                break;
            case KeyEvent.VK_K:
                rowKey[6] &= KEY_PRESSED_BIT2; // K
                break;
            case KeyEvent.VK_J:
                rowKey[6] &= KEY_PRESSED_BIT3; // J
                break;
            case KeyEvent.VK_H:
                rowKey[6] &= KEY_PRESSED_BIT4; // H
                break;
            // Row P - Y
            case KeyEvent.VK_P:
                rowKey[5] &= KEY_PRESSED_BIT0; // P
                break;
            case KeyEvent.VK_O:
                rowKey[5] &= KEY_PRESSED_BIT1; // O
                break;
            case KeyEvent.VK_I:
                rowKey[5] &= KEY_PRESSED_BIT2; // I
                break;
            case KeyEvent.VK_U:
                rowKey[5] &= KEY_PRESSED_BIT3; // U
                break;
            case KeyEvent.VK_Y:
                rowKey[5] &= KEY_PRESSED_BIT4; // Y
                break;
            // Row 0 - 6
            case KeyEvent.VK_0:
                rowKey[4] &= KEY_PRESSED_BIT0; // 0
                break;
            case KeyEvent.VK_9:
                rowKey[4] &= KEY_PRESSED_BIT1; // 9
                break;
            case KeyEvent.VK_8:
                rowKey[4] &= KEY_PRESSED_BIT2; // 8
                break;
            case KeyEvent.VK_7:
                rowKey[4] &= KEY_PRESSED_BIT3; // 7
                break;
            case KeyEvent.VK_6:
                rowKey[4] &= KEY_PRESSED_BIT4; // 6
                break;
            // Row 1 - 5
            case KeyEvent.VK_1:
                rowKey[3] &= KEY_PRESSED_BIT0; // 1
                break;
            case KeyEvent.VK_2:
                rowKey[3] &= KEY_PRESSED_BIT1; // 2
                break;
            case KeyEvent.VK_3:
                rowKey[3] &= KEY_PRESSED_BIT2; // 3
                break;
            case KeyEvent.VK_4:
                rowKey[3] &= KEY_PRESSED_BIT3; // 4
                break;
            case KeyEvent.VK_5:
                rowKey[3] &= KEY_PRESSED_BIT4; // 5
                break;
            // Row Q - T
            case KeyEvent.VK_Q:
                rowKey[2] &= KEY_PRESSED_BIT0; // Q
                break;
            case KeyEvent.VK_W:
                rowKey[2] &= KEY_PRESSED_BIT1; // W
                break;
            case KeyEvent.VK_E:
                rowKey[2] &= KEY_PRESSED_BIT2; // E
                break;
            case KeyEvent.VK_R:
                rowKey[2] &= KEY_PRESSED_BIT3; // R
                break;
            case KeyEvent.VK_T:
                rowKey[2] &= KEY_PRESSED_BIT4; // T
                break;
            // Row A - G
            case KeyEvent.VK_A:
                rowKey[1] &= KEY_PRESSED_BIT0; // A
                break;
            case KeyEvent.VK_S:
                rowKey[1] &= KEY_PRESSED_BIT1; // S
                break;
            case KeyEvent.VK_D:
                rowKey[1] &= KEY_PRESSED_BIT2; // D
                break;
            case KeyEvent.VK_F:
                rowKey[1] &= KEY_PRESSED_BIT3; // F
                break;
            case KeyEvent.VK_G:
                rowKey[1] &= KEY_PRESSED_BIT4; // G
                break;
            // Row Caps Shift - V
            case KeyEvent.VK_SHIFT:
                rowKey[0] &= KEY_PRESSED_BIT0; // Caps Shift
                break;
            case KeyEvent.VK_Z:
                rowKey[0] &= KEY_PRESSED_BIT1; // Z
                break;
            case KeyEvent.VK_X:
                rowKey[0] &= KEY_PRESSED_BIT2; // X
                break;
            case KeyEvent.VK_C:
                rowKey[0] &= KEY_PRESSED_BIT3; // C
                break;
            case KeyEvent.VK_V:
                rowKey[0] &= KEY_PRESSED_BIT4; // V
                break;
            // Additional keys
            case KeyEvent.VK_BACK_SPACE:
                rowKey[0] &= KEY_PRESSED_BIT0; // CAPS
                rowKey[4] &= KEY_PRESSED_BIT0; // 0
                break;
            case KeyEvent.VK_COMMA:
                rowKey[7] &= (KEY_PRESSED_BIT1 & KEY_PRESSED_BIT3); // SYMBOL-SHIFT + N (',')
                break;
            case KeyEvent.VK_PERIOD:
                rowKey[7] &= (KEY_PRESSED_BIT1 & KEY_PRESSED_BIT2); // SYMBOL-SHIFT + M ('.')
                break;
            case KeyEvent.VK_MINUS:
                rowKey[7] &= KEY_PRESSED_BIT1; // SYMBOL-SHIFT
                rowKey[6] &= KEY_PRESSED_BIT3; // J
                break;
            case KeyEvent.VK_PLUS:
                rowKey[7] &= KEY_PRESSED_BIT1; // SYMBOL-SHIFT
                rowKey[6] &= KEY_PRESSED_BIT2; // K
                break;
            case KeyEvent.VK_EQUALS: // UK Keyboard
                rowKey[7] &= KEY_PRESSED_BIT1; // SYMBOL-SHIFT
                rowKey[6] &= KEY_PRESSED_BIT1; // L
                break;
            case KeyEvent.VK_NUMBER_SIGN: // UK Keyboard
                rowKey[7] &= KEY_PRESSED_BIT1; // SYMBOL-SHIFT
                rowKey[3] &= KEY_PRESSED_BIT2; // 3
                break;
            case KeyEvent.VK_SLASH: // UK Keyboard
                rowKey[7] &= KEY_PRESSED_BIT1; // SYMBOL-SHIFT
                rowKey[0] &= KEY_PRESSED_BIT4; // V
                break;
            case KeyEvent.VK_SEMICOLON: // UK Keyboard
                rowKey[7] &= KEY_PRESSED_BIT1; // SYMBOL-SHIFT
                rowKey[5] &= KEY_PRESSED_BIT1; // O
                break;
            case KeyEvent.VK_ALT_GRAPH:
                rowKey[0] &= KEY_PRESSED_BIT0; // CAPS
                rowKey[7] &= KEY_PRESSED_BIT1; // SYMBOL-SHIFT -- Extended Mode
                break;
            case KeyEvent.VK_CAPS_LOCK:
                rowKey[0] &= KEY_PRESSED_BIT0; // CAPS
                rowKey[3] &= KEY_PRESSED_BIT1; // 2  -- Caps Lock
                break;
            // Joystick emulation
            case KeyEvent.VK_LEFT:
                switch (joystick) {
                    case NONE:
                        rowKey[0] &= KEY_PRESSED_BIT0; // Caps
                    case CURSOR:
                        rowKey[3] &= KEY_PRESSED_BIT4; // 5  -- Left arrow
                        break;
                    case KEMPSTON:
                        kempston |= KEY_RELEASED_BIT1;
                        break;
                    case SINCLAIR1:
                        rowKey[4] &= KEY_PRESSED_BIT4; // 6 -- Left
                        break;
                    case SINCLAIR2:
                        rowKey[3] &= KEY_PRESSED_BIT0; // 1 -- Left
                        break;
                    case FULLER:
                        fuller &= KEY_PRESSED_BIT2;
                        break;
                }
                break;
            case KeyEvent.VK_DOWN:
                switch (joystick) {
                    case NONE:
                        rowKey[0] &= KEY_PRESSED_BIT0; // Caps
                    case CURSOR:
                        rowKey[4] &= KEY_PRESSED_BIT4; // 6  -- Down arrow
                        break;
                    case KEMPSTON:
                        kempston |= KEY_RELEASED_BIT2;
                        break;
                    case SINCLAIR1:
                        rowKey[4] &= KEY_PRESSED_BIT2; // 8 -- Down
                        break;
                    case SINCLAIR2:
                        rowKey[3] &= KEY_PRESSED_BIT2; // 3 -- Down
                        break;
                    case FULLER:
                        fuller &= KEY_PRESSED_BIT1;
                        break;
                }
                break;
            case KeyEvent.VK_UP:
                switch (joystick) {
                    case NONE:
                        rowKey[0] &= KEY_PRESSED_BIT0; // Caps
                    case CURSOR:
                        rowKey[4] &= KEY_PRESSED_BIT3; // 7  -- Up arrow
                        break;
                    case KEMPSTON:
                        kempston |= KEY_RELEASED_BIT3;
                        break;
                    case SINCLAIR1:
                        rowKey[4] &= KEY_PRESSED_BIT1; // 9 -- Up
                        break;
                    case SINCLAIR2:
                        rowKey[3] &= KEY_PRESSED_BIT3; // 4 -- Up
                        break;
                    case FULLER:
                        fuller &= KEY_PRESSED_BIT0;
                        break;
                }
                break;
            case KeyEvent.VK_RIGHT:
                switch (joystick) {
                    case NONE:
                        rowKey[0] &= KEY_PRESSED_BIT0; // Caps
                    case CURSOR:
                        rowKey[4] &= KEY_PRESSED_BIT2; // 8  -- Right arrow
                        break;
                    case KEMPSTON:
                        kempston |= KEY_RELEASED_BIT0;
                        break;
                    case SINCLAIR1:
                        rowKey[4] &= KEY_PRESSED_BIT3; // 7 -- Right
                        break;
                    case SINCLAIR2:
                        rowKey[3] &= KEY_PRESSED_BIT1; // 2 -- Right
                        break;
                    case FULLER:
                        fuller &= KEY_PRESSED_BIT3;
                        break;
                }
                break;
            case KeyEvent.VK_CONTROL:
                switch (joystick) {
                    case NONE:
                        break;
                    case KEMPSTON:
                        kempston |= KEY_RELEASED_BIT4;
                        break;
                    case CURSOR:
                    case SINCLAIR1:
                        rowKey[4] &= KEY_PRESSED_BIT0; // 0 -- Fire
                        break;
                    case SINCLAIR2:
                        rowKey[3] &= KEY_PRESSED_BIT4; // 5 -- Fire
                        break;
                    case FULLER:
                        fuller &= 0x7f;
                        break;
                }
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent evt) {

//        char keychar = evt.getKeyChar();
//        boolean done = false;
//
//        if (!evt.isAltDown() && !evt.isControlDown() && keychar != KeyEvent.CHAR_UNDEFINED) {
//            System.out.println("released " + keychar);
//            switch (keychar) {
//                case ' ':
//                    rowKey[7] |= KEY_RELEASED_BIT0; //Spacebar
//                    done = true;
//                    break;
//                case '!':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[3] |= KEY_RELEASED_BIT0; // 1
//                    done = true;
//                    break;
//                case '\"':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[5] |= KEY_RELEASED_BIT0; // P
//                    done = true;
//                    break;
//                case '#':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[3] |= KEY_RELEASED_BIT2; // 3
//                    done = true;
//                    break;
//                case '$':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[3] |= KEY_RELEASED_BIT3; // 4
//                    done = true;
//                    break;
//                case '%':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[3] |= KEY_RELEASED_BIT4; // 5
//                    done = true;
//                    break;
//                case '&':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[4] |= KEY_RELEASED_BIT4; // 6
//                    done = true;
//                    break;
//                case '\'':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[4] |= KEY_RELEASED_BIT3; // 7
//                    done = true;
//                    break;
//                case '(':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[4] |= KEY_RELEASED_BIT2; // 8
//                    done = true;
//                    break;
//                case ')':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[4] |= KEY_RELEASED_BIT1; // 9
//                    done = true;
//                    break;
//                case '*':
//                    rowKey[7] |= 0x12; // Symbol-Shift + B
//                    done = true;
//                    break;
//                case '+':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[6] |= KEY_RELEASED_BIT2; // K
//                    done = true;
//                    break;
//                case ',':
//                    rowKey[7] |= 0x0A; // SYMBOL-SHIFT + N
////                    rowKey[5] |= KEY_RELEASED_BIT1; // O (mapeado del símbolo ';')
//                    done = true;
//                    break;
//                case '-':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[6] |= KEY_RELEASED_BIT3; // J
////                    rowKey[4] |= KEY_RELEASED_BIT0; // 0 (mapeado del símbolo '_')
//                    done = true;
//                    break;
//                case '.':
//                    rowKey[7] |= 0x06; // SYMBOL-SHIFT + M
////                    rowKey[0] |= KEY_RELEASED_BIT1; // Z (mapeado del símbolo ':')
//                    done = true;
//                    break;
//                case '/':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[0] |= KEY_RELEASED_BIT4; // V
//                    done = true;
//                    break;
//                case '0':
//                    rowKey[4] |= KEY_RELEASED_BIT0; // 0
//                    done = true;
//                    break;
//                case '1':
//                    rowKey[3] |= KEY_RELEASED_BIT0; // 1
//                    done = true;
//                    break;
//                case '2':
//                    rowKey[3] |= KEY_RELEASED_BIT1; // 2
////                    rowKey[5] |= KEY_RELEASED_BIT0; // P ('"')
//                    done = true;
//                    break;
//                case '3':
//                    rowKey[3] |= KEY_RELEASED_BIT2; // 3
//                    done = true;
//                    break;
//                case '4':
//                    rowKey[3] |= KEY_RELEASED_BIT3; // 4
//                    done = true;
//                    break;
//                case '5':
//                    rowKey[3] |= KEY_RELEASED_BIT4; // 5
//                    done = true;
//                    break;
//                case '6':
//                    rowKey[4] |= KEY_RELEASED_BIT4; // 6
//                    done = true;
//                    break;
//                case '7':
//                    rowKey[4] |= KEY_RELEASED_BIT3; // 7
////                    rowKey[0] |= KEY_RELEASED_BIT4; // V ('/')
//                    done = true;
//                    break;
//                case '8':
//                    rowKey[4] |= KEY_RELEASED_BIT2; // 8
//                    done = true;
//                    break;
//                case '9':
//                    rowKey[4] |= KEY_RELEASED_BIT1; // 9
//                    done = true;
//                    break;
//                case ':':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[0] |= KEY_RELEASED_BIT1; // Z
//                    done = true;
//                    break;
//                case ';':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[5] |= KEY_RELEASED_BIT1; // O
//                    done = true;
//                    break;
//                case '<':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[2] |= KEY_RELEASED_BIT3; // R
//                    done = true;
//                    break;
//                case '=':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[6] |= KEY_RELEASED_BIT1; // L
//                    done = true;
//                    break;
//                case '>':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[2] |= KEY_RELEASED_BIT4; // T
//                    done = true;
//                    break;
//                case '?':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[0] |= KEY_RELEASED_BIT3; // C
//                    done = true;
//                    break;
//                case '@':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
//                    rowKey[3] |= KEY_RELEASED_BIT1; // 2
//                    done = true;
//                    break;
//                case 'A':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[1] |= KEY_RELEASED_BIT0; // A
//                    done = true;
//                    break;
//                case 'B':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[7] |= KEY_RELEASED_BIT4; // B
//                    done = true;
//                    break;
//                case 'C':
//                    rowKey[0] |= 0x09; // Caps Shift + C
//                    done = true;
//                    break;
//                case 'D':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[1] |= KEY_RELEASED_BIT2; // D
//                    done = true;
//                    break;
//                case 'E':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[2] |= KEY_RELEASED_BIT2; // E
//                    done = true;
//                    break;
//                case 'F':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[1] |= KEY_RELEASED_BIT3; // F
//                    done = true;
//                    break;
//                case 'G':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[1] |= KEY_RELEASED_BIT4; // G
//                    done = true;
//                    break;
//                case 'H':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[6] |= KEY_RELEASED_BIT4; // H
//                    done = true;
//                    break;
//                case 'I':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[5] |= KEY_RELEASED_BIT2; // I
//                    done = true;
//                    break;
//                case 'J':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[6] |= KEY_RELEASED_BIT3; // J
//                    done = true;
//                    break;
//                case 'K':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[6] |= KEY_RELEASED_BIT2; // K
//                    done = true;
//                    break;
//                case 'L':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[6] |= KEY_RELEASED_BIT1; // L
//                    done = true;
//                    break;
//                case 'M':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[7] |= KEY_RELEASED_BIT2; // M
//                    done = true;
//                    break;
//                case 'N':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[7] |= KEY_RELEASED_BIT3; // N
//                    done = true;
//                    break;
//                case 'O':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[5] |= KEY_RELEASED_BIT1; // O
//                    done = true;
//                    break;
//                case 'P':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[5] |= KEY_RELEASED_BIT0; // P
//                    done = true;
//                    break;
//                case 'Q':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[2] |= KEY_RELEASED_BIT0; // Q
//                    done = true;
//                    break;
//                case 'R':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[2] |= KEY_RELEASED_BIT3; // R
//                    done = true;
//                    break;
//                case 'S':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[1] |= KEY_RELEASED_BIT1; // S
//                    done = true;
//                    break;
//                case 'T':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[2] |= KEY_RELEASED_BIT4; // T
//                    done = true;
//                    break;
//                case 'U':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[5] |= KEY_RELEASED_BIT3; // U
//                    done = true;
//                    break;
//                case 'V':
//                    rowKey[0] |= 0x11; // Caps Shift + V
//                    done = true;
//                    break;
//                case 'W':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[2] |= KEY_RELEASED_BIT1; // W
//                    done = true;
//                    break;
//                case 'X':
//                    rowKey[0] |= 0x05; // Caps Shift + X
//                    done = true;
//                    break;
//                case 'Y':
//                    rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
//                    rowKey[5] |= KEY_RELEASED_BIT4; // Y
//                    done = true;
//                    break;
//                case 'Z':
//                    rowKey[0] |= 0x03; // Caps Shift + Z
//                    done = true;
//                    break;
//                case '[':
//                case '\\':
//                case ']':
//                case '^':
//                    // Aren't mappeable
//                    break;
//                case '_':
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[4] |= KEY_RELEASED_BIT0; // 0
//                    done = true;
//                    break;
//                case '`':
//                    // Isn't mappeable
//                    break;
//                case 'a':
//                    rowKey[1] |= KEY_RELEASED_BIT0; // A
//                    done = true;
//                    break;
//                case 'b':
//                    rowKey[7] |= KEY_RELEASED_BIT4; // B
//                    done = true;
//                    break;
//                case 'c':
//                    rowKey[0] |= KEY_RELEASED_BIT3; // C
//                    done = true;
//                    break;
//                case 'd':
//                    rowKey[1] |= KEY_RELEASED_BIT2; // D
//                    done = true;
//                    break;
//                case 'e':
//                    rowKey[2] |= KEY_RELEASED_BIT2; // E
//                    done = true;
//                    break;
//                case 'f':
//                    rowKey[1] |= KEY_RELEASED_BIT3; // F
//                    done = true;
//                    break;
//                case 'g':
//                    rowKey[1] |= KEY_RELEASED_BIT4; // G
//                    done = true;
//                    break;
//                case 'h':
//                    rowKey[6] |= KEY_RELEASED_BIT4; // H
//                    done = true;
//                    break;
//                case 'i':
//                    rowKey[5] |= KEY_RELEASED_BIT2; // I
//                    done = true;
//                    break;
//                case 'j':
//                    rowKey[6] |= KEY_RELEASED_BIT3; // J
//                    done = true;
//                    break;
//                case 'k':
//                    rowKey[6] |= KEY_RELEASED_BIT2; // K
//                    done = true;
//                    break;
//                case 'l':
//                    rowKey[6] |= KEY_RELEASED_BIT1; // L
//                    done = true;
//                    break;
//                case 'm':
//                    rowKey[7] |= KEY_RELEASED_BIT2; // M
//                    done = true;
//                    break;
//                case 'n':
//                    rowKey[7] |= KEY_RELEASED_BIT3; // N
//                    done = true;
//                    break;
//                case 'o':
//                    rowKey[5] |= KEY_RELEASED_BIT1; // O
//                    done = true;
//                    break;
//                case 'p':
//                    rowKey[5] |= KEY_RELEASED_BIT0; // P
//                    done = true;
//                    break;
//                case 'q':
//                    rowKey[2] |= KEY_RELEASED_BIT0; // Q
//                    done = true;
//                    break;
//                case 'r':
//                    rowKey[2] |= KEY_RELEASED_BIT3; // R
//                    done = true;
//                    break;
//                case 's':
//                    rowKey[1] |= KEY_RELEASED_BIT1; // S
//                    done = true;
//                    break;
//                case 't':
//                    rowKey[2] |= KEY_RELEASED_BIT4; // T
//                    done = true;
//                    break;
//                case 'u':
//                    rowKey[5] |= KEY_RELEASED_BIT3; // U
//                    done = true;
//                    break;
//                case 'v':
//                    rowKey[0] |= KEY_RELEASED_BIT4; // V
//                    done = true;
//                    break;
//                case 'w':
//                    rowKey[2] |= KEY_RELEASED_BIT1; // W
//                    done = true;
//                    break;
//                case 'x':
//                    rowKey[0] |= KEY_RELEASED_BIT2; // X
//                    done = true;
//                    break;
//                case 'y':
//                    rowKey[5] |= KEY_RELEASED_BIT4; // Y
//                    done = true;
//                    break;
//                case 'z':
//                    rowKey[0] |= KEY_RELEASED_BIT1; // Z
//                    done = true;
//                    break;
//                case 0xA3: // Pound sign
//                    rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
//                    rowKey[0] |= KEY_RELEASED_BIT2; // X
//                    done = true;
//                    break;
//            }
//        }
//
//        if (done)
//            return;

        int key = evt.getKeyCode();
        switch (key) {
            // Row Break/Space - B
            case KeyEvent.VK_SPACE:
                rowKey[7] |= KEY_RELEASED_BIT0; // Break/Space
                break;
            case KeyEvent.VK_ALT:
                rowKey[7] |= KEY_RELEASED_BIT1; // Symbol-Shift
                break;
            case KeyEvent.VK_M:
                rowKey[7] |= KEY_RELEASED_BIT2; // M
                break;
            case KeyEvent.VK_N:
                rowKey[7] |= KEY_RELEASED_BIT3; // N
                break;
            case KeyEvent.VK_B:
                rowKey[7] |= KEY_RELEASED_BIT4; // B
                break;
            // Row ENTER - H
            case KeyEvent.VK_ENTER:
                rowKey[6] |= KEY_RELEASED_BIT0; // ENTER
                break;
            case KeyEvent.VK_L:
                rowKey[6] |= KEY_RELEASED_BIT1; // L
                break;
            case KeyEvent.VK_K:
                rowKey[6] |= KEY_RELEASED_BIT2; // K
                break;
            case KeyEvent.VK_J:
                rowKey[6] |= KEY_RELEASED_BIT3; // J
                break;
            case KeyEvent.VK_H:
                rowKey[6] |= KEY_RELEASED_BIT4; // H
                break;
            // Row P - Y
            case KeyEvent.VK_P:
                rowKey[5] |= KEY_RELEASED_BIT0; // P
                break;
            case KeyEvent.VK_O:
                rowKey[5] |= KEY_RELEASED_BIT1; // O
                break;
            case KeyEvent.VK_I:
                rowKey[5] |= KEY_RELEASED_BIT2; // I
                break;
            case KeyEvent.VK_U:
                rowKey[5] |= KEY_RELEASED_BIT3; // U
                break;
            case KeyEvent.VK_Y:
                rowKey[5] |= KEY_RELEASED_BIT4; // Y
                break;
            // Row 0 - 6
            case KeyEvent.VK_0:
                rowKey[4] |= KEY_RELEASED_BIT0; // 0
                break;
            case KeyEvent.VK_9:
                rowKey[4] |= KEY_RELEASED_BIT1; // 9
                break;
            case KeyEvent.VK_8:
                rowKey[4] |= KEY_RELEASED_BIT2; // 8
                break;
            case KeyEvent.VK_7:
                rowKey[4] |= KEY_RELEASED_BIT3; // 7
                break;
            case KeyEvent.VK_6:
                rowKey[4] |= KEY_RELEASED_BIT4; // 6
                break;
            // Row 1 - 5
            case KeyEvent.VK_1:
                rowKey[3] |= KEY_RELEASED_BIT0; // 1
                break;
            case KeyEvent.VK_2:
                rowKey[3] |= KEY_RELEASED_BIT1; // 2
                break;
            case KeyEvent.VK_3:
                rowKey[3] |= KEY_RELEASED_BIT2; // 3
                break;
            case KeyEvent.VK_4:
                rowKey[3] |= KEY_RELEASED_BIT3; // 4
                break;
            case KeyEvent.VK_5:
                rowKey[3] |= KEY_RELEASED_BIT4; // 5
                break;
            // Row Q - T
            case KeyEvent.VK_Q:
                rowKey[2] |= KEY_RELEASED_BIT0; // Q
                break;
            case KeyEvent.VK_W:
                rowKey[2] |= KEY_RELEASED_BIT1; // W
                break;
            case KeyEvent.VK_E:
                rowKey[2] |= KEY_RELEASED_BIT2; // E
                break;
            case KeyEvent.VK_R:
                rowKey[2] |= KEY_RELEASED_BIT3; // R
                break;
            case KeyEvent.VK_T:
                rowKey[2] |= KEY_RELEASED_BIT4; // T
                break;
            // Row A - G
            case KeyEvent.VK_A:
                rowKey[1] |= KEY_RELEASED_BIT0; // A
                break;
            case KeyEvent.VK_S:
                rowKey[1] |= KEY_RELEASED_BIT1; // S
                break;
            case KeyEvent.VK_D:
                rowKey[1] |= KEY_RELEASED_BIT2; // D
                break;
            case KeyEvent.VK_F:
                rowKey[1] |= KEY_RELEASED_BIT3; // F
                break;
            case KeyEvent.VK_G:
                rowKey[1] |= KEY_RELEASED_BIT4; // G
                break;
            // Row Caps Shift - V
            case KeyEvent.VK_SHIFT:
                rowKey[0] |= KEY_RELEASED_BIT0; // Caps Shift
                break;
            case KeyEvent.VK_Z:
                rowKey[0] |= KEY_RELEASED_BIT1; // Z
                break;
            case KeyEvent.VK_X:
                rowKey[0] |= KEY_RELEASED_BIT2; // X
                break;
            case KeyEvent.VK_C:
                rowKey[0] |= KEY_RELEASED_BIT3; // C
                break;
            case KeyEvent.VK_V:
                rowKey[0] |= KEY_RELEASED_BIT4; // V
                break;
            // Additional keys
            case KeyEvent.VK_BACK_SPACE:
                rowKey[0] |= KEY_RELEASED_BIT0; // CAPS
                rowKey[4] |= KEY_RELEASED_BIT0; // 0
                break;
            case KeyEvent.VK_COMMA:
                rowKey[7] |= (KEY_RELEASED_BIT1 | KEY_RELEASED_BIT3); // SYMBOL-SHIFT + N
                break;
            case KeyEvent.VK_PERIOD:
                rowKey[7] |= (KEY_RELEASED_BIT1 | KEY_RELEASED_BIT2); // SYMBOL-SHIFT + M
                break;
            case KeyEvent.VK_MINUS:
                rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
                rowKey[6] |= KEY_RELEASED_BIT3; // J
                break;
            case KeyEvent.VK_PLUS:
                rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
                rowKey[6] |= KEY_RELEASED_BIT2; // K
                break;
            case KeyEvent.VK_EQUALS: // UK Keyboard
                rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
                rowKey[6] |= KEY_RELEASED_BIT1; // L
                break;
            case KeyEvent.VK_NUMBER_SIGN: // UK Keyboard
                rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
                rowKey[3] |= KEY_RELEASED_BIT2; // 3
                break;
            case KeyEvent.VK_SLASH: // UK Keyboard
                rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
                rowKey[0] |= KEY_RELEASED_BIT4; // V
                break;
            case KeyEvent.VK_SEMICOLON: // UK Keyboard
                rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
                rowKey[5] |= KEY_RELEASED_BIT1; // O
                break;
            case KeyEvent.VK_ALT_GRAPH:
//            case KeyEvent.VK_SHIFT:
                rowKey[0] |= KEY_RELEASED_BIT0; // CAPS
                rowKey[7] |= KEY_RELEASED_BIT1; // SYMBOL-SHIFT
                break;
            case KeyEvent.VK_CAPS_LOCK:
                rowKey[0] |= KEY_RELEASED_BIT0; // CAPS
                rowKey[3] |= KEY_RELEASED_BIT1; // 2
                break;
            // Joystick emulation
            case KeyEvent.VK_LEFT:
                switch (joystick) {
                    case NONE:
                        rowKey[0] |= KEY_RELEASED_BIT0; // CAPS
                    case CURSOR:
                        rowKey[3] |= KEY_RELEASED_BIT4; // 5 -- Left arrow
                        break;
                    case KEMPSTON:
                        kempston &= KEY_PRESSED_BIT1;
                        break;
                    case SINCLAIR1:
                        rowKey[4] |= KEY_RELEASED_BIT4; // 6 -- Left
                        break;
                    case SINCLAIR2:
                        rowKey[3] |= KEY_RELEASED_BIT0; // 1 -- Left
                        break;
                    case FULLER:
                        fuller |= KEY_RELEASED_BIT2;
                        break;
                }
                break;
            case KeyEvent.VK_DOWN:
                switch (joystick) {
                    case NONE:
                        rowKey[0] |= KEY_RELEASED_BIT0; // CAPS
                    case CURSOR:
                        rowKey[4] |= KEY_RELEASED_BIT4; // 6 -- Down arrow
                        break;
                    case KEMPSTON:
                        kempston &= KEY_PRESSED_BIT2;
                        break;
                    case SINCLAIR1:
                        rowKey[4] |= KEY_RELEASED_BIT2; // 8 -- Down
                        break;
                    case SINCLAIR2:
                        rowKey[3] |= KEY_RELEASED_BIT2; // 3 -- Down
                        break;
                    case FULLER:
                        fuller |= KEY_RELEASED_BIT1;
                        break;
                }
                break;
            case KeyEvent.VK_UP:
                switch (joystick) {
                    case NONE:
                        rowKey[0] |= KEY_RELEASED_BIT0; // CAPS
                    case CURSOR:
                        rowKey[4] |= KEY_RELEASED_BIT3; // 7  -- Up arrow
                        break;
                    case KEMPSTON:
                        kempston &= KEY_PRESSED_BIT3;
                        break;
                    case SINCLAIR1:
                        rowKey[4] |= KEY_RELEASED_BIT1; // 9 -- Up
                        break;
                    case SINCLAIR2:
                        rowKey[3] |= KEY_RELEASED_BIT3; // 4 -- Up
                        break;
                    case FULLER:
                        fuller |= KEY_RELEASED_BIT0;
                        break;
                }
                break;
            case KeyEvent.VK_RIGHT:
                switch (joystick) {
                    case NONE:
                        rowKey[0] |= KEY_RELEASED_BIT0; // CAPS
                    case CURSOR:
                        rowKey[4] |= KEY_RELEASED_BIT2; // 8 -- Right arrow
                        break;
                    case KEMPSTON:
                        kempston &= KEY_PRESSED_BIT0;
                        break;
                    case SINCLAIR1:
                        rowKey[4] |= KEY_RELEASED_BIT3; // 7 -- Right
                        break;
                    case SINCLAIR2:
                        rowKey[3] |= KEY_RELEASED_BIT1; // 2 -- Right
                        break;
                    case FULLER:
                        fuller |= KEY_RELEASED_BIT3;
                        break;
                }
                break;
            case KeyEvent.VK_CONTROL:
                switch (joystick) {
                    case NONE:
                        break;
                    case KEMPSTON:
                        kempston &= KEY_PRESSED_BIT4;
                        break;
                    case CURSOR:
                    case SINCLAIR1:
                        rowKey[4] |= KEY_RELEASED_BIT0;  // 0 -- Fire
                        break;
                    case SINCLAIR2:
                        rowKey[3] |= KEY_RELEASED_BIT4;  // 5  -- Fire
                        break;
                    case FULLER:
                        fuller |= 0x80;
                        break;
                }
                break;
        }
    }

    @Override
    public void keyTyped(java.awt.event.KeyEvent evt) {
        // TODO add your handling code here:
    }
}
