/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import configuration.JSpeccySettings;
import configuration.SpectrumType;
import gui.JSpeccyScreen;
import joystickinput.JoystickRaw;
import lombok.extern.slf4j.Slf4j;
import machine.Keyboard.JoystickModel;
import snapshots.SpectrumState;
import utilities.Tape;
import utilities.Tape.TapeState;
import utilities.TapeStateListener;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
@Slf4j
public class Spectrum extends z80core.MemIoOps implements Runnable, z80core.NotifyOps {

    private final Z80 z80;
    private final Memory memory;
    private final SpectrumClock clock;
    private final boolean[] contendedRamPage = new boolean[4];
    private final boolean[] contendedIOPage = new boolean[4];
    private int portFE, earBit = 0xbf, port7ffd, port1ffd, issueMask;
    private int kmouseX = 0, kmouseY = 0, kmouseW; // Kempston Mouse Turbo Master X, Y, Wheel
    private long framesByInt, speedometer, speed, prevSpeed;
    private boolean muted, enabledAY, kmouseEnabled;
    private final byte delayTstates[] =
        new byte[MachineTypes.SPECTRUM128K.tstatesFrame + 200];
    public MachineTypes spectrumModel;
    public int firstBorderUpdate, lastBorderUpdate, borderMode;
    private final Timer timerFrame;
    private SpectrumTimer taskFrame;
    private JSpeccyScreen jscr;
    private final Keyboard keyboard;
    private final Audio audio;
    private final AY8912 ay8912;
    private Tape tape;
    private volatile boolean paused;
    private volatile boolean acceleratedLoading;
    private volatile boolean isSoundEnabled;
    private boolean resetPending, autoLoadTape;
    private JLabel speedLabel;

    private JoystickModel joystickModel;
    private JoystickRaw joystick1, joystick2;

    private final JSpeccySettings settings;
    private final SpectrumType specSettings;
    /* Config vars */
    private boolean issue2, saveTrap, loadTrap, flashload;
    private boolean connectedIF1;
    private final Interface1 if1;

    public Spectrum(JSpeccySettings config) {
        super(0,0);
        clock = SpectrumClock.INSTANCE;
        settings = config;
        specSettings = settings.getSpectrumSettings();
        z80 = new Z80(this, this);
        memory = new Memory(settings);
        initGFX();
        speedometer = 0;
        framesByInt = 1;
        portFE = 0;
        port7ffd = 0;
        ay8912 = new AY8912();
        audio = new Audio(settings.getAY8912Settings());
        muted = specSettings.isMutedSound();
        isSoundEnabled = false;
        paused = true;
        borderMode = 1;
        if1 = new Interface1(settings.getInterface1Settings());

        if (System.getProperty("os.name").contains("Linux")) {
            try {
                joystick1 = new JoystickRaw(0);
                joystick1.start();
                joystick2 = new JoystickRaw(1);
                joystick2.start();
            } catch (final IOException ex) {
//            log.error("", ex);
                if (joystick1 == null && joystick2 == null)
                    log.info("No physical joystick found!");
            }
        }
        keyboard = new Keyboard(settings.getKeyboardJoystickSettings(), joystick1, joystick2);

        resetPending = false;

        timerFrame = new Timer("SpectrumClock", true);
    }

    public final SpectrumState getSpectrumState() {
        SpectrumState state = new SpectrumState();

        state.setSpectrumModel(spectrumModel);
        state.setZ80State(z80.getZ80State());
        state.setMemoryState(memory.getMemoryState());
        state.setConnectedLec(specSettings.isLecEnabled());

        state.setEarBit(earBit);
        state.setPortFE(portFE);
        state.setJoystick(joystickModel);

        if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUM48K) {
            state.setPort7ffd(port7ffd);
            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                state.setPort1ffd(port1ffd);
            }
        } else {
            state.setIssue2(issue2);
        }

        state.setEnabledAY(enabledAY);
        if (enabledAY) {
            state.setAY8912State(ay8912.getAY8912State());
        }

        state.setConnectedIF1(connectedIF1);
        if (connectedIF1) {
            state.setNumMicrodrives(settings.getInterface1Settings().getMicrodriveUnits());
        }

        state.setMultiface(specSettings.isMultifaceEnabled());

        state.setULAPlusEnabled(specSettings.isULAplus());
        if (specSettings.isULAplus()) {
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
        specSettings.setLecEnabled(state.isConnectedLec());

        earBit = state.getEarBit();
        portFE = state.getPortFE();

        if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUM48K) {
            port7ffd = state.getPort7ffd();
            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                // Set 0x1ffd first, because the paging can be locked at 0x7ffd
                port1ffd = state.getPort1ffd();
                memory.setPort1ffd(port1ffd);

                memory.setPort7ffd(port7ffd);
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
                memory.setPort7ffd(port7ffd);
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

        specSettings.setMultifaceEnabled(state.isMultiface());

        specSettings.setULAplus(state.isULAPlusEnabled());
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
        step = 0;
        while (step < stepStates.length && stepStates[step] < state.getTstates()) {
            step++;
        }
        nextEvent = step < stepStates.length ? stepStates[step] : NO_EVENT;

        loadConfigVars();

        if (memory.isConnectedLEC()) {
           pageLec(state.getMemoryState().getPortFD());
        }
    }

    public void selectHardwareModel(int model) {
        switch (model) {
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
    }

    public void selectHardwareModel(MachineTypes hardwareModel) {

        if (tape != null && tape.isTapePlaying()) {
            tape.stop();
        }

        disableSound();
        spectrumModel = hardwareModel;
        clock.setSpectrumModel(spectrumModel);
        memory.reset(spectrumModel);

        if (tape != null) {
            tape.setSpectrumModel(spectrumModel);
        }

        contendedRamPage[0] = contendedIOPage[0] = false;
        contendedRamPage[1] = contendedIOPage[1] = true;
        contendedRamPage[2] = contendedIOPage[2] = false;
        contendedRamPage[3] = contendedIOPage[3] = false;

        switch (spectrumModel.codeModel) {
            case SPECTRUM48K:
                buildScreenTables48k();
                enabledAY = specSettings.isAYEnabled48K();
                connectedIF1 = settings.getInterface1Settings().isConnectedIF1();
                Paleta = Paleta48k;
                break;
            case SPECTRUM128K:
                buildScreenTables128k();
                enabledAY = true;
                connectedIF1 = settings.getInterface1Settings().isConnectedIF1();
                Paleta = Paleta128k;
                break;
            case SPECTRUMPLUS3:
                buildScreenTablesPlus3();
                enabledAY = true;
                contendedIOPage[1] = false;
                connectedIF1 = false;
                Paleta = Paleta128k;
                break;
        }

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

        step = 0;
        nextEvent = stepStates[0];

        enableSound();
    }

    public final void loadConfigVars() {

        issue2 = settings.getKeyboardJoystickSettings().isIssue2();
        // AND con 0x18 para emular un Issue 2
        // AND con 0x10 para emular un Issue 3
        if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
            issueMask = issue2 ? 0x18 : 0x10;
            enabledAY = specSettings.isAYEnabled48K();
        } else {
            issueMask = 0x10; // los modelos que no son el 48k son todos Issue3
        }

        keyboard.setMapPCKeys(settings.getKeyboardJoystickSettings().isMapPCKeys());
        keyboard.setRZXEnabled(settings.getKeyboardJoystickSettings().isRecreatedZX());

        z80.setBreakpoint(0x0066, specSettings.isMultifaceEnabled());

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

        if (specSettings.isLecEnabled() && spectrumModel == MachineTypes.SPECTRUM48K) {
            memory.setConnectedLEC(true);
            if (memory.isLecPaged()) {
                pageLec(memory.getPortFD());
            } else {
                unpageLec();
            }
        } else {
            memory.setConnectedLEC(false);
        }
    }

    public void startEmulation() {
        if (!paused) {
            return;
        }

        audio.reset();
        invalidateScreen(true);
        lastChgBorder = firstBorderUpdate;
        drawFrame();
        jscr.repaint();
        paused = false;
        enableSound();
        if (!isSoundEnabled) {
            taskFrame = new SpectrumTimer(this);
            timerFrame.scheduleAtFixedRate(taskFrame, 10, 20);
        }
    }

    public void stopEmulation() {
        if (paused) {
            return;
        }

        paused = true;

        if (isSoundEnabled) {
            disableSound();
        } else {
            taskFrame.cancel();
            taskFrame = null;
        }
    }

    @Override
    public void reset() {
        resetPending = true;
        z80.setPinReset();
        tape.stop();
    }

    private void doReset() {
        clock.reset();
        z80.reset();
        if (memory.isLecPaged()) {
            unpageLec();
        }
        memory.reset(spectrumModel);
        ay8912.reset();
        audio.reset();
        keyboard.reset();
        if (connectedIF1) {
            if1.reset();
        }
        portFE = port7ffd = port1ffd = 0;
        kmouseX = kmouseY = 0;
        kmouseW = 0xff;
        kmouseEnabled = true;
        ULAPlusActive = false;
        step = paletteGroup = 0;
        invalidateScreen(true);
        resetPending = false;
    }

    public void hardReset() {
        resetPending = true;
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

    public JoystickModel getJoystick() {
        return joystickModel;
    }

    public void setJoystick(JoystickModel type) {
        joystickModel = type;
        keyboard.setJoystickModel(type);
        kmouseX = kmouseY = 0;
        kmouseW = 0xff;
    }

    public void setJoystick(int model) {
        keyboard.setJoystickModel(model);
        joystickModel = keyboard.getJoystickModel();
        kmouseX = kmouseY = 0;
        kmouseW = 0xff;
    }

    public void setTape(Tape player) {
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

    public void autoLoadTape() {
        autoLoadTape = true;
        reset();
    }

    // Spectrum system variables
    private static final int LAST_K = 23560;
    private static final int FLAGS = 23611;
    private void doAutoLoadTape() {
        autoLoadTape = false;
        Runnable task = () -> {
            try {
                Thread.sleep(1100);
                // El 48k tarda en inicializarse unos 86 frames
                // Los modelos de 128k, tardan unos 56 frames
                // Habrá que comprobar el +3 cuando tenga la disquetera
                long endFrame = spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K ? 100 : 70;
                while (clock.getFrames() < endFrame) {
                    TimeUnit.MILLISECONDS.sleep(20);
                }

                if (endFrame == 100) {
                    memory.writeByte(LAST_K, (byte) 0xEF); // LOAD keyword
                    memory.writeByte(FLAGS, (byte)(memory.readByte(FLAGS) | 0x20));  // signal that a key was pressed
                    Thread.sleep(30);
                    memory.writeByte(LAST_K, (byte) 0x22); // " key
                    memory.writeByte(FLAGS, (byte)(memory.readByte(FLAGS) | 0x20));
                    Thread.sleep(30);
                    memory.writeByte(LAST_K, (byte) 0x22); // " key
                    memory.writeByte(FLAGS, (byte)(memory.readByte(FLAGS) | 0x20));
                    Thread.sleep(30);
                }
                memory.writeByte(LAST_K, (byte) 0x0D); // ENTER key
                memory.writeByte(FLAGS, (byte)(memory.readByte(FLAGS) | 0x20));
                Thread.sleep(30);
            } catch (final InterruptedException ex) {
                log.error("", ex);
            }
        };

        new Thread(task).start();
    }

    /*
     * El emulador hace uso de dos sistemas de sincronización diferentes, que se escoge dependiendo
     * de si está habilitado el sonido o no. Si el sonido está activado, el "metrónomo" es la propia
     * tarjeta de sonido. La llamada al método sendAudioFrame se bloquea en el write hasta que
     * la tarjeta se ha quedado con todos los datos. Cuando no hay audio o el emulador está en pausa,
     * ejecuta un wait del que sale automáticamente para saber si ha salido de la pausa o bien lo saca
     * de la pausa el 'tick' del reloj que se programa para que salte cada 20 ms.
     */
    @Override
    public synchronized void run() {
        while (true) {
            if (paused || !isSoundEnabled) {
                try {
                    wait(250);
                } catch (final InterruptedException ex) {
                    log.error("", ex);
                }

                if (paused) {
                    continue;
                }
            }

            if (acceleratedLoading) {
                acceleratedLoading = false;
                stopEmulation();
                acceleratedLoading();
                startEmulation();
            }

            generateFrame();
            drawFrame();
            if (isSoundEnabled) {
                audio.sendAudioFrame();
            }
        }
    }

    public synchronized void generateFrame() {

//        long startFrame, endFrame, sleepTime;
//        startFrame = System.currentTimeMillis();
//        System.out.println("Start frame: " + startFrame);

        //z80.tEstados = frameStart;
        //System.out.println(String.format("Begin frame. t-states: %d", z80.tEstados));

        if (resetPending) {
            doReset();
            if (autoLoadTape) {
                doAutoLoadTape();
            }
        }

        long counter = framesByInt;

        lastScanLine = rightCol = lastBorderPix = 0;
        firstBorderPix = dataInProgress.length;
        firstScanLine = 191;
        leftCol = 31;
        lastChgBorder = firstBorderUpdate;

        do {

            while (step < stepStates.length) {
                z80.execute(stepStates[step]);
                if (clock.getTstates() >= nextEvent) {
                    updateScreen(clock.getTstates());
                }
            }

            z80.execute(spectrumModel.tstatesFrame);

            if (isSoundEnabled) {
                if (enabledAY) {
                    ay8912.updateAY(spectrumModel.tstatesFrame);
                }
                audio.updateAudio(spectrumModel.tstatesFrame, speaker);
                audio.endFrame();
            }

            clock.endFrame();

            step = 0;
            nextEvent = stepStates[0];

            if (!ULAPlusActive && clock.getFrames() % 16 == 0) {
                toggleFlash();
            }

            if (clock.getFrames() % 50 == 0) {
                long now = System.currentTimeMillis() / 10;
                speed = 10000 / (now - speedometer);
                speedometer = now;
                if (speed != prevSpeed) {
                    prevSpeed = speed;
                    SwingUtilities.invokeLater(() -> {
                        speedLabel.setText(String.format("%5d%%", speed));
                    });
//                    System.out.println(String.format("Time: %d Speed: %d%%", now, speed));
                }
            }
        } while (--counter > 0);

        /*
         * In the 128K/+2 models, when register I is between 0x40-0x7F, or
         * the register I >= 0xC0 and a contended page is at 0xc000-0xffff, the
         * computer resets shortly.
         * From VELESOFT:
         * More Russian software use IM2 vector table in slow memory area (16384-32767
         * and slow mem pages on ZX48/128/+2/+3). This ZX models contain HW bug in HAL10H8
         * chip and if high adress byte of vector table (register I) is set to this slow
         * (contended) memory, ZX is very unstable and ULA show in screen raining effect.
         * ZX will crash.
         *
         * http://www.worldofspectrum.org/forums/showthread.php?t=38284
         *
         * JSpeccy emulates a +2 with a corrected HAL10H8 chip.
         */
        if (specSettings.isEmulate128KBug() && spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM128K) {
            int regI = z80.getRegI();
            if ((regI >= 0x40 && regI <= 0x7f)
                    || (spectrumModel == MachineTypes.SPECTRUM128K && regI > 0xbf && contendedRamPage[3])) {
                log.error(String.format("Incompatible program with 128k. Register I = 0x%02X. Reset!", regI));
                z80.reset();
            }
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
            updateBorder(lastBorderUpdate);
            if (borderDirty) {
                borderDirty = false;
                gcTvImage.drawImage(inProgressImage, 0, 0, null);
                int zoom = jscr.getZoom();
                int fbl = firstBorderPix / screenGeometry.width();
                borderRect.x = 0;
                borderRect.y = fbl * zoom;
                borderRect.width = screenGeometry.width() * zoom;
                borderRect.height = (lastBorderPix / screenGeometry.width() - fbl + zoom) * zoom;
                if (screenDirty) {
                    screenDirty = false;
                    gcTvImage.drawImage(inProgressImage, 0, 0, null);
                    screenRect.x = ((screenGeometry.border().left() + leftCol * 8) * zoom) - zoom;
                    screenRect.y = ((screenGeometry.border().top() + firstScanLine) * zoom) - zoom;
                    screenRect.width = ((rightCol - leftCol + 1) * 8 * zoom) + zoom * 2;
                    screenRect.height = ((lastScanLine - firstScanLine + 1) * zoom) + zoom * 2;
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

            int zoom = jscr.getZoom();
            screenRect.x = ((screenGeometry.border().left() + leftCol * 8) * zoom) - zoom;
            screenRect.y = ((screenGeometry.border().top() + firstScanLine) * zoom) - zoom;
            screenRect.width = ((rightCol - leftCol + 1) * 8 * zoom) + zoom * 2;
            screenRect.height = ((lastScanLine - firstScanLine + 1) * zoom) + zoom * 2;
//            System.out.println("screenDirty @ rect " + screenRect);
            jscr.repaint(screenRect);
        }
    }

    private synchronized void acceleratedLoading() {

        lastScanLine = lastBorderPix = 0;
        firstBorderPix = dataInProgress.length;
        firstScanLine = 191;
        leftCol = 31;
        rightCol = 0;
        lastChgBorder = firstBorderUpdate;

        nextEvent = NO_EVENT;
        do {
            long startFrame = clock.getFrames();
            long end = System.currentTimeMillis() + 300;
            do {
                z80.execute(spectrumModel.tstatesFrame);
                clock.endFrame();
            } while (tape.isTapePlaying() && System.currentTimeMillis() <= end);

            if (screenGeometry.border().left() > 0) {
                updateBorder(lastBorderUpdate);
            }

            step = 0;
            updateScreen(spectrumModel.lastScrUpdate);
            gcTvImage.drawImage(inProgressImage, 0, 0, null);
            jscr.repaint();

            lastScanLine = rightCol = lastBorderPix = 0;
            firstBorderPix = dataInProgress.length;
            firstScanLine = 191;
            leftCol = 31;
            lastChgBorder = firstBorderUpdate;

            speed = clock.getFrames() - startFrame;
            if (speed != prevSpeed) {
                prevSpeed = speed;
                SwingUtilities.invokeLater(() -> speedLabel.setText(String.format("%5d%%", Math.abs(speed * 10))));
            }

        } while (tape.isTapePlaying());

        lastScanLine = rightCol = lastBorderPix = step = 0;
        firstBorderPix = dataInProgress.length;
        firstScanLine = 191;
        leftCol = 31;
        lastChgBorder = firstBorderUpdate;

        updateScreen(spectrumModel.lastScrUpdate);
        borderUpdated = true;

        step = 0;
        nextEvent = stepStates[0];
    }

    @Override
    public int fetchOpcode(int address) {

        if (contendedRamPage[address >>> 14]) {
            clock.addTstates(delayTstates[clock.getTstates()] + 4);
        } else {
            clock.addTstates(4);
        }

        return memory.readByte(address) & 0xff;
    }

    @Override
    public int peek8(int address) {

        if (contendedRamPage[address >>> 14]) {
            clock.addTstates(delayTstates[clock.getTstates()] + 3);
        } else {
            clock.addTstates(3);
        }

        return memory.readByte(address) & 0xff;
    }

    @Override
    public void poke8(int address, int value) {

        if (contendedRamPage[address >>> 14]) {
            clock.addTstates(delayTstates[clock.getTstates()] + 3);
            if (memory.isScreenByteModified(address, (byte) value)) {
                if (clock.getTstates() >= nextEvent) {
                    updateScreen(clock.getTstates());
                }
                notifyScreenWrite(address);
            }
        } else {
            clock.addTstates(3);
        }

        memory.writeByte(address, (byte) value);
    }

    @Override
    public int peek16(int address) {

        if (contendedRamPage[address >>> 14]) {
            clock.addTstates(delayTstates[clock.getTstates()] + 3);
        } else {
            clock.addTstates(3);
        }

        int lsb = memory.readByte(address) & 0xff;

        address = (address + 1) & 0xffff;
        if (contendedRamPage[address >>> 14]) {
            clock.addTstates(delayTstates[clock.getTstates()] + 3);
        } else {
            clock.addTstates(3);
        }

        return ((memory.readByte(address) << 8) & 0xff00 | lsb);
    }

    @Override
    public void poke16(int address, int word) {

        byte lsb = (byte) word;
        byte msb = (byte) (word >>> 8);

        if (contendedRamPage[address >>> 14]) {
            clock.addTstates(delayTstates[clock.getTstates()] + 3);
            if (memory.isScreenByteModified(address, lsb)) {
                if (clock.getTstates() >= nextEvent) {
                    updateScreen(clock.getTstates());
                }
                notifyScreenWrite(address);
            }
        } else {
            clock.addTstates(3);
        }

        memory.writeByte(address, lsb);

        address = (address + 1) & 0xffff;

        if (contendedRamPage[address >>> 14]) {
            clock.addTstates(delayTstates[clock.getTstates()] + 3);
            if (memory.isScreenByteModified(address, msb)) {
                if (clock.getTstates() >= nextEvent) {
                    updateScreen(clock.getTstates());
                }
                notifyScreenWrite(address);
            }
        } else {
            clock.addTstates(3);
        }

        memory.writeByte(address, msb);
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        if (contendedRamPage[address >>> 14]
            && spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
            for (int idx = 0; idx < tstates; idx++) {
                clock.addTstates(delayTstates[clock.getTstates()] + 1);
            }
        } else {
            clock.addTstates(tstates);
        }
    }

    @Override
    public int inPort(int port) {

//        if ((port & 0xff) == 0xff) {
//            System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                    clock.getTstates(), z80.getRegPC()));
//        }
//        if (z80.getRegPC() > 0x3fff)
//            System.out.println(String.format("InPort: %04X\tPC: %04x", port, z80.getRegPC()));
        preIO(port);
        postIO(port);

//        System.out.println(String.format("inPort -> t-state: %d\tPC: %04x",
//                    clock.getTstates(), z80.getRegPC()));

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
        if (specSettings.isMultifaceEnabled()) {
            switch (spectrumModel.codeModel) {
                case SPECTRUM48K:
                    if (specSettings.isMf128On48K()) {
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
         * El interfaz Kempston original decodifica A7=A6=A5=0.
         * Pero hay clónicos que solo decodifican A5=0.
         */
        if (joystickModel == JoystickModel.KEMPSTON && ((port & 0x00E0) == 0 || (port & 0xFF) == 0xDF)) {
//            System.out.println(String.format("InPort: %04X, PC: %04X, Frame: %d: %d",
//                    port, z80.getRegPC(), clock.getFrames(), clock.getTstates()));
//            System.out.println("deadzone: " + sixaxis.getDeadZone() + "\tpoll: " + sixaxis.getPollInterval());
//                long start = System.currentTimeMillis();
//                joystick1.poll();
//                System.out.println(System.currentTimeMillis() - start);
            if (kmouseEnabled && joystick1 != null) {
                switch (port & 0x85FF) {
                    case 0x84DF: // Kempston Mouse Turbo rd7ffd port
                        return port7ffd;
                    case 0x80DF: // Kempston Mouse Turbo Master button port
                        int status = joystick1.getButtonMask();
                        int buttons = 0x0f;
                        if ((status & 0x400) != 0) {
                            buttons &= 0xFD; // Left mouse button
                        }

                        if ((status & 0x800) != 0) {
                            buttons &= 0xFE; // Right mouse button
                        }

                        if ((status & 0x004) != 0) {
                            buttons &= 0xFB; // Middle mouse button
                        }

                        // D4-D7 4-bit wheel counter
                        short waxis = joystick1.getAxisValue(3);
                        if (waxis > 0) {
                            kmouseW -= 16;
                        }
                        if (waxis < 0) {
                            kmouseW += 16;
                        }
                        return buttons | (kmouseW & 0xf0);
                    case 0x81DF:
                        // Kempston Mouse Turbo Master X-AXIS
                        short xaxis = joystick1.getAxisValue(0);
                        if (xaxis != 0) {
                            kmouseX = (kmouseX + xaxis / 6553) & 0xff;
                        }
                        return kmouseX;
                    case 0x85DF:
                        // Kempston Mouse Turbo Master Y-AXIS
                        short yaxis = joystick1.getAxisValue(1);
                        if (yaxis != 0) {
                            kmouseY = (kmouseY - yaxis / 6553) & 0xff;
                        }
                        return kmouseY;
                    default:
                        return keyboard.readKempstonPort();
                }
            }

            // Before exit, reset the additional fire button bits from K-Mouse
            return keyboard.readKempstonPort() & 0x1F;
        }

        if (joystickModel == JoystickModel.FULLER && (port & 0xff) == 0x7f) {
//            System.out.println(String.format("InPort: %04X", port));
            return keyboard.readFullerPort();
        }

        // ULA Port
        if ((port & 0x0001) == 0) {
//            if (!tape.isTapeRunning())
//                System.out.println(String.format("InPort: %04X, PC: %d", port, z80.getRegPC()));
            earBit = tape.getEarBit();
            if (joystick1 == null || tape.isTapeRunning()) {
                return keyboard.readKeyboardPort(port, false) & earBit;
            }

            return keyboard.readKeyboardPort(port, true) & earBit;
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

            if (joystickModel == JoystickModel.FULLER && (port & 0xff) == 0x3f) {
//                System.out.println(String.format("InPort: %04X", port));
                return ay8912.readRegister();
            }
        }

        // ULAplus Data Port (read/write)
        if (specSettings.isULAplus() && (port & 0x4004) == 0x4000) {
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
            if (clock.getTstates() < spectrumModel.firstScrByte || clock.getTstates() > spectrumModel.lastScrUpdate) {
                return 0xff;
            }

            int col = (clock.getTstates() % spectrumModel.tstatesLine) - spectrumModel.outOffset;
            if (col > 124) {
                return 0xff;
            }

            int row = clock.getTstates() / spectrumModel.tstatesLine - spectrumModel.upBorderWidth;

            switch (col % 8) {
                case 0:
                    addr = scrAddr[row] + col / 4;
                    floatbus = memory.readScreenByte(addr & 0x1fff);
                    break;
                case 1:
                    addr = scr2attr[(scrAddr[row] + col / 4) & 0x1fff];
                    floatbus = memory.readScreenByte(addr);
                    break;
                case 2:
                    addr = scrAddr[row] + col / 4 + 1;
                    floatbus = memory.readScreenByte(addr & 0x1fff);
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
            if (specSettings.isLecEnabled() && (port & 0x0002) == 0 && spectrumModel == MachineTypes.SPECTRUM48K) {
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
            if (specSettings.isMultifaceEnabled()) {
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
                if ((portFE & 0x07) != (value & 0x07) && screenGeometry.border().left() > 0) {
                    updateBorder(clock.getTstates());
                    borderUpdated = true;
//                if (z80.tEstados > spectrumModel.lastBorderUpdate)
//                    System.out.println(String.format("Frame: %d tstates: %d border: %d",
//                        nFrame, z80.tEstados, value & 0x07));
                }

                if (!tape.isTapePlaying()) {
                    int spkMic = sp_volt[value >> 3 & 3];
                    if (isSoundEnabled && spkMic != speaker) {
                        audio.updateAudio(clock.getTstates(), speaker);
                        speaker = spkMic;
                    }

                    if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
                        tape.setEarBit((value & issueMask) != 0);
//                    System.out.println(String.format("setEarBit: %b", (value & issueMask) == 0));
                    }
                }

                if (tape.isTapeRecording() && ((portFE ^ value) & 0x08) != 0) {
                    tape.recordPulse((portFE & 0x08) != 0);
                }
                //System.out.println(String.format("outPort: %04X %02x", port, value));
                portFE = value;
            }

//
////                if (((port1ffd ^ value) & 0x08) != 0) {
////                    System.out.println(String.format("Motor %b", (value & 0x08) != 0));
////                }
//
//                    port1ffd = value;

            if (enabledAY && (port & 0x8002) == 0x8000) {
                if ((port & 0x4000) != 0) {
                    ay8912.setAddressLatch(value);
                } else {
                    if (isSoundEnabled && ay8912.getAddressLatch() < 14) {
                        ay8912.updateAY(clock.getTstates());
                    }
                    ay8912.writeRegister(value);
                }
            }

            if (enabledAY && joystickModel == JoystickModel.FULLER
                && spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
//                System.out.println(String.format("OutPort: %04X [%02X]", port, value));
                if ((port & 0xff) == 0x3f) {
                    ay8912.setAddressLatch(value);
                    return;
                }

                if ((port & 0xff) == 0x5f) {
                    if (isSoundEnabled && ay8912.getAddressLatch() < 14) {
                        ay8912.updateAY(clock.getTstates());
                    }
                    ay8912.writeRegister(value);
                    return;
                }
            }

            // ULAplus ports
            if (specSettings.isULAplus() && (port & 0x0004) == 0) {
                // Control port (write only)
                if ((port & 0x4000) == 0) {
                    if ((value & 0x40) != 0) {
                        paletteGroup = 0x40;
                    } else {
                        paletteGroup = value & 0x3f;
                        invalidateScreen(true);
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
                        if (paletteGroup > 7 && paletteGroup < 16 && screenGeometry.border().left() > 0) {
                            borderUpdated = true;
                            updateBorder(clock.getTstates());
                        }
                        ULAPlusPalette[paletteGroup >>> 4][paletteGroup & 0x0f] = value;
                        precompULAplusColor(paletteGroup, value);
                    }
                }
            }

            // Enable/disable kempston mouse turbo (port #3EDF)
            if (joystickModel == JoystickModel.KEMPSTON && (port & 0x85FF) == 0x04DF) {
                kmouseEnabled = (value & 0x80) == 0;
            }
        } finally {
            postIO(port);

            // Any change on paging is latched and relased when IORQ goes to HIGH again
            // That's sure for 128k/+2.
            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM128K) {
                if ((port & 0x8002) == 0) {
//            System.out.println(String.format("outPort: %04X %02x at %d t-states",
//            port, value, z80.tEstados));
                    // Si ha cambiado la pantalla visible hay que invalidar
                    // Se invalida también el borde, o la MDA_DEMO no va bien.
                    if (((port7ffd ^ value) & 0x08) != 0) {
                        updateScreen(clock.getTstates());
                        invalidateScreen(true);
                    }
//                    System.out.printf("%d, %d, %d, %d\n", nextEvent, clock.getTstates(), nextEvent - clock.getTstates(),
//                            delayTstates[clock.getTstates()]);

                    memory.setPort7ffd(value);
                    // En el 128k las páginas impares son contended
                    contendedRamPage[3] = contendedIOPage[3] = (value & 0x01) != 0;
                    port7ffd = value;
                }
            }

            // For +2a/+3 the change instant isn't know exactly.
            if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                // Port 0x7ffd decodes with bit15 and bit1 reset, bit14 set
                if ((port & 0xC002) == 0x4000) {
                    // Si ha cambiado la pantalla visible hay que invalidar
                    // Se invalida también el borde
                    if (((port7ffd ^ value) & 0x08) != 0) {
                        updateScreen(clock.getTstates());
                        invalidateScreen(true);
                    }
//                    System.out.printf("%d, %d, %d, %d\n", nextEvent, clock.getTstates(), nextEvent - clock.getTstates(),
//                            delayTstates[clock.getTstates()]);

                    memory.setPort7ffd(value);
                    // En el +3 las páginas 4 a 7 son contended
                    contendedRamPage[3] = memory.getPlus3HighPage() > 3;
                    port7ffd = value;
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
                }
            }
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
            clock.addTstates(delayTstates[clock.getTstates()] + 1);
            if (clock.getTstates() >= nextEvent) {
                updateScreen(clock.getTstates());
            }
        } else {
            clock.addTstates(1);
        }
    }

    private void postIO(int port) {

        if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
            clock.addTstates(3);
            return;
        }

        if (specSettings.isULAplus() && (port & 0x0004) == 0) {
            clock.addTstates(delayTstates[clock.getTstates()] + 3);
            if (clock.getTstates() >= nextEvent) {
                updateScreen(clock.getTstates());
            }
            return;
        }

        if ((port & 0x0001) != 0) {
            if (contendedIOPage[port >>> 14]) {
                // A0 == 1 y es contended IO
                clock.addTstates(delayTstates[clock.getTstates()] + 1);
                if (clock.getTstates() >= nextEvent) {
                    updateScreen(clock.getTstates());
                }
                clock.addTstates(delayTstates[clock.getTstates()] + 1);
                if (clock.getTstates() >= nextEvent) {
                    updateScreen(clock.getTstates());
                }
                clock.addTstates(delayTstates[clock.getTstates()] + 1);
                if (clock.getTstates() >= nextEvent) {
                    updateScreen(clock.getTstates());
                }
            } else {
                // A0 == 1 y no es contended IO
                clock.addTstates(3);
            }
        } else {
            // A0 == 0
            clock.addTstates(delayTstates[clock.getTstates()] + 3);
            if (clock.getTstates() >= nextEvent) {
                updateScreen(clock.getTstates());
            }
        }
    }

    @Override
    public void interruptHandlingTime(int tstates) {
        clock.addTstates(tstates);
    }

    @Override
    public boolean isActiveINT() {
        int tmp = clock.getTstates();

        if (tmp >= spectrumModel.tstatesFrame)
            tmp -= spectrumModel.tstatesFrame;

        return tmp >= 0 && tmp < spectrumModel.lengthINT;
    }

    @Override
    public void execDone() { }

    @Override
    public int breakpoint(int address, int opcode) {

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
                if (specSettings.isMultifaceEnabled() && !memory.isPlus3RamMode()) {
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
                        tape.play(false);
                    }
                }
                break;
        }

        return opcode;
    }

//    private void updateJoysticks() {
//
//        switch (joystickModel) {
//            case KEMPSTON:
//                if (kmouseEnabled) {
//                    // Kempston Mouse Turbo Master button port
//                    // D4-D7 4-bit wheel counter
//                    short waxis = joystick1.getAxisValue(3);
//                    if (waxis > 0) {
//                        kmouseW -= 16;
//                    }
//                    if (waxis < 0) {
//                        kmouseW += 16;
//                    }
//
//                    // Kempston Mouse Turbo Master X-AXIS
//                    short xaxis = joystick1.getAxisValue(0);
//                    if (xaxis != 0) {
//                        kmouseX = (kmouseX + xaxis / 5) & 0xff;
//                    }
//
//                    // Kempston Mouse Turbo Master Y-AXIS
//                    short yaxis = joystick1.getAxisValue(1);
//                    if (yaxis != 0) {
//                        kmouseY = (kmouseY - yaxis / 5) & 0xff;
//                    }
//                }
//                // No break sentence, that's correct...
////            case SINCLAIR1:
////            case SINCLAIR2:
////                if (joystick2 != null) {
////                    joystick2.poll();
////                }
////                break;
//        }
//    }

    public void saveImage(File filename) {

        if (filename.getName().toLowerCase().endsWith(".scr")) {
            try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filename))) {

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
            } catch (final IOException ioException) {
                log.error("Error in saveImage", ioException);
            }
            return;
        }

        if (filename.getName().toLowerCase().endsWith(".png")) {
            try {
                ImageIO.write(tvImage, "png", filename);
            } catch (final IOException ioExcpt) {
                log.error("", ioExcpt);
            }
        }
    }

    public boolean loadScreen(File filename) {

        if (filename.getName().toLowerCase().endsWith(".scr")) {
            try  (BufferedInputStream fIn = new BufferedInputStream(new FileInputStream(filename))) {

                // Needs to be a screen file (size == 6912) or a
                // ULAplus screen file (size == 6912 + 64)
                if (fIn.available() != 6912 && fIn.available() != 6976) {
                    return false;
                }

                for (int addr = 0x4000; addr < 0x5b00; addr++) {
                    memory.writeByte(addr, (byte)(fIn.read() & 0xff));
                }

                ULAPlusActive = false;
                specSettings.setULAplus(false);
                if (fIn.available() == 64) {
                    ULAPlusActive = true;
                    specSettings.setULAplus(true);
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
            } catch (final FileNotFoundException excpt) {
                log.error("Error in loadScreen", excpt);
            } catch (final IOException ioExcpt) {
                log.error("Error in loadScreen", ioExcpt);
            }
        }
        return false;
    }

    static final int SPEAKER_VOLUME = -32700; // 6300;
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
        if (paused || muted || isSoundEnabled || framesByInt > 1) {
            return;
        }

        audio.open(spectrumModel, ay8912, enabledAY, settings.getSpectrumSettings().isHifiSound() ? 48000 : 32000);

        if (!paused && (taskFrame != null)) {
            taskFrame.cancel();
            taskFrame = null;
        }

        isSoundEnabled = true;
    }

    private void disableSound() {
        if (isSoundEnabled) {
            isSoundEnabled = false;
            audio.endFrame();
            audio.close();
            if (!paused) {
                taskFrame = new SpectrumTimer(this);
                timerFrame.scheduleAtFixedRate(taskFrame, 10, 20);
            }
        }
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
        sp_volt[1] = (int) (SPEAKER_VOLUME * 0.04f); // (int) -(SPEAKER_VOLUME * 1.4);
        sp_volt[2] = (int) (SPEAKER_VOLUME * 0.96f);
        sp_volt[3] = SPEAKER_VOLUME;
    }

    /* Sección gráfica */
    // Vector con los valores correspondientes a lo colores anteriores
    // https://www.worldofspectrum.org/forums/discussion/55858/new-colour-palette-for-16k-48k-machines-based-on-ula-yuv-voltages
    public static final int[] Paleta48k = {
        0x060800, /* negro */
        0x0d13a7, /* azul */
        0xbd0707, /* rojo */
        0xc312af, /* magenta */
        0x07ba0c, /* verde */
        0x0dc6b4, /* cyan */
        0xbcb914, /* amarillo */
        0xc2c4bc, /* blanco */
        0x060800, /* negro "brillante" */
        0x161cb0, /* azul brillante */
        0xce1818, /* rojo brillante	*/
        0xdc2cc8, /* magenta brillante */
        0x28dc2d, /* verde brillante */
        0x36efde, /* cyan brillante */
        0xeeeb46, /* amarillo brillante */
        0xfdfff7  /* blanco brillante */
    };

    public static final String[] ColourName48k = {
            "black",
            "blue",
            "red",
            "magenta",
            "green",
            "cyan",
            "yellow",
            "white",
            "black",
            "bright blue",
            "bright red",
            "bright magenta",
            "bright green",
            "bright cyan",
            "bright yellow",
            "bright white"
    };

    public static final int[] Paleta128k = {
        0x000000, /* negro */
        0x0000c0, /* azul */
        0xc00000, /* rojo */
        0xc000c0, /* magenta */
        0x00c000, /* verde */
        0x00c0c0, /* cyan */
        0xc0c000, /* amarillo */
        0xc0c0c0, /* blanco */
        0x000000, /* negro "brillante" */
        0x0000ff, /* azul brillante */
        0xff0000, /* rojo brillante	*/
        0xff00ff, /* magenta brillante */
        0x00ff00, /* verde brillante */
        0x00ffff, /* cyan brillante */
        0xffff00, /* amarillo brillante */
        0xffffff  /* blanco brillante */
    };

    public static int[] Paleta = Paleta48k;
    // Tablas de valores de Paper/Ink. Para cada valor general de atributo,
    // corresponde una entrada en la tabla que hace referencia al color
    // en la paleta. Para los valores superiores a 127, los valores de Paper/Ink
    // ya están cambiados, lo que facilita el tratamiento del FLASH.
    private static final int Paper[] = new int[256];
    private static final int Ink[] = new int[256];
    // Tabla de correspondencia entre la dirección de pantalla y su atributo - 0x4000
    public final int scr2attr[] = new int[6144];
    // Tabla de correspondencia entre cada atributo y el primer byte del carácter
    // en la pantalla del Spectrum (la contraria de la anterior)
    private final int attr2scr[] = new int[768];
    // Tabla de correspondencia entre la dirección de pantalla del Spectrum
    // y la dirección que le corresponde en el BufferedImage.
    private final int bufAddr[] = new int[6144];
    // Tabla para hacer más rápido el redibujado. Para cada dirección de memoria del 
    // Spectrum, almacena la línea de scan que le corresponde en la pantalla (0-191)
    private final int scanLineTable[] = new int[6144];
    // Tabla que contiene la dirección de pantalla del primer byte de cada
    // carácter en la columna cero.
    private final int scrAddr[] = new int[192];
    // Tabla que indica si un byte de la pantalla ha sido modificado y habrá que
    // redibujarlo.
    private final boolean dirtyByte[] = new boolean[6144];
    // Tabla de traslación entre t-states y la dirección de la pantalla del
    // Spectrum que se vuelca en ese t-state o -1 si no le corresponde ninguna.
    private final int states2scr[] = new int[MachineTypes.SPECTRUM128K.tstatesFrame + 100];
    // Tabla de traslación de t-states al pixel correspondiente del borde.
    private final int states2border[] = new int[MachineTypes.SPECTRUM128K.tstatesFrame + 100];
    // Secuencia de t-states que hay que ejecutar para ir actualizando la pantalla
    int stepStates[] = new int[6144];
    private int step;
    // Constante que indica que no hay un evento próximo
    // El valor de la constante debe ser mayor que cualquier spectrumModel.tstatesframe
    private final int NO_EVENT = 0x1234567;
    // t-states del próximo evento
    private int nextEvent = NO_EVENT;

    // Estos miembros solo cambian cuando cambia el tamaño del borde
    private ScreenGeometry screenGeometry = ScreenGeometry.aScreenGeometry()
            .withStandardBorder()
            .build();

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
    // Primera y última línea a ser actualizada
    private int firstScanLine, lastScanLine;
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

    private final Rectangle screenRect = new Rectangle();
    private int firstBorderPix, lastBorderPix;
    private final Rectangle borderRect = new Rectangle();

    private void initGFX() {
        tvImage = new BufferedImage(screenGeometry.width(), screenGeometry.height(), BufferedImage.TYPE_INT_RGB);
        gcTvImage = tvImage.createGraphics();
        inProgressImage = new BufferedImage(screenGeometry.width(), screenGeometry.height(), BufferedImage.TYPE_INT_RGB);
        dataInProgress = ((DataBufferInt) inProgressImage.getRaster().getDataBuffer()).getBankData()[0];

        lastChgBorder = 0;
        Arrays.fill(dirtyByte, true);
        screenDirty = false;
        borderChanged = screenGeometry.border().left() > 0;

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

            scanLineTable[address & 0x1fff] = (row * 2048 + scan * 256 + col * 8) >>> 8;
            bufAddr[address & 0x1fff] = row * screenGeometry.width() * 8 + (scan + screenGeometry.border().top()) * screenGeometry.width()
                + col * 8 + screenGeometry.border().left();
            scr2attr[address & 0x1fff] = 0x1800 + row * 32 + col;
        }

        for (int address = 0x5800; address < 0x5B00; address++) {
            attr2scr[address & 0x3ff] = 0x4000 | ((address & 0x300) << 3) | (address & 0xff);
        }
    }

    public BufferedImage getTvImage() {
        return tvImage;
    }

    public void setBorderMode(int mode) {
        if (borderMode == mode) {
            return;
        }

        borderMode = mode;

        screenGeometry = switch (mode) {
            case 0 -> ScreenGeometry.aScreenGeometry().withNoBorder().build();         // no border
            case 2 -> ScreenGeometry.aScreenGeometry().withFullBorder().build();       // Full standard border
            case 3 -> ScreenGeometry.aScreenGeometry().withHugeBorder().build();       // Huge border
            default -> ScreenGeometry.aScreenGeometry().withStandardBorder().build();  // Standard border
        };

        tvImage = new BufferedImage(screenGeometry.width(), screenGeometry.height(), BufferedImage.TYPE_INT_RGB);
        if (gcTvImage != null) {
            gcTvImage.dispose();
        }
        gcTvImage = tvImage.createGraphics();

        inProgressImage = new BufferedImage(screenGeometry.width(), screenGeometry.height(), BufferedImage.TYPE_INT_RGB);
        dataInProgress = ((DataBufferInt) inProgressImage.getRaster().getDataBuffer()).getBankData()[0];

        for (int address = 0x4000; address < 0x5800; address++) {
            int row = ((address & 0xe0) >>> 5) | ((address & 0x1800) >>> 8);
            int col = address & 0x1f;
            int scan = (address & 0x700) >>> 8;

            bufAddr[address & 0x1fff] = row * screenGeometry.width() * 8 + (scan + screenGeometry.border().top()) * screenGeometry.width()
                + col * 8 + screenGeometry.border().left();
        }

        switch (spectrumModel.codeModel) {
            case SPECTRUM128K -> buildScreenTables128k();
            case SPECTRUMPLUS3 -> buildScreenTablesPlus3();
            default -> buildScreenTables48k();
        }
    }

    public void toggleFlash() {
        flash ^= 0x80;

        // 0x1800 + 0x4000 = 0x5800 (attr area)
        for (int addrAttr = 0x1800; addrAttr < 0x1b00; addrAttr++) {
            if (memory.readScreenByte(addrAttr) < 0) {
                notifyScreenWrite(addrAttr);
            }
        }
    }

    /*
     * Cada línea completa de imagen dura 224 T-Estados, divididos en:
     * 128 T-Estados en los que se dibujan los 256 píxeles de pantalla
     * 24 T-Estados en los que se dibujan los 48 píxeles del borde derecho
     * 48 T-Estados iniciales de H-Sync y blanking
     * 24 T-Estados en los que se dibujan 48 píxeles del borde izquierdo
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
        if (row < (64 - screenGeometry.border().top() - 1) || row > (256 + screenGeometry.border().bottom() - 1)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la primera línea
        if (row == (64 - screenGeometry.border().top() - 1) && col < 200 + (24 - screenGeometry.border().left() / 2)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la última línea
        if (row == (256 + screenGeometry.border().bottom() - 1) && col > (127 + screenGeometry.border().right() / 2)) {
            return 0xf0cab0ba;
        }

        // Quitamos la parte del borde derecho que no se ve, la zona de H-Sync
        // y la parte izquierda del borde que tampoco se ve
        if (col > (127 + screenGeometry.border().right() / 2) && col < 200 + (24 - screenGeometry.border().left() / 2)) {
            return 0xf0cab0ba;
        }

        // Quitamos la parte correspondiente a SCREEN$
        if (row > 63 && row < 256 && col < 128) {
            return 0xf0cab0ba;
        }

        // 176 t-estados de línea es en medio de la zona de retrazo
        if (col > 176) {
            row++;
            col -= 200 + (24 - screenGeometry.border().left() / 2);
        } else {
            col += screenGeometry.border().right() / 2 - (screenGeometry.border().right() - screenGeometry.border().left()) / 2;
        }

        row -= (64 - screenGeometry.border().top());

        return row * screenGeometry.width() + col * 2;
    }

    private int tStatesToScrPix128k(int tstates) {
        tstates %= spectrumModel.tstatesFrame;

        int row = tstates / spectrumModel.tstatesLine;
        int col = tstates % spectrumModel.tstatesLine;

        // Quitamos las líneas que no se ven por arriba y por abajo
        if (row < (63 - screenGeometry.border().top() - 1) || row > (255 + screenGeometry.border().bottom() - 1)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la primera línea
        if (row == (63 - screenGeometry.border().top() - 1) && col < 204 + (24 - screenGeometry.border().left() / 2)) {
            return 0xf0cab0ba;
        }

        // Caso especial de la última línea
        if (row == (255 + screenGeometry.border().bottom() - 1) && col > (127 + screenGeometry.border().right() / 2)) {
            return 0xf0cab0ba;
        }

        // Quitamos la parte del borde derecho que no se ve, la zona de H-Sync
        // y la parte izquierda del borde que tampoco se ve
        if (col > (127 + screenGeometry.border().right() / 2) && col < 204 + (24 - screenGeometry.border().left() / 2)) {
            return 0xf0cab0ba;
        }

        // Quitamos la parte correspondiente a SCREEN$
        if (row > 62 && row < 255 && col < 128) {
            return 0xf0cab0ba;
        }

        // 176 t-estados de línea es en medio de la zona de retrazo
        if (col > 176) {
            row++;
            col -= 204 + (24 - screenGeometry.border().left() / 2);
        } else {
            col += screenGeometry.border().right() / 2 - (screenGeometry.border().right() - screenGeometry.border().left()) / 2;
        }

        row -= (63 - screenGeometry.border().top());

        return row * screenGeometry.width() + col * 2;
    }

    private void updateBorder(int tstates) {

        log.debug("In @1 updateBorder: lastChgBorder = {}, tstates = {}, portFE = {} ({})",
                lastChgBorder, tstates, portFE & 0x07, ColourName48k[portFE & 0x07]);

        if (tstates < lastChgBorder || lastChgBorder > lastBorderUpdate) {
            return;
        }

        log.debug("In @2 updateBorder: lastChgBorder = {}, tstates = {}, portFE = {} ({})",
                lastChgBorder, tstates, portFE & 0x07, ColourName48k[portFE & 0x07]);

        int nowColor;
        if (ULAPlusActive) {
            nowColor = ULAPlusPrecompPalette[0][(portFE & 0x07) | 0x08];
        } else {
            nowColor = Paleta[portFE & 0x07];
        }

        tstates += spectrumModel.outBorderOffset;
        tstates &= 0x00fffffc;
        while (lastChgBorder < tstates && lastChgBorder < lastBorderUpdate) {
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
            if (firstBorderPix > idxColor) {
                firstBorderPix = idxColor;
            }
            lastBorderPix = idxColor;
        }

        lastChgBorder = tstates;
    }

    public void updateScreen(int tstates) {
        int fromAddr, addrBuf;
        int paper, ink;
        byte scrByte;
        int attr;
//        System.out.println(String.format("from: %d\tto: %d", lastScreenState, toTstates));

        while (step < stepStates.length && stepStates[step] <= tstates) {
            fromAddr = states2scr[stepStates[step++]] & 0x1fff;

            if (!dirtyByte[fromAddr]) {
                continue;
            }

            int scan = scanLineTable[fromAddr];
            if (firstScanLine > scan) {
                firstScanLine = scan;
            }

            if (lastScanLine < scan) {
                lastScanLine = scan;
            }

            int column = fromAddr & 0x1f;

            if (column < leftCol) {
                leftCol = column;
            }
            if (column > rightCol) {
                rightCol = column;
            }

            scrByte = memory.readScreenByte(fromAddr);
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

        nextEvent = step < stepStates.length ? stepStates[step] : NO_EVENT;
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
        borderChanged = screenGeometry.border().left() > 0 && invalidateBorder;
        Arrays.fill(dirtyByte, true);
    }

    private void buildScreenTables48k() {
        int col, scan;

        firstBorderUpdate = ((64 - screenGeometry.border().top()) * spectrumModel.tstatesLine) - screenGeometry.border().left() / 2;
        lastBorderUpdate = (255 + screenGeometry.border().bottom()) * spectrumModel.tstatesLine + 128 + screenGeometry.border().right();

        Arrays.fill(states2scr, 0);

        step = 0;
        for (int tstates = spectrumModel.firstScrByte; tstates < 57248; tstates += 4) {
            col = (tstates % spectrumModel.tstatesLine) / 4;

            if (col > 31) {
                continue;
            }
            scan = tstates / spectrumModel.tstatesLine - spectrumModel.upBorderWidth;
            states2scr[tstates + 2] = scrAddr[scan] + col;
            stepStates[step++] = tstates + 2;
        }

        Arrays.fill(states2border, 0xf0cab0ba);

//        System.out.println(String.format("48k - > firstBU: %d, lastBU: %d",
//                spectrumModel.firstBorderUpdate, spectrumModel.lastBorderUpdate));
        for (int tstates = firstBorderUpdate;
                tstates < lastBorderUpdate; tstates += 4) {
            states2border[tstates] = tStatesToScrPix48k(tstates);
            states2border[tstates + 1] = states2border[tstates];
            states2border[tstates + 2] = states2border[tstates];
            states2border[tstates + 3] = states2border[tstates];
        }

        Arrays.fill(delayTstates, (byte) 0x00);

        for (int idx = 14335; idx < 57247; idx += spectrumModel.tstatesLine) {
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

        firstBorderUpdate = ((63 - screenGeometry.border().top()) * spectrumModel.tstatesLine) - screenGeometry.border().right() / 2;
        lastBorderUpdate = (254 + screenGeometry.border().bottom()) * spectrumModel.tstatesLine + 128 + screenGeometry.border().right();

        Arrays.fill(states2scr, 0);

        step = 0;
        for (int tstates = 14364; tstates < spectrumModel.lastScrUpdate; tstates += 4) {
            col = (tstates % spectrumModel.tstatesLine) / 4;

            if (col > 31) {
                continue;
            }

            scan = tstates / spectrumModel.tstatesLine - spectrumModel.upBorderWidth;
            states2scr[tstates] = scrAddr[scan] + col;
            stepStates[step++] = tstates;
        }

        Arrays.fill(states2border, 0xf0cab0ba);

//        System.out.println(String.format("128k - > firstBU: %d, lastBU: %d",
//                spectrumModel.firstBorderUpdate, spectrumModel.lastBorderUpdate));
        for (int tstates = firstBorderUpdate;
                tstates < lastBorderUpdate; tstates += 4) {
            states2border[tstates] = tStatesToScrPix128k(tstates);
            states2border[tstates + 1] = states2border[tstates];
            states2border[tstates + 2] = states2border[tstates];
            states2border[tstates + 3] = states2border[tstates];
        }

        Arrays.fill(delayTstates, (byte) 0x00);

        for (int idx = 14361; idx < 58037; idx += spectrumModel.tstatesLine) {
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

        firstBorderUpdate = ((63 - screenGeometry.border().top()) * spectrumModel.tstatesLine) - screenGeometry.border().right() / 2;
        lastBorderUpdate = (254 + screenGeometry.border().bottom()) * spectrumModel.tstatesLine  + 128 + screenGeometry.border().right();

        Arrays.fill(states2scr, 0);

        step = 0;
        for (int tstates = spectrumModel.firstScrByte; tstates < spectrumModel.lastScrUpdate; tstates += 4) {
            col = (tstates % spectrumModel.tstatesLine) / 4;

            if (col > 31) {
                continue;
            }

            scan = tstates / spectrumModel.tstatesLine - spectrumModel.upBorderWidth;
            states2scr[tstates + 2] = scrAddr[scan] + col;
            stepStates[step++] = tstates + 2;
        }

        Arrays.fill(states2border, 0xf0cab0ba);

//        System.out.println(String.format("+3 - > firstBU: %d, lastBU: %d",
//                spectrumModel.firstBorderUpdate, spectrumModel.lastBorderUpdate));
        for (int tstates = firstBorderUpdate;
                tstates < lastBorderUpdate; tstates += 4) {
            states2border[tstates] = tStatesToScrPix128k(tstates);
            states2border[tstates + 1] = states2border[tstates];
            states2border[tstates + 2] = states2border[tstates];
            states2border[tstates + 3] = states2border[tstates];
        }

        // Diga lo que diga la FAQ de WoS, los estados de espera comienzan
        // en 14361 y no en 14365. El programa TSTP3 de Pedro Gimeno lo
        // confirma. Gracias Pedro!.
        Arrays.fill(delayTstates, (byte) 0x00);

        for (int idx = 14361; idx < 58036; idx += spectrumModel.tstatesLine) {
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

    public boolean startRecording() {
        return tape.startRecording();
    }

    public boolean stopRecording() {
        if (!tape.isTapeRecording()) {
            return false;
        }

        tape.recordPulse((portFE & 0x08) != 0);
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
        memory.setPortFD(value);
    }

    private void unpageLec() {
        memory.setPortFD(0);
        contendedRamPage[1] = contendedIOPage[1] = true;
        z80.setBreakpoint(0x0066, specSettings.isMultifaceEnabled());
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

    private class TapeChangedListener implements TapeStateListener, ClockTimeoutListener {

        boolean listenerInstalled = false;

        @Override
        public void stateChanged(final TapeState state) {

            log.trace("Tape state changed to " + state);

            switch (state) {
                case PLAY:
                    if (!paused) {
                        if (settings.getTapeSettings().isAccelerateLoading()) {
                            acceleratedLoading = true;
                        }
                        else {
                            if (specSettings.isLoadingNoise() && isSoundEnabled) {
                                listenerInstalled = true;
                                clock.addClockTimeoutListener(this);
                            }
                        }
                    }
                    break;
                case STOP:
                    if (listenerInstalled) {
                        clock.removeClockTimeoutListener(this);
                        listenerInstalled = false;
                    }
                    break;
                default:
                    // do nothing
            }
        }

        @Override
        public void clockTimeout() {

            if (isSoundEnabled) {
                int spkMic = (tape.getEarBit() == 0xbf) ? -8000 : 8000;
                if (spkMic != speaker) {
                    audio.updateAudio(clock.getTstates(), speaker);
                    speaker = spkMic;
                }
            }
        }

    }

}
