/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import machine.TimeCounters;

/**
 *
 * @author jsanchez
 */
public class Microdrive {
    
    private static final int GAP = 0x04;
    private static final int SYNC = 0x02;
    private static final int WRITE_PROT_MASK = 0xfe;
    private static final int SECTOR_SIZE = 792;  //empirically calculated (sigh!)
    private static final int timeTransfer = 168; // 12 usec/bit * 8  at 3.5 Mhz
    
    private byte cartridge[];
    private byte clockGap[];
    private int status;
    private int cartridgePos;
    private boolean isCartridge;
    private boolean modified;
    private boolean writeProtected;
    private boolean wrGapPending;
    
    private FileInputStream mdrFileIn;
    private FileOutputStream mdrFileOut;
    private File filename;
    private TimeCounters clock;
    private long startGap;
    
    private enum CartridgeState { GAP, PREAMBLE, SYNC, DATA };
    private CartridgeState tapeState;
    
    public Microdrive(TimeCounters clock) {
        
        this.clock = clock;
        // A microdrive cartridge can have 254 sectors of 543 bytes length
        isCartridge = false;
        writeProtected = true;
        cartridgePos = 0;
    }
    
    public void selected() {

        if (!isCartridge)
            return;
        
        wrGapPending = false;
        tapeState = CartridgeState.GAP;
        while((clockGap[cartridgePos] & 0xff) != 0xFF) {
            if (++cartridgePos >= clockGap.length)
                cartridgePos = 0;
        }
        
        status = 0xf5;
        if (writeProtected)
            status &= WRITE_PROT_MASK;
    }
    
    public int readStatus() {

        if (!isCartridge)
            return 0xf5;
        
//        System.out.println(String.format("readStatus at %04X: pos: %d, status: %02x, cartridge: %02x",
//                z80.getRegPC(), cartridgePos, clockGap[cartridgePos]  & 0xff, cartridge[cartridgePos] & 0xff));
        
        if (wrGapPending)
            return 0xfb;

        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        if (clockGap[cartridgePos] != 0) {
            status &= ~SYNC;
            status |= GAP;
            tapeState = CartridgeState.GAP;
        }
        
        switch (tapeState) {
            case GAP:
                if ((clockGap[cartridgePos] & 0xff) == 0x00) {
                    status &= ~GAP;
                    status |= SYNC;
                    tapeState = CartridgeState.PREAMBLE;
                }
                break;
            case PREAMBLE:
                if ((cartridge[cartridgePos] & 0xff) == 0xff) {
                    tapeState = CartridgeState.SYNC;
                }
                break;
            case SYNC:
                if ((cartridge[cartridgePos] & 0xff) != 0xff) {
                    status &= ~SYNC;
                    tapeState = CartridgeState.DATA;
                }
                break;
            case DATA:
                status |= SYNC;
        }
        

        if (writeProtected)
            status &= WRITE_PROT_MASK;

//        System.out.println(String.format("readStatus at %04X: pos: %d, status: %02x, cartridge: %02x, clockGAP: %02x, tapeState: %s",
//                z80.getRegPC(), position, status, cartridge[position] & 0xff, clockGap[position]& 0xff, tapeState.toString()));
        
        return status;
    }
    
    public int readData() {

//        System.out.println(String.format("readData at %04X: pos: %d, status: %02x, cartridge: %02x, clockGAP: %02x, tapeState: %s",
//                z80.getRegPC(), position, clockGap[position] & 0xff, cartridge[position] & 0xff, clockGap[position]& 0xff, tapeState.toString()));

        if (!isCartridge)
            return 0xff;
        
        int out = cartridge[cartridgePos] & 0xff;
        
        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;
        
        clock.addTstates(timeTransfer);
        return out;
    }
    
    public void writeControl(int value) {
        
        if (!isCartridge)
            return;
        
//        System.out.println(String.format("writeControl at %04X (%02x): pos: %d, status: %02x, cartridge: %02x, wrGapPending: %b",
//                z80.getRegPC(), value & 0xff, cartridgePos, clockGap[cartridgePos]  & 0xff, cartridge[cartridgePos] & 0xff, wrGapPending));
        
        int op = value & 0x0C;
        if (op == 0x04 && !wrGapPending) { // ERASE: OFF, WRITE: ON
            startGap = clock.getAbsTstates();
            wrGapPending = true;
            return;
        }
        
        // ERASE: OFF, WRITE: OFF or ERASE: ON, WRITE: ON
        if ((op == 0x00 || op == 0x0C) && wrGapPending) {
            long gapLen = (clock.getAbsTstates() - startGap) / timeTransfer;
            while (gapLen-- > 0) {
                cartridge[cartridgePos] = (byte)0xAA;
                clockGap[cartridgePos] = (byte)0xFF;
                if (++cartridgePos >= clockGap.length)
                    cartridgePos = 0;
            }
            wrGapPending = false;
        }
    }
    
    public void writeData(int value) {

        if (!isCartridge)
            return;
//        System.out.println(String.format("writeData at %04x (%02x): pos: %d, status: %02x, cartridge: %02x, wrGapPending: %b",
//                z80.getRegPC(), value & 0xff, position, clockGap[position] & 0xff, cartridge[position] & 0xff, wrGapPending));
        
        clockGap[cartridgePos] = 0x00;
        cartridge[cartridgePos] = (byte) value;
        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        clock.addTstates(timeTransfer);
    }
    
    public boolean isWriteProtected() {
        
        if (!isCartridge)
            return true;
        
        return writeProtected;
    }
    
    public void setWriteProtected(boolean flag) {
        
        if (!isCartridge)
            return;
        
        writeProtected = flag;
        modified = true;
    }
    
    public boolean isCartridge() {
        return isCartridge;
    }
    
    public boolean isModified() {
        return modified;
    }
    
    public final boolean insertNew(int numSectors) {
        
        if (isCartridge)
            return false;
        
        cartridge = new byte[numSectors * SECTOR_SIZE];
        clockGap = new byte[numSectors * SECTOR_SIZE];
        
        Arrays.fill(cartridge, (byte)0xAA); // filler byte
        Arrays.fill(clockGap, (byte) 0xFF); // no clockGap
        
        tapeState = CartridgeState.GAP;
        isCartridge = true;
        writeProtected = false;
        modified = true;
        filename = null;
        
        return true;
    }
    
    public final boolean insertFromFile(File fileName) {
        
        if (isCartridge)
            return false;
        
        try {
            mdrFileIn = new FileInputStream(fileName);
        } catch (FileNotFoundException fex) {
            Logger.getLogger(Microdrive.class.getName()).log(Level.SEVERE, null, fex);
            return false;
        }

        try {
            cartridge = new byte[mdrFileIn.available()];
            mdrFileIn.read(cartridge);
            mdrFileIn.close();
        } catch (IOException ex) {
            Logger.getLogger(Microdrive.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        
        isCartridge = true;
        modified = false;
        writeProtected = cartridge[cartridge.length - 1] != 0;
        filename = fileName;
        return true;
    }
    
    public final boolean eject() {
        
        if (!isCartridge)
            return true;
        
        isCartridge = false;
        modified = false;
        writeProtected = true;
        filename = null;
        return true;
    }
    
    public final String getFilename() {
        if (filename == null)
            return null;
        else
            return filename.getName();
    }
    
    public final String getAbsolutePath() {
        if (filename == null)
            return null;
        else
            return filename.getAbsolutePath();
    }
    
    public final boolean save() {

        if (!isCartridge || !modified) {
            return true;
        }

        try {
            mdrFileOut = new FileOutputStream(filename);
        } catch (FileNotFoundException fex) {
            Logger.getLogger(Microdrive.class.getName()).log(Level.SEVERE, null, fex);
            return false;
        }

        try {
            mdrFileOut.write(cartridge);
            mdrFileOut.close();
        } catch (IOException ex) {
            Logger.getLogger(Microdrive.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }

        modified = false;
        return true;
    }
    
    public final boolean save(File newName) {
        filename = newName;
        modified = true;
        return save();
    }
    
    public int getCartridgePos() {
        return cartridgePos;
    }
    
    public void setCartridgePos(int offset) {
        if (offset < 0 || offset > cartridge.length - 1)
            offset = 0;
        
        cartridgePos = offset;
    }
}
