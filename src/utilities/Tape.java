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
 */
package utilities;

import configuration.TapeType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import machine.MachineTypes;
import machine.Memory;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Tape {

    private Z80 cpu;
    private BufferedInputStream tapeFile;
    private ByteArrayOutputStream record;
    private File filename;
    private String filenameLabel;
    private int tapeBuffer[];
    private int offsetBlocks[] = new int[4096]; // el AMC tiene más de 1500 bloques!!!
    private int nOffsetBlocks;
    private int idxHeader;
    private int tapePos;
    private int blockLen;
    private int mask;
    private int bitTime;
    private byte byteTmp;

    private enum State {

        STOP, START, LEADER, LEADER_NOCHG, SYNC, NEWBYTE,
        NEWBYTE_NOCHG, NEWBIT, HALF2, PAUSE, TZX_HEADER, PURE_TONE,
        PURE_TONE_NOCHG, PULSE_SEQUENCE, PULSE_SEQUENCE_NOCHG, NEWDR_BYTE,
        NEWDR_BIT, PAUSE_STOP
    };
    private State statePlay;
    private int earBit;
    private boolean micBit;
    private static final int EAR_OFF = 0xbf;
    private static final int EAR_ON = 0xff;
    private static final int EAR_MASK = 0x40;
    private long timeout;
    private long timeLastIn, timeLastOut;
    private boolean tapeInserted, tapePlaying, tapeRecording;
    private boolean tzxTape, flashload;
    /* Tiempos en T-estados de duración de cada pulso para cada parte de la carga */
    private final int LEADER_LENGHT = 2168;
    private final int SYNC1_LENGHT = 667;
    private final int SYNC2_LENGHT = 735;
    private final int ZERO_LENGHT = 855;
    private final int ONE_LENGHT = 1710;
    private final int HEADER_PULSES = 8063;
    private final int DATA_PULSES = 3223;
    private final int END_BLOCK_PAUSE = 3500000;
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
    private final TapeNotify tapeNotify;
    private MachineTypes spectrumModel;
    private TapeTableModel tapeTableModel;
    private ListSelectionModel lsm;
    private TapeType settings;

    public Tape(TapeType tapeSettings, Z80 z80, TapeNotify notifyObject) {
        settings = tapeSettings;
        tapeNotify = notifyObject;
        cpu = z80;
        statePlay = State.STOP;
        tapeInserted = tapePlaying = tapeRecording = false;
        tzxTape = false;
        flashload = settings.isFlashload();
        tapePos = 0;
        timeout = timeLastIn = 0;
        earBit = EAR_ON;
        spectrumModel = MachineTypes.SPECTRUM48K;
        filenameLabel = null;
        nOffsetBlocks = 0;
        idxHeader = 0;
        Arrays.fill(offsetBlocks, 0);
        tapeTableModel = new TapeTableModel();
    }

    public void setSpectrumModel(MachineTypes model) {
        spectrumModel = model;
    }

    public void setListSelectionModel(ListSelectionModel list) {
        lsm = list;
    }

    public int getNumBlocks() {
        if (!tapeInserted) {
            return 1;
        }

        return nOffsetBlocks;
    }

    public int getSelectedBlock() {
        return idxHeader;
    }

    public void setSelectedBlock(int block) {
        if (!tapeInserted || isTapePlaying() || block > nOffsetBlocks) {
            return;
        }

        idxHeader = block;
        flashload = settings.isFlashload();
    }

    public String getCleanMsg(int offset, int len) {
        byte msg[] = new byte[len];

        // Hay que quitar los caracteres especiales
        for (int car = 0; car < len; car++) {
            if (tapeBuffer[offset + car] > 31 && tapeBuffer[offset + car] < 128) {
                msg[car] = (byte) tapeBuffer[offset + car];
            } else {
                msg[car] = '?'; // sustituir el carácter no imprimible
            }
        }

        return new String(msg);
    }

    public String getBlockType(int block) {
        java.util.ResourceBundle bundle =
            java.util.ResourceBundle.getBundle("utilities/Bundle"); // NOI18N
        if (!tapeInserted) {
            return bundle.getString("NO_TAPE_INSERTED");
        }

        if (!tzxTape) {
            return bundle.getString("STD_SPD_DATA");
        }

        int offset = offsetBlocks[block];
        if (isTZXHeader(offset)) {
            return "ZXTape!";
        }

        String msg;
        switch (tapeBuffer[offset]) {
            case 0x10: // Standard speed data block
                msg = bundle.getString("STD_SPD_DATA");
                break;
            case 0x11: // Turbo speed data block
                msg = bundle.getString("TURBO_SPD_DATA");
                break;
            case 0x12: // Pure Tone Block
                msg = bundle.getString("PURE_TONE");
                break;
            case 0x13: // Pulse Sequence Block
                msg = bundle.getString("PULSE_SEQUENCE");
                break;
            case 0x14: // Pure Data Block
                msg = bundle.getString("PURE_DATA");
                break;
            case 0x15: // Direct Data Block
                msg = bundle.getString("DIRECT_DATA");
                break;
            case 0x18: // CSW Recording Block
                msg = bundle.getString("CSW_RECORDING");
                break;
            case 0x19: // Generalized Data Block
                msg = bundle.getString("GDB_DATA");
                break;
            case 0x20: // Pause (silence) or 'Stop the Tape' command
                msg = bundle.getString("PAUSE_STOP");
                break;
            case 0x21: // Group Start
                msg = bundle.getString("GROUP_START");
                break;
            case 0x22: // Group End
                msg = bundle.getString("GROUP_STOP");
                break;
            case 0x23: // Jump to Block
                msg = bundle.getString("JUMP_TO");
                break;
            case 0x24: // Loop Start
                msg = bundle.getString("LOOP_START");
                break;
            case 0x25: // Loop End
                msg = bundle.getString("LOOP_STOP");
                break;
            case 0x26: // Call Sequence
                msg = bundle.getString("CALL_SEQ");
                break;
            case 0x27: // Return from Sequence
                msg = bundle.getString("RETURN_SEQ");
                break;
            case 0x28: // Select Block
                msg = bundle.getString("SELECT_BLOCK");
                break;
            case 0x2A: // Stop the tape if in 48K mode
                msg = bundle.getString("STOP_48K_MODE");
                break;
            case 0x2B: // Set Signal Level
                msg = bundle.getString("SET_SIGNAL_LEVEL");
                break;
            case 0x30: // Text Description
                msg = bundle.getString("TEXT_DESC");
                break;
            case 0x31: // Message Block
                msg = bundle.getString("MESSAGE_BLOCK");
                break;
            case 0x32: // Archive Info
                msg = bundle.getString("ARCHIVE_INFO");
                break;
            case 0x33: // Hardware Type
                msg = bundle.getString("HARDWARE_TYPE");
                break;
            case 0x35: // Custom Info Block
                msg = bundle.getString("CUSTOM_INFO");
                break;
            case 0x5A: // "Glue" Block
                msg = bundle.getString("GLUE_BLOCK");
                break;
            default:
                msg = String.format(bundle.getString("UNKN_TZX_BLOCK"), tapeBuffer[offset]);
        }

        return msg;
    }

    public String getBlockInfo(int block) {
        java.util.ResourceBundle bundle =
            java.util.ResourceBundle.getBundle("utilities/Bundle"); // NOI18N
        if (!tapeInserted) {
            return bundle.getString("NO_TAPE_INSERTED");
        }

        String msg;

        if (!tzxTape) {
            int offset = offsetBlocks[block];
            int len = tapeBuffer[offset] + (tapeBuffer[offset + 1] << 8);

            if (tapeBuffer[offset + 2] == 0) { // Header
                switch (tapeBuffer[offset + 3]) {
                    case 0: // Program
                        msg = String.format(bundle.getString("PROGRAM_HEADER"),
                            getCleanMsg(offset + 4, 10));
                        break;
                    case 1: // Number array
                        msg = bundle.getString("NUMBER_ARRAY_HEADER");
                        break;
                    case 2:
                        msg = bundle.getString("CHAR_ARRAY_HEADER");
                        break;
                    case 3:
                        msg = String.format(bundle.getString("BYTES_HEADER"),
                            getCleanMsg(offset + 4, 10));
                        break;
                    default:
                        msg = "";
                }
            } else {
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
            }
            return msg;
        }

        int offset = offsetBlocks[block];
        if (isTZXHeader(offset)) {
            return bundle.getString("TZX_HEADER");
        }

        int len, num;
        switch (tapeBuffer[offset++]) {
            case 0x10: // Standard speed data block
                len = tapeBuffer[offset + 2] + (tapeBuffer[offset + 3] << 8);
                if (tapeBuffer[offset + 4] == 0) { // Header
                    switch (tapeBuffer[offset + 5]) {
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
                len = tapeBuffer[offset + 15] + (tapeBuffer[offset + 16] << 8)
                    + (tapeBuffer[offset + 17] << 16);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x12: // Pure Tone Block
                len = tapeBuffer[offset] + (tapeBuffer[offset + 1] << 8);
                num = tapeBuffer[offset + 2] + (tapeBuffer[offset + 3] << 8);
                msg = String.format(bundle.getString("PURE_TONE_MESSAGE"), num, len);
                break;
            case 0x13: // Pulse Sequence Block
                len = tapeBuffer[offset];
                msg = String.format(bundle.getString("PULSE_SEQ_MESSAGE"), len);
                break;
            case 0x14: // Pure Data Block
                len = tapeBuffer[offset + 7] + (tapeBuffer[offset + 8] << 8)
                    + (tapeBuffer[offset + 9] << 16);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x15: // Direct Data Block
                len = tapeBuffer[offset + 5] + (tapeBuffer[offset + 6] << 8)
                    + (tapeBuffer[offset + 7] << 16);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x18: // CSW Recording Block
                len = tapeBuffer[offset] + (tapeBuffer[offset + 1] << 8)
                    + (tapeBuffer[offset + 2] << 16) + (tapeBuffer[offset + 3] << 24);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x19: // Generalized Data Block
                len = tapeBuffer[offset] + (tapeBuffer[offset + 1] << 8)
                    + (tapeBuffer[offset + 2] << 16) + (tapeBuffer[offset + 3] << 24);
                msg = String.format(bundle.getString("BYTES_MESSAGE"), len);
                break;
            case 0x20: // Pause (silence) or 'Stop the Tape' command
                len = tapeBuffer[offset] + (tapeBuffer[offset + 1] << 8);
                if (len == 0) {
                    msg = bundle.getString("STOP_THE_TAPE");
                } else {
                    msg = String.format(bundle.getString("PAUSE_MS"), len);
                }
                break;
            case 0x21: // Group Start
                len = tapeBuffer[offset];
                msg = getCleanMsg(offset + 1, len);
                break;
            case 0x22: // Group End
                msg = "";
                break;
            case 0x23: // Jump to Block
                byte disp = (byte) tapeBuffer[offset];
                msg = String.format(bundle.getString("NUMBER_OF_BLOCKS"), disp);
                break;
            case 0x24: // Loop Start
                len = tapeBuffer[offset] + (tapeBuffer[offset + 1] << 8);
                msg = String.format(bundle.getString("NUMBER_OF_ITER"), len);
                break;
            case 0x25: // Loop End
                msg = "";
                break;
            case 0x26: // Call Sequence
                len = tapeBuffer[offset] + (tapeBuffer[offset + 1] << 8);
                msg = String.format(bundle.getString("NUMBER_OF_CALLS"), len);
                break;
            case 0x27: // Return from Sequence
                msg = "";
                break;
            case 0x28: // Select Block
                len = tapeBuffer[offset + 2];
                msg = String.format(bundle.getString("NUMBER_OF_SELS"), len);
                break;
            case 0x2A: // Stop the tape if in 48K mode
                msg = "";
                break;
            case 0x2B: // Set Signal Level
                len = tapeBuffer[offset + 2];
                msg = String.format(bundle.getString("SIGNAL_TO_LEVEL"), len);
                break;
            case 0x30: // Text Description
                len = tapeBuffer[offset];
                msg = getCleanMsg(offset + 1, len);
                break;
            case 0x31: // Message Block
                len = tapeBuffer[offset + 1];
                msg = getCleanMsg(offset + 2, len);
                break;
            case 0x32: // Archive Info
                len = tapeBuffer[offset + 2];
                msg = String.format(bundle.getString("NUMBER_OF_STRINGS"), len);
                break;
            case 0x33: // Hardware Type
                msg = "";
                break;
            case 0x35: // Custom Info Block
                msg = getCleanMsg(offset, 10);
                break;
            case 0x5A: // "Glue" Block
                msg = "";
                break;
            default:
                msg = "";
        }

        return msg;
    }

    public TapeTableModel getTapeTableModel() {
        return tapeTableModel;
    }

    public void notifyTstates(long frames, int tstates) {
        long now = frames * spectrumModel.tstatesFrame + tstates;
        timeout -= (now - timeLastIn);

        timeLastIn = now;

        if (timeout > 0) {
            return;
        }

        timeout = 0;

//        if (tapeRecording) {
//            recordPulse();
//        }

        if (tapePlaying) {
            doPlay();
        }

    }

    public boolean insert(File fileName) {
        if (tapeInserted) {
            return false;
        }

        try {
            tapeFile = new BufferedInputStream(new FileInputStream(fileName));
        } catch (FileNotFoundException fex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, fex);
            return false;
        }

        try {
            tapeBuffer = new int[tapeFile.available()];
            int count = 0;
            while (count < tapeBuffer.length) {
                tapeBuffer[count++] = tapeFile.read() & 0xff;
            }
            tapeFile.close();
            filename = fileName;
        } catch (IOException ex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        tapePos = idxHeader = 0;
        tapeInserted = true;
        statePlay = State.STOP;
        timeout = timeLastIn = 0;
        tapePlaying = tapeRecording = false;
        tzxTape = filename.getName().toLowerCase().endsWith(".tzx");
        if (tzxTape) {
            if (findTZXOffsetBlocks() == -1) {
                nOffsetBlocks = 0;
                return false;
            }
//            System.out.println(String.format("Encontrados %d TZX blocks", nOffsetBlocks));
//            for(int blk = 0; blk < nOffsetBlocks; blk++) {
//                System.out.println(String.format("Block %d: %04x", blk, offsetBlocks[blk]));
//            }
        } else {
            findTAPOffsetBlocks();
//            System.out.println(String.format("Encontrados %d TAP blocks", nOffsetBlocks));
//            for(int blk = 0; blk < nOffsetBlocks; blk++) {
//                System.out.println(String.format("Block %d: %04x", blk, offsetBlocks[blk]));
//            }
        }

        tapeTableModel.fireTableDataChanged();
        lsm.setSelectionInterval(0, 0);
        cpu.setExecDone(false);
        filenameLabel = filename.getName();
        flashload = settings.isFlashload();
        updateTapeIcon();
        return true;
    }

    public boolean insertEmbeddedTape(String fileName, String extension,
        byte[] tapeData, int selectedBlock) {
        if (tapeInserted) {
            return false;
        }

//        System.out.println(String.format("fileName: %s, extension: %s", fileName, extension));
        tapeBuffer = new int[tapeData.length];
        // Hay que cambiarle el tipo, sigh!!!
        for (int idx = 0; idx < tapeData.length; idx++) {
            tapeBuffer[idx] = tapeData[idx] & 0xff;
        }

        filename = new File(fileName);
        tapePos = idxHeader = 0;
        tapeInserted = true;
        statePlay = State.STOP;
        timeout = timeLastIn = 0;
        tapePlaying = tapeRecording = false;
//        fastload = settings.isFastload();
        tzxTape = extension.startsWith("tzx");
        if (tzxTape) {
            if (findTZXOffsetBlocks() == -1) {
                nOffsetBlocks = 0;
                return false;
            }
//            System.out.println(String.format("Encontrados %d TZX blocks", nOffsetBlocks));
//            for(int blk = 0; blk < nOffsetBlocks; blk++) {
//                System.out.println(String.format("Block %d: %04x", blk, offsetBlocks[blk]));
//            }
        } else {
            findTAPOffsetBlocks();
//            System.out.println(String.format("Encontrados %d TAP blocks", nOffsetBlocks));
//            for(int blk = 0; blk < nOffsetBlocks; blk++) {
//                System.out.println(String.format("Block %d: %04x", blk, offsetBlocks[blk]));
//            }
        }
        tapeTableModel.fireTableDataChanged();
        lsm.setSelectionInterval(selectedBlock, selectedBlock);
        cpu.setExecDone(false);
        filenameLabel = filename.getName();
        flashload = settings.isFlashload();
        updateTapeIcon();
        return true;
    }

    public void eject() {
        tapeInserted = false;
        tapeBuffer = null;
        filenameLabel = null;
        statePlay = State.STOP;
        tapePlaying = tapeRecording = false;
        updateTapeIcon();
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

    public boolean isFlashLoad() {
        return flashload;
    }

    public void setFlashLoad(boolean fastmode) {
        flashload = fastmode;
    }

    public boolean isTapeInserted() {
        return tapeInserted;
    }

    public boolean isTapeReady() {
        return (tapeInserted && !tapePlaying && !tapeRecording);
    }

    public boolean isTzxTape() {
        return tzxTape;
    }

    public File getTapeFilename() {
        return filename;
    }

    public boolean play() {
        if (!tapeInserted || tapePlaying || tapeRecording) {
            return false;
        }

        tapePlaying = true;
        statePlay = State.START;
//        earBit = EAR_ON;
        tapePos = offsetBlocks[idxHeader];
        timeLastIn = 0;
        cpu.setExecDone(true);
        updateTapeIcon();
        tapeNotify.tapeStart();
        return true;
    }

    public void stop() {
        if (!tapeInserted || !tapePlaying || tapeRecording) {
            return;
        }

        statePlay = State.STOP;
        if (idxHeader == nOffsetBlocks) {
            idxHeader = 0;
        }
        lsm.setSelectionInterval(idxHeader, idxHeader);
        
        cpu.setExecDone(false);
        tapePlaying = false;
        updateTapeIcon();
        timeLastIn = 0;
        tapeNotify.tapeStop();
    }

    public boolean rewind() {
        if (!tapeInserted || tapePlaying || tapeRecording) {
            return false;
        }

        idxHeader = 0;
        lsm.setSelectionInterval(idxHeader, idxHeader);
        flashload = settings.isFlashload();

        return true;
    }

    private boolean doPlay() {
        if (!tapeInserted) {
            return false;
        }

        if (tzxTape) {
            return playTzx();
        } else {
            return playTap();
        }
    }

    private int findTAPOffsetBlocks() {
        nOffsetBlocks = 0;

        int offset = 0;
        Arrays.fill(offsetBlocks, 0);
        while (offset < tapeBuffer.length && nOffsetBlocks < offsetBlocks.length) {
            offsetBlocks[nOffsetBlocks++] = offset;
            int len = tapeBuffer[offset] + (tapeBuffer[offset + 1] << 8);
            offset += len + 2;
        }

        return nOffsetBlocks;
    }

    private boolean playTap() {
//        System.out.println(String.format("Estado de la cinta: %s", statePlay.toString()));
        switch (statePlay) {
            case STOP:
                lsm.setSelectionInterval(idxHeader, idxHeader);
                cpu.setExecDone(false);
                tapePlaying = false;
                updateTapeIcon();
                timeLastIn = 0;
                tapeNotify.tapeStop();
                break;
            case START:
                if (idxHeader == nOffsetBlocks) {
                    idxHeader = 0;
                }
                lsm.setSelectionInterval(idxHeader, idxHeader);
//                System.out.println(String.format("Playing tap block :%d", idxHeader));
                tapePos = offsetBlocks[idxHeader];
                blockLen = tapeBuffer[tapePos] + (tapeBuffer[tapePos + 1] << 8);
                tapePos += 2;
//                System.out.println("blockLen = " + blockLen);
                leaderPulses = tapeBuffer[tapePos] < 0x80 ? HEADER_PULSES : DATA_PULSES;
                earBit ^= EAR_MASK;
                timeout = LEADER_LENGHT;
                statePlay = State.LEADER;
                break;
            case LEADER:
                earBit ^= EAR_MASK;
                if (--leaderPulses > 0) {
                    timeout = LEADER_LENGHT;
                    break;
                }
                timeout = SYNC1_LENGHT;
                statePlay = State.SYNC;
                break;
            case SYNC:
                earBit ^= EAR_MASK;
                timeout = SYNC2_LENGHT;
                statePlay = State.NEWBYTE;
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
                timeout = bitTime;
                statePlay = State.HALF2;
                break;
            case HALF2:
                earBit ^= EAR_MASK;
                timeout = bitTime;
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
                timeout = END_BLOCK_PAUSE; // 1 seg. pausa
                statePlay = State.PAUSE_STOP;
//                System.out.println(String.format("tapeBufferLength: %d, tapePos: %d",
//                    tapeBuffer.length, tapePos));
                break;
            case PAUSE_STOP:
                if (tapePos == tapeBuffer.length) {
                    statePlay = State.STOP;
                    idxHeader = 0;
                    timeout = 1;
                    lsm.setSelectionInterval(idxHeader, idxHeader);
//                    tapeNotify.tapeStop();
                } else {
                    idxHeader++;
                    statePlay = State.START; // START
                }
        }
        return true;
    }

    private boolean isTZXHeader(int offset) {
        if (tapeBuffer[offset] == 'Z' && tapeBuffer[offset + 1] == 'X'
            && tapeBuffer[offset + 2] == 'T' && tapeBuffer[offset + 3] == 'a'
            && tapeBuffer[offset + 4] == 'p' && tapeBuffer[offset + 5] == 'e'
            && tapeBuffer[offset + 6] == '!') {
            return true;
        }
        return false;
    }

    private int findTZXOffsetBlocks() {
        nOffsetBlocks = 0;

        int offset = 0; // saltamos la cabecera del TZX
        int len;
        Arrays.fill(offsetBlocks, 0);
        while (offset < tapeBuffer.length && nOffsetBlocks < offsetBlocks.length) {
            offsetBlocks[nOffsetBlocks++] = offset;
            if (isTZXHeader(offset)) {
                offset += 10;
                continue;
            }

            switch (tapeBuffer[offset]) {
                case 0x10: // Standard speed data block
                    len = tapeBuffer[offset + 3] + (tapeBuffer[offset + 4] << 8);
                    offset += len + 5;
                    break;
                case 0x11: // Turbo speed data block
                    len = tapeBuffer[offset + 16]
                        + (tapeBuffer[offset + 17] << 8)
                        + (tapeBuffer[offset + 18] << 16);
                    offset += len + 19;
                    break;
                case 0x12: // Pure Tone Block
//                    len = tapeBuffer[offset + 1] + (tapeBuffer[offset + 2] << 8);
                    offset += 5;
                    break;
                case 0x13: // Pulse Sequence Block
                    len = tapeBuffer[offset + 1];
                    offset += len * 2 + 2;
                    break;
                case 0x14: // Pure Data Block
                    len = tapeBuffer[offset + 8]
                        + (tapeBuffer[offset + 9] << 8)
                        + (tapeBuffer[offset + 10] << 16);
                    offset += len + 11;
                    break;
                case 0x15: // Direct Data Block
                    len = tapeBuffer[offset + 6]
                        + (tapeBuffer[offset + 7] << 8)
                        + (tapeBuffer[offset + 8] << 16);
                    offset += len + 9;
                    break;
                case 0x18: // CSW Recording Block
                case 0x19: // Generalized Data Block
                    len = tapeBuffer[offset + 1] + (tapeBuffer[offset + 2] << 8)
                        + (tapeBuffer[offset + 3] << 16) + (tapeBuffer[offset + 4] << 24);
                    offset += len + 5;
                    break;
                case 0x20: // Pause (silence) or 'Stop the Tape' command
                case 0x23: // Jump to Block
                case 0x24: // Loop Start
                    offset += 3;
                    break;
                case 0x21: // Group Start
                    len = tapeBuffer[offset + 1];
                    offset += len + 2;
                    break;
                case 0x22: // Group End
                case 0x25: // Loop End
                case 0x27: // Return from Sequence
                    offset++;
                    break;
                case 0x26: // Call Sequence
                    len = tapeBuffer[offset + 1] + (tapeBuffer[offset + 2] << 8);
                    offset += len * 2 + 3;
                    break;
                case 0x28: // Select Block
                case 0x32: // Archive Info
                    len = tapeBuffer[offset + 1] + (tapeBuffer[offset + 2] << 8);
                    offset += len + 3;
                    break;
                case 0x2A: // Stop the tape if in 48K mode
                    offset += 5;
                    break;
                case 0x2B: // Set Signal Level
                    offset += 6;
                    break;
                case 0x30: // Text Description
                    len = tapeBuffer[offset + 1];
                    offset += len + 2;
                    break;
                case 0x31: // Message Block
                    len = tapeBuffer[offset + 2];
                    offset += len + 3;
                    break;
                case 0x33: // Hardware Type
                    len = tapeBuffer[offset + 1];
                    offset += len * 3 + 2;
                    break;
                case 0x35: // Custom Info Block
                    len = tapeBuffer[offset + 17] + (tapeBuffer[offset + 18] << 8)
                        + (tapeBuffer[offset + 19] << 16) + (tapeBuffer[offset + 20] << 24);
                    offset += len + 21;
                    break;
                case 0x5A: // "Glue" Block
                    offset += 10;
                    break;
                default:
                    System.out.println(String.format("Block ID: %02x", tapeBuffer[tapePos]));
                    return -1; // Error en TZX
            }
        }
        return nOffsetBlocks;
    }

    private boolean playTzx() {
        boolean repeat;

        do {
            repeat = false;
            switch (statePlay) {
                case STOP:
                    cpu.setExecDone(false);
                    tapePlaying = false;
                    updateTapeIcon();
                    tapeNotify.tapeStop();
                    break;
                case START:
                    if (idxHeader == nOffsetBlocks) {
                        idxHeader = 0;
                    }
                    tapePos = offsetBlocks[idxHeader];

                    statePlay = State.TZX_HEADER;
                    repeat = true;
                    break;
                case LEADER:
                    earBit ^= EAR_MASK;
                case LEADER_NOCHG:
                    if (--leaderPulses != 0) {
                        timeout = leaderLenght;
                        statePlay = State.LEADER;
                        break;
                    }
                    timeout = sync1Lenght;
                    statePlay = State.SYNC;
                    break;
                case SYNC:
                    earBit ^= EAR_MASK;
                    timeout = sync2Lenght;
                    statePlay = State.NEWBYTE;
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
                    timeout = bitTime;
                    statePlay = State.HALF2;
                    break;
                case HALF2:
                    earBit ^= EAR_MASK;
                    timeout = bitTime;
                    mask >>>= 1;
                    if (blockLen == 1 && bitsLastByte < 8) {
                        if (mask == (0x80 >>> bitsLastByte)) {
                            statePlay = State.PAUSE;
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
                        statePlay = State.PAUSE;
                    }
                    break;
                case PAUSE:
                    earBit ^= EAR_MASK;
                    statePlay = State.TZX_HEADER;
                    if (endBlockPause == 0) {
                        repeat = true;
                        break;
                    }
                    timeout = endBlockPause;
                    break;
                case TZX_HEADER:
                    if (idxHeader == nOffsetBlocks) {
//                        System.out.println(String.format("Last Ear: %02x", earBit));
                        statePlay = State.STOP;
                        idxHeader = 0;
                        repeat = true;
                        break;
                    }
                    decodeTzxHeader();
                    break;
                case PURE_TONE:
                    earBit ^= EAR_MASK;
                case PURE_TONE_NOCHG:
                    if (leaderPulses-- != 0) {
                        timeout = leaderLenght;
                        statePlay = State.PURE_TONE;
                        break;
                    }
                    statePlay = State.TZX_HEADER;
                    repeat = true;
                    break;
                case PULSE_SEQUENCE:
                    earBit ^= EAR_MASK;
                case PULSE_SEQUENCE_NOCHG:
                    if (leaderPulses-- != 0) {
                        timeout = (tapeBuffer[tapePos] + (tapeBuffer[tapePos + 1] << 8));
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
                    System.out.println(String.format("earBit: %02x", earBit));
                case NEWDR_BIT:
                    boolean micPulse;
                    if ((tapeBuffer[tapePos] & mask) != 0) {
//                        earBit = EAR_ON;
                        micPulse = true;
                    } else {
//                        earBit = EAR_OFF;
                        micPulse = false;
                    }
                    earBit ^= EAR_MASK;
                    
                    while (((tapeBuffer[tapePos] & mask) != 0) == micPulse) {
                        timeout += zeroLenght;

                        mask >>>= 1;
                        if (mask == 0) {
                            mask = 0x80;
                            tapePos++;
                            if (--blockLen == 0) {
                                statePlay = State.PAUSE;
                                break;
                            }
                        } else {
                            if (blockLen == 1 && bitsLastByte < 8) {
                                if (mask == (0x80 >>> bitsLastByte)) {
                                    statePlay = State.PAUSE;
                                    tapePos++;
                                    break;
                                }
                            }
                        }
                    }
                    if (timeout == 0) {
                        System.out.println("ERROR!, timeout a cero");
                    }
//                    timeout = zeroLenght;
//                    mask >>>= 1;
//                    if (blockLen == 1 && bitsLastByte < 8) {
//                        if (mask == (0x80 >>> bitsLastByte)) {
//                            statePlay = State.PAUSE;
//                            tapePos++;
//                            break;
//                        }
//                    }
//
//                    if (mask != 0) {
//                        statePlay = State.NEWDR_BIT;
//                        break;
//                    }
//
//                    tapePos++;
//                    if (--blockLen > 0) {
//                        statePlay = State.NEWDR_BYTE;
//                    } else {
////                            System.out.println(String.format("Last byte: %02x",
////                                    tapeBuffer[tapePos-1]));
//                        statePlay = State.PAUSE;
//                    }
                    break;
                case PAUSE_STOP:
                    if (endBlockPause == 0) {
                        statePlay = State.STOP;
//                        tapeNotify.tapeStop();
                        repeat = true;
                    } else {
                        timeout = endBlockPause;
                        statePlay = State.TZX_HEADER;
                    }
                    break;
            }
        } while (repeat);
        return true;
    }

    private void decodeTzxHeader() {
        boolean repeat = true;
        while (repeat) {
            if (idxHeader == nOffsetBlocks) {
                return;
            }

            lsm.setSelectionInterval(idxHeader, idxHeader);
//            System.out.println(String.format("Playing tzx block :%d", idxHeader + 1));
            tapePos = offsetBlocks[idxHeader];

            if (isTZXHeader(tapePos)) {
                idxHeader++;
                continue;
            }

            switch (tapeBuffer[tapePos]) {
                case 0x10: // Standard speed data block
                    leaderLenght = LEADER_LENGHT;
                    sync1Lenght = SYNC1_LENGHT;
                    sync2Lenght = SYNC2_LENGHT;
                    zeroLenght = ZERO_LENGHT;
                    oneLenght = ONE_LENGHT;
                    bitsLastByte = 8;
                    endBlockPause = (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8)) * 3500;
                    if (endBlockPause == 0) {
                        endBlockPause = END_BLOCK_PAUSE;
                    }
                    blockLen = tapeBuffer[tapePos + 3] + (tapeBuffer[tapePos + 4] << 8);
                    tapePos += 5;
                    leaderPulses = tapeBuffer[tapePos] < 0x80 ? HEADER_PULSES : DATA_PULSES;
                    timeout = leaderLenght;
                    statePlay = State.LEADER_NOCHG;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x11: // Turbo speed data block
                    leaderLenght = (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8));
                    sync1Lenght = (tapeBuffer[tapePos + 3]
                        + (tapeBuffer[tapePos + 4] << 8));
                    sync2Lenght = (tapeBuffer[tapePos + 5]
                        + (tapeBuffer[tapePos + 6] << 8));
                    zeroLenght = (tapeBuffer[tapePos + 7]
                        + (tapeBuffer[tapePos + 8] << 8));
                    oneLenght = (tapeBuffer[tapePos + 9]
                        + (tapeBuffer[tapePos + 10] << 8));
                    leaderPulses = (tapeBuffer[tapePos + 11]
                        + (tapeBuffer[tapePos + 12] << 8));
                    bitsLastByte = tapeBuffer[tapePos + 13];
                    endBlockPause = (tapeBuffer[tapePos + 14]
                        + (tapeBuffer[tapePos + 15] << 8)) * 3500;
                    blockLen = tapeBuffer[tapePos + 16]
                        + (tapeBuffer[tapePos + 17] << 8)
                        + (tapeBuffer[tapePos + 18] << 16);
                    tapePos += 19;
                    timeout = leaderLenght;
                    // Hasta el 21/01/2011, el estado era State.LEADER_NOCHG
                    // Así cargan los juegos problemáticos indicados en la cabecera
                    statePlay = State.LEADER;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x12: // Pure Tone Block
                    leaderLenght = (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8));
                    leaderPulses = (tapeBuffer[tapePos + 3]
                        + (tapeBuffer[tapePos + 4] << 8));
                    tapePos += 5;
                    statePlay = State.PURE_TONE_NOCHG;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x13: // Pulse Sequence Block
                    leaderPulses = tapeBuffer[tapePos + 1];
                    tapePos += 2;
                    statePlay = State.PULSE_SEQUENCE_NOCHG;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x14: // Pure Data Block
                    zeroLenght = (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8));
                    oneLenght = (tapeBuffer[tapePos + 3]
                        + (tapeBuffer[tapePos + 4] << 8));
                    bitsLastByte = tapeBuffer[tapePos + 5];
                    endBlockPause = (tapeBuffer[tapePos + 6]
                        + (tapeBuffer[tapePos + 7] << 8)) * 3500;
                    blockLen = tapeBuffer[tapePos + 8]
                        + (tapeBuffer[tapePos + 9] << 8)
                        + (tapeBuffer[tapePos + 10] << 16);
                    tapePos += 11;
                    statePlay = State.NEWBYTE_NOCHG;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x15: // Direct Data Block
                    zeroLenght = (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8));
                    endBlockPause = (tapeBuffer[tapePos + 3]
                        + (tapeBuffer[tapePos + 4] << 8)) * 3500;
                    bitsLastByte = tapeBuffer[tapePos + 5];
                    blockLen = tapeBuffer[tapePos + 6]
                        + (tapeBuffer[tapePos + 7] << 8)
                        + (tapeBuffer[tapePos + 8] << 16);
                    tapePos += 9;
                    statePlay = State.NEWDR_BYTE;
                    idxHeader++;
                    repeat = false;
                    break;
                case 0x18: // CSW Recording Block
                    idxHeader++;
                    System.out.println("CSW Block not supported!. Skipping...");
                    break;
                case 0x19: // Generalized Data Block
//                    printGDBHeader(tapePos);
                    idxHeader++;
                    System.out.println("Gen. Data Block not supported!. Skipping...");
                    break;
                case 0x20: // Pause (silence) or 'Stop the Tape' command
                    endBlockPause = (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8)) * 3500;
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
                    short target = (short) (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8));
                    idxHeader += target;
                    break;
                case 0x24: // Loop Start
                    nLoops = tapeBuffer[tapePos + 1] + (tapeBuffer[tapePos + 2] << 8);
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
                    idxHeader++;
                    break;
                case 0x27: // Return from Sequence
                    idxHeader++;
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
                case 0x5A: // "Glue" Block
                    idxHeader++;
                    break;
                default:
                    System.out.println(String.format("Block ID: %02x", tapeBuffer[tapePos]));
                    repeat = false;
                    idxHeader++;
            }
        }
//        System.out.println(String.format("tapeBufferLength: %d, tapePos: %d, blockLen: %d",
//                    tapeBuffer.length, tapePos, blockLen));
    }

    private void printGDBHeader(int index) {
        int blkLenght = tapeBuffer[index + 1] + (tapeBuffer[index + 2] << 8)
            + (tapeBuffer[index + 3] << 16) + (tapeBuffer[index + 4] << 24);
        int pause = tapeBuffer[index + 5] + (tapeBuffer[index + 6] << 8);
        int totp = tapeBuffer[index + 7] + (tapeBuffer[index + 8] << 8)
            + (tapeBuffer[index + 9] << 16) + (tapeBuffer[index + 10] << 24);
        int npp = tapeBuffer[index + 11];
        int asp = tapeBuffer[index + 12];
        int totd = tapeBuffer[index + 13] + (tapeBuffer[index + 14] << 8)
            + (tapeBuffer[index + 15] << 16) + (tapeBuffer[index + 16] << 24);
        int npd = tapeBuffer[index + 17];
        int asd = tapeBuffer[index + 18];

        System.out.println(String.format("GDB size: %d", blkLenght));
        System.out.println(String.format("Pause: %d ms", pause));
        System.out.println(String.format("TOTP: %d", totp));
        System.out.println(String.format("NPP: %d", npp));
        System.out.println(String.format("ASP: %d", asp));
        System.out.println(String.format("TOTD: %d", totd));
        System.out.println(String.format("NPD: %d", npd));
        System.out.println(String.format("ASD: %d", asd));
    }

    public boolean flashLoad(Memory memory) {

        if (!tapeInserted || cpu == null) {
            return false;
        }

//        System.out.println("flashload!");

        if (idxHeader == nOffsetBlocks) {
            flashload = false;
            cpu.setCarryFlag(false);
            return false;
        }

        if (tzxTape) {
            // Fastload only with Standard Speed Tape Blocks (and some
            // identified erroneusly as Turbo Blocks
            // (Midnight Resistance 128k as an example))
            while (idxHeader < nOffsetBlocks) {
                tapePos = offsetBlocks[idxHeader];
                if (tapeBuffer[tapePos] == 0x10 || tapeBuffer[tapePos] == 0x11) {
                    break;
                }
                idxHeader++;
            }

            if (idxHeader == nOffsetBlocks) {
                cpu.setCarryFlag(false);
                idxHeader = 0;
                lsm.setSelectionInterval(idxHeader, idxHeader);
                return false;
            }

            lsm.setSelectionInterval(idxHeader, idxHeader);
            if (tapeBuffer[tapePos] == 0x10) {
                blockLen = tapeBuffer[tapePos + 3] + (tapeBuffer[tapePos + 4] << 8);
                tapePos += 5;
            } else {
                blockLen = tapeBuffer[tapePos + 16]
                    + (tapeBuffer[tapePos + 17] << 8)
                    + (tapeBuffer[tapePos + 18] << 16);
                tapePos += 19;
            }
        } else {
            tapePos = offsetBlocks[idxHeader];
            blockLen = tapeBuffer[tapePos] + (tapeBuffer[tapePos + 1] << 8);
//        System.out.println(String.format("tapePos: %X. blockLen: %X", tapePos, blockLen));
            tapePos += 2;
        }

        // ¿Coincide el flag? (está en el registro A)
        if (cpu.getRegA() != tapeBuffer[tapePos]) {
            cpu.xor(tapeBuffer[tapePos]);
            idxHeader++;
            return false;
        }
        // La paridad incluye el byte de flag
        cpu.setRegA(tapeBuffer[tapePos]);

        int count = 0;
        int addr = cpu.getRegIX();    // Address start
        int nBytes = cpu.getRegDE();  // Lenght
        while (count < nBytes && count < blockLen - 1) {
            memory.writeByte(addr, (byte) tapeBuffer[tapePos + count + 1]);
            cpu.xor(tapeBuffer[tapePos + count + 1]);
            addr = (addr + 1) & 0xffff;
            count++;
        }

        // Se cargarón los bytes pedidos en DE
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
        lsm.setSelectionInterval(idxHeader, idxHeader);

//        System.out.println(String.format("Salida -> IX: %04X DE: %04X AF: %04X",
//            cpu.getRegIX(), cpu.getRegDE(), cpu.getRegAF()));
        return true;
    }

    public void saveTapeBlock(Memory memory) {
        int addr = cpu.getRegIX();   // Start Address
        int nBytes = cpu.getRegDE(); // Lenght
        BufferedOutputStream fOut = null;
        record = new ByteArrayOutputStream();

        // Si el archivo es nuevo y es un TZX, necesita la preceptiva cabecera
        if (filename.getName().toLowerCase().endsWith("tzx")) {
            if (nOffsetBlocks == 0) {
                record.write('Z');
                record.write('X');
                record.write('T');
                record.write('a');
                record.write('p');
                record.write('e');
                record.write('!');
                record.write(0x1A);
                record.write(01); // TZX v1.20
                record.write(20);
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

        try {
            fOut = new BufferedOutputStream(new FileOutputStream(filename, true));
            record.writeTo(fOut);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                record.close();
                fOut.close();
            } catch (IOException ex) {
                Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        eject();
        insert(filename);
    }

    public boolean startRecording() {
        if (!isTapeReady() || !filename.getName().toLowerCase().endsWith(".tzx")) {
            return false;
        }

        record = new ByteArrayOutputStream();

        timeLastIn = timeLastOut = 0;
        tapeRecording = true;
//        timeout = settings.isHighSamplingFreq() ? 79 : 158;
        updateTapeIcon();

        return true;
    }

    public boolean stopRecording() {
        if (!tapeRecording) {
            return false;
        }

        if (bitsLastByte != 0) {
            byteTmp <<= (8 - bitsLastByte);
            record.write(byteTmp);
        }

//        System.out.println(String.format("Record size: %d", record.size()));

        BufferedOutputStream fOut = null;
        try {
            fOut = new BufferedOutputStream(new FileOutputStream(filename, true));
            // Si el archivo es nuevo y es un TZX, necesita la preceptiva cabecera
            if (nOffsetBlocks == 0) {
                fOut.write('Z');
                fOut.write('X');
                fOut.write('T');
                fOut.write('a');
                fOut.write('p');
                fOut.write('e');
                fOut.write('!');
                fOut.write(0x1A);
                fOut.write(0x01);
                fOut.write(0x20);
            }
            // Y ahora la cabecera de Direct Data Recording
            fOut.write(0x15); // TZX ID: Direct Recording Block
            fOut.write(settings.isHighSamplingFreq() ? 79 : 158);
            fOut.write(0x00); // T-states per sample
            fOut.write(0x00);
            fOut.write(0x00); // no pause
            fOut.write(bitsLastByte);
            fOut.write(record.size());
            fOut.write(record.size() >>> 8);
            fOut.write(record.size() >>> 16);
            record.writeTo(fOut);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fOut.close();
            } catch (IOException ex) {
                Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        eject();
        insert(filename);
        tapeRecording = false;
        updateTapeIcon();

        return true;
    }

//    public void setPulse(boolean bit) {
//        pulse = bit;
//    }

//    public void recordPulse() {
//        timeout = settings.isHighSamplingFreq() ? 79 : 158;
//
//        if (bitsLastByte == 8) {
//            record.write(byteTmp);
//            bitsLastByte = 0;
//            byteTmp = 0;
//        }
//
//        byteTmp <<= 1;
//        if (pulse) {
//            byteTmp |= 0x01;
//        }
//        bitsLastByte++;
//    }
    
    public void recordPulse(long tstates, boolean micState) {
        if (timeLastOut == 0) {
            timeLastOut = tstates;
            micBit = micState;
            return;
        }
        
        long len = tstates - timeLastOut;
        int sample = settings.isHighSamplingFreq() ? 79 : 158;
//        long pulses = (len + (sample >>> 1)) / sample;
        long pulses = len / sample;
//        System.out.println(
//                String.format("(recordPulse2) Pulse Len: %d, , pulses: %d, state: %b",
//                    len, pulses, micBit));
        
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
        timeLastOut = tstates;
        micBit = micState;
    }
    
    private javax.swing.JLabel tapeIcon;
    private boolean enabledIcon;

    public void setTapeIcon(javax.swing.JLabel tapeLabel) {
        tapeIcon = tapeLabel;
        updateTapeIcon();
    }

    private void updateTapeIcon() {
        if (tapeIcon == null) {
            return;
        }

        if (!tapePlaying && !tapeRecording) {
            enabledIcon = false;
        } else {
            enabledIcon = true;
        }

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                tapeIcon.setToolTipText(filenameLabel);
                tapeIcon.setEnabled(enabledIcon);
            }
        });
    }

    public class TapeTableModel extends AbstractTableModel {

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
            String msg;

//        System.out.println(String.format("getValueAt row %d, col %d", row, col));
            switch (col) {
                case 0:
                    return String.format("%4d", row + 1);
                case 1:
                    msg = getBlockType(row);
                    break;
                case 2:
                    msg = getBlockInfo(row);
                    break;
                default:
                    return "NON EXISTANT COLUMN!";
            }
            return msg;
        }

        @Override
        public String getColumnName(int col) {
            java.util.ResourceBundle bundle = 
                java.util.ResourceBundle.getBundle("gui/Bundle"); // NOI18N

            String msg;

            switch (col) {
                case 0:
                    msg = bundle.getString("JSpeccy.tapeCatalog.columnModel.title0");
                    break;
                case 1:
                    msg = bundle.getString("JSpeccy.tapeCatalog.columnModel.title1");
                    break;
                case 2:
                    msg = bundle.getString("JSpeccy.tapeCatalog.columnModel.title2");
                    break;
                default:
                    msg = "COLUMN ERROR!";
            }
            return msg;
        }
    }
}
