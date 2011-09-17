/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import gui.JSpeccyScreen;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import utilities.Snapshots;
import utilities.Tape;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Spectrum extends Thread implements z80core.MemIoOps {

    private Z80 z80;
    private Memory memory;
    private boolean[] contendedRamPage = new boolean[4];
    private boolean[] contendedIOPage = new boolean[4];
    public int portFE, earBit = 0xbf, port7ffd;
    private long nFrame, framesByInt, speedometer, speed, prevSpeed;
    private boolean soundOn, enabledAY, resetPending;
    private static final byte delayTstates[] =
            new byte[MachineTypes.SPECTRUM128K.tstatesFrame + 100];
    public MachineTypes spectrumModel;
    private Timer timerFrame;
    private SpectrumTimer taskFrame;
    private JSpeccyScreen jscr;
    private Keyboard keyboard;
    private Audio audio;
    private AY8912 ay8912;
    public Tape tape;
    private boolean paused;
    private javax.swing.JLabel modelLabel, speedLabel;
    private JMenuItem hardwareMenu48k, hardwareMenu128k, hardwareMenuPlus2;
    private JMenuItem joystickNone, joystickKempston,
            joystickSinclair1, joystickSinclair2, joystickCursor;

    public static enum Joystick {

        NONE, KEMPSTON, SINCLAIR1, SINCLAIR2, CURSOR
    };
    private Joystick joystick;
    private boolean issue3;

    public Spectrum() {
        super("SpectrumThread");
        z80 = new Z80(this);
        memory = new Memory();
        Arrays.fill(contendedRamPage, false);
        Arrays.fill(contendedIOPage, false);
        contendedRamPage[1] = contendedIOPage[1] = true;
        memory.loadRoms();
        initGFX();
        nFrame = speedometer = 0;
        framesByInt = 1;
        portFE = 0;
        port7ffd = 0;
        timerFrame = new Timer("SpectrumClock", true);
        ay8912 = new AY8912();
        audio = new Audio();
        tape = new Tape(z80);
        soundOn = true;
        paused = true;
        selectHardwareModel(MachineTypes.SPECTRUM48K);
        resetPending = false;
        joystick = Joystick.NONE;
        keyboard = new Keyboard();
        keyboard.setJoystick(joystick);
    }

    public final void selectHardwareModel(MachineTypes hardwareModel) {
        if (spectrumModel == hardwareModel) {
            return;
        }

        spectrumModel = hardwareModel;
        memory.setSpectrumModel(spectrumModel);
        tape.setSpectrumModel(spectrumModel);
        enabledAY = spectrumModel.hasAY8912();
        if (spectrumModel == MachineTypes.SPECTRUM48K) {
            buildScreenTables48k();
        } else {
            buildScreenTables128k();
        }
        issue3 = true;
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                modelLabel.setToolTipText(spectrumModel.getLongModelName());
                modelLabel.setText(spectrumModel.getShortModelName());
            }
        });
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
        audio.reset();
        invalidateScreen(true);
        taskFrame = new SpectrumTimer(this);
        timerFrame.scheduleAtFixedRate(taskFrame, 50, 20);
        paused = false;
        if (soundOn) {
            audio.open(spectrumModel, ay8912, enabledAY);
        }
    }

    public void stopEmulation() {
        taskFrame.cancel();
        paused = true;
        if (soundOn) {
            audio.close();
        }
    }

    public void reset() {
        resetPending = true;
    }

    private void doReset() {
        z80.reset();
        memory.reset();
        ay8912.reset();
        audio.reset();
        keyboard.reset();
        contendedRamPage[3] = contendedIOPage[3] = false;
        nFrame = 0;
        portFE = 0;
        port7ffd = 0;
        ULAplusMode = false;
        paletteGroup = 0;
        invalidateScreen(true);
    }

    public boolean isPaused() {
        return paused;
    }

    public void triggerNMI() {
        z80.triggerNMI();
    }

    public Keyboard getKeyboard() {
        return keyboard;
    }

    public void setJoystick(Joystick type) {
        joystick = type;
        keyboard.setJoystick(type);
    }

    public void setJoystickMenuItems(JMenuItem jNone, JMenuItem jKempston,
            JMenuItem jSinclair1, JMenuItem jSinclair2, JMenuItem jCursor) {
        joystickNone = jNone;
        joystickKempston = jKempston;
        joystickSinclair1 = jSinclair1;
        joystickSinclair2 = jSinclair2;
        joystickCursor = jCursor;
    }

    public void setHardwareMenuItems(JMenuItem hw48k, JMenuItem hw128k,
        JMenuItem hwPlus2) {
        hardwareMenu48k = hw48k;
        hardwareMenu128k = hw128k;
        hardwareMenuPlus2 = hwPlus2;
    }

    public void setScreenComponent(JSpeccyScreen jScr) {
        this.jscr = jScr;
    }

    public void setInfoLabels(JLabel nameComponent, JLabel speedComponent) {
        modelLabel = nameComponent;
        speedLabel = speedComponent;
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

        firstLine = lastLine = 0;
        leftCol = 31;
        rightCol = 0;
        lastChgBorder = spectrumModel.firstBorderUpdate;

        do {
            z80.setINTLine(true);
            z80.execute(spectrumModel.lengthINT);
            z80.setINTLine(false);

            z80.execute(spectrumModel.firstScrByte);
            updateScreen(spectrumModel.firstScrUpdate, z80.tEstados);

            //System.out.println(String.format("t-states: %d", z80.tEstados));
            //int fromTstates;

            while (z80.tEstados < spectrumModel.lastScrUpdate) {
                int fromTstates = z80.tEstados + 1;
                z80.execute(fromTstates + 11);
                updateScreen(fromTstates, z80.tEstados);
            }
            
            z80.execute(spectrumModel.tstatesFrame);

            if (borderChanged) {
                updateBorder(spectrumModel.lastBorderUpdate);
            }

            if (soundOn) {
                if (enabledAY) {
                    ay8912.updateAY(z80.tEstados);
                    ay8912.endFrame();
                }
                audio.updateAudio(z80.tEstados, speaker);
                audio.endFrame();
            }

            z80.tEstados -= spectrumModel.tstatesFrame;

            if (++nFrame % 16 == 0 && !ULAplusMode) {
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

//        System.out.println(
//         String.format("borderdirty: %b, screenDirty: %b, lastChgBorder: %d, nBorderChanges %d",
//           borderDirty, screenDirty, lastChgBorder, nBorderChanges));

        if (borderDirty || framesByInt > 1) {
            if (nBorderChanges == 0 && framesByInt == 1) {
                borderDirty = borderChanged = false;
            } else {
                nBorderChanges = 0;
                screenDirty = false;
                gcTvImage.drawImage(inProgressImage, 0, 0, null);
                jscr.repaint();
//                System.out.println("Repaint border");
            }
        }

        if (screenDirty) {
            screenDirty = false;
            gcTvImage.drawImage(inProgressImage, 0, 0, null);
            firstLine = repaintTable[firstLine & 0x1fff];
            lastLine = repaintTable[lastLine & 0x1fff];

            if (jscr.isDoubleSized()) {
                jscr.repaint(BORDER_WIDTH * 2 + leftCol * 16, (BORDER_WIDTH + firstLine) * 2,
                        (rightCol - leftCol + 1) * 16, (lastLine - firstLine + 1) * 2);
//                System.out.println(String.format("repaint x: %d, y: %d, w: %d, h: %d",
//                        BORDER_WIDTH * 2 + leftCol * 16, (BORDER_WIDTH + firstLine) * 2,
//                        (rightCol - leftCol + 1) * 16, (lastLine - firstLine + 1) * 2));
//                jscr.repaint(BORDER_WIDTH * 2, BORDER_WIDTH * 2, 512, 384);
            } else {
                jscr.repaint(BORDER_WIDTH + leftCol * 8, BORDER_WIDTH + firstLine,
                        (rightCol - leftCol + 1) * 8, lastLine - firstLine + 1);
//                System.out.println(String.format("repaint x: %d, y: %d, w: %d, h: %d",
//                        BORDER_WIDTH + leftCol * 8, BORDER_WIDTH + firstLine,
//                        (rightCol - leftCol + 1) * 8, lastLine - firstLine + 1));
//                  jscr.repaint(BORDER_WIDTH, BORDER_WIDTH , 256, 192);

            }
        }


        //endFrame = System.currentTimeMillis();
        //System.out.println("End frame: " + endFrame);
        //sleepTime = endFrame - startFrame;
        //System.out.println("execFrame en: " + sleepTime);
    }

    public int fetchOpcode(int address) {

        if (contendedRamPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados += 4;

//        if ((z80.getRegI() & 0xC0) == 0x40) {
//            m1contended = z80.tEstados;
//            m1regR = z80.getRegR();
//        }

        // LD_BYTES routine in Spectrum ROM at address 0x0556
        if (address == 0x0556 && tape.isTapeInserted() && tape.isStopped()) {
            if (!tape.isFastload() || tape.isTzxTape()) {
                tape.play();
            } else {
                tape.fastload(memory);
                invalidateScreen(true); // thanks Andrew Owen
                return 0xC9; // RET opcode
            }
        }
        return memory.readByte(address);
    }

    public int peek8(int address) {

        if (contendedRamPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados += 3;

        return memory.readByte(address);
    }

    public void poke8(int address, int value) {

        if (contendedRamPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
            if (memory.isScreenByte(address)) {
                notifyScreenWrite(address);
            }
        }
        z80.tEstados += 3;

        memory.writeByte(address, value);
    }

    public int peek16(int address) {

        int lsb;
        if (contendedRamPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados += 3;
        lsb = memory.readByte(address);

        address = (address + 1) & 0xffff;
        if (contendedRamPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados += 3;

        return (memory.readByte(address) << 8) | lsb;
    }

    public void poke16(int address, int word) {

        if (contendedRamPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
            if (memory.isScreenByte(address)) {
                notifyScreenWrite(address);
            }
        }
        z80.tEstados += 3;

        memory.writeByte(address, word & 0xff);

        address = (address + 1) & 0xffff;

        if (contendedRamPage[address >>> 14]) {
            z80.tEstados += delayTstates[z80.tEstados];
            if (memory.isScreenByte(address)) {
                notifyScreenWrite(address);
            }
        }
        z80.tEstados += 3;

        memory.writeByte(address, word >>> 8);
    }

    public void contendedStates(int address, int tstates) {
        if (contendedRamPage[address >>> 14]) {
            for (int idx = 0; idx < tstates; idx++) {
                z80.tEstados += delayTstates[z80.tEstados] + 1;
            }
        } else {
            z80.tEstados += tstates;
        }
    }

    public int inPort(int port) {

//        if ((port & 0xff) == 0xff) {
//            System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                    z80.tEstados, z80.getRegPC()));
//        }
//        System.out.println(String.format("InPort: %04X", port));
        preIO(port);
        postIO(port);

//        System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                    z80.tEstados, z80.getRegPC()));
        /*
         * El interfaz Kempston solo (debería) decodificar A5=0...
         * Las Tres Luces de Glaurung leen el puerto #DF (223) y si se decodifica
         * algo más no funciona con el joystick. Si decodificamos solo A5==0
         * es Buggy Boy el que se cuelga.
         */
        if (((port & 0x00e0) == 0 || (port & 0x0020) == 0)
                && joystick == Joystick.KEMPSTON) {
//            System.out.println(String.format("InPort: %04X, PC: %04X", port, z80.getRegPC()));
            return keyboard.readKempstonPort();
        }

        if ((port & 0x0001) == 0) {
            return keyboard.readKeyboardPort(port) & tape.getEarBit();
        }

        if (enabledAY) {
            if ((port & 0xC002) == 0xC000) {
                return ay8912.readRegister();
            }
        }
        
        // ULAplus Data Port (read/write)
        if ((port & 0x4004) == 0x4000) {
            if (paletteGroup == 0x40) {
                return ULAplusMode ? 0x01 : 0x00;
            } else {
                return ULAplus[paletteGroup >>> 4][paletteGroup & 0x0f];
            }
        }

//        System.out.println(String.format("InPort: %04X at %d t-states", port, z80.tEstados));
        int floatbus = 0xff;
        int addr = 0;
        int tstates = z80.tEstados;
        if (tstates < spectrumModel.firstScrByte || tstates > spectrumModel.lastScrUpdate) {
            return floatbus;
        }

        int col = (tstates % spectrumModel.tstatesLine) - spectrumModel.outOffset;
        if (col > 124) {
            return floatbus;
        }

        int row = tstates / spectrumModel.tstatesLine - spectrumModel.upBorderWidth;

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
        }

        /*
         * Solo en el modelo 128K, pero no en los +2A/+3, si se lee el puerto
         * 0x7ffd, el valor leído es reescrito en el puerto 0x7ffd.
         */
        if (spectrumModel != MachineTypes.SPECTRUM48K) {
            if ((port & 0x8002) == 0) {
                if (spectrumModel == MachineTypes.SPECTRUM128K ||
                    spectrumModel == MachineTypes.SPECTRUMPLUS2) {
                    memory.setPort7ffd(floatbus);
                    // Si ha cambiado la pantalla visible hay que invalidar
                    if ((port7ffd & 0x08) != (floatbus & 0x08)) {
                        invalidateScreen(false);
                    }
                    // En el 128k las páginas impares son contended
                    contendedRamPage[3] = contendedIOPage[3] =
                        (floatbus & 0x01) != 0 ? true : false;
                    port7ffd = floatbus;
                }
            }
        }
//            System.out.println(String.format("tstates = %d, addr = %d, floatbus = %02x",
//                    tstates, addr, floatbus));
        return floatbus;
    }

    public void outPort(int port, int value) {

        preIO(port);

        if ((port & 0x0001) == 0) {
            if ((portFE & 0x07) != (value & 0x07)) {
                updateBorder(z80.tEstados);
                borderChanged = true;
//                if (z80.tEstados > spectrumModel.lastBorderUpdate)
//                    System.out.println(String.format("Frame: %d tstates: %d border: %d",
//                        nFrame, z80.tEstados, value & 0x07));
            }

            int spkMic = sp_volt[value >> 3 & 3];
            if (soundOn && spkMic != speaker) {
                audio.updateAudio(z80.tEstados, speaker);
                speaker = spkMic;
            }

            if (tape.isStopped()) {
                // and con 0x18 para emular un Issue 2
                // and con 0x10 para emular un Issue 3
                int issueMask = issue3 ? 0x10 : 0x18;
                if ((value & issueMask) == 0) {
                    tape.setEarBit(false);
                } else {
                    tape.setEarBit(true);
                }
            }
            //System.out.println(String.format("outPort: %04X %02x", port, value));         
            portFE = value;
        }

        if (spectrumModel != MachineTypes.SPECTRUM48K) {
            if ((port & 0x8002) == 0) {
//            System.out.println(String.format("outPort: %04X %02x at %d t-states",
//            port, value, z80.tEstados));

                memory.setPort7ffd(value);
                // En el 128k las páginas impares son contended
                contendedRamPage[3] = contendedIOPage[3] = (value & 0x01) != 0 ? true : false;
                // Si ha cambiado la pantalla visible hay que invalidar
                if (((port7ffd ^ value) & 0x08) != 0) {
                    invalidateScreen(false);
                }
                port7ffd = value;
            }
        }

        if (enabledAY && (port & 0x8002) == 0x8000) {
            if ((port & 0x4000) != 0) {
                ay8912.setAddressLatch(value);
            } else {
                if (soundOn && ay8912.getAddressLatch() < 14) {
                    ay8912.updateAY(z80.tEstados);
                }
                ay8912.writeRegister(value);
            }
        }

        // ULAplus ports
        if ((port & 0x0004) == 0) {
            // Control port (write only)
            if ((port & 0x4000) == 0) {
                if ((value & 0x40) != 0) {
                    paletteGroup = 0x40;
                } else {
                    paletteGroup = value & 0x3f;
                }
            } else {
                // Data port (read/write)
                if (paletteGroup == 0x40) {
                    ULAplusMode = (value & 0x01) != 0 ? true : false;
                } else {
                    ULAplus[paletteGroup >>> 4][paletteGroup & 0x0f] = value;
                    int blue = (value & 0x03) << 1;
                    if ((value & 0x01) == 0x01) {
                        blue |= 0x01;
                    }
                    blue = (blue << 5) | (blue << 2) | (blue & 0x07);
                    int red = (value & 0x1C) >> 2;
                    red = (red << 5) | (red << 2) | (red & 0x07);
                    int green = (value & 0xE0) >> 5;
                    green = (green << 5) | (green << 2) | (green & 0x07);
                    ULAplusPalette[paletteGroup >>> 4][paletteGroup & 0x0f] =
                        (red << 16) | (green << 8) | blue;
                }
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
            if (contendedIOPage[port >>> 14]) {
                // A0 == 1 y es contended RAM
                z80.tEstados += delayTstates[z80.tEstados] + 1;
                z80.tEstados += delayTstates[z80.tEstados] + 1;
                z80.tEstados += delayTstates[z80.tEstados] + 1;

            } else {
                // A0 == 1 y no es contended RAM
                z80.tEstados += 3;
            }
        } else {
            // A0 == 0
            z80.tEstados += delayTstates[z80.tEstados] + 3;
        }
    }

    private void preIO(int port) {
        if (contendedIOPage[port >>> 14]) {
            // A0 == 1 y es contended RAM
            z80.tEstados += delayTstates[z80.tEstados];
        }
        z80.tEstados++;
//        z80.addTEstados(1);
    }

    public void execDone(int tstates) {
        tape.notifyTstates(nFrame, z80.tEstados);
        if (soundOn && tape.isPlaying()) {
            earBit = tape.getEarBit();
            int spkMic = (earBit == 0xbf) ? -2000 : 2000;
            if (spkMic != speaker) {
                audio.updateAudio(z80.tEstados, speaker);
                speaker = spkMic;
            }
        }
    }

    public void loadSnapshot(File filename) {
        Snapshots snap = new Snapshots();
        if (snap.loadSnapshot(filename, memory)) {
            doReset();

            switch (snap.getSnapshotModel()) {
                case SPECTRUM48K:
                    selectHardwareModel(MachineTypes.SPECTRUM48K);
                    hardwareMenu48k.setSelected(true);
                    break;
                case SPECTRUM128K:
                    selectHardwareModel(MachineTypes.SPECTRUM128K);
                    hardwareMenu128k.setSelected(true);
                    break;
                case SPECTRUMPLUS2:
                    selectHardwareModel(MachineTypes.SPECTRUMPLUS2);
                    hardwareMenuPlus2.setSelected(true);
                    break;
            }

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

            // Solo los 48k pueden ser Issue2. El resto son todos Issue3.
            if (snap.getSnapshotModel() == MachineTypes.SPECTRUM48K) {
                issue3 = snap.isIssue3();
            }

            z80.setRegPC(snap.getRegPC());
            z80.setTEstados(snap.getTstates());

            Joystick snapJoystick = snap.getJoystick();
            if (snapJoystick != Joystick.NONE) {
                joystick = snapJoystick;
                keyboard.setJoystick(joystick);

                switch (joystick) {
                    case NONE:
                        joystickNone.setSelected(true);
                        break;
                    case KEMPSTON:
                        joystickKempston.setSelected(true);
                        break;
                    case SINCLAIR1:
                        joystickSinclair1.setSelected(true);
                        break;
                    case SINCLAIR2:
                        joystickSinclair2.setSelected(true);
                        break;
                    case CURSOR:
                        joystickCursor.setSelected(true);
                        break;
                }
            }

            if (snap.getSnapshotModel() != MachineTypes.SPECTRUM48K) {
                port7ffd = snap.getPort7ffd();
                memory.setPort7ffd(port7ffd);
                contendedRamPage[3] = contendedIOPage[3] =
                        (port7ffd & 0x01) != 0 ? true : false;
            }

            if (snap.getAYEnabled() || snap.getSnapshotModel().hasAY8912()) {
                enabledAY = true;
                for (int reg = 0; reg < 16; reg++) {
                    ay8912.setAddressLatch(reg);
                    ay8912.writeRegister(snap.getPsgReg(reg));
                }
                ay8912.setAddressLatch(snap.getPortFffd());
            }

            System.out.println(ResourceBundle.getBundle("machine/Bundle").getString(
                    "SNAPSHOT_LOADED"));
        } else {
            JOptionPane.showMessageDialog(jscr.getParent(), snap.getErrorString(),
                    ResourceBundle.getBundle("machine/Bundle").getString(
                    "SNAPSHOT_LOAD_ERROR"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public void saveSnapshot(File filename) {
        Snapshots snap = new Snapshots();

        snap.setSnapshotModel(spectrumModel);
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

        snap.setRegPC(z80.getRegPC());
        snap.setTstates(z80.getTEstados());
        snap.setJoystick(joystick);
        snap.setIssue3(issue3);

        if (spectrumModel != MachineTypes.SPECTRUM48K) {
            snap.setPort7ffd(port7ffd);
            int ayLatch = ay8912.getAddressLatch();
            snap.setPortfffd(ayLatch);
            for (int reg = 0; reg < 16; reg++) {
                ay8912.setAddressLatch(reg);
                snap.setPsgReg(reg, ay8912.readRegister());
            }
            ay8912.setAddressLatch(ayLatch);
        }

        if (snap.saveSnapshot(filename, memory)) {
            System.out.println(
                    ResourceBundle.getBundle("machine/Bundle").getString("SNAPSHOT_SAVED"));
        } else {
            JOptionPane.showMessageDialog(jscr.getParent(), snap.getErrorString(),
                    ResourceBundle.getBundle("machine/Bundle").getString(
                    "SNAPSHOT_SAVE_ERROR"), JOptionPane.ERROR_MESSAGE);
        }
    }
//    static final int CHANNEL_VOLUME = 26000;
    static final int SPEAKER_VOLUME = 7000;
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
            audio.open(spectrumModel, ay8912, enabledAY);
        } else {
            audio.flush();
            audio.close();
        }
    }

    public void toggleSpeed() {
        if (framesByInt == 1) {
            if (soundOn) {
                toggleSound();
            }
            framesByInt = 10;
        } else {
            framesByInt = 1;
            toggleSound();
            // La velocidad rápida solo pinta 1 frame de cada 10, así que
            // al volver a velocidad lenta hay que actualizar la pantalla
            jscr.repaint();
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
        sp_volt[1] = (int) (-SPEAKER_VOLUME * 1.06);
        sp_volt[2] = (int) SPEAKER_VOLUME;
        sp_volt[3] = (int) (SPEAKER_VOLUME * 1.06);
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
    // Tabla para hacer más rápido el redibujado
    private final int repaintTable[] = new int[0x1800];
    // Tabla que contiene la dirección de pantalla del primer byte de cada
    // carácter en la columna cero.
    public final int scrAddr[] = new int[192];
    // Tabla que indica si un byte de la pantalla ha sido modificado y habrá que
    // redibujarlo.
    private final boolean dirtyByte[] = new boolean[0x1800];
    // Tabla de traslación entre t-states y la dirección de la pantalla del
    // Spectrum que se vuelca en ese t-state o -1 si no le corresponde ninguna.
    private final int states2scr[] = new int[MachineTypes.SPECTRUM128K.tstatesFrame + 100];
    // Tabla de traslación de t-states al pixel correspondiente del borde.
    private final int states2border[] = new int[MachineTypes.SPECTRUM128K.tstatesFrame + 100];
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
    private BufferedImage tvImage;     // imagen actualizada al final de cada frame
    private BufferedImage inProgressImage; // imagen del borde
    private int dataInProgress[];
    private Graphics2D gcTvImage;
    // t-states del último cambio de border
    private int lastChgBorder;
    // veces que ha cambiado el borde en el último frame
    private int nBorderChanges;
    /*
     * screenDirty indica que se cambiaron bytes de la pantalla. Hay que redibujarla
     * borderDirty indica que hay colores del borde que cambiaron. Hay que redibujarlo
     * borderChanged indica que se hizo un out que cambió el color del borde, lo que
     *               no significa que haya que redibujarlo necesariamente.
     */
    private boolean screenDirty, borderDirty, borderChanged;

    ;
    // t-states del ciclo contended por I=0x40-0x7F o -1
//    private int m1contended;
    // valor del registro R cuando se produjo el ciclo m1
//    private int m1regR;
    // Primera y última línea a ser actualizada
    private int firstLine, lastLine;
    private int leftCol, rightCol;
    // ULAplus support (30/08/2010)
    // Color palette
    private int ULAplus[][];
    // Palette group
    private int paletteGroup;
    // Is ULAplus mode active?
    private boolean ULAplusMode;
    // ULAplus calculated color palette
    private int ULAplusPalette[][];

    private void initGFX() {
        tvImage = new BufferedImage(Spectrum.SCREEN_WIDTH, Spectrum.SCREEN_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        gcTvImage = tvImage.createGraphics();
        inProgressImage =
            new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        dataInProgress =
            ((DataBufferInt) inProgressImage.getRaster().getDataBuffer()).getBankData()[0];

        lastChgBorder = 0;
//        m1contended = -1;
        Arrays.fill(dirtyByte, true);
        screenDirty = borderDirty = false;
        borderChanged = true;

        // Paletas para el soporte de ULAplus
        ULAplus = new int[4][16];
        ULAplusPalette = new int[4][16];
        ULAplusMode = false;
        paletteGroup = 0;

        //Inicialización de la tabla de direcciones de pantalla
        /* Hay una entrada en la tabla con la dirección del primer byte
         * de cada fila de la pantalla.
         */
        int row, col, scan;
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

            repaintTable[address & 0x1fff] = (row * 2048 + scan * 256 + col * 8) >>> 8;
            bufAddr[address & 0x1fff] = row * 2560 + (scan + BORDER_WIDTH) * 320
                    + col * 8 + BORDER_WIDTH;
            scr2attr[address & 0x1fff] = 0x5800 + row * 32 + col;
        }

        for (int address = 0x5800; address < 0x5B00; address++) {
            attr2scr[address & 0x3ff] = 0x4000 | ((address & 0x300) << 3) | (address & 0xff);
        }
    }

    public BufferedImage getTvImage() {
        return tvImage;
    }

    public synchronized void toggleFlash() {
        flash = (flash == 0x7f ? 0xff : 0x7f);
        for (int addrAttr = 0x5800; addrAttr < 0x5b00; addrAttr++) {
            if (memory.readScreenByte(addrAttr) > 0x7f) {
                notifyScreenWrite(addrAttr);
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

        tstates %= spectrumModel.tstatesFrame;

        int row = tstates / spectrumModel.tstatesLine;
        int col = tstates % spectrumModel.tstatesLine;

        // Quitamos las líneas que no se ven por arriba y por abajo
        if (row < (64 - BORDER_WIDTH) || row > (256 + BORDER_WIDTH - 1)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la última línea
        if (row == (256 + BORDER_WIDTH - 1) && col > (127 + BORDER_WIDTH / 2)) {
            return 0xf0cab0ba;
        }

        // Quitamos la parte del borde derecho que no se ve, la zona de H-Sync
        // y la parte izquierda del borde que tampoco se ve
        if (col > (127 + BORDER_WIDTH / 2) && col < 200 + (24 - BORDER_WIDTH / 2)) {
            return 0xf0cab0ba;
        }

        // Quitamos la parte correspondiente a SCREEN$
        if (row > 63 && row < 256 && col < 128) {
            return 0xf0cab0ba;
        }

        // 176 t-estados de línea es en medio de la zona de retrazo
        if (col > 176) {
            row++;
            col -= 200 + (24 - BORDER_WIDTH / 2);
        } else {
            col += BORDER_WIDTH / 2;
        }
        row -= BORDER_WIDTH;



//        System.out.println(String.format("tstates: %d, row = %d, col = %d", tstates, row, col));

        // No se puede dibujar en el borde con precisión de pixel.
//        int mod = col % 8;
//        col -= mod;
//        if (mod > 3) {
//            col += 4;
//        }

//        System.out.println(String.format("t-states: %d\trow: %d\tcol: %d\tmod: %d",
//                tstates+3584, row, col, mod));

        return row * SCREEN_WIDTH + col * 2;
    }

    private int tStatesToScrPix128k(int tstates) {

        tstates %= spectrumModel.tstatesFrame;

        int row = tstates / spectrumModel.tstatesLine;
        int col = tstates % spectrumModel.tstatesLine;

        // Quitamos las líneas que no se ven por arriba y por abajo
        if (row < (63 - BORDER_WIDTH) || row > (255 + BORDER_WIDTH - 1)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la última línea
        if (row == (255 + BORDER_WIDTH - 1) && col > (127 + BORDER_WIDTH / 2)) {
            return 0xf0cab0ba;
        }

        // Quitamos la parte del borde derecho que no se ve, la zona de H-Sync
        // y la parte izquierda del borde que tampoco se ve
        if (col > (127 + BORDER_WIDTH / 2) && col < 204 + (24 - BORDER_WIDTH / 2)) {
            return 0xf0cab0ba;
        }

        // Quitamos la parte correspondiente a SCREEN$
        if (row > 62 && row < 255 && col < 128) {
            return 0xf0cab0ba;
        }

        // 176 t-estados de línea es en medio de la zona de retrazo
        if (col > 176) {
            row++;
            col -= 204 + (24 - BORDER_WIDTH / 2);
        } else {
            col += BORDER_WIDTH / 2;
        }
        row -= (BORDER_WIDTH - 1);

//        System.out.println(String.format("tstates: %d, row = %d, col = %d", tstates, row, col));

//        System.out.println(String.format("t-states: %d\trow: %d\tcol: %d\tmod: %d",
//                tstates+3584, row, col, mod));

        return row * SCREEN_WIDTH + col * 2;
    }

    public void updateBorder(int tstates) {
        int nowColor = Paleta[portFE & 0x07];
        int idxColor;

        if (tstates < lastChgBorder)
            return;

        while (lastChgBorder < tstates) {
            idxColor = states2border[lastChgBorder++];
            if (idxColor == 0xf0cab0ba || nowColor == dataInProgress[idxColor]) {
                continue;
            }

            dataInProgress[idxColor] = nowColor;
            dataInProgress[idxColor + 1] = nowColor;
            dataInProgress[idxColor + 2] = nowColor;
            dataInProgress[idxColor + 3] = nowColor;
            dataInProgress[idxColor + 4] = nowColor;
            dataInProgress[idxColor + 5] = nowColor;
            dataInProgress[idxColor + 6] = nowColor;
            dataInProgress[idxColor + 7] = nowColor;
            nBorderChanges++;
            borderDirty = true;
        }
    }

    public void updateScreen(int fromTstates, int toTstates) {
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

            if (firstLine == 0) {
                firstLine = lastLine = fromAddr;
            } else {
                lastLine = fromAddr;
            }

            int column = fromAddr & 0x1f;
            if (column < leftCol) {
                leftCol = column;
            }
            if (column > rightCol) {
                rightCol = column;
            }

//            System.out.println("Addr: " + fromAddr);
            scrByte = attr = 0;
            // si m1contended != -1 es que hay que emular el efecto snow.
//            if (m1contended == -1) {
                scrByte = memory.readScreenByte(fromAddr);
                fromAddr &= 0x1fff;
                attr = memory.readScreenByte(scr2attr[fromAddr]);
//            } else {
//                int addr;
//                int mod = m1contended % 8;
//                if (mod == 0 || mod == 1) {
//                    addr = (fromAddr & 0xff00) | m1regR;
//                    scrByte = memory.readScreenByte(addr);
//                    attr = memory.readScreenByte(addr & 0x1fff);
//                    //System.out.println("Snow even");
//                }
//                if (mod == 2 || mod == 3) {
//                    addr = (scr2attr[fromAddr & 0x1fff] & 0xff00) | m1regR;
//                    scrByte = memory.readScreenByte(fromAddr);
//                    attr = memory.readScreenByte(addr & 0x1fff);
//                    //System.out.println("Snow odd");
//                }
//                fromAddr &= 0x1fff;
//                m1contended = -1;
//            }

            addrBuf = bufAddr[fromAddr];

            if (ULAplusMode) {
                ink = ULAplusPalette[attr >>> 6][attr & 0x07];
                paper = ULAplusPalette[attr >>> 6][((attr & 0x38) >>> 3) | 0x08];
            } else {
                if (attr > 0x7f) {
                    attr &= flash;
                }
                ink = Ink[attr];
                paper = Paper[attr];
            }

            for (int mask = 0x80; mask != 0; mask >>= 1) {
                if ((scrByte & mask) != 0) {
                    dataInProgress[addrBuf++] = ink;
                } else {
                    dataInProgress[addrBuf++] = paper;
                }
            }
            dirtyByte[fromAddr] = false;
            screenDirty = true;
            fromTstates += 4;
        }
    }

    public void notifyScreenWrite(int address) {
        address &= 0x1fff;
        if (address < 6144) {
            dirtyByte[address] = true;
        } else {
            int addr = attr2scr[address & 0x3ff] & 0x1fff;
            // cuando esto lo hace un compilador, se le llama loop-unrolling
            // cuando lo hace un pogramadó, se le llama shapusa :P
            dirtyByte[addr] = true;
            dirtyByte[addr + 256] = true;
            dirtyByte[addr + 512] = true;
            dirtyByte[addr + 768] = true;
            dirtyByte[addr + 1024] = true;
            dirtyByte[addr + 1280] = true;
            dirtyByte[addr + 1536] = true;
            dirtyByte[addr + 1792] = true;
        }
    }

    public void invalidateScreen(boolean invalidateBorder) {
        if (invalidateBorder) {
            borderChanged = true;
        }
        screenDirty = true;
        Arrays.fill(dirtyByte, true);
    }

    private void buildScreenTables48k() {
        int col, scan;

        Arrays.fill(states2scr, -1);
        for (int tstates = 14336; tstates < 57344; tstates += 4) {
            col = (tstates % 224) / 4;
            if (col > 31) {
                continue;
            }
            scan = tstates / 224 - 64;
            states2scr[tstates - 8] = scrAddr[scan] + col;
        }

        Arrays.fill(states2border, 0xf0cab0ba);
        for (int tstates = spectrumModel.firstBorderUpdate;
            tstates < spectrumModel.lastBorderUpdate; tstates += 4) {
            states2border[tstates] = tStatesToScrPix48k(tstates);
            states2border[tstates + 1] = states2border[tstates];
            states2border[tstates + 2] = states2border[tstates];
            states2border[tstates + 3] = states2border[tstates];
//            if (states2border[tstates] == imgData.length)
//                System.out.println("Array fuera de rango!!!");
//            System.out.println(String.format("tstates: %d, states2border[tstates]: %d",
//                    tstates, states2border[tstates]));
        }

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
        int col, scan;

        Arrays.fill(states2scr, -1);
        for (int tstates = 14364; tstates < 58140; tstates += 4) {
            col = (tstates % 228) / 4;
            if (col > 31) {
                continue;
            }

            scan = tstates / 228 - 63;
            states2scr[tstates - 12] = scrAddr[scan] + col;
        }

        Arrays.fill(states2border, 0xf0cab0ba);
        for (int tstates = 0; tstates < spectrumModel.tstatesFrame; tstates += 4) {
            states2border[tstates] = tStatesToScrPix128k(tstates);
            states2border[tstates + 1] = states2border[tstates];
            states2border[tstates + 2] = states2border[tstates];
            states2border[tstates + 3] = states2border[tstates];
        }

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
