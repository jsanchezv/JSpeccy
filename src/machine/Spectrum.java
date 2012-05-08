/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import configuration.JSpeccySettingsType;
import configuration.SpectrumType;
import gui.JSpeccyScreen;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.util.Arrays;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import machine.Keyboard.Joystick;
import snapshots.SpectrumState;
import utilities.Tape;
import utilities.Tape.TapeState;
import utilities.TapeStateListener;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Spectrum extends Thread implements z80core.MemIoOps, z80core.NotifyOps {

    private Z80 z80;
    private Memory memory;
    private TimeCounters clock;
    private boolean[] contendedRamPage = new boolean[4];
    private boolean[] contendedIOPage = new boolean[4];
    private int portFE, earBit = 0xbf, port7ffd, port1ffd, issueMask;
    private long framesByInt, speedometer, speed, prevSpeed;
    private boolean muted, enabledSound, enabledAY;
    private static final byte delayTstates[] =
        new byte[MachineTypes.SPECTRUM128K.tstatesFrame + 200];
    public MachineTypes spectrumModel;
    private Timer timerFrame;
    private SpectrumTimer taskFrame;
    private JSpeccyScreen jscr;
    private Keyboard keyboard;
    private Audio audio;
    private AY8912 ay8912;
    private Tape tape;
    private boolean paused;
    private boolean hardResetPending, resetPending;
    private JLabel speedLabel;

    private Joystick joystick;
    
    private JSpeccySettingsType settings;
    private SpectrumType specSettings;
    /* Config vars */
    private boolean ULAPlusEnabled, issue2, multiface, mf128on48k,
            saveTrap, loadTrap, flashload, connectedLec;
    private boolean connectedIF1;
    private Interface1 if1;

    public Spectrum(JSpeccySettingsType config) {
        super("SpectrumThread");
        settings = config;
        specSettings = settings.getSpectrumSettings();
        clock = new TimeCounters();
        z80 = new Z80(clock, this, this);
        memory = new Memory(settings);
        initGFX();
        speedometer = 0;
        framesByInt = 1;
        portFE = 0;
        port7ffd = 0;
        ay8912 = new AY8912();
        audio = new Audio(settings.getAY8912Settings());
        muted = specSettings.isMutedSound();
        enabledSound = false;
        paused = true;
        if1 = new Interface1(clock, settings.getInterface1Settings());

        keyboard = new Keyboard(settings.getKeyboardJoystickSettings());
        
        doHardReset();

        resetPending = hardResetPending = false;

        loadConfigVars();

        timerFrame = new Timer("SpectrumClock", true);
    }

    public final SpectrumState getSpectrumState() {
        SpectrumState state = new SpectrumState();
        
        state.setSpectrumModel(spectrumModel);
        state.setZ80State(z80.getZ80State());
        state.setMemoryState(memory.getMemoryState());
        state.setConnectedLec(connectedLec);
        
        state.setEarBit(earBit);
        state.setPortFE(portFE);
        state.setJoystick(joystick);
        
        if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUM48K) {
            state.setPort7ffd(port7ffd);
            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                state.setPort1ffd(port1ffd);
            }
        } else {
            state.setIssue2(issue2);
        }
        

        state.setEnabledAY(enabledAY);
        if (enabledAY)
            state.setAY8912State(ay8912.getAY8912State());

        state.setConnectedIF1(connectedIF1);
        if (connectedIF1)
            state.setNumMicrodrives(settings.getInterface1Settings().getMicrodriveUnits());

        state.setMultiface(multiface);

        state.setULAPlusEnabled(ULAPlusEnabled);
        if (ULAPlusEnabled) {
            state.setULAPlusActive(ULAPlusActive);
            state.setPaletteGroup(paletteGroup);
            int[] palette = new int[64];
            System.arraycopy(ULAPlusPalette[0], 0, palette, 0, 16);
            System.arraycopy(ULAPlusPalette[1], 0, palette, 16, 16);
            System.arraycopy(ULAPlusPalette[2], 0, palette, 32, 16);
            System.arraycopy(ULAPlusPalette[3], 0, palette, 48, 16);
            state.setULAPlusPalette(palette);
        }

        state.setTstates(clock.getTstates());
        return state;
    }
    
    public final void setSpectrumState(SpectrumState state) {
        
        selectHardwareModel(state.getSpectrumModel());
        z80.setZ80State(state.getZ80State());
        memory.setMemoryState(state.getMemoryState());
        settings.getSpectrumSettings().setLecEnabled(state.isConnectedLec());

        earBit = state.getEarBit();
        portFE = state.getPortFE();

        if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUM48K) {
            
            port7ffd = state.getPort7ffd();
            memory.setPort7ffd(port7ffd);

            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                port1ffd = state.getPort1ffd();
                memory.setPort1ffd(port1ffd);

                if (memory.isPlus3RamMode()) {
                    switch (port1ffd & 0x06) {
                        case 0:
                            Arrays.fill(contendedRamPage, false);
                            break;
                        case 2:
                            Arrays.fill(contendedRamPage, true);
                            break;
                        case 4:
                        case 6:
                            Arrays.fill(contendedRamPage, true);
                            contendedRamPage[3] = false;
                    }
                } else {
                    contendedRamPage[0] = contendedRamPage[2] = false;
                    contendedRamPage[1] = true;
                    contendedRamPage[3] = memory.getPlus3HighPage() > 3;
                }
            } else {
                contendedRamPage[3] = contendedIOPage[3] =
                    (port7ffd & 0x01) != 0;
            }
        }
        
        keyboard.reset();
        setJoystick(state.getJoystick());
        settings.getKeyboardJoystickSettings().setIssue2(state.isIssue2());

        enabledAY = state.isEnabledAY();
        if (enabledAY) {
            ay8912.setSpectrumModel(spectrumModel);
            ay8912.setAY8912State(state.getAY8912State());
            settings.getSpectrumSettings().setAYEnabled48K(state.isEnabledAYon48k());
        }

        settings.getInterface1Settings().setConnectedIF1(state.isConnectedIF1());
        if (state.isConnectedIF1()) {
            settings.getInterface1Settings().setMicrodriveUnits(state.getNumMicrodrives());
        }
        
        settings.getSpectrumSettings().setMultifaceEnabled(state.isMultiface());
        
        settings.getSpectrumSettings().setULAplus(state.isULAPlusEnabled());
        if (state.isULAPlusEnabled()) {
            ULAPlusActive = state.isULAPlusActive();
            paletteGroup = state.getPaletteGroup();

            int[] palette = state.getULAPlusPalette();
            for (int register = 0; register < 64; register++) {
                int color = palette[register];
                ULAPlusPalette[register >>> 4][register & 0x0f] = color;
                precompULAplusColor(register, color);
            }
        } else {
            ULAPlusActive = false;
        }

        clock.setTstates(state.getTstates());
        
        loadConfigVars();
        
        if (memory.getMemoryState().isLecPaged())
           pageLec(state.getMemoryState().getPageLec());
    }
    
    public void selectHardwareModel(MachineTypes hardwareModel) {

        disableSound();
        spectrumModel = hardwareModel;
        clock.setSpectrumModel(spectrumModel);
        memory.reset(spectrumModel);
        
        if (tape != null)
            tape.setSpectrumModel(spectrumModel);

        contendedRamPage[0] = contendedIOPage[0] = false;
        contendedRamPage[1] = contendedIOPage[1] = true;
        contendedRamPage[2] = contendedIOPage[2] = false;
        contendedRamPage[3] = contendedIOPage[3] = false;

        switch (spectrumModel.codeModel) {
            case SPECTRUM48K:
                buildScreenTables48k();
                enabledAY = specSettings.isAYEnabled48K();
                connectedIF1 = settings.getInterface1Settings().isConnectedIF1();
                break;
            case SPECTRUM128K:
                buildScreenTables128k();
                enabledAY = true;
                connectedIF1 = settings.getInterface1Settings().isConnectedIF1();
                break;
            case SPECTRUMPLUS3:
                buildScreenTablesPlus3();
                enabledAY = true;
                contendedIOPage[1] = false;
                connectedIF1 = false;
                break;
        }

        enableSound();
    }

    public final void loadConfigVars() {
        ULAPlusEnabled = settings.getSpectrumSettings().isULAplus();
        
        issue2 = settings.getKeyboardJoystickSettings().isIssue2();
        // AND con 0x18 para emular un Issue 2
        // AND con 0x10 para emular un Issue 3
        if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
            issueMask = issue2 ? 0x18 : 0x10;
        } else {
            issueMask = 0x10; // los modelos que no son el 48k son todos Issue3
        }
        
        keyboard.setMapPCKeys(settings.getKeyboardJoystickSettings().isMapPCKeys());
        
        multiface = settings.getSpectrumSettings().isMultifaceEnabled();
        mf128on48k = settings.getSpectrumSettings().isMf128On48K();
        z80.setBreakpoint(0x0066, multiface);
        
        saveTrap = settings.getTapeSettings().isEnableSaveTraps();
        z80.setBreakpoint(0x04D0, saveTrap);
        
        loadTrap = settings.getTapeSettings().isEnableLoadTraps();
        z80.setBreakpoint(0x0556, loadTrap);
        
        flashload = settings.getTapeSettings().isFlashLoad();
        
        if1.setNumDrives(settings.getInterface1Settings().getMicrodriveUnits());
        if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
            connectedIF1 = settings.getInterface1Settings().isConnectedIF1();
        } else {
            connectedIF1 = false;
        }
        z80.setBreakpoint(0x0008, connectedIF1);
        z80.setBreakpoint(0x0700, connectedIF1);
        z80.setBreakpoint(0x1708, connectedIF1);
        
        connectedLec = settings.getSpectrumSettings().isLecEnabled();
        if (connectedLec && spectrumModel == MachineTypes.SPECTRUM48K) {
            if (memory.isLecPaged()) {
                pageLec(memory.getPageLec());
            } else {
                unpageLec();
            }
        }
    }
    
    /*
     * Esto es necesario para conseguir un mejor funcionamiento en Windows.
     * Para los sistemas Unix debería ser una modificación inocua. La razón del
     * hack está explicada en:
     * http://blogs.oracle.com/dholmes/entry/inside_the_hotspot_vm_clocks
     * y en
     * http://www.javamex.com/tutorials/threads/sleep.shtml
     * 
     */

    @Override
    public void run() {
        startEmulation();
        try {
            sleep(Long.MAX_VALUE);
        } catch (InterruptedException excpt) {
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, excpt);
        }
    }

    public synchronized void startEmulation() {
        if (!paused)
            return;
        
        paused = false;
        audio.reset();
        enableSound();
        invalidateScreen(true);
        taskFrame = new SpectrumTimer(this);
        timerFrame.scheduleAtFixedRate(taskFrame, 50, 20);
    }

    public synchronized void stopEmulation() {
        if (paused)
            return;
        
        taskFrame.cancel();
        paused = true;
        disableSound();
    }

    public void reset() {
        resetPending = true;
    }

    private void doReset() {
        tape.stop();
        clock.reset();
        z80.reset();
        if (memory.isLecPaged())
            unpageLec();
        memory.reset(spectrumModel);
        ay8912.reset();
        audio.reset();
        keyboard.reset();
        if (connectedIF1)
            if1.reset();
        portFE = port7ffd = port1ffd = 0;
        ULAPlusActive = false;
        paletteGroup = 0;
        invalidateScreen(true);
        hardResetPending = resetPending = false;
    }

    public void hardReset() {
        hardResetPending = resetPending = true;
    }

    private void doHardReset() {

        switch (specSettings.getDefaultModel()) {
            case 0:
                selectHardwareModel(MachineTypes.SPECTRUM16K);
                break;
            case 2:
                selectHardwareModel(MachineTypes.SPECTRUM128K);
                break;
            case 3:
                selectHardwareModel(MachineTypes.SPECTRUMPLUS2);
                break;
            case 4:
                selectHardwareModel(MachineTypes.SPECTRUMPLUS2A);
                break;
            case 5:
                selectHardwareModel(MachineTypes.SPECTRUMPLUS3);
                break;
            default:
                selectHardwareModel(MachineTypes.SPECTRUM48K);
        }
        
        keyboard.setJoystick(settings.getKeyboardJoystickSettings().getJoystickModel());
        joystick = keyboard.getJoystick();
    }

    public boolean isPaused() {
        return paused;
    }

    public void triggerNMI() {
        z80.triggerNMI();
    }

    public MachineTypes getSpectrumModel() {
        return spectrumModel;
    }
    
    public Keyboard getKeyboard() {
        return keyboard;
    }
    
    public boolean getMapPCKeys() {
        return keyboard.isMapPCKeys();
    }
    
    public void setMapPCKeys(boolean state) {
        keyboard.setMapPCKeys(state);
    }

    public Joystick getJoystick() {
        return joystick;
    }
    
    public final void setJoystick(Joystick type) {
        joystick = type;
        keyboard.setJoystick(type);
    }
    
    public final void setTape(Tape player) {
        tape = player;
        tape.setZ80Cpu(z80);
        tape.setSpectrumModel(spectrumModel);
        tape.addTapeChangedListener(new TapeChangedListener());
    }

    public void setSpeedLabel(JLabel speed) {
        speedLabel = speed;
        
    }

    public void setScreenComponent(JSpeccyScreen jScr) {
        this.jscr = jScr;
    }
    
    public Memory getMemory() {
        return memory;
    }

    public synchronized void generateFrame() {

//        long startFrame, endFrame, sleepTime;
//        startFrame = System.currentTimeMillis();
//        System.out.println("Start frame: " + startFrame);

        //z80.tEstados = frameStart;
        //System.out.println(String.format("Begin frame. t-states: %d", z80.tEstados));

        if (resetPending) {
            if (hardResetPending) {
                doHardReset();                
            } else {
                z80.setPinReset();
            }

            doReset();
        }

        long counter = framesByInt;

        firstLine = lastLine = lastBorderPix = 0;
        firstBorderPix = dataInProgress.length;
        leftCol = 31;
        rightCol = 0;
        lastChgBorder = spectrumModel.firstBorderUpdate;
        lastScreenState = spectrumModel.firstScrByte;

        do {
            // Cuando se entra desde una carga de snapshot los t-states pueden
            // no ser 0 y el frame estar a mitad (pojemplo)
            if (clock.tstates < spectrumModel.lengthINT) {
                z80.setINTLine(true);
                z80.execute(spectrumModel.lengthINT);
            }
            z80.setINTLine(false);

            if (clock.tstates < spectrumModel.firstScrByte) {
                z80.execute(spectrumModel.firstScrByte);
                updateScreen(clock.tstates);
            }

            while (clock.tstates < spectrumModel.lastScrUpdate) {
                z80.execute(clock.tstates + 48);
                updateScreen(clock.tstates);
            }

            z80.execute(spectrumModel.tstatesFrame);

            if (enabledSound) {
                if (enabledAY) {
                    ay8912.updateAY(clock.tstates);
                }
                audio.updateAudio(clock.tstates, speaker);
                audio.endFrame();
            }

            clock.endFrame();
            
            if (!ULAPlusActive && clock.getFrames() % 16 == 0) {
                toggleFlash();
            }

            if ( clock.getFrames() % 50 == 0) {
                long now = System.currentTimeMillis();
                speed = 100000 / (now - speedometer);
                speedometer = now;
                if (speed != prevSpeed) {
                    prevSpeed = speed;
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            speedLabel.setText(String.format("%4d%%", speed));
                        }
                    });
//                    System.out.println(String.format("Time: %d Speed: %d%%", now, speed));
                }
            }
        } while (--counter > 0);

        // In the 128K/+2 models, when register I is between 0x40-0x7F, the
        // computer resets shortly.
        if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM128K
            && z80.getRegI() < 0x80 && z80.getRegI() > 0x3f) {
            z80.reset();
        }

        //endFrame = System.currentTimeMillis();
        //System.out.println("End frame: " + endFrame);
        //sleepTime = endFrame - startFrame;
        //System.out.println("execFrame en: " + sleepTime);
    }

    public synchronized void drawFrame() {
        /* 20/03/2010
         * La pantalla se ha de dibujar en un solo paso. Si el borde no se
         * modificó, se vuelca sobre el doble búfer solo la pantalla. Si se
         * modificó el borde, primero se vuelca la pantalla sobre la imagen
         * del borde y luego se vuelca el borde. Si no, se pueden ver "artifacts"
         * en juegos como el TV-game.
         */

//        System.out.println(
//         String.format("screenDirty: %b, lastChgBorder: %d, borderChanged: %b, nBorderChanges: %d",
//           screenDirty, lastChgBorder, borderChanged, nBorderChanges));

        if (borderUpdated || borderChanged) {
            borderChanged = borderUpdated;
            borderUpdated = false;
            updateBorder(spectrumModel.lastBorderUpdate);
            if (borderDirty) {
                borderDirty = false;
                gcTvImage.drawImage(inProgressImage, 0, 0, null);
                int zoom = jscr.getZoom();
                int fbl = firstBorderPix / 320;
                borderRect.x = 0;
                borderRect.y = fbl * zoom;
                borderRect.width = 320 * zoom;
                borderRect.height = (lastBorderPix / 320 - fbl + 1) * zoom;
                if (screenDirty) {
                    screenDirty = false;
                    gcTvImage.drawImage(inProgressImage, 0, 0, null);
                    firstLine = repaintTable[firstLine & 0x1fff];
                    lastLine = repaintTable[lastLine & 0x1fff];
                    screenRect.x = (BORDER_WIDTH + leftCol * 8) * zoom;
                    screenRect.y = (BORDER_HEIGHT + firstLine) * zoom;
                    screenRect.width = (rightCol - leftCol + 1) * 8 * zoom;
                    screenRect.height = (lastLine - firstLine + 1) * zoom;
//                    System.out.println("borderDirty + screenDirty @ rect " + borderRect.union(screenRect));
                    jscr.repaint(borderRect.union(screenRect));
                } else {
//                    System.out.println("borderDirty @ rect " + borderRect);
                    jscr.repaint(borderRect);
                }
                return;
            }
//                System.out.println(String.format("Frame: %8d - Repaint border", clock.getFrames()));
        }

        if (screenDirty) {
            screenDirty = false;
            gcTvImage.drawImage(inProgressImage, 0, 0, null);
            firstLine = repaintTable[firstLine & 0x1fff];
            lastLine = repaintTable[lastLine & 0x1fff];

            int zoom = jscr.getZoom();
            screenRect.x = (BORDER_WIDTH + leftCol * 8) * zoom;
            screenRect.y = (BORDER_HEIGHT + firstLine) * zoom;
            screenRect.width = (rightCol - leftCol + 1) * 8 * zoom;
            screenRect.height = (lastLine - firstLine + 1) * zoom;
//            System.out.println("screenDirty @ rect " + screenRect);
            jscr.repaint(screenRect);

        }
    }

    private synchronized void acceleratedLoading() {

        stopEmulation();

        firstLine = lastLine = lastBorderPix = 0;
        firstBorderPix = dataInProgress.length;
        leftCol = 31;
        rightCol = 0;
        lastChgBorder = spectrumModel.firstBorderUpdate;
        lastScreenState = spectrumModel.firstScrUpdate;

        speedometer = System.currentTimeMillis();
        do {
            z80.setINTLine(true);
            z80.execute(spectrumModel.lengthINT);
            z80.setINTLine(false);

            z80.execute(spectrumModel.tstatesFrame);
            
            clock.endFrame();

            if (clock.getFrames() % 500 == 0) {
                updateScreen(spectrumModel.lastScrUpdate);

                drawFrame();
                firstLine = lastLine = rightCol = lastBorderPix = 0;
                firstBorderPix = dataInProgress.length;
                leftCol = 31;
                lastChgBorder = spectrumModel.firstBorderUpdate;
                lastScreenState = spectrumModel.firstScrUpdate;
                
                long now = System.currentTimeMillis();
                speed = 1000000 / (now - speedometer);
                speedometer = now;
                if (speed != prevSpeed) {
                    prevSpeed = speed;
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            speedLabel.setText(String.format("%4d%%", speed));
                        }
                    });
                }
            }
        } while (tape.isTapePlaying());
        
        lastScreenState = spectrumModel.firstScrUpdate;
        updateScreen(spectrumModel.lastScrUpdate);
        
        lastChgBorder = spectrumModel.firstBorderUpdate;
        
        drawFrame();
        
        startEmulation();
    }
    
    @Override
    public int fetchOpcode(int address) {

        if (contendedRamPage[address >>> 14]) {
            clock.tstates += delayTstates[clock.tstates];
        }
        clock.tstates += 4;

        return memory.readByte(address) & 0xff;
    }

    @Override
    public int peek8(int address) {

        if (contendedRamPage[address >>> 14]) {
            clock.tstates += delayTstates[clock.tstates];
        }
        clock.tstates += 3;

        return memory.readByte(address) & 0xff;
    }

    @Override
    public void poke8(int address, int value) {
        
        boolean contended = contendedRamPage[address >>> 14];
        if (contended) {
            clock.tstates += delayTstates[clock.tstates];
        }
        clock.tstates += 3;
        
        updateScreen(clock.tstates);
        
        if (contended && memory.isScreenByteModified(address, (byte) value)) {
            notifyScreenWrite(address);
        }
        
        memory.writeByte(address, (byte) value);
    }

    @Override
    public int peek16(int address) {

        if (contendedRamPage[address >>> 14]) {
            clock.tstates += delayTstates[clock.tstates];
        }
        clock.tstates += 3;
        int lsb = memory.readByte(address) & 0xff;

        address = (address + 1) & 0xffff;
        if (contendedRamPage[address >>> 14]) {
            clock.tstates += delayTstates[clock.tstates];
        }
        clock.tstates += 3;

        return ((memory.readByte(address) << 8) & 0xff00 | lsb);
    }

    @Override
    public void poke16(int address, int word) {

        byte lsb = (byte) word;
        byte msb = (byte) (word >>> 8);
        
        boolean contended = contendedRamPage[address >>> 14];
        if (contended) {
            clock.tstates += delayTstates[clock.tstates];
        }
        clock.tstates += 3;

        updateScreen(clock.tstates);
        
        if (contended && memory.isScreenByteModified(address, lsb)) {
            notifyScreenWrite(address);
        }
        
        memory.writeByte(address, lsb);

        address = (address + 1) & 0xffff;

        contended = contendedRamPage[address >>> 14];
        if (contended) {
            clock.tstates += delayTstates[clock.tstates];
        }
        clock.tstates += 3;

        updateScreen(clock.tstates);
        
        if (contended && memory.isScreenByteModified(address, msb)) {
            notifyScreenWrite(address);
        }
        
        memory.writeByte(address, msb);
    }

    @Override
    public void contendedStates(int address, int tstates) {
        if (contendedRamPage[address >>> 14]
                && spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
            for (int idx = 0; idx < tstates; idx++) {
                clock.tstates += delayTstates[clock.tstates] + 1;
            }
        } else {
            clock.tstates += tstates;
        }
    }

    @Override
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

        // Interface I
        if (connectedIF1) {
            // Port 0xE7 (Data Port)
            if ((port & 0x0018) == 0) {
//                System.out.println(String.format("IN from MDR-DATA. PC = %04x",
//                    z80.getRegPC()));
                return if1.readDataPort();
            }
            
            // Port 0xEF (Control Port)
            if ((port & 0x0018) == 0x08) {
//                System.out.println(String.format("IN from MDR-CRTL. PC = %04x",
//                    z80.getRegPC()));
                return if1.readControlPort();
            }
            
            // Port 0xF7 (RS232/Network Port)
            if ((port & 0x0018) == 0x10) {
//                System.out.println(String.format("IN from RS232/Net. PC = %04x",
//                    z80.getRegPC()));
                return if1.readLanPort();
            }
        }
        
        // Multiface emulation
        if (multiface) {
            switch (spectrumModel.codeModel) {
                case SPECTRUM48K:
                    if (mf128on48k) {
                        // MF128 en el Spectrum 48k
                        if ((port & 0xff) == 0xbf && !memory.isMultifaceLocked()) {
                            memory.pageMultiface();
                        }
                        if ((port & 0xff) == 0x3f && memory.isMultifacePaged()) {
                            memory.unpageMultiface();
                        }
                    } else {
                        // MF1 en el Spectrum 48k
                        if ((port & 0xff) == 0x9f) {
                            memory.pageMultiface();
                        }
                        // Este puerto es el mismo que el Kempston. De hecho, el
                        // MF1 incorporaba un puerto Kempston...
                        if ((port & 0xff) == 0x1f && memory.isMultifacePaged()) {
                            memory.unpageMultiface();
                        }
                    }
                    break;
                case SPECTRUM128K:
                    if ((port & 0xff) == 0xbf && !memory.isMultifaceLocked()) {
//                        System.out.println(String.format("inPort: %04x\tPC: %04x",
//                            port, z80.getRegPC()));
                        if (port == 0x01bf && !memory.isMultifaceLocked()
                            && memory.isMultifacePaged()) {
                            return port7ffd;
                        }
                        memory.pageMultiface();
                    }
                    if ((port & 0xff) == 0x3f && memory.isMultifacePaged()) {
//                        System.out.println(String.format("inPort: %04x\tPC: %04x",
//                            port, z80.getRegPC()));
                        memory.unpageMultiface();
                    }
                    break;
                case SPECTRUMPLUS3:
                    if ((port & 0xff) == 0xbf && memory.isMultifacePaged()) {
                        memory.unpageMultiface();
                    }

                    if ((port & 0xff) == 0x3f) {
//                        System.out.println(String.format("inPort: %04x\tPC: %04x",
//                            port, z80.getRegPC()));
                        if (port == 0x7f3f && !memory.isMultifaceLocked()
                            && memory.isMultifacePaged()) {
                            return port7ffd;
                        }
                        if (port == 0x1f3f && !memory.isMultifaceLocked()
                            && memory.isMultifacePaged()) {
                            return port1ffd;
                        }

                        if (!memory.isMultifaceLocked()) {
                            memory.pageMultiface();
                        }
                    }
            }
        }

//        if (spectrumModel == MachineTypes.SPECTRUMPLUS3) {
//            if ((port & 0xF002) == 0x2000) {
//                System.out.println("Reading FDC status port");
//                return 0x80; // ready to fly
//            }
//
//            if ((port & 0xF002) == 0x3000) {
//                System.out.println("Reading FDC data port");
//                return 0;
//            }
//        }

        /*
         * El interfaz Kempston solo (debería) decodificar A5=0...
         */
        if (joystick == Joystick.KEMPSTON && (port & 0x0020) == 0) {
//            System.out.println(String.format("InPort: %04X, PC: %04X", port, z80.getRegPC()));
            return keyboard.readKempstonPort();
        }

        if (joystick == Joystick.FULLER && (port & 0xff) == 0x7f) {
//            System.out.println(String.format("InPort: %04X", port));
            return keyboard.readFullerPort();
        }

        // ULA Port
        if ((port & 0x0001) == 0) {
            earBit = tape.getEarBit();
            return keyboard.readKeyboardPort(port) & earBit;
        }

        if (enabledAY) {
            /* On the 128K/+2, reading from BFFDh will return the floating bus
             * value as normal for unattached ports, but on the +2A/+3, it will
             * return the same as reading from FFFDh */
            if ((port & 0xC002) == 0xC000) {
                return ay8912.readRegister();
            }

            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3
                && (port & 0xC002) == 0x8000) {
                return ay8912.readRegister();
            }

            if (joystick == Joystick.FULLER && (port & 0xff) == 0x3f) {
//                System.out.println(String.format("InPort: %04X", port));
                return ay8912.readRegister();
            }
        }

        // ULAplus Data Port (read/write)
        if ((port & 0x4004) == 0x4000 && ULAPlusEnabled) {
            if (paletteGroup == 0x40) {
                return ULAPlusActive ? 0x01 : 0x00;
            } else {
                return ULAPlusPalette[paletteGroup >>> 4][paletteGroup & 0x0f];
            }
        }

//        System.out.println(String.format("InPort: %04X at %d t-states", port, z80.tEstados));
//        int floatbus = 0xff;
        // El +3 no tiene bus flotante, responde siempre con 0xFF a puertos no usados
        int floatbus = 0xff;
        if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
            int addr;
            if (clock.tstates < spectrumModel.firstScrByte || clock.tstates > spectrumModel.lastScrUpdate) {
                return 0xff;
            }

            int col = (clock.tstates % spectrumModel.tstatesLine) - spectrumModel.outOffset;
            if (col > 124) {
                return 0xff;
            }

            int row = clock.tstates / spectrumModel.tstatesLine - spectrumModel.upBorderWidth;

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
             * Solo en el modelo 128K, pero no en los +2/+2A/+3, si se lee el puerto
             * 0x7ffd, el valor leído es reescrito en el puerto 0x7ffd.
             * http://www.speccy.org/foro/viewtopic.php?f=8&t=2374
             */
            if ((port & 0x8002) == 0 && spectrumModel == MachineTypes.SPECTRUM128K) {
                memory.setPort7ffd(floatbus);
                // Si ha cambiado la pantalla visible hay que invalidar
                if ((port7ffd & 0x08) != (floatbus & 0x08)) {
                    invalidateScreen(true);
                }
                // En el 128k las páginas impares son contended
                contendedRamPage[3] = contendedIOPage[3] = (floatbus & 0x01) != 0;
                port7ffd = floatbus;
            }
        }
//            System.out.println(String.format("tstates = %d, addr = %d, floatbus = %02x",
//                    tstates, addr, floatbus));
//        System.out.println(String.format("InPort: %04X", port));
        return floatbus & 0xff;
    }

    @Override
    public void outPort(int port, int value) {

        preIO(port);

//        System.out.println(String.format("OutPort: %04X [%02X]", port, value));

        try {
            // LEC have preference over any other device
            if (connectedLec && (port & 0x0002) == 0 && spectrumModel == MachineTypes.SPECTRUM48K) {
//                System.out.println(String.format("Port: %04X, value: %02x", port, value));
                if ((value & 0x80) != 0) {
                    pageLec(value);
                } else {
                    unpageLec();
                }
                return;
            }

            // Interface I
            if (connectedIF1) {
                // Port 0xE7 (Microdrive Data Port)
                if ((port & 0x0018) == 0) {
//                    System.out.println(String.format("OUT to MDR-DATA: %02x PC = %04x",
//                        value, z80.getRegPC()));
                    if1.writeDataPort(value);
                    return;
                }
                
                // Port 0xEF (IF1 Control Port)
                if ((port & 0x0018) == 0x08) {
//                    System.out.println(String.format("OUT to MDR-CRTL: %02x. PC = %04x",
//                        value, z80.getRegPC()));
                    if1.writeControlPort(value);
                    return;
                }
                
                // Port 0xF7 (RS232/Network Port)
                if ((port & 0x0018) == 0x10) {
//                    System.out.println(String.format("OUT to RS232/Net: %02x. PC = %04x",
//                        value, z80.getRegPC()));
                    if1.writeLanPort(value);
                    return;
                }
            }
        
            // Multiface 128/+3 ports
            if (multiface) {
                if (memory.isMultifacePaged() && z80.getRegPC() < 0x4000) {
//            System.out.println(String.format("OutPort: %04X [%02X]", port, value));
                    if ((port & 0x00ff) == 0x3f) {
                        memory.setMultifaceLocked(true);
                    }
                    if ((port & 0x00ff) == 0xbf) {
                        memory.setMultifaceLocked(false);
                    }
                }
            }

            if ((port & 0x0001) == 0) {
                if ((portFE & 0x07) != (value & 0x07)) {
                    updateBorder(clock.tstates);
                    borderUpdated = true;
//                if (z80.tEstados > spectrumModel.lastBorderUpdate)
//                    System.out.println(String.format("Frame: %d tstates: %d border: %d",
//                        nFrame, z80.tEstados, value & 0x07));
                }

                if (!tape.isTapePlaying()) {
                    int spkMic = sp_volt[value >> 3 & 3];
                    if (enabledSound && spkMic != speaker) {
                        audio.updateAudio(clock.tstates, speaker);
                        speaker = spkMic;
                    }

                    if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
                        if ((value & issueMask) == 0) {
                            tape.setEarBit(false);
                        } else {
                            tape.setEarBit(true);
                        }
//                    System.out.println(String.format("setEarBit: %b", (value & issueMask) == 0));
                    }
                }

                if (tape.isTapeRecording() && ((portFE ^ value) & 0x08) != 0) {
                    tape.recordPulse(clock.getAbsTstates(), (portFE & 0x08) != 0);
                }
                //System.out.println(String.format("outPort: %04X %02x", port, value));
                portFE = value;
                return;
            }

            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM128K) {
                if ((port & 0x8002) == 0) {
//            System.out.println(String.format("outPort: %04X %02x at %d t-states",
//            port, value, z80.tEstados));

                    memory.setPort7ffd(value);
                    // En el 128k las páginas impares son contended
                    contendedRamPage[3] = contendedIOPage[3] = (value & 0x01) != 0;
                    // Si ha cambiado la pantalla visible hay que invalidar
                    // Se invalida también el borde, o la MDA_DEMO no va bien.
                    if (((port7ffd ^ value) & 0x08) != 0) {
                        invalidateScreen(true);
                    }
                    port7ffd = value;
                    return;
                }
            }

            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                // Port 0x7ffd decodes with bit15 and bit1 reset, bit14 set
                if ((port & 0xC002) == 0x4000) {
                    memory.setPort7ffd(value);
                    // En el +3 las páginas 4 a 7 son contended
                    contendedRamPage[3] = memory.getPlus3HighPage() > 3;
                    // Si ha cambiado la pantalla visible hay que invalidar
                    // Se invalida también el borde, o la MDA_DEMO no va bien.
                    if (((port7ffd ^ value) & 0x08) != 0) {
                        invalidateScreen(true);
                    }

                    port7ffd = value;
                    return;
                }

                // Port 0x1ffd decodes with bit1, bit13, bit14 & bit15 reset,
                // bit12 set.
                if ((port & 0xF002) == 0x1000) {
                    memory.setPort1ffd(value);
                    if (memory.isPlus3RamMode()) {
                        switch (value & 0x06) {
                            case 0:
                                Arrays.fill(contendedRamPage, false);
                                break;
                            case 2:
                                Arrays.fill(contendedRamPage, true);
                                break;
                            case 4:
                            case 6:
                                Arrays.fill(contendedRamPage, true);
                                contendedRamPage[3] = false;
                        }
                    } else {
                        contendedRamPage[0] = contendedRamPage[2] = false;
                        contendedRamPage[1] = true;
                        contendedRamPage[3] = memory.getPlus3HighPage() > 3;
                    }

//                if (((port1ffd ^ value) & 0x08) != 0) {
//                    System.out.println(String.format("Motor %b", (value & 0x08) != 0));
//                }

                    port1ffd = value;
                    return;
                }

//                if (spectrumModel == MachineTypes.SPECTRUMPLUS3) {
//                    if ((port & 0xF002) == 0x3000) {
//                        System.out.println(String.format("Writing FDC data port with %02x", value));
//                    }
//                }
            }

            if (enabledAY && (port & 0x8002) == 0x8000) {
                if ((port & 0x4000) != 0) {
                    ay8912.setAddressLatch(value);
                } else {
                    if (enabledSound && ay8912.getAddressLatch() < 14) {
                        ay8912.updateAY(clock.tstates);
                    }
                    ay8912.writeRegister(value);
                }
                return;
            }

            if (enabledAY && joystick == Joystick.FULLER
                && spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
//                System.out.println(String.format("OutPort: %04X [%02X]", port, value));
                if ((port & 0xff) == 0x3f) {
                    ay8912.setAddressLatch(value);
                    return;
                }

                if ((port & 0xff) == 0x5f) {
                    if (enabledSound && ay8912.getAddressLatch() < 14) {
                        ay8912.updateAY(clock.tstates);
                    }
                    ay8912.writeRegister(value);
                    return;
                }
            }

            // ULAplus ports
            if (ULAPlusEnabled && (port & 0x0004) == 0) {
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
                        ULAPlusActive = (value & 0x01) != 0;
                        invalidateScreen(true);
                    } else {
                        // Solo es necesario redibujar el borde si se modificó uno
                        // de los colores de paper de la paleta 0. (8-15)
                        // Pero hay que hacerlo *antes* de modificar la paleta.
                        if (paletteGroup > 7 && paletteGroup < 16) {
                            invalidateScreen(true);
                            updateBorder(clock.tstates);
                        }
                        ULAPlusPalette[paletteGroup >>> 4][paletteGroup & 0x0f] = value;
                        precompULAplusColor(paletteGroup, value);
                    }
                }
            }
        } finally {
            postIO(port);
        }
    }

    /*
     * Las operaciones de I/O se producen entre los ciclos T3 y T4 de la CPU,
     * y justo ahí es donde podemos encontrar la contención en los accesos. Los
     * ciclos de contención son exactamente iguales a los de la memoria, con los
     * siguientes condicionantes dependiendo del estado del bit A0 y de si el
     * puerto accedido se encuentra entre las direcciones 0x4000-0x7FFF:
     *
     * High byte in 0x40 (0xc0) to 0x7f (0xff)?     Low bit  Contention pattern
     *                                      No      Reset    N:1, C:3
     *                                      No      Set      N:1, N:3
     *                                      Yes     Reset    C:1, C:3
     *                                      Yes     Set      C:1, C:1, C:1, C:1
     *
     * La columna 'Contention Pattern' se lee 'N:x', no contención x ciclos
     * 'C:n' se lee contención seguido de n ciclos sin contención.
     * Así pues se necesitan dos rutinas, la que añade el t-estado inicial
     * con sus contenciones cuando procede y la que añade los 3 estados finales
     * con la contención correspondiente.
     */
    private void preIO(int port) {
        
        if (contendedIOPage[port >>> 14]) {
            clock.tstates += delayTstates[clock.tstates];
        }
        clock.tstates++;
    }
    
    private void postIO(int port) {

        if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
            clock.tstates += 3;
            return;
        }
        
        if ((port & 0x0001) != 0) {
            if (contendedIOPage[port >>> 14]) {
                // A0 == 1 y es contended IO
                clock.tstates += delayTstates[clock.tstates] + 1;
                clock.tstates += delayTstates[clock.tstates] + 1;
                clock.tstates += delayTstates[clock.tstates] + 1;
            } else {
                // A0 == 1 y no es contended IO
                clock.tstates += 3;
            }
        } else {
            // A0 == 0
            clock.tstates += delayTstates[clock.tstates] + 3;
        }
    }

    @Override
    public void execDone(int tstates) {

        if (tape.isTapePlaying()) {
            tape.notifyTimeout();

            earBit = tape.getEarBit();

            if (enabledSound && specSettings.isLoadingNoise()) {
                int spkMic = (earBit == 0xbf) ? 0 : 4000;

                if (spkMic != speaker) {
                    audio.updateAudio(clock.tstates, speaker);
                    speaker = spkMic;
                }
            }
        }
    }

    @Override
    public int atAddress(int address, int opcode) {
        
//        System.out.println(String.format("atAddress: 0x%04X", address));
        
        switch (address) {
            case 0x0008: // PAGE In Interface 1
            case 0x1708:
                if (connectedIF1) {
                    memory.pageIF1Rom();
                    return memory.readByte(address) & 0xff;
                }
                break;
            case 0x0700: // Page Out Interface 1
                if (connectedIF1) {
                    memory.unpageIF1Rom();
                }
                break;
            case 0x0066: // NMI address for Multiface One/128/+3
                if (multiface && !memory.isPlus3RamMode()) {
                    memory.setMultifaceLocked(false);
                    memory.pageMultiface();
                    return memory.readByte(address) & 0xff;
                }
                break;
            case 0x04D0:
                // SA_BYTES routine in Spectrum ROM at 0x04D0
                // SA_BYTES starts at 0x04C2, but the +3 ROM don't enter
                // to SA_BYTES by his start address.
                if (saveTrap && memory.isSpectrumRom() && tape.isTapeReady()) {
                    if (tape.saveTapeBlock(memory)) {
                        return 0xC9; // RET opcode
                    }
                }
                break;
            case 0x0556:
                // LD_BYTES routine in Spectrum ROM at address 0x0556
                if (loadTrap && memory.isSpectrumRom() && tape.isTapeReady()) {
                    if (flashload && tape.flashLoad(memory)) {
                        invalidateScreen(true); // thanks Andrew Owen
                        return 0xC9; // RET opcode
                    } else {
                        tape.play();
                    }
                }
                break;
        }
        
        return opcode;
    }

    public void saveImage(File filename) {
        BufferedOutputStream fOut = null;

        if (filename.getName().toLowerCase().endsWith(".scr")) {
            try {
                fOut = new BufferedOutputStream(new FileOutputStream(filename));

                for (int addr = 0; addr < 6912; addr++) {
                    fOut.write(memory.readScreenByte(addr));
                }

                if (ULAPlusActive) {
                    for (int palette = 0; palette < 4; palette++) {
                        for (int color = 0; color < 16; color++) {
                            fOut.write(ULAPlusPalette[palette][color]);
                        }
                    }
                }
            } catch (FileNotFoundException excpt) {
                Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, excpt);
            } catch (IOException ioExcpt) {
                Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ioExcpt);
            } finally {
                try {
                    if (fOut != null)
                        fOut.close();
                } catch (IOException ex) {
                    Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return;
        }

        if (filename.getName().toLowerCase().endsWith(".png")) {
            try {
                ImageIO.write(tvImage, "png", filename);
            } catch (IOException ioExcpt) {
                Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ioExcpt);
            }
        }
    }

    public boolean loadScreen(File filename) {
        BufferedInputStream fIn = null;

        if (filename.getName().toLowerCase().endsWith(".scr")) {
            try {
                fIn = new BufferedInputStream(new FileInputStream(filename));

                // Needs to be a screen file (size == 6912) or a
                // ULAplus screen file (size == 6912 + 64)
                if (fIn.available() != 6912 && fIn.available() != 6976) {
                    return false;
                }

                for (int addr = 0x4000; addr < 0x5b00; addr++) {
                    memory.writeByte(addr, (byte)(fIn.read() & 0xff));
                }

                ULAPlusActive = ULAPlusEnabled = false;
                if (fIn.available() == 64) {
                    ULAPlusActive = ULAPlusEnabled = true;
                    for (int palette = 0; palette < 4; palette++) {
                        for (int color = 0; color < 16; color++) {
                            int value = fIn.read() & 0xff;
                            ULAPlusPalette[palette][color] = value;
                            int blue = (value & 0x03) << 1;
                            if ((value & 0x01) == 0x01) {
                                blue |= 0x01;
                            }
                            blue = (blue << 5) | (blue << 2) | (blue & 0x03);
                            int red = (value & 0x1C) >> 2;
                            red = (red << 5) | (red << 2) | (red & 0x03);
                            int green = (value & 0xE0) >> 5;
                            green = (green << 5) | (green << 2) | (green & 0x03);
                            ULAPlusPrecompPalette[palette][color] = (red << 16) | (green << 8) | blue;
                        }
                    }
                }
                return true;
            } catch (FileNotFoundException excpt) {
                Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, excpt);
            } catch (IOException ioExcpt) {
                Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ioExcpt);
            } finally {
                try {
                    if (fIn != null)
                        fIn.close();
                } catch (IOException ex) {
                    Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return false;
    }

    static final int SPEAKER_VOLUME = 11000;
    private int speaker;
    private static final int sp_volt[];

    public boolean isMuteSound() {
        return muted;
    }

    public void muteSound(boolean state) {
        muted = state;

        if (muted) {
            disableSound();
        } else {
            enableSound();
        }
    }

    private void enableSound() {
        if (paused || muted || enabledSound) {
            return;
        }

        audio.open(spectrumModel, ay8912, enabledAY,
            settings.getSpectrumSettings().isHifiSound() ? 48000 : 32000);
        enabledSound = true;


    }

    private void disableSound() {
        if (!enabledSound) {
            return;
        }

        enabledSound = false;
        audio.endFrame();
        audio.close();
    }

    public void changeSpeed(int speed) {
        if (speed > 1) {
            disableSound();
            framesByInt = speed;
        } else {
            framesByInt = 1;
            // La velocidad rápida solo pinta 1 frame de cada 10, así que
            // al volver a velocidad lenta hay que actualizar la pantalla
            invalidateScreen(true);
            enableSound();
        }
    }

    static {
        sp_volt = new int[4];
        setvol();
    }

    static void setvol() {
        sp_volt[0] = 0; //(int) -SPEAKER_VOLUME;
        sp_volt[1] = 0; // (int) -(SPEAKER_VOLUME * 1.4);
        sp_volt[2] = (int) SPEAKER_VOLUME;
        sp_volt[3] = (int) (SPEAKER_VOLUME * 1.4);
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
    public static final int BORDER_HEIGHT = 28;
    public static final int SCREEN_HEIGHT = BORDER_HEIGHT + 192 + BORDER_HEIGHT;

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
    //private int nBorderChanges;
    /*
     * screenDirty indica que se cambiaron bytes de la pantalla. Hay que redibujarla
     * borderDirty indica que hay colores del borde que cambiaron. Hay que redibujarlo
     * borderChanged indica que se hizo un out que cambió el color del borde, lo que
     *               no significa que haya que redibujarlo necesariamente.
     */
    private boolean screenDirty, borderDirty, borderChanged, borderUpdated;

    // t-states del ciclo contended por I=0x40-0x7F o -1
//    private int m1contended;
    // valor del registro R cuando se produjo el ciclo m1
//    private int m1regR;
    // Ultimo valor de t-states en que se actualizó la pantalla
    private int lastScreenState;
    // Primera y última línea a ser actualizada
    private int firstLine, lastLine;
    private int leftCol, rightCol;
    // ULAplus support (30/08/2010)
    // Color palette
    private int ULAPlusPalette[][];
    // Palette group
    private int paletteGroup;
    // Is ULAplus mode active?
    private boolean ULAPlusActive;
    // ULAplus precomputed color palette
    private int ULAPlusPrecompPalette[][];
    
    private Rectangle screenRect = new Rectangle();
    private int firstBorderPix, lastBorderPix;
    private Rectangle borderRect = new Rectangle();

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
        screenDirty = false;
        borderChanged = true;

        // Paletas para el soporte de ULAplus
        ULAPlusPalette = new int[4][16];
        ULAPlusPrecompPalette = new int[4][16];
        ULAPlusActive = false;
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
            bufAddr[address & 0x1fff] = row * SCREEN_WIDTH * 8 + (scan + BORDER_HEIGHT) * SCREEN_WIDTH
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
            if (memory.readScreenByte(addrAttr) < 0) {
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
        if (row < (64 - BORDER_HEIGHT - 1) || row > (256 + BORDER_HEIGHT - 1)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la primera línea
        if (row == (64 - BORDER_HEIGHT - 1) && col < 200 + (24 - BORDER_WIDTH / 2)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la última línea
        if (row == (256 + BORDER_HEIGHT - 1) && col > (127 + BORDER_WIDTH / 2)) {
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

        row -= (64 - BORDER_HEIGHT);

        return row * SCREEN_WIDTH + col * 2;
    }

    private int tStatesToScrPix128k(int tstates) {
        tstates %= spectrumModel.tstatesFrame;

        int row = tstates / spectrumModel.tstatesLine;
        int col = tstates % spectrumModel.tstatesLine;

        // Quitamos las líneas que no se ven por arriba y por abajo
        if (row < (63 - BORDER_HEIGHT - 1) || row > (255 + BORDER_HEIGHT - 1)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la primera línea
        if (row == (63 - BORDER_HEIGHT - 1) && col < 204 + (24 - BORDER_WIDTH / 2)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la última línea
        if (row == (255 + BORDER_HEIGHT - 1) && col > (127 + BORDER_WIDTH / 2)) {
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
        row -= (63 - BORDER_HEIGHT);

        return row * SCREEN_WIDTH + col * 2;
    }

    private void updateBorder(int tstates) {

        if (tstates < lastChgBorder || tstates > spectrumModel.lastBorderUpdate) {
//            System.out.println("Out from updateBorder by the fast lane");
            return;
        }
        
//        System.out.println(String.format("In @ updateBorder: lastChgBorder = %d, tstates = %d",
//                lastChgBorder, tstates));

        int nowColor;
        if (ULAPlusActive) {
            nowColor = ULAPlusPrecompPalette[0][(portFE & 0x07) | 0x08];
        } else {
            nowColor = Paleta[portFE & 0x07];
        }

        tstates += spectrumModel.outBorderOffset;
        tstates &= 0x00fffffc;
        while (lastChgBorder < tstates && lastChgBorder < spectrumModel.lastBorderUpdate) {
            int idxColor = states2border[lastChgBorder];
            lastChgBorder += 4;

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
            borderDirty = true;
            if (firstBorderPix > idxColor)
                firstBorderPix = idxColor;
            lastBorderPix = idxColor;
        }
        
        lastChgBorder = tstates;
//        System.out.println(String.format("Out @ updateBorder: lastChgBorder = %d, nBorderChanges = %d",
//                lastChgBorder, nBorderChanges));
    }

    private void updateScreen(int toTstates) {
        int fromAddr, addrBuf;
        int paper, ink;
        byte scrByte;
        int attr;
        //System.out.println(String.format("from: %d\tto: %d", fromTstates, toTstates));

        if (toTstates < spectrumModel.firstScrByte)
            return;

        toTstates &= 0xffffc;
        while (lastScreenState <= toTstates && lastScreenState <= spectrumModel.lastScrUpdate) {
            fromAddr = states2scr[lastScreenState];
            lastScreenState += 4;

            if (fromAddr == -1 || !dirtyByte[fromAddr & 0x1fff]) {
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

            scrByte = memory.readScreenByte(fromAddr);
            fromAddr &= 0x1fff;
            attr = memory.readScreenByte(scr2attr[fromAddr]) & 0xff;

            addrBuf = bufAddr[fromAddr];

            if (ULAPlusActive) {
                ink = ULAPlusPrecompPalette[attr >>> 6][attr & 0x07];
                paper = ULAPlusPrecompPalette[attr >>> 6][((attr & 0x38) >>> 3) | 0x08];
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
        }
    }

    private void notifyScreenWrite(int address) {
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
        borderChanged = invalidateBorder;
        Arrays.fill(dirtyByte, true);
    }

    private void buildScreenTables48k() {
        int col, scan;

        Arrays.fill(states2scr, -1);

        for (int tstates = 14336; tstates < 57252; tstates += 4) {
            col = (tstates % 224) / 4;

            if (col > 31) {
                continue;
            }
            scan = tstates / 224 - 64;
            states2scr[tstates + 4] = scrAddr[scan] + col;
        }
        
        Arrays.fill(states2border, 0xf0cab0ba);

//        System.out.println(String.format("48k - > firstBU: %d, lastBU: %d",
//                spectrumModel.firstBorderUpdate, spectrumModel.lastBorderUpdate));
        for (int tstates = spectrumModel.firstBorderUpdate;
                tstates < spectrumModel.lastBorderUpdate; tstates += 4) {
            states2border[tstates] = tStatesToScrPix48k(tstates);
            states2border[tstates + 1] = states2border[tstates];
            states2border[tstates + 2] = states2border[tstates];
            states2border[tstates + 3] = states2border[tstates];
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

        for (int tstates = 14364; tstates < 58044; tstates += 4) {
            col = (tstates % 228) / 4;

            if (col > 31) {
                continue;
            }

            scan = tstates / 228 - 63;
            states2scr[tstates] = scrAddr[scan] + col;
        }

        Arrays.fill(states2border, 0xf0cab0ba);

//        System.out.println(String.format("128k - > firstBU: %d, lastBU: %d",
//                spectrumModel.firstBorderUpdate, spectrumModel.lastBorderUpdate));
        for (int tstates = spectrumModel.firstBorderUpdate;
                tstates < spectrumModel.lastBorderUpdate; tstates += 4) {
            states2border[tstates] = tStatesToScrPix128k(tstates);
            states2border[tstates + 1] = states2border[tstates];
            states2border[tstates + 2] = states2border[tstates];
            states2border[tstates + 3] = states2border[tstates];
        }

        Arrays.fill(delayTstates, (byte) 0x00);

        for (int idx = 14361; idx < 58037; idx += 228) {
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

    private void buildScreenTablesPlus3() {
        int col, scan;

        Arrays.fill(states2scr, -1);

        for (int tstates = 14364; tstates < 58044; tstates += 4) {
            col = (tstates % 228) / 4;

            if (col > 31) {
                continue;
            }

            scan = tstates / 228 - 63;
            states2scr[tstates + 4] = scrAddr[scan] + col;
        }

        Arrays.fill(states2border, 0xf0cab0ba);

//        System.out.println(String.format("+3 - > firstBU: %d, lastBU: %d",
//                spectrumModel.firstBorderUpdate, spectrumModel.lastBorderUpdate));
        for (int tstates = spectrumModel.firstBorderUpdate;
                tstates < spectrumModel.lastBorderUpdate; tstates += 4) {
            states2border[tstates] = tStatesToScrPix128k(tstates);
            states2border[tstates + 1] = states2border[tstates];
            states2border[tstates + 2] = states2border[tstates];
            states2border[tstates + 3] = states2border[tstates];
        }

        // Diga lo que diga la FAQ de WoS, los estados de espera comienzan
        // en 14361 y no en 14365. El programa TSTP3 de Pedro Gimeno lo
        // confirma. Gracias Pedro!.
        Arrays.fill(delayTstates, (byte) 0x00);

        for (int idx = 14361; idx < 58036; idx += 228) {
            for (int ndx = 0; ndx < 128; ndx += 8) {
                int frame = idx + ndx;
                delayTstates[frame++] = 1;
                delayTstates[frame++] = 0;
                delayTstates[frame++] = 7;
                delayTstates[frame++] = 6;
                delayTstates[frame++] = 5;
                delayTstates[frame++] = 4;
                delayTstates[frame++] = 3;
                delayTstates[frame++] = 2;
            }
        }
    }

    private void precompULAplusColor(int register, int color) {
        int blue = (color & 0x03) << 1;
        if ((color & 0x01) == 0x01) {
            blue |= 0x01;
        }
        blue = (blue << 5) | (blue << 2) | (blue & 0x03);

        int red = (color & 0x1C) >> 2;
        red = (red << 5) | (red << 2) | (red & 0x03);

        int green = (color & 0xE0) >> 5;
        green = (green << 5) | (green << 2) | (green & 0x03);

        ULAPlusPrecompPalette[register >>> 4][register & 0x0f] =
            (red << 16) | (green << 8) | blue;
    }
    
    public synchronized boolean startRecording() {
        if (!tape.isTapeReady()) {
            return false;
        }

        if (!tape.startRecording()) {
            return false;
        }

        return true;
    }

    public synchronized boolean stopRecording() {
        if (!tape.isTapeRecording())
            return false;
        
        tape.recordPulse(clock.getAbsTstates(), (portFE & 0x08) != 0);
        tape.stopRecording();

        return true;
    }

    public boolean isIF2RomInserted() {
        return memory.isIF2RomPaged();
    }
    
    public boolean insertIF2Rom(File filename) {
        return memory.insertIF2Rom(filename);
    }

    public void ejectIF2Rom() {
        memory.extractIF2Rom();
    }

    // Accessors for IF1 methods
    
    public Interface1 getInterface1() {
        return if1;
    }
    
    public boolean isIF1Connected() {
        return connectedIF1;
    }
    
    private void pageLec(int value) {
        if (!memory.isLecPaged()) {
            contendedRamPage[1] = contendedIOPage[1] = false;
            z80.setBreakpoint(0x0066, false); // multiface
            z80.setBreakpoint(0x04D0, false); // saveTrap
            z80.setBreakpoint(0x0556, false); // loadTrap
            if (connectedIF1) {
                z80.setBreakpoint(0x0008, false); // IF1 Trap
                z80.setBreakpoint(0x0700, false); // IF1 Trap
                z80.setBreakpoint(0x1708, false); // IF1 Trap
            }
            loadTrap = saveTrap = enabledAY = false;
        }
        memory.pageLec(value);
    }

    private void unpageLec() {
        memory.unpageLec();
        contendedRamPage[1] = contendedIOPage[1] = true;
        z80.setBreakpoint(0x0066, multiface);
        saveTrap = settings.getTapeSettings().isEnableSaveTraps();
        z80.setBreakpoint(0x04D0, saveTrap);
        loadTrap = settings.getTapeSettings().isEnableLoadTraps();
        z80.setBreakpoint(0x0556, loadTrap);
        if (connectedIF1) {
            z80.setBreakpoint(0x0008, connectedIF1);
            z80.setBreakpoint(0x0700, connectedIF1);
            z80.setBreakpoint(0x1708, connectedIF1);
        }
        enabledAY = settings.getSpectrumSettings().isAYEnabled48K();
    }
    
    private class TapeChangedListener implements TapeStateListener {

        @Override
        public void stateChanged(final TapeState state) {
//            System.out.println("Spectrum::TapeChangedListener: state = " + state + "");
            
            switch (state) {
                case PLAY:
                    if (!paused && settings.getTapeSettings().isAccelerateLoading()) {

                        new Thread() {

                            @Override
                            public void run() {
                                acceleratedLoading();
                            }
                        }.start();
                    }
                    break;
            }
        }
    }
}
