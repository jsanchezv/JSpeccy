/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import gui.JSpeccyScreen;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Spectrum implements z80core.MemIoOps, KeyListener {
    private Z80 z80;
    private int z80Ram[] = new int[0x10000];
    public int portMap[] = new int[0x10000];
    // veces que ha cambiado el borde en el último frame
    // tEstados cuando cambió la N vez el borde
    // valor del borde en el cambio N
    public int nTimesBorderChg;
    public int statesBorderChg[] = new int[1024];
    public int valueBorderChg[] = new int[1024];

    private FileInputStream fIn;

    private int frameStart;
    private int nFrame;
    public boolean scrMod;

    public static final int FRAMES48k = 70000;
    private static final byte delayTstates[] = new byte[FRAMES48k];
    static {
        Arrays.fill(delayTstates, (byte)0x00);
        for( int idx = 14335; idx < 57343; idx += 224  ) {
            for( int ndx = 0; ndx < 128; ndx += 8) {
                int frame = idx + ndx;
                delayTstates[frame++] = 6;
                delayTstates[frame++] = 5;
                delayTstates[frame++] = 4;
                delayTstates[frame++] = 3;
                delayTstates[frame++] = 2;
                delayTstates[frame++] = 1;
                delayTstates[frame++] = 0;
                delayTstates[frame++] = 0;
            }
        }
    }
    private Timer timerFrame;
    private SpectrumTimer taskFrame;
    JSpeccyScreen jscr;
    //private final Clock clk;

    public Spectrum() {
        //super("SpectrumThread");
        z80 = new Z80(this);
        loadRom();
        loadSNA("/home/jsanchez/src/JSpeccy/dist/btime.sna");
        frameStart = 0;
        nFrame = 0;
        Arrays.fill(portMap, 0xff);
        taskFrame = new SpectrumTimer(this);
        nTimesBorderChg = 1;
        statesBorderChg[0] = 0;
        valueBorderChg[0] = 7;
    }

    public void startEmulation() {
        timerFrame = new Timer();
        timerFrame.scheduleAtFixedRate(taskFrame, 0, 20);
        z80.tEstados = 0;
    }

    public void stopEmulation() {
        timerFrame.cancel();
    }

    public void generateFrame() {
        //long startFrame, endFrame, sleepTime;
        //startFrame = System.currentTimeMillis();
        //System.out.println("Start frame: " + startFrame);
        scrMod = false;

        z80.tEstados = frameStart;
        z80.statesLimit = 69888;

        z80.interrupcion();
        z80.execute();

        //System.out.println("execFrame: ejecutados " + z80.getTEstados());
        //System.out.println("PC: " + z80.getRegPC());
        frameStart = z80.tEstados - 69888;

        if (++nFrame % 16 == 0) {
            jscr.toggleFlash();
            scrMod = true;
        }
        
//        if( nTimesBorderChg > 0)
//            System.out.println("El borde cambió " + nTimesBorderChg+ " veces");

        if ( nTimesBorderChg != 0 || scrMod ) {
            jscr.repaint();
        }

        //endFrame = System.currentTimeMillis();
        //System.out.println("End frame: " + endFrame);
        //sleepTime = endFrame - startFrame;
        //System.out.println("execFrame en: " + sleepTime);
    }

    public void setScreen(JSpeccyScreen jscr) {
        this.jscr = jscr;
        jscr.toggleDoubleSize();
    }

    private void loadRom() {
        try {
            try {
                fIn = new FileInputStream("/home/jsanchez/src/JSpeccy/dist/spectrum.rom");
            } catch (FileNotFoundException ex) {
                System.out.println("No se pudo abrir el fichero spectrum.rom");
                Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            int count;
            for (count = 0; count < 0x4000; count++) {
                z80Ram[count] = (int) fIn.read() & 0xff;
            }
            if (count != 0x4000) {
                System.out.println("No se pudo cargar la ROM");
                return;
            }

            fIn.close();
        } catch (IOException ex) {
            System.out.println("No se pudo leer el fichero spectrum.rom");
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("ROM cargada");
    }

    public int[] getSpectrumMem() {
        return z80Ram;
    }

//    public void run() {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }

    public int getOpcode(int address) {
        //int delay = 4;

        //System.out.println(String.format(strMC, z80.getTEstados(), address));

        if( (address & 0xC000) == 0x4000 ) {
//            System.out.println(String.format("getOpcode: %d %d %d",
//                    z80.tEstados, address, delayTstates[z80.tEstados]));
            z80.tEstados += delayTstates[z80.tEstados];
                //System.out.println("R(" + dire +"): ");
        }

        if( (z80.getPairIR() & 0xC000) == 0x4000 )
            z80.tEstados += 8;
        else
            z80.tEstados += 4;
        //System.out.println(String.format(strMR, z80.getTEstados(), address, z80Ram[address]));
        return z80Ram[address];
    }

    public int peek8(int address) {
        //int delay = 3;

        //System.out.println(String.format(strMC, z80.getTEstados(), address));
        //address &= 0xffff;
        if( (address & 0xC000) == 0x4000 ) {
            z80.tEstados += delayTstates[z80.tEstados];
                //System.out.println("R(" + dire +"): ");
        }
        z80.tEstados += 3;

        //System.out.println(String.format(strMR, z80.getTEstados(), address, z80Ram[address]));
        return z80Ram[address];
    }

    public void poke8(int address, int value) {
        //int delay = 3;

        //System.out.println(String.format(strMC, z80.getTEstados(), address));

        //address &= 0xffff;
        if( address < 0x4000 ) // en la rom no se escribe (o no sería ROM)
            return;

        if( (address & 0xC000) == 0x4000 ) {
            z80.tEstados += delayTstates[z80.tEstados];
                //System.out.println("R(" + dire +"): ");
        }
        
        if( address < 0x5B00 )
            scrMod = true;

        z80.tEstados += 3;
        z80Ram[address] = value & 0xff;
        //System.out.println(String.format(strMW, z80.getTEstados(), address, z80Ram[address]));
    }

    public int peek16(int address) {
//        int lsb = peek8(address);
//        int msb = peek8(address + 1);
//        return (msb << 8) | lsb;
        int lsb, msb;
        if( (address & 0xC000) == 0x4000 ) {
            z80.tEstados += delayTstates[z80.tEstados];
                //System.out.println("R(" + dire +"): ");
        }
        z80.tEstados += 3;
        //System.out.println(String.format(strMR, z80.getTEstados(), address, z80Ram[address]));
        lsb = z80Ram[address];

        address = (address + 1) & 0xffff;
        if( (address & 0xC000) == 0x4000 ) {
            z80.tEstados += delayTstates[z80.tEstados];
                //System.out.println("R(" + dire +"): ");
        }
        z80.tEstados += 3;

        //System.out.println(String.format(strMR, z80.getTEstados(), address, z80Ram[address]));
        msb = z80Ram[address];
        return (msb << 8) | lsb;
    }

    public void poke16(int address, int word) {
        //poke8(address, word);
        //poke8(address + 1, word >>> 8);
        if( address < 0x4000 ) // en la rom no se escribe (o no sería ROM)
            return;

        if( (address & 0xC000) == 0x4000 ) {
            z80.tEstados += delayTstates[z80.tEstados];
                //System.out.println("R(" + dire +"): ");
        }

        if( address < 0x5B00 )
            scrMod = true;

        z80.tEstados += 3;
        z80Ram[address] = word & 0xff;

        address = (address + 1) & 0xffff;
        if( address < 0x4000 ) // en la rom no se escribe (o no sería ROM)
            return;

        if( (address & 0xC000) == 0x4000 ) {
            z80.tEstados += delayTstates[z80.tEstados];
                //System.out.println("R(" + dire +"): ");
        }

        if( address < 0x5B00 )
            scrMod = true;
        
        z80.tEstados += 3;
        z80Ram[address] = word >>> 8;
    }

    public void contendedStates(int address, int tstates) {
        //address &= 0xffff;
        if( (address & 0xC000) == 0x4000 ) {
            //System.out.println(address + " " + tstates);
            for (int idx = 0; idx < tstates; idx++)
//                System.out.println(String.format("t-States: %d\taddress:%d\ttstates %d",
//                    z80.tEstados, address, tstates));
                z80.tEstados += delayTstates[z80.tEstados] + 1;
        } else {
            z80.tEstados += tstates;
        }
    }

    public int inPort(int port) {
        int res = port >>> 8;
        int keys = 0xff;
        contendedIO(port);
        //System.out.println(String.format("%5d PR %04x %02x", z80.tEstados, port, res));
        if( (port & 0xff) == 0xfe ) {
            switch (res) {
                case 0x7f:
                    keys = portMap[0x7ffe];
                    break;
                case 0xbf:
                    keys = portMap[0xbffe];
                    break;
                case 0xdf:
                    keys = portMap[0xdffe];
                    break;
                case 0xef:
                    keys = portMap[0xeffe];
                    break;
                case 0xf7:
                    keys = portMap[0xf7fe];
                    break;
                case 0xfb:
                    keys = portMap[0xfbfe];
                    break;
                case 0xfd:
                    keys = portMap[0xfdfe];
                    break;
                case 0xfe:
                    keys = portMap[0xfefe];
                    break;
                default:
                    for (int rowKeys = 0x01; rowKeys < 0x100; rowKeys <<= 1) {
//                   System.out.println(String.format("res: %02x rowKeys: %02x portMap: %04x",
//                           res, rowKeys, (((~rowKeys & 0xff) << 8) | 0xfe)));
                        if ((res & rowKeys) == 0) {
                            keys &= portMap[(((~rowKeys & 0xff) << 8) | 0xfe)];
                        }
                    }
            }
            return keys;
        }

        if( (port & 0xff) == 0xff ) {
            int tstates = z80.tEstados;
            if( tstates < 14347 || tstates > 57343 )
                return 0xff;

            int row = tstates / 224 - 64;
            int col = tstates % 224;
            if( col < 11 || col > 139 )
                return 0xff;


            col = col % 8;
            switch( col ) {
                case 3:
                    return z80Ram[jscr.scrAddr[row] + col];
                case 4:
                    return z80Ram[jscr.scr2attr[row] + col];
                case 5:
                    return z80Ram[jscr.scrAddr[row] + col + 1];
                case 6:
                    return z80Ram[jscr.scr2attr[row] + col + 1];
            }
        }
        return 0xff;
    }

    public void outPort(int port, int value) {
        //int tEstados = z80.tEstados;
        contendedIO(port);
//        System.out.println(String.format("%d %5d PW %04x %02x %d",
//                    System.currentTimeMillis(), tEstados, port, value,
//                    (tEstados < oldstate ? (tEstados+69888-oldstate) : (tEstados-oldstate))));
//        oldstate = tEstados;
        if( (port & 0xff) == 0xfe ) {
            if( (portMap[0xfe] & 0x07) != (value & 0x07) ) {
                statesBorderChg[nTimesBorderChg] = z80.tEstados;
                valueBorderChg[nTimesBorderChg] = value & 0x07;
                nTimesBorderChg++;
            }
            portMap[0xfe] = value;   
        }
    }

//    private void preIO(int port) {
//        if( ( port & 0xc000 ) == 0x4000 ) {
//            //System.out.println(String.format(strPC, z80.getTEstados(), port));
//            z80.tEstados += delayTstates[z80.tEstados];
//        }
//        z80.tEstados++;
//    }

    private void contendedIO(int port) {
        if( (port & 0x0001) != 0 ) {
            if( ( port & 0xc000 ) == 0x4000 ) {
                // A0 == 1 y es contended RAM
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados++;
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados++;
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados++;
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados++;
            } else {
                // A0 == 1 y no es contended RAM
                z80.tEstados += 4;
            }
        } else {
            if( ( port & 0xc000 ) == 0x4000 ) {
                // A0 == 0 y es contended RAM
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados++;
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados += 3;
            } else {
                // A0 == 0 y no es contended RAM
                z80.tEstados++;
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados += 3;
            }
        }
//        if( (port & 0x0001) != 0 ) {
//            if( ( port & 0xc000 ) == 0x4000 ) {
//                //System.out.println(String.format(strPC, z80.getTEstados(), port));
//                z80.tEstados++;
//                //System.out.println(String.format(strPC, z80.getTEstados(), port));
//                z80.tEstados++;
//                //System.out.println(String.format(strPC, z80.getTEstados(), port));
//                z80.tEstados++;
//            } else {
//                z80.tEstados += 3;
//            }
//        } else {
//            //System.out.println(String.format(strPC, z80.getTEstados(), port));
//            z80.tEstados += 3;
//        }
    }

    public void keyPressed(KeyEvent evt) {
        int key = evt.getKeyCode();
        switch( key ) {
            // Fila B - SPACE
            case KeyEvent.VK_SPACE:
                portMap[0x7ffe] &= 0xfe;
                break;
            case KeyEvent.VK_ALT_GRAPH:
                portMap[0x7ffe] &= 0xfd;
                break;
            case KeyEvent.VK_M:
                portMap[0x7ffe] &= 0xfb;
                break;
            case KeyEvent.VK_N:
                portMap[0x7ffe] &= 0xf7;
                break;
            case KeyEvent.VK_B:
                portMap[0x7ffe] &= 0x0f;
                break;
            // Fila ENTER - H
            case KeyEvent.VK_ENTER:
                portMap[0xbffe] &= 0xfe;
                break;
            case KeyEvent.VK_L:
                portMap[0xbffe] &= 0xfd;
                break;
            case KeyEvent.VK_K:
                portMap[0xbffe] &= 0xfb;
                break;
            case KeyEvent.VK_J:
                portMap[0xbffe] &= 0xf7;
                break;
            case KeyEvent.VK_H:
                portMap[0xbffe] &= 0x0f;
                break;
            // Fila P - Y
            case KeyEvent.VK_P:
                portMap[0xdffe] &= 0xfe;
                break;
            case KeyEvent.VK_O:
                portMap[0xdffe] &= 0xfd;
                break;
            case KeyEvent.VK_I:
                portMap[0xdffe] &= 0xfb;
                break;
            case KeyEvent.VK_U:
                portMap[0xdffe] &= 0xf7;
                break;
            case KeyEvent.VK_Y:
                portMap[0xdffe] &= 0x0f;
                break;
            // Fila 0 - 6
            case KeyEvent.VK_0:
                portMap[0xeffe] &= 0xfe;
                break;
            case KeyEvent.VK_9:
                portMap[0xeffe] &= 0xfd;
                break;
            case KeyEvent.VK_8:
                portMap[0xeffe] &= 0xfb;
                break;
            case KeyEvent.VK_7:
                portMap[0xeffe] &= 0xf7;
                break;
            case KeyEvent.VK_6:
                portMap[0xeffe] &= 0x0f; // 6
                break;
            // Fila 1 - 5
            case KeyEvent.VK_1:
                portMap[0xf7fe] &= 0xfe;
                break;
            case KeyEvent.VK_2:
                portMap[0xf7fe] &= 0xfd;
                break;
            case KeyEvent.VK_3:
                portMap[0xf7fe] &= 0xfb;
                break;
            case KeyEvent.VK_4:
                portMap[0xf7fe] &= 0xf7;
                break;
            case KeyEvent.VK_5:
                portMap[0xf7fe] &= 0x0f;
                break;
            // Fila Q - T
            case KeyEvent.VK_Q:
                portMap[0xfbfe] &= 0xfe;
                break;
            case KeyEvent.VK_W:
                portMap[0xfbfe] &= 0xfd;
                break;
            case KeyEvent.VK_E:
                portMap[0xfbfe] &= 0xfb;
                break;
            case KeyEvent.VK_R:
                portMap[0xfbfe] &= 0xf7;
                break;
            case KeyEvent.VK_T:
                portMap[0xfbfe] &= 0x0f;
                break;
            // Fila A - G
            case KeyEvent.VK_A:
                portMap[0xfdfe] &= 0xfe;
                break;
            case KeyEvent.VK_S:
                portMap[0xfdfe] &= 0xfd;
                break;
            case KeyEvent.VK_D:
                portMap[0xfdfe] &= 0xfb;
                break;
            case KeyEvent.VK_F:
                portMap[0xfdfe] &= 0xf7;
                break;
            case KeyEvent.VK_G:
                portMap[0xfdfe] &= 0x0f;
                break;
            // Fila CAPS_SHIFT - V
            case KeyEvent.VK_SHIFT:
                portMap[0xfefe] &= 0xfe;
                break;
            case KeyEvent.VK_Z:
                portMap[0xfefe] &= 0xfd;
                break;
            case KeyEvent.VK_X:
                portMap[0xfefe] &= 0xfb;
                break;
            case KeyEvent.VK_C:
                portMap[0xfefe] &= 0xf7;
                break;
            case KeyEvent.VK_V:
                portMap[0xfefe] &= 0x0f;
                break;
            // Teclas de conveniencia, para mayor comodidad de uso
            case KeyEvent.VK_BACK_SPACE:
                portMap[0xfefe] &= 0xfe; // CAPS
                portMap[0xeffe] &= 0xfe; // 0
                break;
            case KeyEvent.VK_COMMA:
                portMap[0x7ffe] &= 0xfd; // SYMBOL-SHIFT
                portMap[0x7ffe] &= 0xf7; // N
                break;
            case KeyEvent.VK_PERIOD:
                portMap[0x7ffe] &= 0xfd; // SYMBOL-SHIFT
                portMap[0x7ffe] &= 0xfb; // M
                break;
            case KeyEvent.VK_MINUS:
                portMap[0x7ffe] &= 0xfd; // SYMBOL-SHIFT
                portMap[0xbffe] &= 0xf7; // J
                break;
            case KeyEvent.VK_PLUS:
                portMap[0x7ffe] &= 0xfd; // SYMBOL-SHIFT
                portMap[0xbffe] &= 0xfb; // K
                break;
            case KeyEvent.VK_CONTROL:
                portMap[0xfefe] &= 0xfe; // CAPS
                portMap[0x7ffe] &= 0xfd; // SYMBOL-SHIFT -- Extended Mode
                break;
            case KeyEvent.VK_CAPS_LOCK:
                portMap[0xfefe] &= 0xfe; // CAPS
                portMap[0xf7fe] &= 0xfd; // 2  -- Caps Lock
                break;
            case KeyEvent.VK_LEFT:
                portMap[0xfefe] &= 0xfe; // CAPS
                portMap[0xf7fe] &= 0x0f; // 5  -- Left arrow
                break;
            case KeyEvent.VK_DOWN:
                portMap[0xfefe] &= 0xfe; // CAPS
                portMap[0xeffe] &= 0x0f; // 6  -- Down arrow
                break;
            case KeyEvent.VK_UP:
                portMap[0xfefe] &= 0xfe; // CAPS
                portMap[0xeffe] &= 0xf7; // 7  -- Up arrow
                break;
            case KeyEvent.VK_RIGHT:
                portMap[0xfefe] &= 0xfe; // CAPS
                portMap[0xeffe] &= 0xfb; // 8  -- Left arrow
                break;
        }
    }

    public void keyReleased(KeyEvent evt) {
        int key = evt.getKeyCode();
        switch( key ) {
            // Fila SPACE - B
            case KeyEvent.VK_SPACE:
                portMap[0x7ffe] |= 0x01; //Spacebar
                break;
            case KeyEvent.VK_ALT_GRAPH:
                portMap[0x7ffe] |= 0x02; // Symbol-Shift
                break;
            case KeyEvent.VK_M:
                portMap[0x7ffe] |= 0x04; // M
                break;
            case KeyEvent.VK_N:
                portMap[0x7ffe] |= 0x08; // N
                break;
            case KeyEvent.VK_B:
                portMap[0x7ffe] |= 0x10; // B
                break;
            // Fila ENTER - H
            case KeyEvent.VK_ENTER:
                portMap[0xbffe] |= 0x01; // ENTER
                break;
            case KeyEvent.VK_L:
                portMap[0xbffe] |= 0x02; // L
                break;
            case KeyEvent.VK_K:
                portMap[0xbffe] |= 0x04; // K
                break;
            case KeyEvent.VK_J:
                portMap[0xbffe] |= 0x08; // J
                break;
            case KeyEvent.VK_H:
                portMap[0xbffe] |= 0x10; // H
                break;
            // Fila P - Y
            case KeyEvent.VK_P:
                portMap[0xdffe] |= 0x01; //P
                break;
            case KeyEvent.VK_O:
                portMap[0xdffe] |= 0x02; // O
                break;
            case KeyEvent.VK_I:
                portMap[0xdffe] |= 0x04; // I
                break;
            case KeyEvent.VK_U:
                portMap[0xdffe] |= 0x08; // U
                break;
            case KeyEvent.VK_Y:
                portMap[0xdffe] |= 0x10; // Y
                break;
            // Fila 0 - 6
            case KeyEvent.VK_0:
                portMap[0xeffe] |= 0x01; // 0
                break;
            case KeyEvent.VK_9:
                portMap[0xeffe] |= 0x02; // 9
                break;
            case KeyEvent.VK_8:
                portMap[0xeffe] |= 0x04; // 8
                break;
            case KeyEvent.VK_7:
                portMap[0xeffe] |= 0x08; // 7
                break;
            case KeyEvent.VK_6:
                portMap[0xeffe] |= 0x10; // 6
                break;
            // Fila 1 - 5
            case KeyEvent.VK_1:
                portMap[0xf7fe] |= 0x01;
                break;
            case KeyEvent.VK_2:
                portMap[0xf7fe] |= 0x02;
                break;
            case KeyEvent.VK_3:
                portMap[0xf7fe] |= 0x04;
                break;
            case KeyEvent.VK_4:
                portMap[0xf7fe] |= 0x08;
                break;
            case KeyEvent.VK_5:
                portMap[0xf7fe] |= 0x10; // 5
                break;
            // Fila Q - T
            case KeyEvent.VK_Q:
                portMap[0xfbfe] |= 0x01;
                break;
            case KeyEvent.VK_W:
                portMap[0xfbfe] |= 0x02;
                break;
            case KeyEvent.VK_E:
                portMap[0xfbfe] |= 0x04;
                break;
            case KeyEvent.VK_R:
                portMap[0xfbfe] |= 0x08;
                break;
            case KeyEvent.VK_T:
                portMap[0xfbfe] |= 0x10;
                break;
            // Fila A - G
            case KeyEvent.VK_A:
                portMap[0xfdfe] |= 0x01;
                break;
            case KeyEvent.VK_S:
                portMap[0xfdfe] |= 0x02;
                break;
            case KeyEvent.VK_D:
                portMap[0xfdfe] |= 0x04;
                break;
            case KeyEvent.VK_F:
                portMap[0xfdfe] |= 0x08;
                break;
            case KeyEvent.VK_G:
                portMap[0xfdfe] |= 0x10;
                break;
            // Fila CAPS_SHIFT - V
            case KeyEvent.VK_SHIFT:
                portMap[0xfefe] |= 0x01;
                break;
            case KeyEvent.VK_Z:
                portMap[0xfefe] |= 0x02;
                break;
            case KeyEvent.VK_X:
                portMap[0xfefe] |= 0x04;
                break;
            case KeyEvent.VK_C:
                portMap[0xfefe] |= 0x08;
                break;
            case KeyEvent.VK_V:
                portMap[0xfefe] |= 0x10;
                break;
            // Teclas de conveniencia
            case KeyEvent.VK_BACK_SPACE:
                portMap[0xfefe] |= 0x01; // CAPS
                portMap[0xeffe] |= 0x01; // 0
                break;
            case KeyEvent.VK_COMMA:
                portMap[0x7ffe] |= 0x02; // SYMBOL-SHIFT
                portMap[0x7ffe] |= 0x08; // N
                break;
            case KeyEvent.VK_PERIOD:
                portMap[0x7ffe] |= 0x02; // SYMBOL-SHIFT
                portMap[0x7ffe] |= 0x04; // M
                break;
            case KeyEvent.VK_MINUS:
                portMap[0x7ffe] |= 0x02; // SYMBOL-SHIFT
                portMap[0xbffe] |= 0x08; // J
                break;
            case KeyEvent.VK_PLUS:
                portMap[0x7ffe] |= 0x02; // SYMBOL-SHIFT
                portMap[0xbffe] |= 0x04; // K
                break;
            case KeyEvent.VK_CONTROL:
                portMap[0xfefe] |= 0x01; // CAPS
                portMap[0x7ffe] |= 0x02; // SYMBOL-SHIFT
                break;
            case KeyEvent.VK_CAPS_LOCK:
                portMap[0xfefe] |= 0x01; // CAPS
                portMap[0xf7fe] |= 0x02; // 2
                break;
            case KeyEvent.VK_LEFT:
                portMap[0xfefe] |= 0x01; // CAPS
                portMap[0xf7fe] |= 0x10; // 5  -- Left arrow
                break;
            case KeyEvent.VK_DOWN:
                portMap[0xfefe] |= 0x01; // CAPS
                portMap[0xeffe] |= 0x10; // 6  -- Down arrow
                break;
            case KeyEvent.VK_UP:
                portMap[0xfefe] |= 0x01; // CAPS
                portMap[0xeffe] |= 0x08; // 7  -- Up arrow
                break;
            case KeyEvent.VK_RIGHT:
                portMap[0xfefe] |= 0x01; // CAPS
                portMap[0xeffe] |= 0x04; // 8  -- Right arrow
                break;
        }
    }

    public void keyTyped(java.awt.event.KeyEvent evt) {
        // TODO add your handling code here:
    }

    public void loadSNA(String filename) {
        //stopEmulation();
        z80.reset();
        try {
            try {
                fIn = new FileInputStream(filename);
            } catch (FileNotFoundException ex) {
                System.out.println("No se pudo abrir el fichero " + filename);
                Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
                //startEmulation();
                return;
            }
            z80.setRegI(fIn.read());
            z80.setRegHLalt( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegDEalt( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegBCalt( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegAFalt( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegHL( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegDE( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegBC( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegIY( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegIX( (fIn.read() | (fIn.read() << 8) & 0xffff) );

            int iff2EI = fIn.read() & 0xff;
            if( (iff2EI & 0x02) != 0 )
                z80.setIFF2(true);

            if( (iff2EI & 0x01) != 0 )
                z80.setIFF1(true);

            z80.setRegR(fIn.read());
            z80.setRegAF( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setRegSP( (fIn.read() | (fIn.read() << 8) & 0xffff) );
            z80.setIM(fIn.read() & 0xff);

            int border = fIn.read() & 0x07;
            portMap[0xfe] &= 0xf8;
            portMap[0xfe] |= border;

            int count;
            for (count = 0x4000; count < 0xFFFF; count++) {
                z80Ram[count] = (int) fIn.read() & 0xff;
            }

            if (count != 0xffff) {
                System.out.println("No se pudo cargar la imagen");
                z80.reset();
                //startEmulation();
                return;
            }
            z80.setRegPC(0x72);  // código de RETN en la ROM
            fIn.close();
        } catch (IOException ex) {
            System.out.println("No se pudo leer el fichero " + filename);
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Imagen cargada");
        //startEmulation();
    }
}
