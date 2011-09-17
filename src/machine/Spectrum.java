/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import gui.JSpeccyScreen;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Spectrum implements Runnable, z80core.MemIoOps, KeyListener {

    private Z80 z80;
    private int z80Ram[] = new int[0x10000];
    private int rowKey[] = new int[8];
    public int portFE, earBit = 0xbf, kempston;
    private FileInputStream fIn;
    private int nFrame;
    private boolean soundOn = true;
    public static final int FRAMES48k = 69888;
    private static final byte delayTstates[] = new byte[FRAMES48k + 100];

    static {
        Arrays.fill(delayTstates, (byte) 0x00);
        for (int idx = 14335; idx < 57343; idx += 224) {
            for (int ndx = 0; ndx < 128; ndx += 8) {
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
//    private Timer timerFrame;
//    private SpectrumTimer taskFrame;
    private JSpeccyScreen jscr;
    private Audio audio;
    private ScheduledThreadPoolExecutor stpe;
    private boolean paused;
    private boolean loading;

    public Spectrum() {
        z80 = new Z80(this);
        loadRom();
        nFrame = 0;
        Arrays.fill(rowKey, 0xff);
        portFE = 0xff;
        kempston = 0;
//        timerFrame = new Timer("SpectrumClock", true);
        audio = new Audio();
        audio.open(3500000);
        paused = true;
        loading = true;
        stpe = new ScheduledThreadPoolExecutor(2);
        stpe.scheduleAtFixedRate(this, 0, 20, TimeUnit.MILLISECONDS);
    }

    public void startEmulation() {
        z80.setTEstados(0);
        audio.audiotstates = 0;
        jscr.invalidateScreen();
//        taskFrame = new SpectrumTimer(this);
//        timerFrame.scheduleAtFixedRate(taskFrame, 20, 20);
        paused = false;
    }

    public void stopEmulation() {
//        taskFrame.cancel();
        paused = true;
        audio.bufp = audio.flush(audio.bufp);
    }

    public void reset() {
        z80.reset();
        z80.setTEstados(0);
        audio.audiotstates = 0;
    }

    public void generateFrame() {
//        long startFrame, endFrame, sleepTime;
//        startFrame = System.currentTimeMillis();
//        System.out.println("Start frame: " + startFrame);

        //z80.tEstados = frameStart;
        //System.out.println(String.format("Begin frame. t-states: %d", z80.tEstados));
        z80.statesLimit = 32;
        z80.setINTLine(true);
        z80.execute();
        z80.setINTLine(false);
        z80.statesLimit = 14335;
        z80.execute();
        jscr.updateInterval(14328, z80.tEstados);
        //System.out.println(String.format("t-states: %d", z80.tEstados));
        int fromTstates;
        // El último byte de pantalla se muestra en el estado 57236
        while (z80.statesLimit < 57237) {
            fromTstates = z80.tEstados + 1;
            z80.statesLimit = fromTstates + 15;
            z80.execute();
            jscr.updateInterval(fromTstates, z80.tEstados);
        }

        z80.statesLimit = FRAMES48k;
        z80.execute();

        audio.updateAudio(z80.tEstados, speaker);
        audio.audiotstates -= FRAMES48k;
//        System.out.println(String.format("Bytes en buffer: %d", audio.bufp));
//        audio.bufp = audio.flush(audio.bufp);

        //System.out.println(String.format("End frame. t-states: %d", z80.tEstados));
        z80.tEstados -= FRAMES48k;

        if (++nFrame % 16 == 0) {
            jscr.toggleFlash();
        }

        if (jscr.screenUpdated || jscr.nBorderChanges > 0) {
            jscr.repaint();
        }

        //endFrame = System.currentTimeMillis();
        //System.out.println("End frame: " + endFrame);
        //sleepTime = endFrame - startFrame;
        //System.out.println("execFrame en: " + sleepTime);
    }

    public void run() {
        if (paused) {
            return;
        }
        
        generateFrame();
    }

    public void setScreen(JSpeccyScreen jscr) {
        this.jscr = jscr;
    }

    private void loadRom() {
        InputStream inRom;

        try {
            inRom = Spectrum.class.getResourceAsStream("/roms/spectrum.rom");
            if (inRom == null) {
                System.out.println("No se pudo leer la ROM desde /roms/spectrum.rom");
                return;
            }
            int count;
            for (count = 0; count < 0x4000; count++) {
                z80Ram[count] = (int) inRom.read() & 0xff;
            }
            if (count != 0x4000) {
                System.out.println("No se pudo cargar la ROM");
                return;
            }

            inRom.close();
        } catch (IOException ex) {
            System.out.println("No se pudo leer el fichero spectrum.rom");
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("ROM cargada");
    }

    public int[] getSpectrumMem() {
        return z80Ram;
    }

    public int fetchOpcode(int address) {

        if ((address & 0xC000) == 0x4000) {
//            System.out.println(String.format("getOpcode: %d %d %d",
//                    z80.tEstados, address, delayTstates[z80.tEstados]));
            z80.tEstados += delayTstates[z80.tEstados];
        }

        z80.tEstados += 4;

        if ((z80.getRegI() & 0xC0) == 0x40) {
            jscr.m1contended = z80.tEstados;
            jscr.m1regR = z80.getRegR();
        }

        return z80Ram[address];
    }

    public int peek8(int address) {

        if ((address & 0xC000) == 0x4000) {
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados += 3;

        return z80Ram[address];
    }

    public void poke8(int address, int value) {

        if ((address & 0xC000) == 0x4000) {
            z80.tEstados += delayTstates[z80.tEstados];
            if (address < 0x5b00) {
                jscr.screenUpdated(address);
            }
        }
        z80.tEstados += 3;

        if (address > 0x3fff) {
            z80Ram[address] = value;
        }
    }

    public int peek16(int address) {

        int lsb, msb;
        if ((address & 0xC000) == 0x4000) {
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados += 3;
        lsb = z80Ram[address];

        address = (address + 1) & 0xffff;
        if ((address & 0xC000) == 0x4000) {
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados += 3;

        msb = z80Ram[address];
        return (msb << 8) | lsb;
    }

    public void poke16(int address, int word) {
//        poke8(address, word & 0xff);
//        poke8((address + 1) & 0xffff, word >>> 8);

        if ((address & 0xC000) == 0x4000) {
            z80.tEstados += delayTstates[z80.tEstados];
            if (address < 0x5b00) {
                jscr.screenUpdated(address);
            }
        }
        z80.tEstados += 3;

        if (address > 0x3fff) {
            z80Ram[address] = word & 0xff;
        }

        address = (address + 1) & 0xffff;

        if ((address & 0xC000) == 0x4000) {
            z80.tEstados += delayTstates[z80.tEstados];
            if (address < 0x5b00) {
                jscr.screenUpdated(address);
            }
        }
        z80.tEstados += 3;

        if (address > 0x3fff) {
            z80Ram[address] = word >>> 8;
        }
    }

    public void contendedStates(int address, int tstates) {
        //address &= 0xffff;
        if ((address & 0xC000) == 0x4000) {
            for (int idx = 0; idx < tstates; idx++)
//                System.out.println(String.format("t-States: %d\taddress:%d\ttstates %d",
//                    z80.tEstados, address, tstates));
            {
                z80.tEstados += delayTstates[z80.tEstados] + 1;
            }
        } else {
            z80.tEstados += tstates;
        }
    }

    public int inPort(int port) {
        int res = port >>> 8;
        int keys = 0xff;
        int floatbus = 0xff;

//        if ((port & 0xff) == 0xff) {
//            System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                    z80.tEstados, z80.getRegPC()));
//        }
        //int tstates = z80.tEstados;
        //postIO(port);
        //System.out.println(String.format("InPort: %04X", port));
        preIO(port);
        postIO(port);

        if ((port & 0x00e0) == 0) {
            return kempston;
        }

//        if ((port & 0xC002) == 0xC000 && ay_enabled) {
//            if (ay_idx >= 14 && (ay_reg[7] >> ay_idx - 8 & 1) == 0) {
//                return 0xFF;
//            }
//            return ay_reg[ay_idx];
//        }

        if ((port & 0x0001) == 0) {
//            System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                   z80.tEstados, z80.getRegPC()));
            res = ~res & 0xff;
            for (int row = 0, mask = 0x01; row < 8; row++, mask <<= 1) {
                if ((res & mask) != 0) {
                    keys &= rowKey[row];
                }
            }

//            int noise = 0xff;
//            if ( (portFE & 0x18) == 8 ) {
//                noise &= (z80.getRegR() & 0x40);
//            }

//            if( loading )
//                earBit = readTape(z80.getTEstados());
            return keys & earBit;
        }

        int addr = 0;
        if ((port & 0xff) == 0xff) {
            int tstates = z80.getTEstados();
            if (tstates < 14336 || tstates > 57343) {
                return 0xff;
            }

            int col = (tstates % 224) - 3;
            if (col > 124) {
                return 0xff;
            }

            int row = tstates / 224 - 64;

            switch (col % 8) {
                case 0:
                    addr = jscr.scrAddr[row] + col / 4;
                    break;
                case 1:
                    addr = jscr.scr2attr[(jscr.scrAddr[row] + col / 4) & 0x1fff];
                    break;
                case 2:
                    addr = jscr.scrAddr[row] + col / 4 + 1;
                    break;
                case 3:
                    addr = jscr.scr2attr[(jscr.scrAddr[row] + col / 4 + 1) & 0x1fff];
                    break;
                default:
                    return 0xff;
            }
            floatbus = z80Ram[addr];
        }
        return floatbus;
    }

    public void outPort(int port, int value) {
        preIO(port);
//        int tEstados = z80.tEstados;
        //contendedIO(port);
//        if( (port & 0x0001) == 0 ){
//            System.out.println(String.format("%07d %5d PW %04x %02x %d",
//                   nFrame, tEstados, port, value,
//                  (tEstados < oldstate ? (tEstados+(69888-oldstate)) : (tEstados-oldstate))));
//            oldstate = tEstados;
//        }
        //postIO(port);

        if ((port & 0x0001) == 0) {
            if ((portFE & 0x07) != (value & 0x07)) {
//                if( (value & 0x07) == 0x07 )
//                    System.out.println(String.format("tstates: %d border: %d",
//                        z80.tEstados, value&0x07));
                jscr.updateBorder(z80.tEstados);
            }

            int spkMic = sp_volt[value >> 3 & 3];
            if (spkMic != speaker) {
//                au_update();
                if (soundOn) {
                    audio.updateAudio(z80.tEstados, speaker);
                }
                speaker = spkMic;
            }

            if ((value & 0x10) == 0) {
                earBit = 0xbf;
            } else {
                earBit = 0xff;
            }
            //System.out.println(String.format("outPort: %04X %02x", port, value));         
            portFE = value;
        }

//        if ((port & 0x8002) == 0x8000 && ay_enabled) {
//            if ((port & 0x4000) != 0) {
//                ay_idx = (byte) (value & 15);
//            } else {
//                au_update();
//                ay_write(ay_idx, value);
//            }
//        }
        //preIO(port);
        postIO(port);
    }

    /*
     * Las operaciones de I/O se producen entre los estados T3 y T4 de la CPU,
     * y justo ahí es donde podemos encontrar la contención en los accesos. Los
     * ciclos de contención son exactamente iguales a los de la memoria, con los
     * siguientes condicionantes dependiendo del estado del bit A0 y de si el
     * puerto accedido se encuentra entre las direcciones 0x4000-0x7FFF:
     *
     * High byte in 0x40 (0xc0) to 0x7f (0xff)? 	Low bit  Contention pattern
     *                                      No      Reset    N:1, C:3
     *                                      No      Set      N:4
     *                                      Yes     Reset    C:1, C:3
     *                                      Yes 	Set      C:1, C:1, C:1, C:1
     *
     * La columna 'Contention Pattern' se lee 'N:x', no contención x ciclos
     * 'C:n' se lee contención seguido de n ciclos sin contención.
     * Así pues se necesitan dos rutinas, la que añade los primeros 3 t-estados
     * con sus contenciones cuando procede y la que añade el estado final con
     * la contención correspondiente.
     */
    private void postIO(int port) {
        if ((port & 0x0001) != 0) {
            if ((port & 0xc000) == 0x4000) {
                // A0 == 1 y es contended RAM
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados++;
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados++;
                z80.tEstados += delayTstates[z80.tEstados];
                z80.tEstados++;
            } else {
                // A0 == 1 y no es contended RAM
                z80.tEstados += 3;
            }
        } else {
//            if ((port & 0xc000) == 0x4000) {
//                // A0 == 0 y es contended RAM
//                z80.tEstados += delayTstates[z80.tEstados];
//                z80.tEstados += 3;
//            } else {
            // A0 == 0 y no es contended RAM
            z80.tEstados += delayTstates[z80.tEstados];
            z80.tEstados += 3;
            // }
        }
    }

    private void preIO(int port) {
        if ((port & 0xc000) == 0x4000) {
            // A0 == 1 y es contended RAM
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados++;
    }

    public void keyPressed(KeyEvent evt) {
        int key = evt.getKeyCode();
        switch (key) {
            // Fila B - SPACE
            case KeyEvent.VK_SPACE:
                rowKey[7] &= 0xfe;
                break;
            case KeyEvent.VK_ALT_GRAPH:
                rowKey[7] &= 0xfd;
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
            case KeyEvent.VK_CONTROL:
                rowKey[0] &= 0xfe; // CAPS
                rowKey[7] &= 0xfd; // SYMBOL-SHIFT -- Extended Mode
                break;
            case KeyEvent.VK_CAPS_LOCK:
                rowKey[0] &= 0xfe; // CAPS
                rowKey[3] &= 0xfd; // 2  -- Caps Lock
                break;
            // Emulación joystick Kempston
            case KeyEvent.VK_LEFT:
//                rowKey[0] &= 0xfe; // CAPS
//                rowKey[3] &= 0xef; // 5  -- Left arrow
                kempston |= 0x02;
                break;
            case KeyEvent.VK_DOWN:
//                rowKey[0] &= 0xfe; // CAPS
//                rowKey[4] &= 0xef; // 6  -- Down arrow
                kempston |= 0x04;
                break;
            case KeyEvent.VK_UP:
//                rowKey[0] &= 0xfe; // CAPS
//                rowKey[4] &= 0xf7; // 7  -- Up arrow
                kempston |= 0x08;
                break;
            case KeyEvent.VK_RIGHT:
//                rowKey[0] &= 0xfe; // CAPS
//                rowKey[4] &= 0xfb; // 8  -- Left arrow
                kempston |= 0x01;
                break;
            case KeyEvent.VK_ESCAPE:
//                rowKey[0] &= 0xfe; // CAPS
//                rowKey[4] &= 0xfb; // 8  -- Left arrow
                kempston |= 0x10; // Fire
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
            case KeyEvent.VK_ALT_GRAPH:
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
            case KeyEvent.VK_CONTROL:
                rowKey[0] |= 0x01; // CAPS
                rowKey[7] |= 0x02; // SYMBOL-SHIFT
                break;
            case KeyEvent.VK_CAPS_LOCK:
                rowKey[0] |= 0x01; // CAPS
                rowKey[3] |= 0x02; // 2
                break;
            case KeyEvent.VK_LEFT:
//                rowKey[0] |= 0x01; // CAPS
//                rowKey[3] |= 0x10; // 5  -- Left arrow
                kempston &= 0xfd;
                break;
            case KeyEvent.VK_DOWN:
//                rowKey[0] |= 0x01; // CAPS
//                rowKey[4] |= 0x10; // 6  -- Down arrow
                kempston &= 0xfb;
                break;
            case KeyEvent.VK_UP:
//                rowKey[0] |= 0x01; // CAPS
//                rowKey[4] |= 0x08; // 7  -- Up arrow
                kempston &= 0xf7;
                break;
            case KeyEvent.VK_RIGHT:
//                rowKey[0] |= 0x01; // CAPS
//                rowKey[4] |= 0x04; // 8  -- Right arrow
                kempston &= 0xfe;
                break;
            case KeyEvent.VK_ESCAPE:
//                rowKey[0] |= 0x01; // CAPS
//                rowKey[4] |= 0x04; // 8  -- Right arrow
                kempston &= 0xef;
                break;
        }
    }

    public void keyTyped(java.awt.event.KeyEvent evt) {
        // TODO add your handling code here:
    }

    public void loadSNA(File filename) {
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
            z80.setRegHLalt((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegDEalt((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegBCalt((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegAFalt((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegHL((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegDE((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegBC((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegIY((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegIX((fIn.read() | (fIn.read() << 8) & 0xffff));

            int iff2EI = fIn.read() & 0xff;
            if ((iff2EI & 0x02) != 0) {
                z80.setIFF2(true);
            }

            if ((iff2EI & 0x01) != 0) {
                z80.setIFF1(true);
            }

            z80.setRegR(fIn.read());
            z80.setRegAF((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setRegSP((fIn.read() | (fIn.read() << 8) & 0xffff));
            z80.setIM(fIn.read() & 0xff);

            int border = fIn.read() & 0x07;
            portFE &= 0xf8;
            portFE |= border;

            int count;
            for (count = 0x4000; count < 0x10000; count++) {
                z80Ram[count] = (int) fIn.read() & 0xff;
            }

            fIn.close();
            if (count != 0x10000) {
                System.out.println("No se pudo cargar la imagen");
                z80.reset();
                //startEmulation();
                return;
            }
            z80.setRegPC(0x72);  // código de RETN en la ROM
        } catch (IOException ex) {
            System.out.println("No se pudo leer el fichero " + filename);
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Imagen cargada");
        //startEmulation();
    }
    static final int CHANNEL_VOLUME = 26000;
    static final int SPEAKER_VOLUME = 5000;
    private int speaker;
    private static final int sp_volt[];
    static boolean muted = false;
    static int volume = 40; // %

    void mute(boolean v) {
        muted = v;
        setvol();
    }

    int volume(int v) {
        if (v < 0) {
            v = 0;
        } else if (v > 100) {
            v = 100;
        }
        volume = v;
        setvol();
        return v;
    }

    int volumeChg(int chg) {
        return volume(volume + chg);
    }

    public void toggleSound() {
        soundOn = !soundOn;
        if (soundOn) {
            audio = new Audio();
            audio.open(3500000);
        } else {
            audio.bufp = audio.flush(audio.bufp);
            audio.close();
        }
    }

    static {
        sp_volt = new int[4];
        setvol();
    }

    static void setvol() {
        double a = muted ? 0 : volume / 100.;
        a *= a;

//      sp_volt[0] = (int)(-SPEAKER_VOLUME*a);
//		sp_volt[1] = (int)(-SPEAKER_VOLUME*1.06*a);
//		sp_volt[2] = (int)(SPEAKER_VOLUME*a);
//		sp_volt[3] = (int)(SPEAKER_VOLUME*1.06*a);
        sp_volt[0] = (int) -SPEAKER_VOLUME;
        sp_volt[1] = (int) (-SPEAKER_VOLUME * 1.06);
        sp_volt[2] = (int) SPEAKER_VOLUME;
        sp_volt[3] = (int) (SPEAKER_VOLUME * 1.06);
    }

    private int tapeStates;
    private int readTape(int tstates) {
        int earIn = 0xbf;
        if (tstates < tapeStates)
            tstates += FRAMES48k;
        return earIn;
    }
}
