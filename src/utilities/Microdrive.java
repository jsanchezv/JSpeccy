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

    private static final int SECTOR_SIZE = 792;  // empirically calculated (sigh!)
    private static final int timeTransfer = 168; // 12 usec/bit * 8 at 3.5 Mhz

    private static final byte GAP = 0x04;
    private static final byte SYNC = 0x02;
    private static final byte WRITE_PROT_MASK = (byte)0xfe;
    private static final byte CLOCK_GAP = 0x01;
    private static final byte CLOCK_DATA = 0x00;
    private static final byte DATA_FILLER = 0x5A;
    private static final byte preamble[] = { 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x00, 0x00, 0x00, 0x00, 0x00,
                                             (byte)0xFF, (byte)0xFF };
    
    private byte cartridge[];
    private byte clockGap[];
    private byte lastData;
    private byte lastGap;
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

    public Microdrive(TimeCounters clock) {
        
        this.clock = clock;
        isCartridge = false;
        writeProtected = true;
        cartridgePos = 0;
    }
    
    public void selected() {

        if (!isCartridge)
            return;

        wrGapPending = false;

        // Find a GAP to start from a known position
        int pos = cartridgePos - 1;
        if (pos < 0)
            pos = clockGap.length - 1;

        while(clockGap[cartridgePos] != CLOCK_GAP && cartridgePos != pos) {
            if (++cartridgePos >= clockGap.length)
                cartridgePos = 0;
        }

        lastGap = clockGap[cartridgePos];
        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        if (writeProtected)
            status &= WRITE_PROT_MASK;
    }
    
    public int readStatus() {

        if (!isCartridge)
            return 0xf5;

        if (wrGapPending)
            return 0xfb;

        status = 0xfd; // GAP, NO SYNC

        // Is a GAP?
        if ((lastGap | clockGap[cartridgePos]) == 0) {
            status &= ~GAP;
        }

        // When isn't a GAP, can be needed to assert SYNC
        if ((status & GAP) == 0 && (lastData & cartridge[cartridgePos]) == 0)
            status |= SYNC;

        lastData = cartridge[cartridgePos];
        lastGap = clockGap[cartridgePos];

        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        if (writeProtected)
            status &= WRITE_PROT_MASK;

//        System.out.println(String.format("readStatus at pos: %d, status: %02x, cartridge: %02x, clockGAP: %02x",
//                cartridgePos, status, cartridge[cartridgePos] & 0xff, clockGap[cartridgePos]& 0xff));

        return status & 0xff;
    }
    
    public int readData() {

//        System.out.println(String.format("readData at pos: %d, status: %02x, cartridge: %02x, clockGAP: %02x",
//                cartridgePos, status, cartridge[cartridgePos] & 0xff, clockGap[cartridgePos]& 0xff));

        if (!isCartridge)
            return 0xff;

        lastData = cartridge[cartridgePos];
        lastGap = clockGap[cartridgePos];

        int out = cartridge[cartridgePos] & 0xff;

        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        clock.addTstates(timeTransfer);

        return out;
    }
    
    public void writeControl(int value) {

        if (!isCartridge)
            return;

//        System.out.println(String.format("writeControl (%02x) at : pos: %d, status: %02x, cartridge: %02x, wrGapPending: %b",
//                value & 0xff, cartridgePos, clockGap[cartridgePos]  & 0xff, cartridge[cartridgePos] & 0xff, wrGapPending));

        int op = value & 0x0C;
        // ERASE: ON, WRITE: OFF
        if (op == 0x04 && !wrGapPending) {
            startGap = clock.getAbsTstates();
            wrGapPending = true;
            return;
        }

        // ERASE: ON, WRITE: ON or ERASE: OFF, WRITE: OFF
        if ((op == 0x00 || op == 0x0C) && wrGapPending) {
            long gapLen = (clock.getAbsTstates() - startGap) / timeTransfer;
            while (gapLen-- > 0) {
                cartridge[cartridgePos] = DATA_FILLER;
                clockGap[cartridgePos] = CLOCK_GAP;
                if (++cartridgePos >= clockGap.length)
                    cartridgePos = 0;
            }
            wrGapPending = false;
        }
    }
    
    public void writeData(int value) {

        if (!isCartridge)
            return;
//        System.out.println(String.format("writeData (%02x) at: pos: %d, status: %02x, cartridge: %02x, wrGapPending: %b",
//                value & 0xff, cartridgePos, clockGap[cartridgePos] & 0xff, cartridge[cartridgePos] & 0xff, wrGapPending));

        clockGap[cartridgePos] = CLOCK_DATA;
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

        Arrays.fill(cartridge, DATA_FILLER);
        Arrays.fill(clockGap, CLOCK_GAP);

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

            if (fileName.getName().toLowerCase().endsWith(".mdr")) {
                int mdrLen = mdrFileIn.available();
                int nsectors = (mdrLen - 1) / 543;
                int pos = 0;
                
                // 587 = 543 + 2 * 10 (GAP) + 2 * 12 (PREAMBLE)
                clockGap = new byte[nsectors * 587];
                cartridge = new byte[nsectors * 587];
                while (--nsectors > 0) {
                    // Create header GAP
                    for(int gap = 0; gap < 10; gap++) {
                        cartridge[pos] = DATA_FILLER;
                        clockGap[pos++] = CLOCK_GAP;
                    }

                    // Create header preamble (10 * 0x00 + 2 * 0xFF)
                    for(int pream = 0; pream < preamble.length; pream++) {
                        cartridge[pos] = preamble[pream];
                        clockGap[pos++] = CLOCK_DATA;
                    }

                    // Read header
                    for(int header = 0; header < 15; header++) {
                        cartridge[pos] = (byte)mdrFileIn.read();
                        clockGap[pos++] = CLOCK_DATA;
                    }

                    // Create data GAP
                    for(int gap = 0; gap < 10; gap++) {
                        cartridge[pos] = DATA_FILLER;
                        clockGap[pos++] = CLOCK_GAP;
                    }

                    // Create data preamble (10 * 0x00 + 2 * 0xFF)
                    for(int pream = 0; pream < preamble.length; pream++) {
                        cartridge[pos] = preamble[pream];
                        clockGap[pos++] = CLOCK_DATA;
                    }

                    // Read data
                    for(int data = 0; data < 528; data++) {
                        cartridge[pos] = (byte)mdrFileIn.read();
                        clockGap[pos++] = CLOCK_DATA;
                    }
                }
                
                writeProtected = mdrFileIn.read() != 0;
            } else {
                return false;
            }
        } catch (IOException ex) {
            Logger.getLogger(Microdrive.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } finally {
            try {
                mdrFileIn.close();
            } catch (IOException ex) {
                Logger.getLogger(Microdrive.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        }
        
        isCartridge = true;
        modified = false;
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
