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
    private File tapeName;
    private int tapeBuffer[];
    private int tapePos;
    private int blockLen;
    private int mask;
    private int bitTime;
    private int leaderPulses;
    private enum State { STOP, START, LEADER, SYNC, NEWBYTE, NEWBIT, HALF2, PAUSE };
    private State statePlay;
    private int earBit;
    private boolean fastload;
    /* Tiempos en T-estados de duración de cada pulso para cada parte de la carga */
    private final int LEADER_LENGHT = 2168;
    private final int SYNC1_LENGHT = 667;
    private final int SYNC2_LENGHT = 735;
    private final int ZERO_LENGHT = 855;
    private final int ONE_LENGHT = 1710;
    private final int HEADER_PULSES = 8063;
    private final int DATA_PULSES = 3223;

    public Tape(Z80 z80) {
        cpu = z80;
        statePlay = State.STOP;
        tapeName = null;
        tapePos = 0;
        fastload = true;
    }

    public Tape() {
        tapeName = null;
    }

    public boolean insert(File filename) {

        if( tapeName != null )
            return false;

        try {
            tapeFile = new FileInputStream(filename);
        } catch( FileNotFoundException fex ) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, fex);
            return false;
        }

        tapeName = filename;

        try {
            tapeBuffer = new int[tapeFile.available()];
            int count = 0;
            while( count < tapeBuffer.length )
                tapeBuffer[count++] = tapeFile.read() & 0xff;
            tapeFile.close();
        } catch (IOException ex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
        }

        tapePos = 0;
        return true;
    }

    public void eject() {
        tapeName = null;
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

    public boolean play() {
        if (tapeName == null || statePlay != State.STOP)
            return false;

        cpu.setTimeout(3500000); // espera mínima
        statePlay = State.START;
        fastload = false;
        return true;
    }

    public boolean doPlay() {
        if (tapeName == null || statePlay == State.STOP)
            return false;

        switch (statePlay) {
            case STOP:
                break;
            case START:
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
                cpu.setTimeout(3500000); // 1 seg. pausa
                System.out.println(String.format("tapeBufferLength: %d, tapePos: %d",
                    tapeBuffer.length, tapePos));
                if( tapePos == tapeBuffer.length ) {
                    statePlay = State.STOP;
                    tapePos = 0;
                }
                else {
                    statePlay = State.STOP; // START
                    fastload = true;
                }
        }
        return true;
    }

    public void stop() {
        if (tapeName == null)
            return;

        statePlay = State.STOP;
        tapePos += blockLen;
    }

    public boolean rewind() {
        if (tapeName == null)
            return false;

        tapePos = 0;
        return true;
    }

    public boolean fastload(int Ram[]) {

        if( tapeName == null || cpu == null )
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
