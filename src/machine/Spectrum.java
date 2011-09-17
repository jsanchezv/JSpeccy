/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import gui.JSpeccyScreen;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import utilities.Snapshots;
import utilities.Tape;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Spectrum extends Thread implements z80core.MemIoOps, KeyListener {

    private Z80 z80;
    private Memory memory;
    private boolean[] contendedPage = new boolean[4];
    //private int z80Ram[] = new int[0x10000];
    private int rowKey[] = new int[8];
    public int portFE, earBit = 0xbf, kempston, port7ffd;
    private long nFrame, framesByInt, speedometer, speed, prevSpeed;
    private boolean soundOn, resetPending;
    public static final int FRAMES48k = 69888;
    public static final int FRAMES128k = 70908;
    private static final byte delayTstates[] = new byte[FRAMES128k + 100];
    public enum SpectrumModel { SPECTRUM_48K, SPECTRUM_128K, SPECTRUM_PLUS3 };
    public SpectrumModel spectrumModel;
    private Timer timerFrame;
    private SpectrumTimer taskFrame;
    private JSpeccyScreen jscr;
    private Audio audio;
    private AY8912 ay8912;
    public Tape tape;
    private boolean paused;

    private javax.swing.JLabel speedLabel;

    public Spectrum() {
        super("SpectrumThread");
        spectrumModel = SpectrumModel.SPECTRUM_48K;
        z80 = new Z80(this);
        memory = new Memory();
        memory.setMemoryMap128k();
        Arrays.fill(contendedPage, false);
        contendedPage[1] = true;
        memory.loadRoms();
        initGFX();
        nFrame = speedometer = 0;
        framesByInt = 1;
        Arrays.fill(rowKey, 0xff);
        portFE = 0;
        port7ffd = 0;
        kempston = 0;
        timerFrame = new Timer("SpectrumClock", true);
        ay8912 = new AY8912(1773450);
        audio = new Audio();
        soundOn = true;
        paused = true;
        resetPending = false;
        tape = new Tape(z80);
    }

    /*
     * Esto es necesario para conseguir un mejor funcionamiento en Windows.
     * Para los sistemas Unix debería ser una modificación inocua. La razón del
     * hack está explicada en:
     * http://blogs.sun.com/dholmes/entry/inside_the_hotspot_vm_clocks
     * y en
     * http://www.javamex.com/tutorials/threads/sleep.shtml
     * 
     */
    @Override
    public void run() {
        try {
            sleep(Long.MAX_VALUE);
        } catch (InterruptedException excpt) {
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, excpt);
        }
    }

    public void startEmulation() {
        z80.setTEstados(0);
        audio.audiotstates = 0;
        invalidateScreen();
        taskFrame = new SpectrumTimer(this);
        timerFrame.scheduleAtFixedRate(taskFrame, 50, 20);
        paused = false;
        if (soundOn)
            audio.open(FRAMES128k);
    }

    public void stopEmulation() {
        taskFrame.cancel();
        paused = true;
//        audio.flush();
        if (soundOn)
            audio.close();
    }

    public void reset() {
        resetPending = true;
    }

    private void doReset() {
        z80.reset();
        memory.setMemoryMap128k();
        contendedPage[3] = false;
        nFrame = 0;
        audio.audiotstates = 0;
        Arrays.fill(rowKey, 0xff);
        portFE = 0;
        port7ffd = 0;
        kempston = 0;
        invalidateScreen();
    }

    public boolean isPaused() {
        return paused;
    }

    public void triggerNMI() {
        z80.emitNMI();
    }

    public void generateFrame48k() {
//        long startFrame, endFrame, sleepTime;
//        startFrame = System.currentTimeMillis();
//        System.out.println("Start frame: " + startFrame);

        //z80.tEstados = frameStart;
        //System.out.println(String.format("Begin frame. t-states: %d", z80.tEstados));
        if (resetPending) {
            resetPending = false;
            doReset();
        }

        long counter = framesByInt;

        do {
            z80.setINTLine(true);
            z80.execute(32); // La INT dura 32 t-states
            z80.setINTLine(false);
            z80.execute(14335);
            updateInterval(14328, z80.tEstados);
            //System.out.println(String.format("t-states: %d", z80.tEstados));
            //int fromTstates;
            // El último byte de pantalla se muestra en el estado 57236
            while (z80.tEstados < 57237) {
                int fromTstates = z80.tEstados + 1;
                z80.execute(fromTstates + 15);
                updateInterval(fromTstates, z80.tEstados);
            }

            //z80.statesLimit = FRAMES48k;
            z80.execute(FRAMES48k);

            audio.updateAudio(z80.tEstados, speaker);
            audio.endFrame();
//            System.out.println("Playing " + audio.bufp + " bytes");
            audio.audiotstates -= FRAMES48k;

            z80.tEstados -= FRAMES48k;
//            z80.addTEstados(-FRAMES48k);

            if (++nFrame % 16 == 0) {
                toggleFlash();
            }

            if (nFrame % 50 == 0) {
                long now = System.currentTimeMillis();
                speed = 100000 / (now - speedometer);
                speedometer = now;
                if (speed != prevSpeed) {
                    prevSpeed = speed;
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            speedLabel.setText(String.format("%4d%%", speed));
                        }
                    });
//                    System.out.println(String.format("Time: %d Speed: %d%%",now, speed));
                }
            }
        } while (--counter > 0);

        /* 20/03/2010
         * La pantalla se ha de dibujar en un solo paso. Si el borde no se
         * modificó, se vuelca sobre el doble búfer solo la pantalla. Si se
         * modificó el borde, primero se vuelca la pantalla sobre la imagen
         * del borde y luego se vuelca el borde. Si no, se pueden ver "artifacts"
         * en juegos como el TV-game.
         */
        if (screenUpdated || nBorderChanges > 0) {
//            System.out.print(System.currentTimeMillis() + " ");
            if (nBorderChanges > 0) {
                if (nBorderChanges == 1) {
                    intArrayFill(imgData, Paleta[portFE & 0x07]);
                    nBorderChanges = 0;
                } else {
                    nBorderChanges = 1;
                }
                gcBorderImage.drawImage(screenImage, BORDER_WIDTH, BORDER_WIDTH, null);
                gcTvImage.drawImage(borderImage, 0, 0, null);
//                System.out.print("Border + ");
            } else {
                gcTvImage.drawImage(screenImage, BORDER_WIDTH, BORDER_WIDTH, null);
            }
//            System.out.println("Screen$");

            if (nBorderChanges == 0) {
                screenUpdated = false;
            }
            jscr.repaint();
        }

        //endFrame = System.currentTimeMillis();
        //System.out.println("End frame: " + endFrame);
        //sleepTime = endFrame - startFrame;
        //System.out.println("execFrame en: " + sleepTime);
    }

    public void generateFrame() {
//        long startFrame, endFrame, sleepTime;
//        startFrame = System.currentTimeMillis();
//        System.out.println("Start frame: " + startFrame);

        //z80.tEstados = frameStart;
        //System.out.println(String.format("Begin frame. t-states: %d", z80.tEstados));
        if (resetPending) {
            resetPending = false;
            doReset();
        }

        long counter = framesByInt;

        do {
            z80.setINTLine(true);
            z80.execute(36); // La INT dura 36 t-states en el 128k
            z80.setINTLine(false);
            z80.execute(14363);
            updateInterval(14356, z80.tEstados);
            //System.out.println(String.format("t-states: %d", z80.tEstados));
            //int fromTstates;
            // El último byte de pantalla se muestra en el estado 57236
            while (z80.tEstados < 58040) {
                int fromTstates = z80.tEstados + 1;
                z80.execute(fromTstates + 15);
                updateInterval(fromTstates, z80.tEstados);
            }

            //z80.statesLimit = FRAMES48k;
            z80.execute(FRAMES128k);

            audio.updateAudio(z80.tEstados, speaker);
            audio.endFrame();
//            System.out.println("Playing " + audio.bufp + " bytes");
            audio.audiotstates -= FRAMES128k;

            z80.tEstados -= FRAMES128k;
//            z80.addTEstados(-FRAMES48k);

            if (++nFrame % 16 == 0) {
                toggleFlash();
            }

            if (nFrame % 50 == 0) {
                long now = System.currentTimeMillis();
                speed = 100000 / (now - speedometer);
                speedometer = now;
                if (speed != prevSpeed) {
                    prevSpeed = speed;
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            speedLabel.setText(String.format("%4d%%", speed));
                        }
                    });
//                    System.out.println(String.format("Time: %d Speed: %d%%",now, speed));
                }
            }
        } while (--counter > 0);

        /* 20/03/2010
         * La pantalla se ha de dibujar en un solo paso. Si el borde no se
         * modificó, se vuelca sobre el doble búfer solo la pantalla. Si se
         * modificó el borde, primero se vuelca la pantalla sobre la imagen
         * del borde y luego se vuelca el borde. Si no, se pueden ver "artifacts"
         * en juegos como el TV-game.
         */
        if (screenUpdated || nBorderChanges > 0) {
//            System.out.print(System.currentTimeMillis() + " ");
            if (nBorderChanges > 0) {
                if (nBorderChanges == 1) {
                    intArrayFill(imgData, Paleta[portFE & 0x07]);
                    nBorderChanges = 0;
                } else {
                    nBorderChanges = 1;
                }
                gcBorderImage.drawImage(screenImage, BORDER_WIDTH, BORDER_WIDTH, null);
                gcTvImage.drawImage(borderImage, 0, 0, null);
//                System.out.print("Border + ");
            } else {
                gcTvImage.drawImage(screenImage, BORDER_WIDTH, BORDER_WIDTH, null);
            }
//            System.out.println("Screen$");

            if (nBorderChanges == 0) {
                screenUpdated = false;
            }
            jscr.repaint();
        }

        //endFrame = System.currentTimeMillis();
        //System.out.println("End frame: " + endFrame);
        //sleepTime = endFrame - startFrame;
        //System.out.println("execFrame en: " + sleepTime);
    }

    public void setScreenComponent(JSpeccyScreen jScr) {
        this.jscr = jScr;
    }

    public void setSpeedLabel(javax.swing.JLabel speedComponent) {
        speedLabel = speedComponent;
    }

    public int fetchOpcode(int address) {

        if (contendedPage[address >>> 14]) {
//            System.out.println(String.format("getOpcode: %d %d %d",
//                    z80.tEstados, address, delayTstates[z80.tEstados]));
            z80.tEstados += delayTstates[z80.tEstados];
//            z80.addTEstados(delayTstates[z80.getTEstados()]);
        }
        z80.tEstados += 4;
//        z80.addTEstados(4);

//        if ((z80.getRegI() & 0xC0) == 0x40) {
//            m1contended = z80.tEstados;
////            m1contended = z80.getTEstados();
//            m1regR = z80.getRegR();
//        }

        // LD_BYTES routine in Spectrum ROM at address 0x0556
        if (address == 0x0556 && tape.isTapeInserted()&& tape.isStopped()) {
            if (!tape.isFastload() || tape.isTzxTape())
                tape.play();
            else{
                tape.fastload(memory);
                invalidateScreen(); // thank's Andrew Owen
                return 0xC9; // RET opcode
            }
        }
        return memory.readByte(address);
    }

    public int peek8(int address) {

        if (contendedPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
//            z80.addTEstados(delayTstates[z80.getTEstados()]);
        }
        z80.tEstados += 3;
//        z80.addTEstados(3);

        return memory.readByte(address);
    }

    public void poke8(int address, int value) {

        if (contendedPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
//            z80.addTEstados(delayTstates[z80.getTEstados()]);
            if (address < 0x5b00) {
                screenUpdated(address);
            }
        }
        z80.tEstados += 3;
//        z80.addTEstados(3);

        memory.writeByte(address, value);
    }

    public int peek16(int address) {

        int lsb;
        if (contendedPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
//            z80.addTEstados(delayTstates[z80.getTEstados()]);
        }
        z80.tEstados += 3;
//        z80.addTEstados(3);
        lsb = memory.readByte(address);

        address = (address + 1) & 0xffff;
        if (contendedPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
//            z80.addTEstados(delayTstates[z80.getTEstados()]);
        }
        z80.tEstados += 3;
//        z80.addTEstados(3);

        //msb = z80Ram[address];
        return (memory.readByte(address) << 8) | lsb;
    }

    public void poke16(int address, int word) {
//        poke8(address, word & 0xff);
//        poke8((address + 1) & 0xffff, word >>> 8);

        if (contendedPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
//            z80.addTEstados(delayTstates[z80.getTEstados()]);
            if (address < 0x5b00) {
                screenUpdated(address);
            }
        }
        z80.tEstados += 3;
//        z80.addTEstados(3);

        memory.writeByte(address, word & 0xff);

        address = (address + 1) & 0xffff;

        if (contendedPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
//            z80.addTEstados(delayTstates[z80.getTEstados()]);
            if (address < 0x5b00) {
                screenUpdated(address);
            }
        }
        z80.tEstados += 3;
//        z80.addTEstados(3);

        memory.writeByte(address, word >>> 8);
    }

    public void contendedStates(int address, int tstates) {
        if (contendedPage[address >>> 14]) {
            for (int idx = 0; idx < tstates; idx++) {
                z80.tEstados += delayTstates[z80.tEstados] + 1;
//                z80.addTEstados(delayTstates[z80.getTEstados()] + 1);
            }
        } else {
            z80.tEstados += tstates;
//            z80.addTEstados(tstates);
        }
    }

    public int inPort(int port) {
        int res = port >>> 8;

//        if ((port & 0xff) == 0xff) {
//            System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                    z80.tEstados, z80.getRegPC()));
//        }
        //int tstates = z80.tEstados;
        //postIO(port);
//        System.out.println(String.format("InPort: %04X", port));
        preIO(port);
        postIO(port);
//        tape.notifyTstates(nFrame, z80.tEstados);

//        System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                    z80.tEstados, z80.getRegPC()));
        /*
         * El interfaz Kempston solo (debería) decodificar A5=0...
         * Las Tres Luces de Glaurung leen el puerto #DF (223) y si se decodifica
         * algo más no funciona con el joystick. Si decodificamos solo A5==0
         * es Buggy Boy el que se cuelga.
         */
        if ((port & 0x00e0) == 0 || (port & 0x0020) == 0) {
//            System.out.println(String.format("InPort: %04X, PC: %04X", port, z80.getRegPC()));
            return kempston;
        }

        if ((port & 0xC002) == 0xC000) {
            return ay8912.getAYRegister();
        }

        if ((port & 0x0001) == 0) {
            int keys = 0xff;
//            System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                   z80.tEstados, z80.getRegPC()));
//            System.out.println(String.format("InPort: %04X", port));
            res = ~res & 0xff;
            for (int row = 0, mask = 0x01; row < 8; row++, mask <<= 1) {
                if ((res & mask) != 0) {
                    keys &= rowKey[row];
                }
            }

            return keys & tape.getEarBit();
        }

//        System.out.println(String.format("InPort: %04X at %d t-states", port, z80.tEstados));
        int floatbus = 0xff;
        int addr = 0;
        int tstates = z80.tEstados;
//        if (tstates < 14336 || tstates > 57248) {
        if (tstates < 14364 || tstates > 58040) {
            return floatbus;
        }

        int col = (tstates % 228) -  1; // - 3;
        if (col > 124) {
            return floatbus;
        }

        int row = tstates / 228 - 63; // - 64

        switch (col % 8) {
            case 0:
                addr = scrAddr[row] + col / 4;
                floatbus = memory.readScreenByte(addr);
                break;
            case 1:
                addr = scr2attr[(scrAddr[row] + col / 4) & 0x1fff];
                floatbus = memory.readScreenByte(addr);
                break;
            case 2:
                addr = scrAddr[row] + col / 4 + 1;
                floatbus = memory.readScreenByte(addr);
                break;
            case 3:
                addr = scr2attr[(scrAddr[row] + col / 4 + 1) & 0x1fff];
                floatbus = memory.readScreenByte(addr);
                break;
//            default:
//                return floatbus;
        }
        
        if ((port & 0x8002) == 0) {
            memory.setMemoryMap128k(floatbus);
            // En el 128k las páginas impares son contended
            contendedPage[3] = (floatbus & 0x01) != 0 ? true : false;
            // Si ha cambiado la pantalla visible hay que invalidar
            if( (port7ffd & 0x08) != (floatbus & 0x08))
                invalidateScreen();
            port7ffd = floatbus;
        }
//        floatbus = memory.readScreenByte(addr);
//            System.out.println(String.format("tstates = %d, addr = %d, floatbus = %02x",
//                    tstates, addr, floatbus));
        return floatbus;
    }

    public void outPort(int port, int value) {
        preIO(port);

        if ((port & 0x0001) == 0) {
            if ((portFE & 0x07) != (value & 0x07)) {
//                if( (value & 0x07) == 0x07 )
//                    System.out.println(String.format("tstates: %d border: %d",
//                        z80.tEstados, value&0x07));
                updateBorder(z80.tEstados);
            }

            //if (tape.isStopped()) {
                int spkMic = sp_volt[value >> 3 & 3];
                if (spkMic != speaker) {
//                au_update();
//                    if (soundOn) {
                        audio.updateAudio(z80.tEstados, speaker);
//                    }
                    speaker = spkMic;
                }
            //}

            if (tape.isStopped()) {
                // and con 0x18 para emular un Issue 2
                // and con 0x10 para emular un Issue 3
                if ((value & 0x10) == 0)
                    tape.setEarBit(false);
                else
                    tape.setEarBit(true);
            }
            //System.out.println(String.format("outPort: %04X %02x", port, value));         
            portFE = value;
        }

        if ((port & 0x8002) == 0) {
            memory.setMemoryMap128k(value);
            // En el 128k las páginas impares son contended
            contendedPage[3] = (value & 0x01) != 0 ? true : false;
            // Si ha cambiado la pantalla visible hay que invalidar
            if( (port7ffd & 0x08) != (value & 0x08))
                invalidateScreen();
            port7ffd = value;
        }
        
        if ((port & 0x8002) == 0x8000) {
            if ((port & 0x4000) != 0) {
                ay8912.setIndexRegister(value);
            } else {
                audio.updateAudio(z80.tEstados, speaker);
                ay8912.setAYRegister(value);
            }
        }
        //preIO(port);
        postIO(port);
//        tape.notifyTstates(nFrame, z80.tEstados);
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
            if (contendedPage[port >>> 14]) {
                // A0 == 1 y es contended RAM
                z80.tEstados += delayTstates[z80.tEstados] + 1;
                z80.tEstados += delayTstates[z80.tEstados] + 1;
                z80.tEstados += delayTstates[z80.tEstados] + 1;

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
                z80.tEstados += delayTstates[z80.tEstados] + 3;
//            }
        }
    }

    private void preIO(int port) {
        if (contendedPage[port >>> 14]) {
            // A0 == 1 y es contended RAM
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados++;
//        z80.addTEstados(1);
    }

    public void execDone(int tstates) {
        tape.notifyTstates(nFrame, z80.tEstados);
        if (tape.isPlaying()) {
            earBit = tape.getEarBit();
            int spkMic = sp_volt[(earBit >>> 5) & 0x02];
            if (spkMic != speaker) {
//                au_update();
//                if (soundOn) {
                audio.updateAudio(z80.tEstados, speaker);
            }
            speaker = spkMic;
//            }
        }
    }

    public void keyPressed(KeyEvent evt) {
        int key = evt.getKeyCode();
        //System.out.println(evt.getKeyText(key));
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
//                rowKey[4] &= 0xfb; // 8  -- Right arrow
                kempston |= 0x01;
                break;
            case KeyEvent.VK_DELETE:
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
            case KeyEvent.VK_DELETE:
//                rowKey[0] |= 0x01; // CAPS
//                rowKey[4] |= 0x04; // 8  -- Right arrow
                kempston &= 0xef;
                break;
        }
    }

    public void keyTyped(java.awt.event.KeyEvent evt) {
        // TODO add your handling code here:
    }

    public void loadSnapshot(File filename) {
        Snapshots snap = new Snapshots();
        if (snap.loadSnapshot(filename, memory)) {
            doReset();

            z80.setRegI(snap.getRegI());
            z80.setRegHLalt(snap.getRegHLalt());
            z80.setRegDEalt(snap.getRegDEalt());
            z80.setRegBCalt(snap.getRegBCalt());
            z80.setRegAFalt(snap.getRegAFalt());
            z80.setRegHL(snap.getRegHL());
            z80.setRegDE(snap.getRegDE());
            z80.setRegBC(snap.getRegBC());
            z80.setRegIY(snap.getRegIY());
            z80.setRegIX(snap.getRegIX());

            z80.setIFF2(snap.getIFF2());
            z80.setIFF1(snap.getIFF1());

            z80.setRegR(snap.getRegR());
            z80.setRegAF(snap.getRegAF());
            z80.setRegSP(snap.getRegSP());
            z80.setIM(snap.getModeIM());

            int border = snap.getBorder();
            portFE &= 0xf8;
            portFE |= border;

            //int count;
//            for (int count = 0x4000; count < 0x10000; count++) {
//                memory.writeByte(count, snap.getRamAddr(count));
//            }

            z80.setRegPC(snap.getRegPC());  // código de RETN en la ROM
            z80.setTEstados(snap.getTstates());

            System.out.println(ResourceBundle.getBundle("machine/Bundle").getString(
                "IMAGEN_CARGADA"));
        } else {
            JOptionPane.showMessageDialog(jscr.getParent(), snap.getErrorString(),
                ResourceBundle.getBundle("machine/Bundle").getString(
                "NO_SE_PUDO_CARGAR_LA_IMAGEN"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public void saveSnapshot(File filename) {
        Snapshots snap = new Snapshots();

        snap.setRegI(z80.getRegI());
        snap.setRegHLalt(z80.getRegHLalt());
        snap.setRegDEalt(z80.getRegDEalt());
        snap.setRegBCalt(z80.getRegBCalt());
        snap.setRegAFalt(z80.getRegAFalt());
        snap.setRegHL(z80.getRegHL());
        snap.setRegDE(z80.getRegDE());
        snap.setRegBC(z80.getRegBC());
        snap.setRegIY(z80.getRegIY());
        snap.setRegIX(z80.getRegIX());

        snap.setIFF2(z80.isIFF2());
        snap.setIFF1(z80.isIFF1());

        snap.setRegR(z80.getRegR());
        snap.setRegAF(z80.getRegAF());
        snap.setRegSP(z80.getRegSP());
        snap.setModeIM(z80.getIM());
        snap.setBorder(portFE & 0x07);

//        int count;
//        for (count = 0x4000; count < 0x10000; count++) {
//            snap.setRamAddr(count, memory.readByte(count));
//        }

        snap.setRegPC(z80.getRegPC());
        snap.setTstates(z80.getTEstados());

        if (snap.saveSnapshot(filename, memory)) {
            System.out.println(
                ResourceBundle.getBundle("machine/Bundle").getString("IMAGEN_GUARDADA"));
        } else {
            JOptionPane.showMessageDialog(jscr.getParent(), snap.getErrorString(),
                ResourceBundle.getBundle("machine/Bundle").getString(
                "NO_SE_PUDO_CARGAR_LA_IMAGEN"), JOptionPane.ERROR_MESSAGE);
        }
    }

    static final int CHANNEL_VOLUME = 26000;
    static final int SPEAKER_VOLUME = 8000;
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
//            audio = new Audio();
            audio.open(FRAMES128k);
            framesByInt = 1;
        } else {
            audio.flush();
            audio.close();
            framesByInt = 10;
        }
    }

    public void toggleTape() {
        if (tape.isStopped()) {
            tape.play();
        } else {
            tape.stop();
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
        sp_volt[1] = (int) (-SPEAKER_VOLUME * 1.25);
        sp_volt[2] = (int) SPEAKER_VOLUME;
        sp_volt[3] = (int) (SPEAKER_VOLUME * 1.25);
    }

    /* Sección gráfica */
    //Vector con los valores correspondientes a lo colores anteriores
    private static final int[] Paleta = {
        0x000000, /* negro */
        0x0000c0, /* azul */
        0xc00000, /* rojo */
        0xc000c0, /* magenta */
        0x00c000, /* verde */
        0x00c0c0, /* cyan */
        0xc0c000, /* amarillo */
        0xc0c0c0, /* blanco */
        0x000000, /* negro brillante */
        0x0000ff, /* azul brillante */
        0xff0000, /* rojo brillante	*/
        0xff00ff, /* magenta brillante */
        0x00ff00, /* verde brillante */
        0x00ffff, /* cyan brillante */
        0xffff00, /* amarillo brillante */
        0xffffff /* blanco brillante */};

    // Tablas de valores de Paper/Ink. Para cada valor general de atributo,
    // corresponde una entrada en la tabla que hace referencia al color
    // en la paleta. Para los valores superiores a 127, los valores de Paper/Ink
    // ya están cambiados, lo que facilita el tratamiento del FLASH.
    private static final int Paper[] = new int[256];
    private static final int Ink[] = new int[256];
    // Tabla de correspondencia entre la dirección de pantalla y su atributo
    public final int scr2attr[] = new int[0x1800];
    // Tabla de correspondencia entre cada atributo y el primer byte del carácter
    // en la pantalla del Spectrum (la contraria de la anterior)
    private final int attr2scr[] = new int[768];
    // Tabla de correspondencia entre la dirección de pantalla del Spectrum
    // y la dirección que le corresponde en el BufferedImage.
    private final int bufAddr[] = new int[0x1800];
    // Tabla que contiene la dirección de pantalla del primer byte de cada
    // carácter en la columna cero.
    public final int scrAddr[] = new int[192];
    // Tabla que indica si un byte de la pantalla ha sido modificado y habrá que
    // redibujarlo.
    private final boolean dirtyByte[] = new boolean[0x1800];
    // Tabla de traslación entre t-states y la dirección de la pantalla del
    // Spectrum que se vuelca en ese t-state o -1 si no le corresponde ninguna.
    private final int states2scr[] = new int[FRAMES128k+100];
    // Tabla de traslación de t-states al pixel correspondiente del borde.
    private final int states2border[] = new int[FRAMES128k+100];
    public static final int BORDER_WIDTH = 32;
    public static final int SCREEN_WIDTH = BORDER_WIDTH + 256 + BORDER_WIDTH;
    public static final int SCREEN_HEIGHT = BORDER_WIDTH + 192 + BORDER_WIDTH;

    static {
        // Inicialización de las tablas de Paper/Ink
        /* Para cada valor de atributo, hay dos tablas, donde cada una
         * ya tiene el color que le corresponde, para no tener que extraerlo
         */
        for (int idx = 0; idx < 256; idx++) {
            int ink = (idx & 0x07) | ((idx & 0x40) != 0 ? 0x08 : 0x00);
            int paper = ((idx >>> 3) & 0x07) | ((idx & 0x40) != 0 ? 0x08 : 0x00);
            if (idx < 128) {
                Ink[idx] = Paleta[ink];
                Paper[idx] = Paleta[paper];
            } else {
                Ink[idx] = Paleta[paper];
                Paper[idx] = Paleta[ink];
            }
        }
    }
    private int flash = 0x7f; // 0x7f == ciclo off, 0xff == ciclo on
    private BufferedImage borderImage; // imagen del borde
    private int imgData[];
    private BufferedImage screenImage; // imagen de la pantalla
    private BufferedImage tvImage;  // doble buffer de borde+pantalla
    private int imgDataScr[];
    // t-states del último cambio de border
    private int lastChgBorder;
    // veces que ha cambiado el borde en el último frame
    public int nBorderChanges;
    public boolean screenUpdated;
    // t-states del ciclo contended por I=0x40-0x7F o -1
    public int m1contended;
    // valor del registro R cuando se produjo el ciclo m1
    public int m1regR;
    private Graphics2D gcBorderImage, gcTvImage;

    private void initGFX() {
        tvImage = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        gcTvImage = tvImage.createGraphics();
        borderImage = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        gcBorderImage = borderImage.createGraphics();
        imgData = ((DataBufferInt) borderImage.getRaster().getDataBuffer()).getBankData()[0];
        screenImage = new BufferedImage(256, 192, BufferedImage.TYPE_INT_RGB);
        imgDataScr = ((DataBufferInt) screenImage.getRaster().getDataBuffer()).getBankData()[0];
        buildScreenTables128k();

        lastChgBorder = 0;
        m1contended = -1;
        Arrays.fill(dirtyByte, true);
        screenUpdated = false;
    }

    public BufferedImage getTvImage() {
        return tvImage;
    }
    
    public synchronized void toggleFlash() {
        flash = (flash == 0x7f ? 0xff : 0x7f);
        for (int addrAttr = 0x5800; addrAttr < 0x5b00; addrAttr++) {
            if (memory.readScreenByte(addrAttr) > 0x7f) {
                int address = attr2scr[addrAttr & 0x3ff] & 0x1fff;
                for (int scan = 0; scan < 8; scan++) {
                    dirtyByte[address] = true;
                    address += 256;
                }
            }
        }
    }

    /*
     * Cada línea completa de imagen dura 224 T-Estados, divididos en:
     * 128 T-Estados en los que se dibujan los 256 pixeles de pantalla
     * 24 T-Estados en los que se dibujan los 48 pixeles del borde derecho
     * 48 T-Estados iniciales de H-Sync y blanking
     * 24 T-Estados en los que se dibujan 48 pixeles del borde izquierdo
     *
     * Cada pantalla consta de 312 líneas divididas en:
     * 16 líneas en las cuales el haz vuelve a la parte superior de la pantalla
     * 48 líneas de borde superior
     * 192 líneas de pantalla
     * 56 líneas de borde inferior de las cuales se ven solo 48
     */
    private int tStatesToScrPix48k(int tstates) {

        // Si los tstates son < 3584 (16 * 224), no estamos en la zona visible
        if (tstates < (3584 + ((48 - BORDER_WIDTH) * 224))) {
            return 0;
        }

        // Se evita la zona no visible inferior
        if (tstates > (256 + BORDER_WIDTH) * 224) {
            return imgData.length - 1;
        }

        tstates -= 3584 + ((48 - BORDER_WIDTH) * 224);

        int row = tstates / 224;
        int col = tstates % 224;

        // No se puede dibujar en el borde con precisión de pixel.
        int mod = col % 8;
        col -= mod;
        if (mod > 3) {
            col += 4;
        }

//        System.out.println(String.format("t-states: %d\trow: %d\tcol: %d\tmod: %d",
//                tstates+3584, row, col, mod));

        int pix = row * SCREEN_WIDTH;

        if (col < (128 + BORDER_WIDTH / 2)) {
            return pix + col * 2 + BORDER_WIDTH;
        }
        if (col > (199 + (48 - BORDER_WIDTH) / 2)) {
            return pix + (col - (200 + (48 - BORDER_WIDTH) / 2)) * 2 + SCREEN_WIDTH;
        } else {
            return pix + SCREEN_WIDTH;
        }
    }

    private int tStatesToScrPix128k(int tstates) {

        // Si los tstates son < 3420 (15 * 228), no estamos en la zona visible
        if (tstates < (3420 + ((48 - BORDER_WIDTH) * 228))) {
            return 0;
        }

        // Se evita la zona no visible inferior
        if (tstates > (256 + BORDER_WIDTH) * 228) {
            return imgData.length - 1;
        }

        tstates -= 3420 + ((48 - BORDER_WIDTH) * 228);

        int row = tstates / 228;
        int col = tstates % 228;

        // No se puede dibujar en el borde con precisión de pixel.
        int mod = col % 8;
        col -= mod;
        if (mod > 3) {
            col += 4;
        }

//        System.out.println(String.format("t-states: %d\trow: %d\tcol: %d\tmod: %d",
//                tstates+3584, row, col, mod));

        int pix = row * SCREEN_WIDTH;

        if (col < (128 + BORDER_WIDTH / 2)) {
            return pix + col * 2 + BORDER_WIDTH;
        }
        if (col > (199 + (48 - BORDER_WIDTH) / 2)) {
            return pix + (col - (200 + (48 - BORDER_WIDTH) / 2)) * 2 + SCREEN_WIDTH;
        } else {
            return pix + SCREEN_WIDTH;
        }
    }

    public void updateBorder(int tstates) {
        int startPix, endPix, color;

        if (tstates < lastChgBorder) {
            //startPix = tStatesToScrPix(lastChgBorder);
            startPix = states2border[lastChgBorder];
            if (startPix < imgData.length - 1) {
                color = Paleta[portFE & 0x07];
                for (int count = startPix; count < imgData.length - 1; count++) {
                    imgData[count] = color;
                }
            }
            lastChgBorder = 0;
            nBorderChanges++;
        }

        startPix = states2border[lastChgBorder];
        //startPix = tStatesToScrPix(lastChgBorder);
        if (startPix > imgData.length - 1) {
            lastChgBorder = tstates;
            return;
        }

        //endPix = tStatesToScrPix(tstates);
        endPix = states2border[tstates];
        if (endPix > imgData.length - 1) {
            endPix = imgData.length - 1;
        }

        if (startPix < endPix) {
            color = Paleta[portFE & 0x07];
            for (int count = startPix; count < endPix; count++) {
                imgData[count] = color;
            }
        }
        lastChgBorder = tstates;
        nBorderChanges++;
    }

    public void updateInterval(int fromTstates, int toTstates) {
        int fromAddr, addrBuf;
        int paper, ink;
        int scrByte, attr;
        //System.out.println(String.format("from: %d\tto: %d", fromTstates, toTstates));

        while (fromTstates % 4 != 0) {
            fromTstates++;
        }

        while (fromTstates <= toTstates) {
            fromAddr = states2scr[fromTstates];
            if (fromAddr == -1 || !dirtyByte[fromAddr & 0x1fff]) {
                fromTstates += 4;
                continue;
            }

            scrByte = attr = 0;
            // si m1contended != -1 es que hay que emular el efecto snow.
            if (m1contended == -1) {
                scrByte = memory.readScreenByte(fromAddr);
                fromAddr &= 0x1fff;
                attr = memory.readScreenByte(scr2attr[fromAddr]);
            } else {
                int addr;
                int mod = m1contended % 8;
                if (mod == 0 || mod == 1) {
                    addr = (fromAddr & 0xff00) | m1regR;
                    scrByte = memory.readScreenByte(addr);
                    attr = memory.readScreenByte(scr2attr[fromAddr & 0x1fff]);
                    //System.out.println("Snow even");
                }
                if (mod == 2 || mod == 3) {
                    addr = (scr2attr[fromAddr & 0x1fff] & 0xff00) | m1regR;
                    scrByte = memory.readScreenByte(fromAddr);
                    attr = memory.readScreenByte(addr & 0x1fff);
                    //System.out.println("Snow odd");
                }
                fromAddr &= 0x1fff;
                m1contended = -1;
            }

            addrBuf = bufAddr[fromAddr];
            if (attr > 0x7f) {
                attr &= flash;
            }
            ink = Ink[attr];
            paper = Paper[attr];
            for (int mask = 0x80; mask != 0; mask >>= 1) {
                if ((scrByte & mask) != 0) {
                    imgDataScr[addrBuf++] = ink;
                } else {
                    imgDataScr[addrBuf++] = paper;
                }
            }
            dirtyByte[fromAddr] = false;
            screenUpdated = true;
            fromTstates += 4;
        }
    }

    public void screenUpdated(int address) {
        if (address < 0x5800) {
            dirtyByte[address & 0x1fff] = true;
        } else {
            int addr = attr2scr[address & 0x3ff] & 0x1fff;
            for (int scan = 0; scan < 8; scan++) {
                dirtyByte[addr] = true;
                addr += 256;
            }
        }
    }

    public void invalidateScreen() {
        nBorderChanges = 1;
        screenUpdated = true;
        Arrays.fill(dirtyByte, true);
        //intArrayFill(imgData, Paleta[portFE & 0x07]);
    }

    public void intArrayFill(int[] array, int value) {
        int len = array.length;
        if (len > 0) {
            array[0] = value;
        }

        for (int idx = 1; idx < len; idx += idx) {
            System.arraycopy(array, 0, array, idx, ((len - idx) < idx) ? (len - idx) : idx);
        }
    }

    private void buildScreenTables48k() {
        int row, col, scan;

        //Inicialización de la tabla de direcciones de pantalla
        /* Hay una entrada en la tabla con la dirección del primer byte
         * de cada fila de la pantalla.
         */
        for (int linea = 0; linea < 24; linea++) {
            int idx, lsb, msb, addr;
            lsb = ((linea & 0x07) << 5);
            msb = linea & 0x18;
            addr = (msb << 8) + lsb;
            idx = linea << 3;
            for (scan = 0; scan < 8; scan++, addr += 256) {
                scrAddr[scan + idx] = 0x4000 + addr;
            }
        }

        for (int address = 0x4000; address < 0x5800; address++) {
            row = ((address & 0xe0) >>> 5) | ((address & 0x1800) >>> 8);
            col = address & 0x1f;
            scan = (address & 0x700) >>> 8;

            bufAddr[address & 0x1fff] = row * 2048 + scan * 256 + col * 8;
            scr2attr[address & 0x1fff] = 0x5800 + row * 32 + col;
        }

        for (int address = 0x5800; address < 0x5B00; address++) {
            attr2scr[address & 0x3ff] = 0x4000 | ((address & 0x300) << 3) | (address & 0xff);
        }

        Arrays.fill(states2scr, -1);
        for (int tstates = 14336; tstates < 57344; tstates += 4) {
            col = (tstates % 224) / 4;
            if (col > 31) {
                continue;
            }

            scan = tstates / 224 - 64;
            states2scr[tstates - 8] = scrAddr[scan] + col;
        }
        
        for (int tstates = 0; tstates < states2border.length - 1; tstates++)
            states2border[tstates] = tStatesToScrPix48k(tstates);

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

    private void buildScreenTables128k() {
        int row, col, scan;

        //Inicialización de la tabla de direcciones de pantalla
        /* Hay una entrada en la tabla con la dirección del primer byte
         * de cada fila de la pantalla.
         */
        for (int linea = 0; linea < 24; linea++) {
            int idx, lsb, msb, addr;
            lsb = ((linea & 0x07) << 5);
            msb = linea & 0x18;
            addr = (msb << 8) + lsb;
            idx = linea << 3;
            for (scan = 0; scan < 8; scan++, addr += 256) {
                scrAddr[scan + idx] = 0x4000 + addr;
            }
        }

        for (int address = 0x4000; address < 0x5800; address++) {
            row = ((address & 0xe0) >>> 5) | ((address & 0x1800) >>> 8);
            col = address & 0x1f;
            scan = (address & 0x700) >>> 8;

            bufAddr[address & 0x1fff] = row * 2048 + scan * 256 + col * 8;
            scr2attr[address & 0x1fff] = 0x5800 + row * 32 + col;
        }

        for (int address = 0x5800; address < 0x5B00; address++) {
            attr2scr[address & 0x3ff] = 0x4000 | ((address & 0x300) << 3) | (address & 0xff);
        }

        Arrays.fill(states2scr, -1);
        for (int tstates = 14364; tstates < 58140; tstates += 4) {
            col = (tstates % 228) / 4;
            if (col > 31) {
                continue;
            }

            scan = tstates / 228 - 63;
            states2scr[tstates - 8] = scrAddr[scan] + col;
        }

        for (int tstates = 0; tstates < states2border.length - 1; tstates++)
            states2border[tstates] = tStatesToScrPix128k(tstates);

        Arrays.fill(delayTstates, (byte) 0x00);
        for (int idx = 14361; idx < 58040; idx += 228) {
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
}
