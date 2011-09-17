/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import machine.Spectrum;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Tape {
    private Z80 cpu;
    private FileInputStream tapeFile;
//    private File tapeName;
    private int tapeBuffer[];
    private int tapePos;
    private int blockLen;
    private int mask;
    private int bitTime;
    private enum State { STOP, START, LEADER, LEADER_NOCHG, SYNC, NEWBYTE,
        NEWBYTE_NOCHG,NEWBIT, HALF2, PAUSE, TZX_HEADER, PURE_TONE,
        PURE_TONE_NOCHG, PULSE_SEQUENCE, PULSE_SEQUENCE_NOCHG, PAUSE_STOP };
    private State statePlay;
    private int earBit;
    private long timeout;
    private long timeLastIn;
    private boolean fastload;
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
    private int targetJump;

    public Tape(Z80 z80) {
        cpu = z80;
        statePlay = State.STOP;
        tapeInserted = tzxTape = false;
        tapePos = 0;
        timeout = timeLastIn = 0;
        fastload = true;
        earBit = 0xbf;
    }

    public void notifyTstates(long tstates) {
        if (timeout == 0)
            return;

        timeout -= tstates;
        if (timeout > 0)
            return;

        //System.out.println("timeout: " + timeout);
        timeout = 0;
        doPlay();

    }

    public boolean insert(File filename) {
        if( tapeInserted )
            return false;

        try {
            tapeFile = new FileInputStream(filename);
        } catch( FileNotFoundException fex ) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, fex);
            return false;
        }

        try {
            tapeBuffer = new int[tapeFile.available()];
            int count = 0;
            while( count < tapeBuffer.length )
                tapeBuffer[count++] = tapeFile.read() & 0xff;
            tapeFile.close();
        } catch (IOException ex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        tapePos = 0;
        tapeInserted = true;
        statePlay = State.STOP;
        timeout = timeLastIn = 0;
        fastload = true;
        earBit = 0xbf;
        tzxTape = filename.getName().toLowerCase().endsWith(".tzx");
        if (tzxTape) {
            fastload = false;
            tapePos = 10; // saltamos la cabecera
        }
        return true;
    }

    public void eject() {
        tapeInserted = false;
        tapeBuffer = null;
    }

    public int getEarBit(long frames, int tstates) {
        if (statePlay == State.STOP)
            return earBit;

        if (timeLastIn == 0) {
            timeLastIn = frames * Spectrum.FRAMES48k + tstates;
            return earBit;
        }

        long now = frames * Spectrum.FRAMES48k + tstates;
        notifyTstates(now - timeLastIn);
        timeLastIn = now;
        
        return earBit;
    }

    public boolean isPlaying() {
        return statePlay != State.STOP ? true : false;
    }

    public boolean isStopped() {
        return statePlay == State.STOP ? true : false;
    }

    public boolean isFastload() {
        return fastload;
    }

    public void setFastload(boolean fastmode) {
        fastload = fastmode;
    }

    public boolean isTapeInserted() {
        return tapeInserted;
    }

    public boolean play() {
        if (!tapeInserted || statePlay != State.STOP)
            return false;

        statePlay = State.START;
        fastload = false;
        timeLastIn = 0;
        timeout = 1; // espera mínima
        earBit = 0xbf;
        return true;
    }

    public void stop() {
        if (!tapeInserted)
            return;

        statePlay = State.STOP;
        tapePos += blockLen;
        if( tapePos == tapeBuffer.length )
            if (tzxTape)
                tapePos = 10;
            else
                tapePos = 0;
    }

    public boolean rewind() {
        if (!tapeInserted)
            return false;

        if (tzxTape)
            tapePos = 10;
        else
            tapePos = 0;
        return true;
    }

    private boolean doPlay() {
        if (!tapeInserted || statePlay == State.STOP)
            return false;

        if (tzxTape)
            return playTzx();
        else
            return playTap();
    }

    private boolean playTap() {
        if (!tapeInserted || statePlay == State.STOP)
            return false;

        switch (statePlay) {
            case STOP:
                timeLastIn = 0;
                break;
            case START:
                if( tapePos == tapeBuffer.length )
                    tapePos = 0;

                blockLen = tapeBuffer[tapePos] + (tapeBuffer[tapePos + 1] << 8);
                tapePos += 2;
                System.out.println("blockLen = " + blockLen);
                leaderPulses = tapeBuffer[tapePos] < 0x80 ? HEADER_PULSES : DATA_PULSES;
                earBit = 0xbf;
                timeout = LEADER_LENGHT;
                statePlay = State.LEADER;
                break;
            case LEADER:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                if (--leaderPulses != 0) {
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
                if ((tapeBuffer[tapePos] & mask) == 0)
                    bitTime = ZERO_LENGHT;
                else
                    bitTime = ONE_LENGHT;
                timeout = bitTime;
                statePlay = State.HALF2;
                break;
            case HALF2:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                timeout = bitTime;
                mask >>>= 1;
                if (mask == 0) {
                    tapePos++;
                    if( --blockLen > 0)
                        statePlay = State.NEWBYTE;
                    else
                        statePlay = State.PAUSE;
                } else
                    statePlay = State.NEWBIT;
                break;
            case PAUSE:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                timeout = END_BLOCK_PAUSE; // 1 seg. pausa
                System.out.println(String.format("tapeBufferLength: %d, tapePos: %d",
                    tapeBuffer.length, tapePos));
                if( tapePos == tapeBuffer.length ) {
                    statePlay = State.STOP;
                    tapePos = 0;
                    timeLastIn = 0;
                    //fastload = true;
                }
                else {
                    statePlay = State.START; // START
                    //fastload = true;
                }
                fastload = true;
        }
        return true;
    }

    private boolean playTzx() {

        boolean repeat;
        if (!tapeInserted || statePlay == State.STOP) {
            return false;
        }

        do {
            repeat = false;

            switch (statePlay) {
                case STOP:
                    timeLastIn = 0;
                    break;
                case START:
                    if (tapePos == tapeBuffer.length) {
                        tapePos = 10;
                    }

                    //earBit = 0xbf;
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
                        if( mask == (0x80 >>> bitsLastByte)) {
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
                    if (endBlockPause == 0) {
                        statePlay = State.TZX_HEADER;
                        repeat = true;
                        //earBit = 0xbf;
                        break;
                    }

                    timeout = endBlockPause;
                    if (tapePos == tapeBuffer.length) {
                        statePlay = State.STOP;
                        tapePos = 10;
                    } else {
                        statePlay = State.TZX_HEADER;
                    }
                    break;
                case TZX_HEADER:
                    decodeTzxHeader();
                    if (tapePos == tapeBuffer.length) {
//                        System.out.println(String.format("Last Ear: %02x", earBit));
                        //earBit = earBit == 0xbf ? 0xff : 0xbf;
                        statePlay = State.STOP;
                        tapePos = 10;
                    }
                    //earBit = earBit == 0xbf ? 0xff : 0xbf;
                    repeat = true;
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
                case PAUSE_STOP:
                    timeout = endBlockPause;
                    statePlay = State.TZX_HEADER;
                    earBit = 0xbf;
                    break;
            }
        } while (repeat);
        return true;
    }

    private void decodeTzxHeader() {
        boolean repeat = true;
        while (repeat) {
            if (tapePos == tapeBuffer.length)
                return;
            
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
                    if (endBlockPause == 0)
                        endBlockPause = END_BLOCK_PAUSE;
                    blockLen = tapeBuffer[tapePos + 3] + (tapeBuffer[tapePos + 4] << 8);
                    tapePos += 5;
                    leaderPulses = tapeBuffer[tapePos] < 0x80 ? HEADER_PULSES : DATA_PULSES;
                    timeout = leaderLenght;
                    statePlay = State.LEADER_NOCHG;
                    repeat = false;
                    break;
                case 0x11: // Turbo speed data block
                    leaderLenght = (tapeBuffer[tapePos + 1] +
                            (tapeBuffer[tapePos + 2] << 8));
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
                    blockLen = tapeBuffer[tapePos + 16] +
                            (tapeBuffer[tapePos + 17] << 8) +
                            (tapeBuffer[tapePos + 18] << 16);
                    tapePos += 19;
                    timeout = leaderLenght;
                    statePlay = State.LEADER_NOCHG;
                    repeat = false;
                    break;
                case 0x12: // Pure Tone Block
                    leaderLenght = (tapeBuffer[tapePos + 1] +
                            (tapeBuffer[tapePos + 2] << 8));
                    leaderPulses = (tapeBuffer[tapePos + 3]
                        + (tapeBuffer[tapePos + 4] << 8));
                    tapePos += 5;
                    statePlay = State.PURE_TONE_NOCHG;
                    repeat = false;
                    break;
                case 0x13: // Pulse Sequence Block
                    leaderPulses = tapeBuffer[tapePos + 1];
                    tapePos += 2;
                    statePlay = State.PULSE_SEQUENCE_NOCHG;
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
                    blockLen = tapeBuffer[tapePos + 8] +
                            (tapeBuffer[tapePos + 9] << 8) +
                            (tapeBuffer[tapePos + 10] << 16);
                    tapePos += 11;
                    statePlay = State.NEWBYTE_NOCHG;
                    repeat = false;
                    break;
                case 0x20: // Pause (silence) or 'Stop the Tape' command
                    endBlockPause = (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8)) * 3500;
                    tapePos += 3;
                    statePlay = State.PAUSE_STOP;
                    repeat = false;
                    break;
                case 0x21: // Group Start
                    blockLen = tapeBuffer[tapePos + 1];
                    tapePos += blockLen + 2;
                    break;
                case 0x22: // Group End
                    tapePos++;
                    break;
                case 0x24: // Loop Start
                    nLoops = tapeBuffer[tapePos + 1] + (tapeBuffer[tapePos + 2] << 8);
                    tapePos += 3;
                    targetJump = tapePos;
                    break;
                case 0x25: // Loop End
                    if (--nLoops == 0) {
                        tapePos++;
                        break;
                    }
                    tapePos = targetJump;
                    break;
                case 0x2a: // Stop the tape if in 48K mode
                    statePlay = State.STOP;
                    repeat = false;
                case 0x30: // Text Description
                    blockLen = tapeBuffer[tapePos + 1];
                    tapePos += blockLen + 2;
                    break;
                case 0x32: // Archive Info
                    blockLen = tapeBuffer[tapePos + 1] + (tapeBuffer[tapePos + 2] << 8);
                    tapePos += blockLen + 3;
                    break;
                case 0x33: //Hardware Type
                    blockLen = tapeBuffer[tapePos + 1];
                    tapePos += blockLen * 3 + 2;
                    break;
                case 0x35: // Custom Info Block
                    blockLen = tapeBuffer[tapePos + 11] + (tapeBuffer[tapePos + 12] << 8) +
                            (tapeBuffer[tapePos + 13] << 16) + (tapeBuffer[tapePos + 14] << 24);
                    tapePos += blockLen + 11;
                    break;
                default:
                    System.out.println(String.format("Block ID: %02x", tapeBuffer[tapePos]));
                    repeat = false;
            }
        }
        System.out.println(String.format("tapeBufferLength: %d, tapePos: %d, blockLen: %d",
                    tapeBuffer.length, tapePos, blockLen));
    }

    public boolean fastload(int Ram[]) {

        System.out.println("fastload!");
        if (!tapeInserted || cpu == null)
            return false;

        int addr = cpu.getRegIX(); // Address start
        int len = cpu.getRegDE();  // Length
        int flag = cpu.getRegA();  // Flag

        if( tapePos >= tapeBuffer.length ) {
            cpu.setCarryFlag(false);
            return false;
        }

        blockLen = tapeBuffer[tapePos] + (tapeBuffer[tapePos + 1] << 8);
        tapePos += 2;
        if( tapeBuffer[tapePos] != flag ) {
            cpu.setCarryFlag(false);
            tapePos += blockLen;
            return false;
        }
//        System.arraycopy(tapeBuffer, tapePos + 1, Ram, addr, len);
        int count = 0;
        int nBytes = (len <= blockLen - 2) ? len : blockLen - 1;
        while (count < nBytes) {
            if( addr > 0x3fff )
                Ram[addr++] = tapeBuffer[tapePos + count + 1];
            addr &= 0xffff;
            count++;
        }
        cpu.setRegIX(addr);
        cpu.setRegDE(len - nBytes);
        if (len == nBytes )
            cpu.setCarryFlag(true);
        else
            cpu.setCarryFlag(false);
        tapePos += blockLen;
        return true;
    }

//    public static void main(String args[]) {
//         Tape tape = new Tape();
//         if( tape.insert("chopin.tap") == false )
//             System.out.println("Error at insert");
//         System.out.println(String.format("Tape: %s, length: %d",
//                 tape.tapeName, tape.tapeBuffer.length));
//         tape.eject();
//    }
}
