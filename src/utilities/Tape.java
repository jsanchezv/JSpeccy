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
 */
package utilities;

import configuration.TapeType;
import java.io.BufferedOutputStream;
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
import machine.MachineTypes;
import machine.Memory;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Tape {

    private Z80 cpu;
    private FileInputStream tapeFile;
    private File filename;
    private String filenameLabel;
//    private File tapeName;
    private int tapeBuffer[];
    private int offsetBlocks[] = new int[4096]; // el AMC tiene más de 1500 bloques!!!
    private int nOffsetBlocks;
    private int idxHeader;
    private int tapePos;
    private int blockLen;
    private int mask;
    private int bitTime;

    private enum State {

        STOP, START, LEADER, LEADER_NOCHG, SYNC, NEWBYTE,
        NEWBYTE_NOCHG, NEWBIT, HALF2, PAUSE, TZX_HEADER, PURE_TONE,
        PURE_TONE_NOCHG, PULSE_SEQUENCE, PULSE_SEQUENCE_NOCHG, NEWDR_BYTE,
        NEWDR_BIT, PAUSE_STOP
    };
    private State statePlay;
    private int earBit;
    private long timeout;
    private long timeLastIn;
//    private boolean fastload;
    private boolean tapeInserted;
    private boolean tzxTape;
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
        tapeInserted = tzxTape = false;
        tapePos = 0;
        timeout = timeLastIn = 0;
//        fastload = settings.isFastload();
        earBit = 0xbf;
        spectrumModel = MachineTypes.SPECTRUM48K;
        filenameLabel = null;
        nOffsetBlocks = 0;
        idxHeader = 0;
        Arrays.fill(offsetBlocks, 0);
        tapeTableModel = new TapeTableModel(this);
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
        if (!tapeInserted || isPlaying() || block > nOffsetBlocks) {
            return;
        }

//        tapePos = offsetBlocks[block];
        idxHeader = block;
//        lsm.setSelectionInterval(block, block);
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
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("utilities/Bundle"); // NOI18N
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
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("utilities/Bundle"); // NOI18N
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
//        System.out.println("timeout: " + timeout);
        timeLastIn = now;
        if (timeout > 0) {
            return;
        }

//        System.out.println("timeout: " + timeout);
        timeout = 0;
        doPlay();

    }

    public boolean insert(File fileName) {
        if (tapeInserted) {
            return false;
        }

        try {
            tapeFile = new FileInputStream(fileName);
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
//        fastload = settings.isFastload();
        tzxTape = filename.getName().toLowerCase().endsWith(".tzx");
        if (tzxTape) {
            findTZXOffsetBlocks();
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
        updateTapeIcon();
        return true;
    }

    public void eject() {
        tapeInserted = false;
        tapeBuffer = null;
        filenameLabel = null;
        statePlay = State.STOP;
        updateTapeIcon();
    }

    public int getEarBit() {
        return earBit;
    }

    public void setEarBit(boolean earValue) {
        earBit = earValue ? 0xff : 0xbf;
    }

    public boolean isPlaying() {
        return statePlay != State.STOP;
    }

    public boolean isStopped() {
        return statePlay == State.STOP;
    }

    public boolean isFlashLoad() {
        return settings.isFlashload();
    }

    public void setFlashLoad(boolean fastmode) {
        settings.setFlashload(fastmode);
    }

    public boolean isTapeInserted() {
        return tapeInserted;
    }

    public boolean isTapeReady() {
        return (tapeInserted && statePlay == State.STOP);
    }

    public boolean isTzxTape() {
        return tzxTape;
    }

    public boolean play() {
        if (!tapeInserted || statePlay != State.STOP) {
            return false;
        }

        statePlay = State.START;
        tapePos = offsetBlocks[idxHeader];
        timeLastIn = 0;
        earBit = 0xbf;
        cpu.setExecDone(true);
        updateTapeIcon();
        tapeNotify.tapeStart();
        return true;
    }

    public void stop() {
        if (!tapeInserted || statePlay == State.STOP) {
            return;
        }

        statePlay = State.STOP;
        if (idxHeader == nOffsetBlocks) {
            idxHeader = 0;
        }
    }

    public boolean rewind() {
        if (!tapeInserted || statePlay != State.STOP) {
            return false;
        }

        idxHeader = 0;
        lsm.setSelectionInterval(idxHeader, idxHeader);

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
                updateTapeIcon();
                cpu.setExecDone(false);
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
                earBit = 0xbf;
                timeout = LEADER_LENGHT;
                statePlay = State.LEADER;
                break;
            case LEADER:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                if (--leaderPulses > 0) {
                    timeout = LEADER_LENGHT;
                    break;
                }
                timeout = SYNC1_LENGHT;
                statePlay = State.SYNC;
                break;
            case SYNC:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                timeout = SYNC2_LENGHT;
                statePlay = State.NEWBYTE;
                break;
            case NEWBYTE:
                mask = 0x80; // se empieza por el bit 7
            case NEWBIT:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                if ((tapeBuffer[tapePos] & mask) == 0) {
                    bitTime = ZERO_LENGHT;
                } else {
                    bitTime = ONE_LENGHT;
                }
                timeout = bitTime;
                statePlay = State.HALF2;
                break;
            case HALF2:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
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
                earBit = earBit == 0xbf ? 0xff : 0xbf;
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
                    tapeNotify.tapeStop();
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
                    earBit = earBit == 0xbf ? 0xff : 0xbf;
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
                    earBit = earBit == 0xbf ? 0xff : 0xbf;
                    timeout = sync2Lenght;
                    statePlay = State.NEWBYTE;
                    break;
                case NEWBYTE_NOCHG:
                    // este cambio es para que se deshaga al entrar en el case
                    // de NEWBIT.
                    earBit = earBit == 0xbf ? 0xff : 0xbf;
                case NEWBYTE:
                    mask = 0x80; // se empieza por el bit 7
                case NEWBIT:
                    earBit = earBit == 0xbf ? 0xff : 0xbf;
                    if ((tapeBuffer[tapePos] & mask) == 0) {
                        bitTime = zeroLenght;
                    } else {
                        bitTime = oneLenght;
                    }
                    timeout = bitTime;
                    statePlay = State.HALF2;
                    break;
                case HALF2:
                    earBit = earBit == 0xbf ? 0xff : 0xbf;
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
                    earBit = earBit == 0xbf ? 0xff : 0xbf;
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
                        //earBit = earBit == 0xbf ? 0xff : 0xbf;
                        statePlay = State.STOP;
                        idxHeader = 0;
                        repeat = true;
                        break;
                    }
                    decodeTzxHeader();
                    break;
                case PURE_TONE:
                    earBit = earBit == 0xbf ? 0xff : 0xbf;
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
                    earBit = earBit == 0xbf ? 0xff : 0xbf;
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
                case NEWDR_BIT:
                    if ((tapeBuffer[tapePos] & mask) == 0) {
                        earBit = 0xbf;
                    } else {
                        earBit = 0xff;
                    }
                    timeout = zeroLenght;
                    mask >>>= 1;
                    if (blockLen == 1 && bitsLastByte < 8) {
                        if (mask == (0x80 >>> bitsLastByte)) {
                            statePlay = State.PAUSE;
                            tapePos++;
                            break;
                        }
                    }

                    if (mask != 0) {
                        statePlay = State.NEWDR_BIT;
                        break;
                    }

                    tapePos++;
                    if (--blockLen > 0) {
                        statePlay = State.NEWDR_BYTE;
                    } else {
//                            System.out.println(String.format("Last byte: %02x",
//                                    tapeBuffer[tapePos-1]));
                        statePlay = State.PAUSE;
                    }
                    break;
                case PAUSE_STOP:
                    if (endBlockPause == 0) {
                        statePlay = State.STOP;
                        tapeNotify.tapeStop();
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
                    statePlay = State.LEADER_NOCHG;
                    idxHeader++;
                    repeat = false;
                    //earBit = 0xff; // ver nota en la cabecera
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
                    //printGDBHeader(tapePos);
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
                    short target = (short)(tapeBuffer[tapePos + 1] + (tapeBuffer[tapePos + 2] << 8));
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
                    earBit = tapeBuffer[tapePos + 5] == 0 ? 0xbf : 0xff;
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

    public void flashLoad(Memory memory) {

        if (!tapeInserted || cpu == null) {
            return;
        }

//        System.out.println("flashload!");

        if (idxHeader == nOffsetBlocks) {
            cpu.setCarryFlag(false);
            return;
        }

        if(tzxTape) {            
            // Fastload only with Standard Speed Tape Blocks (and some
            // identified erroneusly as Turbo Blocks
            // (Midnight Resistance 128k as an example)
            while (idxHeader < nOffsetBlocks) {
                tapePos = offsetBlocks[idxHeader];
                if (tapeBuffer[tapePos] == 0x10 || tapeBuffer[tapePos] == 0x11)
                    break;
                    idxHeader++;
            }

            if (idxHeader == nOffsetBlocks) {
                cpu.setCarryFlag(false);
                idxHeader = 0;
                lsm.setSelectionInterval(idxHeader, idxHeader);
                return;
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
            return;
        }
        // La paridad incluye el byte de flag
        cpu.setRegA(tapeBuffer[tapePos]);

        int count = 0;
        int addr = cpu.getRegIX();    // Address start
        int nBytes = cpu.getRegDE();  // Lenght
        while (count < nBytes && count < blockLen - 1) {
            memory.writeByte(addr, (byte)tapeBuffer[tapePos + count + 1]);
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
        return;
    }

    public void saveTapeBlock(Memory memory) {
        int addr = cpu.getRegIX();   // Start Address
        int nBytes = cpu.getRegDE(); // Lenght
        BufferedOutputStream fOut = null;

        try {
            fOut = new BufferedOutputStream(new FileOutputStream(filename, true));
            // Si el archivo es nuevo y es un TZX, necesita la preceptiva cabecera
            if (filename.getName().toLowerCase().endsWith("tzx")) {
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
                // Y ahora la cabecera de Normal Speed block
                fOut.write(0x10); // TZX ID: Normal Speed Block
                fOut.write(0xE8);
                fOut.write(0x03); // pausa de 1000 ms estándar
            }
            fOut.write(nBytes + 2);
            fOut.write((nBytes + 2) >>> 8);
            int parity = cpu.getRegA();
            fOut.write(parity);
            int value;
            for(int address = addr; address < addr + nBytes; address++) {
                value = memory.readByte(address) & 0xff;
                fOut.write(value);
                parity ^= value;
            }
            fOut.write(parity);
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

        if (statePlay == State.STOP) {
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
}
