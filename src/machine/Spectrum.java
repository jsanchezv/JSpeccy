/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import gui.JSpeccyScreen;
import java.awt.event.KeyEvent;
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
public class Spectrum implements z80core.MemIoOps {
    private Z80 z80;
    private int z80Ram[] = new int[0x10000];
    public int portMap[] = new int[0x10000];

    private FileInputStream fIn;

    private int frameStart;
    private int nFrame;
    public boolean scrMod;

    public static final int FRAMES48k = 70000;
    private static final byte delayTstates[] = new byte[FRAMES48k];
    static {
        Arrays.fill(delayTstates, (byte)0x00);
        for( int idx = 14336; idx < 57344; idx += 224  ) {
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
        frameStart = 0;
        nFrame = 0;
        Arrays.fill(portMap, 0xff);
        taskFrame = new SpectrumTimer(this);
    }

    public void startEmulation() {
        timerFrame = new Timer();
        timerFrame.scheduleAtFixedRate(taskFrame, 0, 20);
    }

    public void stopEmulation() {
        timerFrame.cancel();
    }

    public void generateFrame() {
        long startFrame, endFrame, sleepTime;
        startFrame = System.currentTimeMillis();
        //System.out.println("Start frame: " + startFrame);
        scrMod = false;
        execFrame();
        if (++nFrame % 16 == 0) {
            jscr.toggleFlash();
            scrMod = true;
        }
        if (scrMod) {
            jscr.repaint();
        }
        endFrame = System.currentTimeMillis();
        //System.out.println("End frame: " + endFrame);
        //sleepTime = endFrame - startFrame;
        //System.out.println("execFrame en: " + sleepTime);
    }

    public void setScreen(JSpeccyScreen jscr) {
        this.jscr = jscr;
    }

    public void execFrame() {
        //long start = System.currentTimeMillis();
        boolean intREQ = false;

        z80.tEstados = frameStart;
        z80.setINTLine(true);
        intREQ = true;
        //z80.statesLimit = 69888;
        while( z80.tEstados < 69888 ) {
            z80.execInstruction();
            if( intREQ ) {
                z80.setINTLine(false);
                intREQ = false;
            }
        }
        //System.out.println("execFrame: ejecutados " + z80.getTEstados());
        //System.out.println("PC: " + z80.getRegPC());
        frameStart = z80.tEstados - 69888;
        //System.out.println("execFrame en: " + (System.currentTimeMillis() - start));
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
            z80.tEstados += delayTstates[z80.tEstados];
                //System.out.println("R(" + dire +"): ");
        }
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
        address++;
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

        address++;
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

    public int inPort(int port) {
        int res = port >>> 8;
        preIO(port);
        //System.out.println(String.format("%5d PR %04x %02x", z80.tEstados, port, res));
        if( (port & 0xff) == 0xfe ) {
            switch (res) {
                case 0x7f:
                    res = portMap[0x7ffe];
                    break;
                case 0xbf:
                    res = portMap[0xbffe];
                    break;
                case 0xdf:
                    res = portMap[0xdffe];
                    break;
                case 0xef:
                    res = portMap[0xeffe];
                    break;
                case 0xf7:
                    res = portMap[0xf7fe];
                    break;
                case 0xfb:
                    res = portMap[0xfbfe];
                    break;
                case 0xfd:
                    res = portMap[0xfdfe];
                    break;
                case 0xfe:
                    res = portMap[0xfefe];
                    break;
            }
        }
        postIO(port);
        return res;
    }

    public void outPort(int port, int value) {
        preIO(port);
        //System.out.println(String.format("%5d PW %04x %02x", z80.tEstados, port, value));
        if( (port & 0xff) == 0xfe ) {
            portMap[0xfe] = value;
            scrMod = true;
        }
        postIO(port);
    }

    public int MREQstates(int address, int tstates) {
        int delay = 0;

        //address &= 0xffff;
        if( (address & 0xC000) == 0x4000 ) {
            int tEstados = z80.tEstados;
            for( int idx = 0; idx < tstates; idx++ ) {
                //System.out.println(address + " " + (tEstados+delay+idx));
                //System.out.println(String.format(strMC, tEstados+delay+idx, address));
                delay += delayTstates[(tEstados + delay)];

            }
        }
        return (tstates + delay);
    }

    private void preIO(int port) {
        if( ( port & 0xc000 ) == 0x4000 ) {
            //System.out.println(String.format(strPC, z80.getTEstados(), port));
        }
        z80.tEstados++;
    }

    private void postIO(int port) {
        if( (port & 0x0001) != 0 ) {
            if( ( port & 0xc000 ) == 0x4000 ) {
                //System.out.println(String.format(strPC, z80.getTEstados(), port));
                z80.tEstados++;
                //System.out.println(String.format(strPC, z80.getTEstados(), port));
                z80.tEstados++;
                //System.out.println(String.format(strPC, z80.getTEstados(), port));
                z80.tEstados++;
            } else {
                z80.tEstados += 3;
            }
        } else {
            //System.out.println(String.format(strPC, z80.getTEstados(), port));
            z80.tEstados += 3;
        }
    }

    public void keyPressed(KeyEvent evt) {
        int key = evt.getKeyCode();
        switch( key ) {
            // Fila B - SPACE
            case KeyEvent.VK_SPACE:
                portMap[0x7ffe] &= 0xfe;
                System.out.println("keyPressed: Spacebar");
                break;
            case KeyEvent.VK_ALT_GRAPH:
                portMap[0x7ffe] &= 0xfd;
                System.out.println("keyPressed: L");
                break;
            case KeyEvent.VK_M:
                portMap[0x7ffe] &= 0xfb;
                System.out.println("keyPressed: M");
                break;
            case KeyEvent.VK_N:
                portMap[0x7ffe] &= 0xf7;
                System.out.println("keyPressed: N");
                break;
            case KeyEvent.VK_B:
                portMap[0x7ffe] &= 0x0f;
                System.out.println("keyPressed: B");
                break;
            // Fila ENTER - H
            case KeyEvent.VK_ENTER:
                portMap[0xbffe] &= 0xfe;
                System.out.println("keyPressed: ENTER");
                break;
            case KeyEvent.VK_L:
                portMap[0xbffe] &= 0xfd;
                System.out.println("keyPressed: L");
                break;
            case KeyEvent.VK_K:
                portMap[0xbffe] &= 0xfb;
                System.out.println("keyPressed: K");
                break;
            case KeyEvent.VK_J:
                portMap[0xbffe] &= 0x0f7;
                System.out.println("keyPressed: J");
                break;
            case KeyEvent.VK_H:
                portMap[0xbffe] &= 0x0f;
                System.out.println("keyPressed: H");
                break;
            // Fila P - Y
            case KeyEvent.VK_P:
                portMap[0xdffe] &= 0xfe;
                System.out.println("keyPressed: P");
                break;
            case KeyEvent.VK_O:
                portMap[0xdffe] &= 0xfd;
                System.out.println("keyPressed: O");
                break;
            case KeyEvent.VK_I:
                portMap[0xdffe] &= 0xfb;
                System.out.println("keyPressed: I");
                break;
            case KeyEvent.VK_U:
                portMap[0xdffe] &= 0x0f7;
                System.out.println("keyPressed: U");
                break;
            case KeyEvent.VK_Y:
                portMap[0xdffe] &= 0x0f;
                System.out.println("keyPressed: Y");
                break;
            // Fila 0 - 6
            case KeyEvent.VK_0:
                portMap[0xeffe] &= 0xfe;
                System.out.println("keyPressed: 0");
                break;
            case KeyEvent.VK_9:
                portMap[0xeffe] &= 0xfd;
                System.out.println("keyPressed: 9");
                break;
            case KeyEvent.VK_8:
                portMap[0xeffe] &= 0xfb;
                System.out.println("keyPressed: 8");
                break;
            case KeyEvent.VK_7:
                portMap[0xeffe] &= 0x0f7;
                System.out.println("keyPressed: 7");
                break;
            case KeyEvent.VK_6:
                portMap[0xeffe] &= 0x0f;
                System.out.println("keyPressed: 6");
                break;
            // Fila 1 - 5
            case KeyEvent.VK_1:
                portMap[0xf7fe] &= 0xfe;
                System.out.println("keyPressed: 1");
                break;
            case KeyEvent.VK_2:
                portMap[0xf7fe] &= 0xfd;
                System.out.println("keyPressed: 2");
                break;
            case KeyEvent.VK_3:
                portMap[0xf7fe] &= 0xfb;
                System.out.println("keyPressed: 3");
                break;
            case KeyEvent.VK_4:
                portMap[0xf7fe] &= 0x0f7;
                System.out.println("keyPressed: 4");
                break;
            case KeyEvent.VK_5:
                portMap[0xf7fe] &= 0x0f;
                System.out.println("keyPressed: 5");
                break;
            // Fila Q - T
            case KeyEvent.VK_Q:
                portMap[0xfbfe] &= 0xfe;
                System.out.println("keyPressed: Q");
                break;
            case KeyEvent.VK_W:
                portMap[0xfbfe] &= 0xfd;
                System.out.println("keyPressed: W");
                break;
            case KeyEvent.VK_E:
                portMap[0xfbfe] &= 0xfb;
                System.out.println("keyPressed: E");
                break;
            case KeyEvent.VK_R:
                portMap[0xfbfe] &= 0x0f7;
                System.out.println("keyPressed: R");
                break;
            case KeyEvent.VK_T:
                portMap[0xfbfe] &= 0x0f;
                System.out.println("keyPressed: T");
                break;
            // Fila A - G
            case KeyEvent.VK_A:
                portMap[0xfdfe] &= 0xfe;
                System.out.println("keyPressed: A");
                break;
            case KeyEvent.VK_S:
                portMap[0xfdfe] &= 0xfd;
                System.out.println("keyPressed: S");
                break;
            case KeyEvent.VK_D:
                portMap[0xfdfe] &= 0xfb;
                System.out.println("keyPressed: D");
                break;
            case KeyEvent.VK_F:
                portMap[0xfdfe] &= 0x0f7;
                System.out.println("keyPressed: F");
                break;
            case KeyEvent.VK_G:
                portMap[0xfdfe] &= 0x0f;
                System.out.println("keyPressed: G");
                break;
            // Fila CAPS_SHIFT - V
            case KeyEvent.VK_SHIFT:
                portMap[0xfefe] &= 0xfe;
                System.out.println("keyPressed: CAPS");
                break;
            case KeyEvent.VK_Z:
                portMap[0xfefe] &= 0xfd;
                System.out.println("keyPressed: Z");
                break;
            case KeyEvent.VK_X:
                portMap[0xfefe] &= 0xfb;
                System.out.println("keyPressed: X");
                break;
            case KeyEvent.VK_C:
                portMap[0xfefe] &= 0x0f7;
                System.out.println("keyPressed: C");
                break;
            case KeyEvent.VK_V:
                portMap[0xfefe] &= 0x0f;
                System.out.println("keyPressed: V");
                break;
        }
    }

    public void keyReleased(KeyEvent evt) {
        int key = evt.getKeyCode();
        switch( key ) {
            // Fila SPACE - B
            case KeyEvent.VK_SPACE:
                portMap[0x7ffe] |= 0x01;
                System.out.println("keyReleased: Spacebar");
                break;
            case KeyEvent.VK_ALT_GRAPH:
                portMap[0x7ffe] |= 0x02;
                System.out.println("keyReleased: SYMBOL-SHIFT");
                break;
            case KeyEvent.VK_M:
                portMap[0x7ffe] |= 0x04;
                System.out.println("keyReleased: M");
                break;
            case KeyEvent.VK_N:
                portMap[0x7ffe] |= 0x08;
                System.out.println("keyReleased: N");
                break;
            case KeyEvent.VK_B:
                portMap[0x7ffe] |= 0x10;
                System.out.println("keyReleased: B");
                break;
            // Fila ENTER - H
            case KeyEvent.VK_ENTER:
                portMap[0xbffe] |= 0x01;
                System.out.println("keyReleased: ENTER");
                break;
            case KeyEvent.VK_L:
                portMap[0xbffe] |= 0x02;
                System.out.println("keyReleased: L");
                break;
            case KeyEvent.VK_K:
                portMap[0xbffe] |= 0x04;
                System.out.println("keyReleased: K");
                break;
            case KeyEvent.VK_J:
                portMap[0xbffe] |= 0x08;
                System.out.println("keyReleased: J");
                break;
            case KeyEvent.VK_H:
                portMap[0xbffe] |= 0x10;
                System.out.println("keyReleased: H");
                break;
            // Fila P - Y
            case KeyEvent.VK_P:
                portMap[0xdffe] |= 0x01;
                System.out.println("keyReleased: P");
                break;
            case KeyEvent.VK_O:
                portMap[0xdffe] |= 0x02;
                System.out.println("keyReleased: O");
                break;
            case KeyEvent.VK_I:
                portMap[0xdffe] |= 0x04;
                System.out.println("keyReleased: U");
                break;
            case KeyEvent.VK_U:
                portMap[0xdffe] |= 0x08;
                System.out.println("keyReleased: U");
                break;
            case KeyEvent.VK_Y:
                portMap[0xdffe] |= 0x10;
                System.out.println("keyReleased: Y");
                break;
            // Fila 0 - 6
            case KeyEvent.VK_0:
                portMap[0xeffe] |= 0x01;
                System.out.println("keyPressed: 0");
                break;
            case KeyEvent.VK_9:
                portMap[0xeffe] |= 0x02;
                System.out.println("keyPressed: 9");
                break;
            case KeyEvent.VK_8:
                portMap[0xeffe] |= 0x04;
                System.out.println("keyPressed: 8");
                break;
            case KeyEvent.VK_7:
                portMap[0xeffe] |= 0x08;
                System.out.println("keyPressed: 7");
                break;
            case KeyEvent.VK_6:
                portMap[0xeffe] |= 0x10;
                System.out.println("keyPressed: 6");
                break;
            // Fila 1 - 5
            case KeyEvent.VK_1:
                portMap[0xf7fe] |= 0x01;
                System.out.println("keyPressed: 1");
                break;
            case KeyEvent.VK_2:
                portMap[0xf7fe] |= 0x02;
                System.out.println("keyPressed: 2");
                break;
            case KeyEvent.VK_3:
                portMap[0xf7fe] |= 0x04;
                System.out.println("keyPressed: 3");
                break;
            case KeyEvent.VK_4:
                portMap[0xf7fe] |= 0x08;
                System.out.println("keyPressed: 4");
                break;
            case KeyEvent.VK_5:
                portMap[0xf7fe] |= 0x10;
                System.out.println("keyPressed: 5");
                break;
            // Fila Q - T
            case KeyEvent.VK_Q:
                portMap[0xfbfe] |= 0x01;
                System.out.println("keyPressed: Q");
                break;
            case KeyEvent.VK_W:
                portMap[0xfbfe] |= 0x02;
                System.out.println("keyPressed: W");
                break;
            case KeyEvent.VK_E:
                portMap[0xfbfe] |= 0x04;
                System.out.println("keyPressed: E");
                break;
            case KeyEvent.VK_R:
                portMap[0xfbfe] |= 0x08;
                System.out.println("keyPressed: R");
                break;
            case KeyEvent.VK_T:
                portMap[0xfbfe] |= 0x10;
                System.out.println("keyPressed: T");
                break;
            // Fila A - G
            case KeyEvent.VK_A:
                portMap[0xfdfe] |= 0x01;
                System.out.println("keyPressed: A");
                break;
            case KeyEvent.VK_S:
                portMap[0xfdfe] |= 0x02;
                System.out.println("keyPressed: S");
                break;
            case KeyEvent.VK_D:
                portMap[0xfdfe] |= 0x04;
                System.out.println("keyPressed: D");
                break;
            case KeyEvent.VK_F:
                portMap[0xfdfe] |= 0x08;
                System.out.println("keyPressed: F");
                break;
            case KeyEvent.VK_G:
                portMap[0xfdfe] |= 0x10;
                System.out.println("keyPressed: G");
                break;
            // Fila CAPS_SHIFT - V
            case KeyEvent.VK_SHIFT:
                portMap[0xfefe] |= 0x01;
                System.out.println("keyPressed: CAPS");
                break;
            case KeyEvent.VK_Z:
                portMap[0xfefe] |= 0x02;
                System.out.println("keyPressed: Z");
                break;
            case KeyEvent.VK_X:
                portMap[0xfefe] |= 0x04;
                System.out.println("keyPressed: X");
                break;
            case KeyEvent.VK_C:
                portMap[0xfefe] |= 0x08;
                System.out.println("keyPressed: C");
                break;
            case KeyEvent.VK_V:
                portMap[0xfefe] |= 0x10;
                System.out.println("keyPressed: V");
                break;
        }
    }
}
