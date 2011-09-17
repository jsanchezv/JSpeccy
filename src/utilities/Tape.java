/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

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
    private String tapeName;
    private int tapeBuffer[];
    private int tapePos;

    public Tape(Z80 z80) {
        cpu = z80;
    }

    public Tape() {
        tapeName = null;
    }

    public boolean insert(String filename) {

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

        int blockLen = tapeBuffer[tapePos] + (tapeBuffer[tapePos + 1] << 8);
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

    public static void main(String args[]) {
         Tape tape = new Tape();
         if( tape.insert("chopin.tap") == false )
             System.out.println("Error at insert");
         System.out.println(String.format("Tape: %s, length: %d",
                 tape.tapeName, tape.tapeBuffer.length));
         tape.eject();
    }
}
