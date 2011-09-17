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
    private enum State { STOP, START, LEADER, SYNC, NEWBYTE, NEWBIT, HALF2, PAUSE };
    private State statePlay;
    private int earBit;
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

    public Tape(Z80 z80) {
        cpu = z80;
        statePlay = State.STOP;
        tapeInserted = tzxTape = false;
        tapePos = 0;
        fastload = true;
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

    public int getEarBit() {
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

        cpu.setTimeout(4); // espera mínima
        statePlay = State.START;
        fastload = false;
        return true;
    }

    public boolean doPlay() {
        if (!tapeInserted || statePlay == State.STOP)
            return false;
        if (tzxTape)
            return playTzx();
        else
            return playTap();
    }

    public boolean playTap() {
        if (!tapeInserted || statePlay == State.STOP)
            return false;

        switch (statePlay) {
            case STOP:
                break;
            case START:
                if( tapePos == tapeBuffer.length )
                    tapePos = 0;

                blockLen = tapeBuffer[tapePos] + (tapeBuffer[tapePos + 1] << 8);
                tapePos += 2;
                System.out.println("blockLen = " + blockLen);
                leaderPulses = tapeBuffer[tapePos] < 0x80 ? HEADER_PULSES : DATA_PULSES;
                earBit = 0xbf;
                cpu.setTimeout(LEADER_LENGHT);
                statePlay = State.LEADER;
                break;
            case LEADER:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                if (--leaderPulses != 0) {
                    cpu.setTimeout(LEADER_LENGHT);
                    break;
                }
                cpu.setTimeout(SYNC1_LENGHT);
                statePlay = State.SYNC;
                break;
            case SYNC:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                cpu.setTimeout(SYNC2_LENGHT);
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
                cpu.setTimeout(bitTime);
                statePlay = State.HALF2;
                break;
            case HALF2:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                cpu.setTimeout(bitTime);
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
                cpu.setTimeout(END_BLOCK_PAUSE); // 1 seg. pausa
                System.out.println(String.format("tapeBufferLength: %d, tapePos: %d",
                    tapeBuffer.length, tapePos));
                if( tapePos == tapeBuffer.length ) {
                    statePlay = State.STOP;
                    tapePos = 0;
                    //fastload = true;
                }
                else {
                    statePlay = State.STOP; // START
                    //fastload = true;
                }
                fastload = true;
        }
        return true;
    }

    public boolean playTzx() {
        if (!tapeInserted || statePlay == State.STOP)
            return false;

        switch (statePlay) {
            case STOP:
                break;
            case START:
                if( tapePos == tapeBuffer.length )
                    tapePos = 10;

                decodeTzxHeader();
                System.out.println("blockLen = " + blockLen);
                earBit = 0xbf;
                cpu.setTimeout(leaderLenght);
                statePlay = State.LEADER;
                break;
            case LEADER:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                if (--leaderPulses != 0) {
                    cpu.setTimeout(leaderLenght);
                    break;
                }
                cpu.setTimeout(sync1Lenght);
                statePlay = State.SYNC;
                break;
            case SYNC:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                cpu.setTimeout(sync2Lenght);
                statePlay = State.NEWBYTE;
                break;
            case NEWBYTE:
                mask = 0x80; // se empieza por el bit 7
            case NEWBIT:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                if ((tapeBuffer[tapePos] & mask) == 0)
                    bitTime = zeroLenght;
                else
                    bitTime = oneLenght;
                cpu.setTimeout(bitTime);
                statePlay = State.HALF2;
                break;
            case HALF2:
                earBit = earBit == 0xbf ? 0xff : 0xbf;
                cpu.setTimeout(bitTime);
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
                cpu.setTimeout(endBlockPause); // 1 seg. pausa
                System.out.println(String.format("tapeBufferLength: %d, tapePos: %d",
                    tapeBuffer.length, tapePos));
                if( tapePos == tapeBuffer.length ) {
                    statePlay = State.STOP;
                    tapePos = 10;
                    //fastload = true;
                }
                else {
                    statePlay = State.START;
                    //fastload = true;
                }
        }
        return true;
    }

    private void decodeTzxHeader() {
        boolean cont = true;
        while (cont) {
            System.out.println(String.format("tapeBufferLength: %d, tapePos: %d",
                    tapeBuffer.length, tapePos));

            switch (tapeBuffer[tapePos]) {
                case 10: // Standard speed data block
                    leaderLenght = LEADER_LENGHT;
                    sync1Lenght = SYNC1_LENGHT;
                    sync2Lenght = SYNC2_LENGHT;
                    zeroLenght = ZERO_LENGHT;
                    oneLenght = ONE_LENGHT;
                    bitsLastByte = 8;
                    endBlockPause = (tapeBuffer[tapePos + 1]
                        + (tapeBuffer[tapePos + 2] << 8)) * 3500;
                    blockLen = tapeBuffer[tapePos + 3] + (tapeBuffer[tapePos + 4] << 8);
                    tapePos += 5;
                    leaderPulses = tapeBuffer[tapePos] < 0x80 ? HEADER_PULSES : DATA_PULSES;
                    cont = false;
                    break;
                case 32: // Archive Info
                    blockLen = tapeBuffer[tapePos + 1] + (tapeBuffer[tapePos + 2] << 8);
                    tapePos = blockLen + 3;
            }
        }
    }

    public void stop() {
        if (!tapeInserted)
            return;

        statePlay = State.STOP;
        tapePos += blockLen;
        if( tapePos == tapeBuffer.length )
            if (tzxTape)
                tapePos = 9;
            else
                tapePos = 0;
    }

    public boolean rewind() {
        if (!tapeInserted)
            return false;

        if (tzxTape)
            tapePos = 9;
        else
            tapePos = 0;
        return true;
    }

    public boolean fastload(int Ram[]) {

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
