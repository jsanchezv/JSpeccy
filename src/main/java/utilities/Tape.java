/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 *
 * 26/02/2010 Nota al bloque Turbo Mode del formato TZX: algunos programas
 * necesitan empezar con una polaridad concreta. Dos ejemplos son "MASK" y
 * "Basil the Great Mouse Detective". Al resto de programas que he probado eso
 * les da igual, excepto a los juegos de The Edge con su propia protección,
 * como "Starbike", "Brian Bloodaxe" y "That's the Spirit" que no cargan justamente
 * por eso. Debería ser algo seleccionado por el usuario.
 *
 * 21/01/2011 El problema del "Basil, the Great Mouse Detective" y del "MASK"
 * ha sido solucionado en el tratamiento de los Turbo Data Block sin afectar a
 * los juegos de The Edge. El único que sigue dando problemillas para cargar
 * es el "Lone Wolf 3", pero da la impresión de que la rutina de carga reiniciable
 * que usa es muy sensible y suele dar problemas en todos los emuladores que he
 * probado. Con un poco de paciencia, acaba por cargar. :)
 *
 * 08/04/2012 Añadido el soporte de bloques CALL y RETURN en los TZX. Solo hay un
 * programa que lo necesita (que yo sepa), el Hollywood Poker que está en tzxvault.
 *
 */
package utilities;

import configuration.TapeSettingsType;
import machine.SpectrumClock;
import machine.MachineTypes;
import machine.Memory;
import z80core.Z80;

import javax.swing.table.AbstractTableModel;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author jsanchez
 */
@Slf4j
public class Tape implements machine.ClockTimeoutListener {

    private Z80 cpu;
    private ByteArrayOutputStream record;
    private DeflaterOutputStream dos;
    private ByteArrayInputStream bais;
    private InflaterInputStream iis;
    private File filename;
    private byte tapeBuffer[];
    private final int offsetBlocks[] = new int[4096]; // el AMC tiene más de 1500 bloques!!!
    private int nOffsetBlocks;
    private int idxHeader;
    private int tapePos;
    private int blockLen;
    private int mask;
    private int bitTime;
    private byte byteTmp;
    private int cswPulses;
    private final SpectrumClock clock;

    public enum TapeState {

        EJECT, INSERT, STOP, PLAY, RECORD
    }

    private final ArrayList<TapeStateListener> stateListeners;
    private final ArrayList<TapeBlockListener> blockListeners;

    private enum State {

        STOP, START, LEADER, LEADER_NOCHG, SYNC, NEWBYTE,
        NEWBYTE_NOCHG, NEWBIT, HALF2, LAST_PULSE, PAUSE, TZX_HEADER, PURE_TONE,
        PURE_TONE_NOCHG, PULSE_SEQUENCE, PULSE_SEQUENCE_NOCHG, NEWDR_BYTE,
        NEWDR_BIT, PAUSE_STOP, CSW_RLE, CSW_ZRLE
    }

    private State statePlay;
    private int earBit;
    private boolean micBit;
    private static final int EAR_OFF = 0xbf;
    private static final int EAR_ON = 0xff;
    private static final int EAR_MASK = 0x40;
    private long timeLastOut;
    private boolean tapePlaying, tapeRecording;
    private enum TapeExtensionType {
        NO_TAPE, TAP, TZX, CSW
    }

    private TapeExtensionType tapeExtension;
    /*
     * Tiempos en T-estados de duración de cada pulso para cada parte de la
     * carga
     */
    private final int LEADER_LENGHT = 2168;
    private final int SYNC1_LENGHT = 667;
    private final int SYNC2_LENGHT = 735;
    private final int ZERO_LENGHT = 855;
    private final int ONE_LENGHT = 1710;
    private final int HEADER_PULSES = 8063;
    private final int DATA_PULSES = 3223;
    private final int END_BLOCK_PAUSE = 3_500_000;
    // Variables para los tiempos de los ficheros TZX
    private int leaderLenght;
    private int leaderPulses;
    private int sync1Lenght;
    private int sync2Lenght;
    private int zeroLenght;
    private int oneLenght;
    private int bitsLastByte;
    private int endBlockPause;
    private int nLoops;
    private int loopStart;
    private int freqSample;
    private float cswStatesSample;
    private MachineTypes spectrumModel;
    private final TapeTableModel tapeTableModel;
    private final TapeSettingsType settings;
    // # of Calls & origin block of TZX CALL block 
    private int nCalls, callBlk;
    // Call sequence for TZX CALL block
    private short[] callSeq;
    // vars for GDB's
    private int totp, npp, asp, totd, npd, asd, ptrSymbol, ptrDataStream, numPulses, nTotp;
    private static final String tzxHeader = "ZXTape!\u001A";
    private static final String tzxCreator = "TZX created with JSpeccy v0.95";
    private boolean manualMode = false;

    public Tape(TapeSettingsType tapeSettings) {
        blockListeners = new ArrayList<>();
        stateListeners = new ArrayList<>();
        clock = SpectrumClock.INSTANCE;
        settings = tapeSettings;
        statePlay = State.STOP;
        tapePlaying = tapeRecording = false;
        tapeExtension = TapeExtensionType.NO_TAPE;
        tapePos = 0;
        earBit = EAR_ON;
        spectrumModel = MachineTypes.SPECTRUM48K;
        nOffsetBlocks = 0;
        idxHeader = 0;
        Arrays.fill(offsetBlocks, 0);
        tapeTableModel = new TapeTableModel();
    }

    /**
     * Adds a new event listener to the list of event listeners.
     *
     * @param listener The new event listener.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     */
    public void addTapeChangedListener(final TapeStateListener listener) {

        Objects.requireNonNull(listener, "Internal error: tape change listener can't be null");

        // Avoid duplicates
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
    }

    /**
     * Remove a new event listener from the list of event listeners.
     *
     * @param listener The event listener to remove.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     * @throws IllegalArgumentException Thrown if the listener wasn't
     * registered.
     */
    public void removeTapeChangedListener(final TapeStateListener listener) {

        Objects.requireNonNull(listener, "Internal error: tape change listener can't be null");

        if (!stateListeners.remove(listener)) {
            throw new IllegalArgumentException("Internal error: Listener was not listening on object");
        }
    }

    private void fireTapeStateChanged(final TapeState state) {

        stateListeners.forEach(listener -> listener.stateChanged(state));
    }

    /**
     * Adds a new event listener to the list of event listeners.
     *
     * @param listener The new event listener.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     */
    public void addTapeBlockListener(final TapeBlockListener listener) {

        Objects.requireNonNull(listener, "Internal error: tape block listener can't be null");

        // Avoid duplicates
        if (!blockListeners.contains(listener)) {
            blockListeners.add(listener);
        }
    }

    /**
     * Remove a new event listener from the list of event listeners.
     *
     * @param listener The event listener to remove.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     * @throws IllegalArgumentException Thrown if the listener wasn't
     * registered.
     */
    public void removeTapeBlockListener(final TapeBlockListener listener) {

        Objects.requireNonNull(listener, "Internal error: tape block listener can't be null");

        if (!blockListeners.remove(listener)) {
            throw new IllegalArgumentException("Internal error: tape block listener was not listening on object");
        }
    }

    private void fireTapeBlockChanged(final int block) {

        blockListeners.forEach(listener -> listener.blockChanged(block));
    }

    public void setSpectrumModel(MachineTypes model) {

        spectrumModel = model;
    }

    public void setZ80Cpu(Z80 z80) {
        cpu = z80;
    }

    private int getNumBlocks() {
        return tapeExtension == TapeExtensionType.NO_TAPE ? 1 : nOffsetBlocks + 1;
    }

    public int getSelectedBlock() {
        return idxHeader;
    }

    public void setSelectedBlock(int block) {
        if (tapeExtension == TapeExtensionType.NO_TAPE || isTapePlaying() || block > nOffsetBlocks) {
            return;
        }

        idxHeader = block;
        fireTapeBlockChanged(block);
    }

    private String getCleanMsg(int offset, int len) {
        byte[] msg = new byte[len];

        // Hay que quitar los caracteres especiales
        for (int car = 0; car < len; car++) {
            if ((tapeBuffer[offset + car] & 0xff) > 31 && (tapeBuffer[offset + car] & 0xff) < 128) {
                msg[car] = tapeBuffer[offset + car];
            } else {
                msg[car] = '?'; // sustituir el carácter no imprimible
            }
        }

        return new String(msg);
    }

    private String getBlockType(int block) {
        java.util.ResourceBundle bundle =
                java.util.ResourceBundle.getBundle("utilities/Bundle"); // NOI18N
        if (tapeExtension == TapeExtensionType.NO_TAPE) {
            return bundle.getString("NO_TAPE_INSERTED");
        }

        if (block >= nOffsetBlocks) {
            return bundle.getString("END_OF_TAPE");
        }

        if (tapeExtension == TapeExtensionType.CSW) {
            return String.format(bundle.getString("CSW_DATA"),
                    tapeBuffer[0x17], tapeBuffer[0x18]); // CSW major.minor version
        }

        if (tapeExtension == TapeExtensionType.TAP) {
            return bundle.getString("STD_SPD_DATA");
        }

        int offset = offsetBlocks[block];

        return switch ((tapeBuffer[offset] & 0xff)) {
            case 0x10 -> // Standard speed data block
                    bundle.getString("STD_SPD_DATA");
            case 0x11 -> // Turbo speed data block
                    bundle.getString("TURBO_SPD_DATA");
            case 0x12 -> // Pure Tone Block
                    bundle.getString("PURE_TONE");
            case 0x13 -> // Pulse Sequence Block
                    bundle.getString("PULSE_SEQUENCE");
            case 0x14 -> // Pure Data Block
                    bundle.getString("PURE_DATA");
            case 0x15 -> // Direct Data Block
                    bundle.getString("DIRECT_DATA");
            case 0x18 -> // CSW Recording Block
                    bundle.getString("CSW_RECORDING");
            case 0x19 -> // Generalized Data Block
                    bundle.getString("GDB_DATA");
            case 0x20 -> // Pause (silence) or 'Stop the Tape' command
                    bundle.getString("PAUSE_STOP");
            case 0x21 -> // Group Start
                    bundle.getString("GROUP_START");
            case 0x22 -> // Group End
                    bundle.getString("GROUP_STOP");
            case 0x23 -> // Jump to Block
                    bundle.getString("JUMP_TO");
            case 0x24 -> // Loop Start
                    bundle.getString("LOOP_START");
            case 0x25 -> // Loop End
                    bundle.getString("LOOP_STOP");
            case 0x26 -> // Call Sequence
                    bundle.getString("CALL_SEQ");
            case 0x27 -> // Return from Sequence
                    bundle.getString("RETURN_SEQ");
            case 0x28 -> // Select Block
                    bundle.getString("SELECT_BLOCK");
            case 0x2A -> // Stop the tape if in 48K mode
                    bundle.getString("STOP_48K_MODE");
            case 0x2B -> // Set Signal Level
                    bundle.getString("SET_SIGNAL_LEVEL");
            case 0x30 -> // Text Description
                    bundle.getString("TEXT_DESC");
            case 0x31 -> // Message Block
                    bundle.getString("MESSAGE_BLOCK");
            case 0x32 -> // Archive Info
                    bundle.getString("ARCHIVE_INFO");
            case 0x33 -> // Hardware Type
                    bundle.getString("HARDWARE_TYPE");
            case 0x35 -> // Custom Info Block
                    bundle.getString("CUSTOM_INFO");
            case 'Z' -> // ZXTape!
//                msg = bundle.getString("GLUE_BLOCK");
                    "ZXTape!";
            default -> String.format(bundle.getString("UNKN_TZX_BLOCK"), tapeBuffer[offset]);
        };
    }

    private String getBlockInfo(int block) {
        java.util.ResourceBundle bundle =
                java.util.ResourceBundle.getBundle("utilities/Bundle"); // NOI18N

        if (tapeExtension == TapeExtensionType.NO_TAPE) {
            return bundle.getString("NO_TAPE_INSERTED");
        }

        if (block >= nOffsetBlocks) {
            return bundle.getString("END_OF_TAPE");
        }

        if (tapeExtension == TapeExtensionType.CSW) {
            if ((tapeBuffer[0x17] & 0xff) == 0x01) { // CSW v1.01
                return String.format(bundle.getString("CSW1_PULSES"),
                        readInt(tapeBuffer, 0x19, 2));
            } else { // CSW v2.0
                if ((tapeBuffer[0x21] & 0xff) == 0x02) { // Z-RLE encoding
                    return String.format(bundle.getString("CSW2_ZRLE_PULSES"),
                            readInt(tapeBuffer, 0x1D, 4), readInt(tapeBuffer, 0x19, 4));
                } else {
                    return String.format(bundle.getString("CSW2_RLE_PULSES"),
                            readInt(tapeBuffer, 0x1D, 4), readInt(tapeBuffer, 0x19, 4));
                }
            }
        }

        String msg;

        if (tapeExtension == TapeExtensionType.TAP) {
            int offset = offsetBlocks[block];
            int len = readInt(tapeBuffer, offset, 2);

            if ((tapeBuffer[offset + 2] & 0xff) == 0) { // Header
                msg = switch (tapeBuffer[offset + 3] & 0xff) {
                    // Program
                    case 0 -> String.format(bundle.getString("PROGRAM_HEADER"), getCleanMsg(offset + 4, 10));
                    // Number array
                    case 1 -> bundle.getString("NUMBER_ARRAY_HEADER");
                    case 2 -> bundle.getString("CHAR_ARRAY_HEADER");
                    case 3 -> String.format(bundle.getString("BYTES_HEADER"), getCleanMsg(offset + 4, 10));
                    default -> "";
                };
            } else {
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
            }
            return msg;
        }

        int offset = offsetBlocks[block];

        int len, num;
        switch (tapeBuffer[offset++] & 0xff) {
            case 0x10: // Standard speed data block
                len = readInt(tapeBuffer, offset + 2, 2);
                if (tapeBuffer[offset + 4] == 0) { // Header
                    switch (tapeBuffer[offset + 5] & 0xff) {
                        case 0: // Program
                            msg = String.format(bundle.getString("PROGRAM_HEADER"),
                                    getCleanMsg(offset + 6, 10));
                            break;
                        case 1: // Number array
                            msg = bundle.getString("NUMBER_ARRAY_HEADER");
                            break;
                        case 2: // Character array
                            msg = bundle.getString("CHAR_ARRAY_HEADER");
                            break;
                        case 3:
                            msg = String.format(bundle.getString("BYTES_HEADER"),
                                    getCleanMsg(offset + 6, 10));
                            break;
                        default:
                            msg = String.format(bundle.getString("UNKN_HEADER_ID"),
                                    tapeBuffer[offset + 5]);
                    }
                } else {
                    msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                }
                break;
            case 0x11: // Turbo speed data block
                len = readInt(tapeBuffer, offset + 15, 3);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x12: // Pure Tone Block
                len = readInt(tapeBuffer, offset, 2);
                num = readInt(tapeBuffer, offset + 2, 2);
                msg = String.format(bundle.getString("PURE_TONE_MESSAGE"), num, len);
                break;
            case 0x13: // Pulse Sequence Block
                len = tapeBuffer[offset] & 0xff;
                msg = String.format(bundle.getString("PULSE_SEQ_MESSAGE"), len);
                break;
            case 0x14: // Pure Data Block
                len = readInt(tapeBuffer, offset + 7, 3);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x15: // Direct Data Block
                len = readInt(tapeBuffer, offset + 5, 3);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x18: // CSW Recording Block
//                len = readInt(tapeBuffer, offset, 4);
                if ((tapeBuffer[offset + 0x09] & 0xff) == 0x02) { // Z-RLE encoding
                    msg = String.format(bundle.getString("CSW2_ZRLE_PULSES"),
                            readInt(tapeBuffer, offset + 0x0A, 4),
                            readInt(tapeBuffer, offset + 0x06, 3));
                } else {
                    msg = String.format(bundle.getString("CSW2_RLE_PULSES"),
                            readInt(tapeBuffer, offset + 0x0A, 4),
                            readInt(tapeBuffer, offset + 0x06, 3));
                }
//                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x19: // Generalized Data Block
                len = readInt(tapeBuffer, offset, 4);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x20: // Pause (silence) or 'Stop the Tape' command
                len = readInt(tapeBuffer, offset, 2);
                if (len == 0) {
                    msg = bundle.getString("STOP_THE_TAPE");
                } else {
                    msg = String.format(bundle.getString("PAUSE_MS"), len);
                }
                break;
            case 0x21: // Group Start
                len = tapeBuffer[offset] & 0xff;
                msg = getCleanMsg(offset + 1, len);
                break;
            case 0x22: // Group End
                msg = "";
                break;
            case 0x23: // Jump to Block
                msg = String.format(bundle.getString("NUMBER_OF_BLOCKS"),
                        tapeBuffer[offset]);
                break;
            case 0x24: // Loop Start
                len = readInt(tapeBuffer, offset, 2);
                msg = String.format(bundle.getString("NUMBER_OF_ITER"), len);
                break;
            case 0x25: // Loop End
                msg = "";
                break;
            case 0x26: // Call Sequence
                len = readInt(tapeBuffer, offset, 2);
                msg = String.format(bundle.getString("NUMBER_OF_CALLS"), len);
                break;
            case 0x27: // Return from Sequence
                msg = "";
                break;
            case 0x28: // Select Block
                len = tapeBuffer[offset + 2] & 0xff;
                msg = String.format(bundle.getString("NUMBER_OF_SELS"), len);
                break;
            case 0x2A: // Stop the tape if in 48K mode
                msg = "";
                break;
            case 0x2B: // Set Signal Level
                len = tapeBuffer[offset + 4] & 0xff;
                msg = String.format(bundle.getString("SIGNAL_TO_LEVEL"), len);
                break;
            case 0x30: // Text Description
                len = tapeBuffer[offset] & 0xff;
                msg = getCleanMsg(offset + 1, len);
                break;
            case 0x31: // Message Block
                len = tapeBuffer[offset + 1] & 0xff;
                msg = getCleanMsg(offset + 2, len);
                break;
            case 0x32: // Archive Info
                len = tapeBuffer[offset + 2] & 0xff;
                msg = String.format(bundle.getString("NUMBER_OF_STRINGS"), len);
                break;
            case 0x33: // Hardware Type
                msg = "";
                break;
            case 0x35: // Custom Info Block
                msg = getCleanMsg(offset, 10);
                break;
            case 'Z': // TZX Header or "Glue" Block
                msg = String.format(bundle.getString("TZX_HEADER"),
                        tapeBuffer[offset + 7] & 0xff, tapeBuffer[offset + 8] & 0xff);
                break;
            default:
                msg = "";
        }

        return msg;
    }

    public TapeTableModel getTapeTableModel() {
        return tapeTableModel;
    }

    @Override
    public void clockTimeout() {

        switch (tapeExtension) {
            case TAP:
                playTap();
                break;
            case TZX:
                playTzx();
                break;
            case CSW:
                playCsw();
                break;
            default:
                log.warn("Warning!, clockTimeout without tape playing");
        }
    }

    private int readInt(byte buffer[], int start, int len) {
        int res = 0;

        for (int idx = 0; idx < len; idx++) {
            res |= ((buffer[start + idx] << (idx * 8)) & (0xff << idx * 8));
        }
        return res;
    }

    public boolean insert(File fileName) {
        if (tapeExtension != TapeExtensionType.NO_TAPE) {
            return false;
        }

        try (BufferedInputStream tapeFile = new BufferedInputStream(new FileInputStream(fileName))) {
            tapeBuffer = new byte[tapeFile.available()];
            tapeFile.read(tapeBuffer);
            tapeFile.close();
            filename = fileName;
        } catch (final FileNotFoundException fex) {
            log.error("File {} not found", fileName, fex);
            return false;
        } catch (final IOException ex) {
            log.error("IOexception: ", ex);
            return false;
        }

        tapePos = idxHeader = 0;
        statePlay = State.STOP;
        tapePlaying = tapeRecording = false;
        String name = filename.getName().toLowerCase();
//        System.out.printf("File extension: %s", name.substring(name.lastIndexOf("."), name.length()));
        switch (name.substring(name.lastIndexOf("."), name.length())) {
            case ".tap":
                tapeExtension = TapeExtensionType.TAP;
                if (!findTAPOffsetBlocks()) {
                    nOffsetBlocks = 0;
                    return false;
                }
                break;
            case ".tzx":
                tapeExtension = TapeExtensionType.TZX;
                if (!findTZXOffsetBlocks()) {
                    nOffsetBlocks = 0;
                    return false;
                }
                break;
            case ".csw":
                tapeExtension = TapeExtensionType.CSW;
                nOffsetBlocks = 1;
                Arrays.fill(offsetBlocks, 0);
                break;
            default:
                tapeExtension = TapeExtensionType.NO_TAPE;
                return false;
        }

        tapeTableModel.fireTableDataChanged();
        fireTapeStateChanged(TapeState.INSERT);
        fireTapeBlockChanged(0);
        return true;
    }

    public boolean insertEmbeddedTape(String fileName, String extension,
            byte[] tapeData, int selectedBlock) {
        if (tapeExtension != TapeExtensionType.NO_TAPE) {
            return false;
        }

//        System.out.println(String.format("fileName: %s, extension: %s", fileName, extension));
        tapeBuffer = new byte[tapeData.length];
        System.arraycopy(tapeData, 0, tapeBuffer, 0, tapeBuffer.length);

        filename = new File(fileName);
        tapePos = idxHeader = 0;
        statePlay = State.STOP;
        tapePlaying = tapeRecording = false;
        switch (extension) {
            case "tap":
                tapeExtension = TapeExtensionType.TAP;
                if (!findTAPOffsetBlocks()) {
                    nOffsetBlocks = 0;
                    return false;
                }
                break;
            case "tzx":
                tapeExtension = TapeExtensionType.TZX;
                if (!findTZXOffsetBlocks()) {
                    nOffsetBlocks = 0;
                    return false;
                }
                break;
            default:
                tapeExtension = TapeExtensionType.NO_TAPE;
                return false;
        }

        tapeTableModel.fireTableDataChanged();
        fireTapeStateChanged(TapeState.INSERT);
        fireTapeBlockChanged(selectedBlock);
        return true;
    }

    public boolean eject() {
        if (tapeExtension == TapeExtensionType.NO_TAPE || tapePlaying || tapeRecording) {
            return false;
        }

        tapeExtension = TapeExtensionType.NO_TAPE;
        tapeBuffer = null;
        filename = null;
        nOffsetBlocks = 0;
        tapeTableModel.fireTableDataChanged();
        fireTapeStateChanged(TapeState.EJECT);
        return true;
    }

    public int getEarBit() {
        return earBit;
    }

    public void setEarBit(boolean earValue) {
        earBit = earValue ? EAR_ON : EAR_OFF;
    }

    public boolean isTapePlaying() {
        return tapePlaying;
    }

    public boolean isTapeRecording() {
        return tapeRecording;
    }

    public boolean isTapeRunning() {
        return tapePlaying || tapeRecording;
    }

    public boolean isTapeInserted() {
        return tapeExtension != TapeExtensionType.NO_TAPE;
    }

    public boolean isTapeReady() {
        return (tapeExtension != TapeExtensionType.NO_TAPE && !tapePlaying && !tapeRecording);
    }

    public File getTapeFilename() {
        return filename;
    }

    public boolean play(boolean origin) {
        if (tapeExtension == TapeExtensionType.NO_TAPE || tapePlaying || tapeRecording) {
            return false;
        }

        if (idxHeader >= nOffsetBlocks) {
            return false;
        }

        manualMode = origin;
        statePlay = State.START;

        fireTapeStateChanged(TapeState.PLAY);
        tapePlaying = true;
        clock.addClockTimeoutListener(this);
        clockTimeout();
        return true;
    }

    public void stop() {
        if (tapeExtension == TapeExtensionType.NO_TAPE || !tapePlaying || tapeRecording) {
            return;
        }

        tapePlaying = false;
        statePlay = State.STOP;

        fireTapeBlockChanged(idxHeader);
        fireTapeStateChanged(TapeState.STOP);
        clock.removeClockTimeoutListener(this);
    }

    public boolean rewind() {
        if (tapeExtension == TapeExtensionType.NO_TAPE || tapePlaying || tapeRecording) {
            return false;
        }

        idxHeader = 0;
        tapePos = offsetBlocks[0];
        fireTapeBlockChanged(0);

        return true;
    }

    private boolean findTAPOffsetBlocks() {
        nOffsetBlocks = 0;

        int offset = 0;
        Arrays.fill(offsetBlocks, 0);

        while (offset < tapeBuffer.length && nOffsetBlocks < offsetBlocks.length) {
            if ((tapeBuffer.length - offset) < 2) {
                return false;
            }
            int len = readInt(tapeBuffer, offset, 2);

            if (offset + len + 2 > tapeBuffer.length) {
                return false;
            }

            offsetBlocks[nOffsetBlocks++] = offset;
            offset += len + 2;
        }

        return true;
    }

    private boolean playTap() {
//        System.out.println(String.format("Estado de la cinta: %s", statePlay.toString()));
        switch (statePlay) {
            case STOP:
                stop();
                break;
            case START:
                fireTapeBlockChanged(idxHeader);
//                System.out.println(String.format("Playing tap block :%d", idxHeader));
                tapePos = offsetBlocks[idxHeader];
                blockLen = readInt(tapeBuffer, tapePos, 2);
                tapePos += 2;
//                System.out.println("blockLen = " + blockLen);
                leaderPulses = tapeBuffer[tapePos] >= 0 ? HEADER_PULSES : DATA_PULSES;
                earBit = EAR_ON;
                statePlay = State.LEADER;
                clock.setTimeout(LEADER_LENGHT);
                break;
            case LEADER:
                earBit ^= EAR_MASK;
                if (leaderPulses-- > 0) {
                    clock.setTimeout(LEADER_LENGHT);
                    break;
                }
                statePlay = State.SYNC;
                clock.setTimeout(SYNC1_LENGHT);
                break;
            case SYNC:
                earBit ^= EAR_MASK;
                statePlay = State.NEWBYTE;
                clock.setTimeout(SYNC2_LENGHT);
                break;
            case NEWBYTE:
                mask = 0x80; // se empieza por el bit 7
            case NEWBIT:
                earBit ^= EAR_MASK;
                if ((tapeBuffer[tapePos] & mask) == 0) {
                    bitTime = ZERO_LENGHT;
                } else {
                    bitTime = ONE_LENGHT;
                }
                statePlay = State.HALF2;
                clock.setTimeout(bitTime);
                break;
            case HALF2:
                earBit ^= EAR_MASK;
                clock.setTimeout(bitTime);
                mask >>>= 1;
                if (mask == 0) {
                    tapePos++;
                    if (--blockLen > 0) {
                        statePlay = State.NEWBYTE;
                    } else {
                        statePlay = State.PAUSE;
                    }
                } else {
                    statePlay = State.NEWBIT;
                }
                break;
            case PAUSE:
                earBit ^= EAR_MASK;
                statePlay = State.PAUSE_STOP;
                clock.setTimeout(manualMode ? 3500 : 10); // 1 ms pause in manual mode
//                System.out.println(String.format("tapeBufferLength: %d, tapePos: %d",
//                    tapeBuffer.length, tapePos));
                break;
            case PAUSE_STOP:
                idxHeader++;
                if (tapePos == tapeBuffer.length || !manualMode) {
                    stop();
                } else {
                    statePlay = State.START; // START
                    playTap();
                }
        }
        return true;
    }

    private boolean findTZXOffsetBlocks() {
        nOffsetBlocks = 0;

        int offset = 0; // saltamos la cabecera del TZX
        int len;
        Arrays.fill(offsetBlocks, 0);

        if (tapeBuffer.length == 0) {
            return true;
        }

        if (tapeBuffer[0] != 'Z') {
            return false;
        }

        while (offset < tapeBuffer.length && nOffsetBlocks < offsetBlocks.length) {
            offsetBlocks[nOffsetBlocks++] = offset;

            switch (tapeBuffer[offset] & 0xff) {
                case 0x10: // Standard speed data block
                    if (tapeBuffer.length - offset < 5) {
                        return false;
                    }
                    len = readInt(tapeBuffer, offset + 3, 2);
                    offset += len + 5;
                    break;
                case 0x11: // Turbo speed data block
                    if (tapeBuffer.length - offset < 19) {
                        return false;
                    }
                    len = readInt(tapeBuffer, offset + 16, 3);
                    offset += len + 19;
                    break;
                case 0x12: // Pure Tone Block
                    offset += 5;
                    break;
                case 0x13: // Pulse Sequence Block
                    if (tapeBuffer.length - offset < 2) {
                        return false;
                    }
                    len = tapeBuffer[offset + 1] & 0xff;
                    offset += len * 2 + 2;
                    break;
                case 0x14: // Pure Data Block
                    if (tapeBuffer.length - offset < 11) {
                        return false;
                    }
                    len = readInt(tapeBuffer, offset + 8, 3);
                    offset += len + 11;
                    break;
                case 0x15: // Direct Data Block
                    if (tapeBuffer.length - offset < 9) {
                        return false;
                    }
                    len = readInt(tapeBuffer, offset + 6, 3);
                    offset += len + 9;
                    break;
                case 0x18: // CSW Recording Block
                case 0x19: // Generalized Data Block
                    if (tapeBuffer.length - offset < 5) {
                        return false;
                    }
                    len = readInt(tapeBuffer, offset + 1, 4);
                    offset += len + 5;
                    break;
                case 0x20: // Pause (silence) or 'Stop the Tape' command
                case 0x23: // Jump to Block
                case 0x24: // Loop Start
                    offset += 3;
                    break;
                case 0x21: // Group Start
                    if (tapeBuffer.length - offset < 2) {
                        return false;
                    }
                    len = tapeBuffer[offset + 1] & 0xff;
                    offset += len + 2;
                    break;
                case 0x22: // Group End
                case 0x25: // Loop End
                case 0x27: // Return from Sequence
                    offset++;
                    break;
                case 0x26: // Call Sequence
                    if (tapeBuffer.length - offset < 3) {
                        return false;
                    }
                    len = readInt(tapeBuffer, offset + 1, 2);
                    offset += len * 2 + 3;
                    break;
                case 0x28: // Select Block
                case 0x32: // Archive Info
                    if (tapeBuffer.length - offset < 3) {
                        return false;
                    }
                    len = readInt(tapeBuffer, offset + 1, 2);
                    offset += len + 3;
                    break;
                case 0x2A: // Stop the tape if in 48K mode
                    offset += 5;
                    break;
                case 0x2B: // Set Signal Level
                    offset += 6;
                    break;
                case 0x30: // Text Description
                    if (tapeBuffer.length - offset < 2) {
                        return false;
                    }
                    len = tapeBuffer[offset + 1] & 0xff;
                    offset += len + 2;
                    break;
                case 0x31: // Message Block
                    if (tapeBuffer.length - offset < 3) {
                        return false;
                    }
                    len = tapeBuffer[offset + 2] & 0xff;
                    offset += len + 3;
                    break;
                case 0x33: // Hardware Type
                    if (tapeBuffer.length - offset < 2) {
                        return false;
                    }
                    len = tapeBuffer[offset + 1] & 0xff;
                    offset += len * 3 + 2;
                    break;
                case 0x35: // Custom Info Block
                    if (tapeBuffer.length - offset < 21) {
                        return false;
                    }
                    len = readInt(tapeBuffer, offset + 17, 4);
                    offset += len + 21;
                    break;
                case 'Z': // ZXTape Header or "Glue" Block
                    offset += 10;
                    break;
                default:
                    log.info(String.format("Block ID: %02x unknown", tapeBuffer[offset]));
                    return false; // Error en TZX
            }

            if (offset > tapeBuffer.length) {
                return false;
            }
        }

        return true;
    }

    private boolean playTzx() {
        boolean repeat;
        int timeout;

        do {
            repeat = false;
            switch (statePlay) {
                case STOP:
                    stop();
                    break;
                case START:
                    tapePos = offsetBlocks[idxHeader];
                    earBit = settings.isInvertedEar() ? EAR_ON : EAR_OFF;
                    statePlay = State.TZX_HEADER;
                    repeat = true;
                    break;
                case LEADER:
                    earBit ^= EAR_MASK;
                case LEADER_NOCHG:
                    if (leaderPulses-- > 0) {
                        statePlay = State.LEADER;
                        clock.setTimeout(leaderLenght);
                        break;
                    }
                    clock.setTimeout(sync1Lenght);
                    statePlay = State.SYNC;
                    break;
                case SYNC:
                    earBit ^= EAR_MASK;
                    clock.setTimeout(sync2Lenght);
                    if (blockLen > 0) {
                        statePlay = State.NEWBYTE;
                    } else {
                        statePlay = State.PAUSE;
                    }
                    break;
                case NEWBYTE_NOCHG:
                    // este cambio es para que se deshaga al entrar en el case
                    // de NEWBIT.
                    earBit ^= EAR_MASK;
                case NEWBYTE:
                    mask = 0x80; // se empieza por el bit 7
                case NEWBIT:
                    earBit ^= EAR_MASK;
                    if ((tapeBuffer[tapePos] & mask) == 0) {
                        bitTime = zeroLenght;
                    } else {
                        bitTime = oneLenght;
                    }
                    statePlay = State.HALF2;
                    clock.setTimeout(bitTime);
                    break;
                case HALF2:
                    earBit ^= EAR_MASK;
                    clock.setTimeout(bitTime);
                    mask >>>= 1;
                    if (blockLen == 1 && bitsLastByte < 8) {
                        if (mask == (0x80 >>> bitsLastByte)) {
                            statePlay = State.LAST_PULSE;
                            tapePos++;
                            break;
                        }
                    }

                    if (mask != 0) {
                        statePlay = State.NEWBIT;
                        break;
                    }

                    tapePos++;
                    if (--blockLen > 0) {
                        statePlay = State.NEWBYTE;
                    } else {
//                            System.out.println(String.format("Last byte: %02x",
//                                    tapeBuffer[tapePos-1]));
                        statePlay = State.LAST_PULSE;
                    }
                    break;
                case LAST_PULSE:
                    earBit ^= EAR_MASK;
                    if (endBlockPause == 0) {
                        statePlay = State.TZX_HEADER;
                        repeat = true;
                        break;
                    }
                    statePlay = State.PAUSE;
                    clock.setTimeout(3500); // 1 ms by TZX spec
                    break;
                case PAUSE:
                    earBit = settings.isInvertedEar() ? EAR_ON : EAR_OFF;
                    statePlay = State.TZX_HEADER;
                    clock.setTimeout(endBlockPause);
                    break;
                case TZX_HEADER:
                    if (idxHeader >= nOffsetBlocks) {
//                        System.out.println(String.format("Last Ear: %02x", earBit));
                        statePlay = State.STOP;
                        repeat = true;
                        break;
                    }
                    decodeTzxHeader();
                    repeat = true;
                    break;
                case PURE_TONE:
                    earBit ^= EAR_MASK;
                case PURE_TONE_NOCHG:
                    if (leaderPulses-- > 0) {
                        clock.setTimeout(leaderLenght);
                        statePlay = State.PURE_TONE;
                        break;
                    }
                    statePlay = State.TZX_HEADER;
                    repeat = true;
                    break;
                case PULSE_SEQUENCE:
                    earBit ^= EAR_MASK;
                case PULSE_SEQUENCE_NOCHG:
                    if (leaderPulses-- > 0) {
                        clock.setTimeout(readInt(tapeBuffer, tapePos, 2));
                        tapePos += 2;
                        statePlay = State.PULSE_SEQUENCE;
                        break;
                    }
                    statePlay = State.TZX_HEADER;
                    repeat = true;
                    break;
                case NEWDR_BYTE:
                    mask = 0x80;
                    statePlay = State.NEWDR_BIT;
//                    System.out.println(String.format("earBit: %02x", earBit));
                case NEWDR_BIT:
                    boolean earState;
                    if ((tapeBuffer[tapePos] & mask) != 0) {
                        earState = true;
                        earBit = EAR_ON;
                    } else {
                        earState = false;
                        earBit = EAR_OFF;
                    }
                    timeout = 0;

                    while (((tapeBuffer[tapePos] & mask) != 0) == earState) {
                        timeout += zeroLenght;

                        mask >>>= 1;
                        if (mask == 0) {
                            mask = 0x80;
                            tapePos++;
                            if (--blockLen == 0) {
                                statePlay = State.LAST_PULSE;
                                break;
                            }
                        } else {
                            if (blockLen == 1 && bitsLastByte < 8) {
                                if (mask == (0x80 >>> bitsLastByte)) {
                                    statePlay = State.LAST_PULSE;
                                    tapePos++;
                                    break;
                                }
                            }
                        }
                    }
                    clock.setTimeout(timeout);
                    break;
                case PAUSE_STOP:
                    if (endBlockPause == 0) {
                        statePlay = State.STOP;
                        repeat = true;
                    } else {
                        earBit = settings.isInvertedEar() ? EAR_ON : EAR_OFF;
                        statePlay = State.TZX_HEADER;
                        clock.setTimeout(endBlockPause);
                    }
                    break;
                case CSW_RLE:
                    if (blockLen == 0) {
                        statePlay = State.PAUSE;
                        repeat = true;
                    }

                    earBit ^= EAR_MASK;

                    timeout = tapeBuffer[tapePos++] & 0xff;
                    blockLen--;
                    if (timeout == 0) {
                        timeout = readInt(tapeBuffer, tapePos, 4);
                        tapePos += 4;
                        blockLen -= 4;
                    }

                    timeout *= cswStatesSample;
                    clock.setTimeout(timeout);
                    break;
                case CSW_ZRLE:
                    earBit ^= EAR_MASK;

                    try {
                        timeout = iis.read();
                        if (timeout < 0) {
                            iis.close();
                            bais.close();
                            repeat = true;
                            statePlay = State.PAUSE;
                            break;
                        }

                        if (timeout == 0) {
                            byte nSamples[] = new byte[4];
                            while (timeout < 4) {
                                int count = iis.read(nSamples, timeout,
                                        nSamples.length - timeout);
                                if (count == -1) {
                                    break;
                                }
                                timeout += count;
                            }

                            if (timeout == 4) {
                                timeout = readInt(nSamples, 0, 4);
                            } else {
                                iis.close();
                                bais.close();
                                repeat = true;
                                statePlay = State.PAUSE;
                                break;
                            }
                        }

                        timeout *= cswStatesSample;
                        clock.setTimeout(timeout);

                    } catch (IOException ex) {
                        log.error("IOexception: ", ex);
                    }
                    break;
//                case GDB_PULSE_SYNC:
//                    if (numPulses < npp) {
//                        if (numPulses == 0) {
//
//                        }
//                        leaderLenght =
//                        leaderPulses =
//                    }
//                    if (leaderPulses-- > 0) {
//                        clock.setTimeout(leaderLenght);
//                        break;
//                    }
//                    if (totp-- > 0) {
//                        leaderPulses = ptrDataStream;
//                    }
//                    break;
            }
        } while (repeat);
        return true;
    }

    private void decodeTzxHeader() {
        boolean repeat = true;

        while (repeat) {
            if (idxHeader >= nOffsetBlocks) {
                return;
            }

            fireTapeBlockChanged(idxHeader);
//            System.out.println(String.format("Playing tzx block :%d", idxHeader + 1));
            tapePos = offsetBlocks[idxHeader];

            switch (tapeBuffer[tapePos] & 0xff) {
                case 0x10: // Standard speed data block
                    leaderLenght = LEADER_LENGHT;
                    sync1Lenght = SYNC1_LENGHT;
                    sync2Lenght = SYNC2_LENGHT;
                    zeroLenght = ZERO_LENGHT;
                    oneLenght = ONE_LENGHT;
                    bitsLastByte = 8;
                    endBlockPause = readInt(tapeBuffer, tapePos + 1, 2);
                    blockLen = readInt(tapeBuffer, tapePos + 3, 2);
                    tapePos += 5;
                    leaderPulses =
                            (tapeBuffer[tapePos] & 0xff) < 0x80 ? HEADER_PULSES : DATA_PULSES;
                    statePlay = State.LEADER_NOCHG;
                    idxHeader++;
                    if (idxHeader >= nOffsetBlocks && endBlockPause > 1000) {
                        endBlockPause = 1;
                    }
                    endBlockPause *= (END_BLOCK_PAUSE / 1000);
                    repeat = false;
                    break;
                case 0x11: // Turbo speed data block
                    leaderLenght = readInt(tapeBuffer, tapePos + 1, 2);
                    sync1Lenght = readInt(tapeBuffer, tapePos + 3, 2);
                    sync2Lenght = readInt(tapeBuffer, tapePos + 5, 2);
                    zeroLenght = readInt(tapeBuffer, tapePos + 7, 2);
                    oneLenght = readInt(tapeBuffer, tapePos + 9, 2);
                    leaderPulses = readInt(tapeBuffer, tapePos + 11, 2);
                    bitsLastByte = tapeBuffer[tapePos + 13] & 0xff;
                    endBlockPause = readInt(tapeBuffer, tapePos + 14, 2);
                    blockLen = readInt(tapeBuffer, tapePos + 16, 3);
                    tapePos += 19;
                    // Hasta el 21/01/2011, el estado era State.LEADER_NOCHG
                    // Así cargan los juegos problemáticos indicados en la cabecera
                    // Cambio deshecho el 23/09/2011
                    statePlay = State.LEADER_NOCHG;
                    idxHeader++;
                    if (idxHeader >= nOffsetBlocks && endBlockPause > 1000) {
                        endBlockPause = 1;
                    }
                    endBlockPause *= (END_BLOCK_PAUSE / 1000);
                    repeat = false;
                    break;
                case 0x12: // Pure Tone Block
                    leaderLenght = readInt(tapeBuffer, tapePos + 1, 2);
                    leaderPulses = readInt(tapeBuffer, tapePos + 3, 2);
                    tapePos += 5;
                    statePlay = State.PURE_TONE_NOCHG;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x13: // Pulse Sequence Block
                    leaderPulses = tapeBuffer[tapePos + 1] & 0xff;
                    tapePos += 2;
                    statePlay = State.PULSE_SEQUENCE_NOCHG;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x14: // Pure Data Block
                    zeroLenght = readInt(tapeBuffer, tapePos + 1, 2);
                    oneLenght = readInt(tapeBuffer, tapePos + 3, 2);
                    bitsLastByte = tapeBuffer[tapePos + 5] & 0xff;
                    endBlockPause = readInt(tapeBuffer, tapePos + 6, 2)
                            * (END_BLOCK_PAUSE / 1000);
                    blockLen = readInt(tapeBuffer, tapePos + 8, 3);
                    tapePos += 11;
                    statePlay = State.NEWBYTE_NOCHG;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x15: // Direct Data Block
                    zeroLenght = readInt(tapeBuffer, tapePos + 1, 2);
                    endBlockPause = readInt(tapeBuffer, tapePos + 3, 2)
                            * (END_BLOCK_PAUSE / 1000);
                    bitsLastByte = tapeBuffer[tapePos + 5] & 0xff;
                    blockLen = readInt(tapeBuffer, tapePos + 6, 3);
                    tapePos += 9;
                    statePlay = State.NEWDR_BYTE;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x18: // CSW Recording Block
                    endBlockPause = readInt(tapeBuffer, tapePos + 5, 2)
                            * (END_BLOCK_PAUSE / 1000);
                    cswStatesSample = 3500000.0f / readInt(tapeBuffer, tapePos + 7, 3);
                    blockLen = readInt(tapeBuffer, tapePos + 1, 4) - 10;
                    if (tapeBuffer[tapePos + 10] == 0x02) {
                        statePlay = State.CSW_ZRLE;
                        bais = new ByteArrayInputStream(tapeBuffer, tapePos + 15, blockLen);
                        iis = new InflaterInputStream(bais);
                    } else {
                        statePlay = State.CSW_RLE;
                    }
                    tapePos += 15;
                    idxHeader++;
                    // al entrar la primera vez deshará el cambio
                    earBit ^= EAR_MASK;
                    repeat = false;
                    break;
                case 0x19: // Generalized Data Block
                    endBlockPause = readInt(tapeBuffer, tapePos + 5, 2)
                            * (END_BLOCK_PAUSE / 1000);
                    totp = readInt(tapeBuffer, tapePos + 7, 4);
                    npp = tapeBuffer[tapePos + 11] & 0xff;
                    asp = tapeBuffer[tapePos + 12] & 0xff;
                    totd = readInt(tapeBuffer, tapePos + 13, 4);
                    npd = tapeBuffer[tapePos + 17] & 0xff;
                    asd = tapeBuffer[tapePos + 18] & 0xff;
//                    printGDBHeader(tapePos);
//                    ptrSymbol = tapePos + 0x19;
//                    if (totp > 0) {
//                        statePlay = State.GDB_PULSE_SYNC;
//                        ptrDataStream = ptrSymbol + (2 * npp + 1) * asp;
//                        numPulses = 0;
//                        leaderLenght =
//                        leaderPulses =
//                    } else {
//                        statePlay = State.GDB_DATA;
//                        ptrDataStream = ptrSymbol + (2 * npd + 1) * asd;
//                    }
                    idxHeader++;
                    log.info("Gen. Data Block not supported!. Skipping...");
                    break;
                case 0x20: // Pause (silence) or 'Stop the Tape' command
                    endBlockPause = readInt(tapeBuffer, tapePos + 1, 2)
                            * (END_BLOCK_PAUSE / 1000);
                    tapePos += 3;
                    statePlay = State.PAUSE_STOP;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x21: // Group Start
                    idxHeader++;
                    break;
                case 0x22: // Group End
                    idxHeader++;
                    break;
                case 0x23: // Jump to Block
                    short target = (short) readInt(tapeBuffer, tapePos + 1, 2);
                    idxHeader += target;
                    break;
                case 0x24: // Loop Start
                    nLoops = readInt(tapeBuffer, tapePos + 1, 2);
                    loopStart = ++idxHeader;
                    break;
                case 0x25: // Loop End
                    if (--nLoops == 0) {
                        idxHeader++;
                        break;
                    }
                    idxHeader = loopStart;
                    break;
                case 0x26: // Call Sequence
                    if (callSeq == null) {
                        nCalls = readInt(tapeBuffer, tapePos + 1, 2);
                        callSeq = new short[nCalls];
//                        System.out.print(String.format("Call sequence of %d calls: ", nCalls));
                        for (int idx = 0; idx < nCalls; idx++) {
                            callSeq[idx] = (short) (readInt(tapeBuffer, tapePos + idx * 2 + 3, 2));
//                            System.out.print(String.format("%d ", callSeq[idx]));
                        }
//                        System.out.println("");
                        callBlk = idxHeader;
                        nCalls = 0;
                        idxHeader += callSeq[nCalls++];
                    } else {
                        log.info("The CALL blocks can't be nested!. Skipping!!!");
                        idxHeader++;
                    }
                    break;
                case 0x27: // Return from Sequence
//                    System.out.println("Return from sequence");
                    if (nCalls < callSeq.length) {
                        idxHeader = callBlk + callSeq[nCalls++];
                    } else {
                        idxHeader = callBlk + 1;
                        callSeq = null;
                    }
                    break;
                case 0x28: // Select Block
                    idxHeader++;
                    break;
                case 0x2A: // Stop the tape if in 48K mode
                    if (spectrumModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                        statePlay = State.STOP;
                        repeat = false;
                    }
                    idxHeader++;
                    break;
                case 0x2B: // Set Signal Level
                    earBit = tapeBuffer[tapePos + 5] == 0 ? EAR_OFF : EAR_ON;
                    idxHeader++;
                    break;
                case 0x30: // Text Description
                    idxHeader++;
                    break;
                case 0x31: // Message Block
                    idxHeader++;
                    break;
                case 0x32: // Archive Info
                    idxHeader++;
                    break;
                case 0x33: // Hardware Type
                    idxHeader++;
                    break;
                case 0x35: // Custom Info Block
                    idxHeader++;
                    break;
                case 'Z': // TZX Header && "Glue" Block
                    idxHeader++;
                    break;
                default:
                    log.info(String.format("Block ID: %02x", tapeBuffer[tapePos]));
                    repeat = false;
                    idxHeader++;
            }
        }
//        System.out.println(String.format("tapeBufferLength: %d, tapePos: %d, blockLen: %d",
//                    tapeBuffer.length, tapePos, blockLen));
    }

    private void printGDBHeader(int index) {
        index++; // Skip GDB Header Code (0x19)
        int blkLenght = readInt(tapeBuffer, index, 4);

        System.out.println(String.format("GDB size: %d bytes", blkLenght));
        System.out.println(String.format("End Block Pause: %d ms", endBlockPause));
        System.out.println(String.format("Total number of symbols in pilot/sync block (TOTP): %d", totp));
        System.out.println(String.format("Maximum number of pulses per pilot/sync symbol (NPP): %d", npp));
        System.out.println(String.format("Number of pilot/sync symbols in the alphabet table (ASP): %d", asp));
        if (totp > 0) {
            int offset = index + 0x12;
            for (int symbol = 0; symbol < asp; symbol++) {
                System.out.print(String.format("\tSymbol %d, type %d: ", symbol, tapeBuffer[offset++] & 0xff));
                for (int npulse = 0; npulse < npp; npulse++) {
                    System.out.print(String.format("%d ", readInt(tapeBuffer, offset, 2)));
                    offset += 2;
                }
                System.out.println("");
            }

            for (int pulse = 0; pulse < totp; pulse++) {
                System.out.println(String.format("\t\tRepeat %d: symbol %d repeated %d times",
                        pulse, tapeBuffer[offset++] & 0xff, readInt(tapeBuffer, offset, 2)));
                offset += 2;
            }
        }

        System.out.println(String.format("Total number of symbols in data stream (TOTD): %d", totd));
        System.out.println(String.format("Maximum number of pulses per data symbol (NPD): %d", npd));
        System.out.println(String.format("Number of data symbols in the alphabet table (ASD): %d", asd));
        int offset = index + 0x12;
        if (totp > 0) {
            offset += ((2 * npp + 1) * asp) + totp * 3;
        }
        for (int symbol = 0; symbol < asd; symbol++) {
            System.out.print(String.format("\tSymbol %d, type %d: ", symbol, tapeBuffer[offset++] & 0xff));
            for (int npulse = 0; npulse < npd; npulse++) {
                System.out.print(String.format("%d ", readInt(tapeBuffer, offset, 2)));
                offset += 2;
            }
            System.out.println("");
        }
    }

    private boolean playCsw() {
        int timeout;

        switch (statePlay) {
            case STOP:
                idxHeader++;
                stop();
                break;
            case START:
                if ((tapeBuffer[0x17] & 0xff) == 0x01) { // CSW v1.01
                    earBit = ((tapeBuffer[0x1C] & 0x01) != 0) ? EAR_OFF : EAR_ON;
                    cswStatesSample = 3500000.0f / readInt(tapeBuffer, 0x19, 2);
                    tapePos = 0x20;
                    statePlay = State.CSW_RLE;
                } else { // CSW v2.0
                    earBit = ((tapeBuffer[0x22] & 0x01) != 0) ? EAR_OFF : EAR_ON;
                    cswStatesSample = 3500000.0f / readInt(tapeBuffer, 0x19, 4);
                    tapePos = 0x34 + tapeBuffer[0x23];
                    if ((tapeBuffer[0x21] & 0xff) == 0x02) { // Z-RLE
                        bais = new ByteArrayInputStream(tapeBuffer, tapePos,
                                tapeBuffer.length - tapePos);
                        iis = new InflaterInputStream(bais);
                        statePlay = State.CSW_ZRLE;
                        clock.setTimeout(1);
                        return true;
                    } else { // RLE as CSW v1.01
                        statePlay = State.CSW_RLE;
                    }
                }
            // No break statement, that's correct. :)
            case CSW_RLE:
                if (tapePos == tapeBuffer.length) {
                    stop();
                    break;
                }
                earBit ^= EAR_MASK;

                timeout = tapeBuffer[tapePos++] & 0xff;
                if (timeout == 0) {
                    timeout = readInt(tapeBuffer, tapePos, 4);
                    tapePos += 4;
                }

                timeout *= cswStatesSample;
                clock.setTimeout(timeout);
                break;
            case CSW_ZRLE:
                earBit ^= EAR_MASK;

                try {
                    timeout = iis.read();
                    if (timeout < 0) {
                        iis.close();
                        bais.close();
                        stop();
                        break;
                    }

                    if (timeout == 0) {
                        byte nSamples[] = new byte[4];
                        while (timeout < 4) {
                            int count = iis.read(nSamples, timeout,
                                    nSamples.length - timeout);
                            if (count == -1) {
                                break;
                            }
                            timeout += count;
                        }

                        if (timeout == 4) {
                            timeout = readInt(nSamples, 0, 4);
                        } else {
                            iis.close();
                            bais.close();
                            stop();
                            break;
                        }
                    }

                    timeout *= cswStatesSample;
                    clock.setTimeout(timeout);

                } catch (final IOException ex) {
                    log.error("IOexception: ", ex);
                }
                break;
        }
        return true;
    }

    public boolean flashLoad(Memory memory) {

        if (tapeExtension == TapeExtensionType.NO_TAPE || cpu == null ||
                (tapeExtension != TapeExtensionType.TAP && tapeExtension != TapeExtensionType.TZX)) {
            return false;
        }

//        System.out.println("flashload!");

        if (idxHeader >= nOffsetBlocks) {
//            cpu.setCarryFlag(false);
            return false;
        }

        if (tapeExtension == TapeExtensionType.TZX) {
            // Fastload only with Standard Speed Tape Blocks (and some
            // identified erroneusly as Turbo Blocks
            // (Midnight Resistance 128k as an example)
            found:
            {
                while (idxHeader < nOffsetBlocks) {
                    tapePos = offsetBlocks[idxHeader];
                    switch ((tapeBuffer[tapePos] & 0xff)) {
                        case 0x10: // Normal Speed Tape Block
                        case 0x11: // Turbo Speed Tape Block
                            break found;
                        case 0x12:
                        case 0x13:
                        case 0x14:
                        case 0x15:
                        case 0x18:
                        case 0x19:
                        case 0x20:
                        case 0x23:
                        case 0x24:
                        case 0x25:
                        case 0x26:
                        case 0x27:
                        case 0x28:
                        case 0x2A:
                        case 0x2B:
                            return false;
                        case 0x21:
                        case 0x22:
                        case 0x30:
                        case 0x31:
                        case 0x32:
                        case 0x33:
                        case 0x35:
                        case 'Z':
                        default:
                            idxHeader++;
                    }
                }
            }

            if (idxHeader >= nOffsetBlocks) {
//                cpu.setCarryFlag(false);
                return false;
            }

            fireTapeBlockChanged(idxHeader);
            if (tapeBuffer[tapePos] == 0x10) {
                blockLen = readInt(tapeBuffer, tapePos + 3, 2);
                tapePos += 5;
            } else {
                blockLen = readInt(tapeBuffer, tapePos + 16, 3);
                tapePos += 19;
            }
        } else {
            tapePos = offsetBlocks[idxHeader];
            blockLen = readInt(tapeBuffer, tapePos, 2);
//        System.out.println(String.format("tapePos: %X. blockLen: %X", tapePos, blockLen));
            tapePos += 2;
        }

        // ¿Coincide el flag? (está en el registro A)
        if (cpu.getRegA() != (tapeBuffer[tapePos] & 0xff)) {
            cpu.xor(tapeBuffer[tapePos]);
            cpu.setCarryFlag(false);
            idxHeader++;
            return true;
        }
        // La paridad incluye el byte de flag
        cpu.setRegA(tapeBuffer[tapePos]);

        int count = 0;
        int addr = cpu.getRegIX();    // Address start
        int nBytes = cpu.getRegDE();  // Lenght
        while (count < nBytes && count < blockLen - 1) {
            memory.writeByte(addr, tapeBuffer[tapePos + count + 1]);
            cpu.xor(tapeBuffer[tapePos + count + 1]);
            addr = (addr + 1) & 0xffff;
            count++;
        }

        // Se cargarán los bytes pedidos en DE
        if (count == nBytes) {
            cpu.xor(tapeBuffer[tapePos + count + 1]); // Byte de paridad
            cpu.cp(0x01);
        }

        // Hay menos bytes en la cinta de los indicados en DE
        // En ese caso habrá dado un error de timeout en LD-SAMPLE (0x05ED)
        // que se señaliza con CARRY==reset & ZERO==set
        if (count < nBytes) {
            cpu.setFlags(0x50); // when B==0xFF, then INC B, B=0x00, F=0x50
        }

        cpu.setRegIX(addr);
        cpu.setRegDE(nBytes - count);
        idxHeader++;
        fireTapeBlockChanged(idxHeader);

//        System.out.println(String.format("Salida -> IX: %04X DE: %04X AF: %04X",
//            cpu.getRegIX(), cpu.getRegDE(), cpu.getRegAF()));
        return true;
    }

    public boolean saveTapeBlock(Memory memory) {

        if (!filename.canWrite()) {
            return false;
        }

        int addr = cpu.getRegIX();   // Start Address
        int nBytes = cpu.getRegDE(); // Lenght

        record = new ByteArrayOutputStream();

        // Si el archivo es nuevo y es un TZX, necesita la preceptiva cabecera
        if (filename.getName().toLowerCase().endsWith("tzx")) {
            if (nOffsetBlocks == 0) {

                byte hdrTZX[], idTZX[];
                try {
                    hdrTZX = tzxHeader.getBytes("US-ASCII");
                    idTZX = tzxCreator.getBytes("US-ASCII");
                } catch (final UnsupportedEncodingException ex) {
                    log.error("Exception: ", ex);
                    return false;
                }

                record.write(hdrTZX, 0, hdrTZX.length);
                record.write(01);  // TZX v1.20
                record.write(20);
                record.write(0x30);
                record.write(idTZX.length);
                record.write(idTZX, 0, idTZX.length);
            }
            // Y ahora la cabecera de Normal Speed block
            record.write(0x10); // TZX ID: Normal Speed Block
            record.write(0xE8);
            record.write(0x03); // pausa de 1000 ms estándar
        }
        record.write(nBytes + 2);
        record.write((nBytes + 2) >>> 8);
        int parity = cpu.getRegA();
        record.write(parity);
        int value;
        for (int address = addr; address < addr + nBytes; address++) {
            value = memory.readByte(address);
            record.write(value);
            parity ^= value;
        }
        record.write(parity);

        try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filename, true))) {
            record.writeTo(fOut);
        } catch (final IOException ex) {
            log.error("IOexception: ", ex);
        }

        File tmp = filename;
        eject();
        insert(tmp);
        return true;
    }

    public boolean startRecording() {
        if (!isTapeReady() || !filename.getName().toLowerCase().endsWith(".tzx")) {
            return false;
        }

        record = new ByteArrayOutputStream();

        timeLastOut = 0;
        tapeRecording = true;
        if (settings.isHighSamplingFreq()) {
            freqSample = 48000;
            cswStatesSample = 3500000.0f / freqSample;
            cswPulses = 0;
            dos = new DeflaterOutputStream(record);
        } else {
            freqSample = 79; // 44.1 Khz
        }

        fireTapeStateChanged(TapeState.RECORD);

        return true;
    }

    public boolean stopRecording() {
        if (!tapeRecording) {
            return false;
        }

//        System.out.println(String.format("Record size: %d", record.size()));

        try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filename, true))) {
            // Si el archivo es nuevo y es un TZX, necesita la preceptiva cabecera
            if (nOffsetBlocks == 0) {
                fOut.write(tzxHeader.getBytes("US-ASCII"));
                fOut.write(01);
                fOut.write(20);
                byte idTZX[] = tzxCreator.getBytes("US-ASCII");
                fOut.write(0x30);
                fOut.write(idTZX.length);
                fOut.write(idTZX);
            }

            if (settings.isHighSamplingFreq()) {
                dos.close();
                record.close();
                // Y ahora la cabecera de CSW Recording
                fOut.write(0x18); // TZX ID: CSW Recording
                fOut.write(record.size() + 10);
                fOut.write((record.size() + 10) >>> 8);
                fOut.write((record.size() + 10) >>> 16);
                fOut.write((record.size() + 10) >>> 24);
                fOut.write(0x00);
                fOut.write(0x00); // 0 sec end block pause
                fOut.write(freqSample);
                fOut.write(freqSample >>> 8);
                fOut.write(freqSample >>> 16);
                fOut.write(0x02); // Z-RLE encoding
                fOut.write(cswPulses);
                fOut.write(cswPulses >>> 8);
                fOut.write(cswPulses >>> 16);
                fOut.write(cswPulses >>> 24);
                record.writeTo(fOut);
            } else {
                if (bitsLastByte != 0) {
                    byteTmp <<= (8 - bitsLastByte);
                    record.write(byteTmp);
                }

                // Y ahora la cabecera de Direct Data Recording
                fOut.write(0x15); // TZX ID: Direct Recording Block
                fOut.write(freqSample);
                fOut.write(0x00); // T-states per sample
                fOut.write(0x00);
                fOut.write(0x00); // 0 sec end block pause
                fOut.write(bitsLastByte);
                fOut.write(record.size());
                fOut.write(record.size() >>> 8);
                fOut.write(record.size() >>> 16);
                record.close();
                record.writeTo(fOut);
            }
        } catch (final IOException ex) {
            log.error("IOException: ", ex);
        }

        tapeRecording = false;
        fireTapeStateChanged(TapeState.STOP);
        File tmp = filename;
        eject();
        insert(tmp);

        return true;
    }

    public void recordPulse(boolean micState) {
        if (timeLastOut == 0) {
            timeLastOut = clock.getAbsTstates();
            micBit = micState;
            return;
        }

        int len = (int) (clock.getAbsTstates() - timeLastOut);

        if (settings.isHighSamplingFreq()) { // CSW
            cswPulses++;
            int pulses = (int) ((len / cswStatesSample) + 0.49f);

            try {
                if (pulses > 255) {
                    dos.write(0);
                    dos.write(pulses);
                    dos.write(pulses >>> 8);
                    dos.write(pulses >>> 16);
                    dos.write(pulses >>> 24);
                } else {
                    dos.write(pulses);
                }
            } catch (final IOException ex) {
                log.error("IOException: ", ex);
            }
        } else { // DRB
            int pulses = len + (freqSample >>> 1);
            pulses /= freqSample;
            while (pulses-- > 0) {
                if (bitsLastByte == 8) {
                    record.write(byteTmp);
                    bitsLastByte = 0;
                    byteTmp = 0;
                }

                byteTmp <<= 1;
                if (micBit) {
                    byteTmp |= 0x01;
                }
                bitsLastByte++;
            }
        }

        timeLastOut = clock.getAbsTstates();
        micBit = micState;
    }

    private class TapeTableModel extends AbstractTableModel {

        public TapeTableModel() {
        }

        @Override
        public int getRowCount() {
            return getNumBlocks();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Object getValueAt(int row, int col) {

//            log.trace("getValueAt row {}, col {}", row, col);
            return switch (col) {
                case 0 -> String.format("%4d", row + 1);
                case 1 -> getBlockType(row);
                case 2 -> getBlockInfo(row);
                default -> "NON EXISTENT COLUMN!";
            };
        }

        @Override
        public String getColumnName(int col) {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("gui/Bundle"); // NOI18N

            return switch (col) {
                case 0 -> bundle.getString("JSpeccy.tapeCatalog.columnModel.title0");
                case 1 -> bundle.getString("JSpeccy.tapeCatalog.columnModel.title1");
                case 2 -> bundle.getString("JSpeccy.tapeCatalog.columnModel.title2");
                default -> "COLUMN ERROR!";
            };
        }
    }
}
