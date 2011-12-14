/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 * 
 * MDVT file format
 *
 * 'MDVT' (4 bytes)
 * HEADER LENGTH (4 bytes) [from VERSION to #GAP ENTRIES, first 8 bytes not included]
 *
 * VERSION (2 bytes) [Major.Minor]
 * FLAGS (2 bytes) [COMPRESSED, CONVERTED, WR-PROT]
 * RAW SECTOR SIZE [2 bytes]
 * NUM SECTORS [2 byte]
 * GAP SIZE USED (1 byte) [in converted mdr files]
 * FIRST GAP STATE (1 byte)
 * # GAP ENTRIES (2 bytes) [CSW coded]
 *
 * CREATOR LENGTH (1 bytes)
 * CREATOR FIELD (CREATOR LENGTH bytes)
 *
 * COMMENTS LENGTH (1 bytes)
 * COMMENTS FIELD (COMMENTS LENGTH bytes)
 *
 * DATA (RAW SECTOR SIZE * NUM SECTORS bytes)
 * GAP DATA (# GAP ENTRIES  * 2 bytes)
 *
 * FLAGS
 * -------------
 * WR_PROT 0x01
 * COMPRESSED 0x02
 * CONVERTED 0x04
 * 
 */
package utilities;

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
import java.util.zip.DeflaterOutputStream;
import machine.TimeCounters;

/**
 *
 * @author jsanchez
 */
public class Microdrive {

    private static final int RAW_SECTOR_SIZE = 792;  // empirically calculated (sigh!)
    private static final int timeTransfer = 168;     // 12 usec/bit * 8 at 3.5 Mhz

    private static final int MDVT_WR_PROT = 0x01;       // Cartridge protected flag
    private static final int MDVT_MDR_CONVERTED = 0x02; // Cartridge converted from MDR
    private static final int MDVT_COMPRESSED = 0x04;    // Cartridge data is compressed

    private static final byte GAP = 0x04;
    private static final byte SYNC = 0x02;
    private static final byte WRITE_PROT_MASK = (byte)0xfe;
    private static final byte CLOCK_GAP = 0x04;
    private static final byte CLOCK_DATA = 0x00;
    private static final byte DATA_FILLER = 0x5A;
    private static final byte preamble[] = { 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x00, 0x00, 0x00, 0x00, 0x00,
                                             (byte)0xFF, (byte)0xFF };
    
    private byte cartridge[];
    private byte clockGap[];
    private byte lastData;
    private int status;
    private int cartridgePos;
    private int numSectors;
    private boolean isCartridge;
    private boolean modified;
    private boolean writeProtected;
    private boolean wrGapPending;
    
    private BufferedInputStream fIn;
    private BufferedOutputStream fOut;
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

        while(clockGap[cartridgePos] == CLOCK_DATA && cartridgePos != pos) {
            if (++cartridgePos >= clockGap.length)
                cartridgePos = 0;
        }

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

        status = 0xf9; // NO GAP, NO SYNC

        // Is a GAP?
        if (clockGap[cartridgePos] != CLOCK_DATA) {
            status |= GAP;
        }

        // When isn't a GAP, can be needed to assert SYNC
        if (clockGap[cartridgePos] == CLOCK_DATA && (lastData & cartridge[cartridgePos]) == 0)
            status |= SYNC;

        lastData = cartridge[cartridgePos];

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

        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        clock.addTstates(timeTransfer);

        return lastData & 0xff;
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

        cartridge = new byte[numSectors * RAW_SECTOR_SIZE];
        clockGap = new byte[numSectors * RAW_SECTOR_SIZE];

        Arrays.fill(cartridge, DATA_FILLER);
        Arrays.fill(clockGap, CLOCK_GAP);

        isCartridge = true;
        writeProtected = false;
        modified = true;
        filename = null;
        this.numSectors = numSectors;

        return true;
    }
    
    public final boolean insertFromFile(File fileName) {

        if (isCartridge)
            return false;
        
        try {
            fIn = new BufferedInputStream(new FileInputStream(fileName));

            if (fileName.getName().toLowerCase().endsWith(".mdr")) {
                int mdrLen = fIn.available();
                int nsectors = (mdrLen - 1) / 543;
                int pos = 0;
                
                // 587 = 543 + 2 * 10 (GAP) + 2 * 12 (PREAMBLE)
                clockGap = new byte[nsectors * 587];
                cartridge = new byte[nsectors * 587];
                while (nsectors-- > 0) {
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
                        cartridge[pos] = (byte)fIn.read();
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
                        cartridge[pos] = (byte)fIn.read();
                        clockGap[pos++] = CLOCK_DATA;
                    }
                }
                
                writeProtected = fIn.read() != 0;
            } else {
                return false;
            }
        } catch (IOException ex) {
            Logger.getLogger(Microdrive.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } finally {
            try {
                fIn.close();
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
            fOut = new BufferedOutputStream(new FileOutputStream(filename));
        } catch (FileNotFoundException fex) {
            Logger.getLogger(Microdrive.class.getName()).log(Level.SEVERE, null, fex);
            return false;
        }

        try {
        // SZX Header
            String blockID = "MDVT";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(14);   // MDVT 1.0 header length
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);

            fOut.write(0x01); // MDV major version
            fOut.write(0x00); // MDV minor version
            // Flags (WORD)
            int flags = MDVT_COMPRESSED;
            if (writeProtected)
                flags |= MDVT_WR_PROT;
            fOut.write(flags);
            fOut.write(flags >>> 8);
            // Raw sector size (WORD)
            fOut.write(RAW_SECTOR_SIZE);
            fOut.write(RAW_SECTOR_SIZE >>> 8);
            // Num sectors (QWORD)
            fOut.write(numSectors);
            fOut.write(numSectors >>> 8);
            
            // GAP size used (in converted mdr files)
            fOut.write(0x00);
            
            // First GAP value
            fOut.write(clockGap[0]);
            
            // GAP array length (WORD)
            ByteArrayOutputStream buffer = createCswBuffer();
            int buflen = buffer.size();
            fOut.write(buflen >>> 1);
            fOut.write(buflen >>> 9);
            
            // Creator ID
            String creatorID = "JSpeccy 0.89 (14/12/2011)";
            // Creator Length
            fOut.write(creatorID.length());
            // Creator message
            fOut.write(creatorID.getBytes("US-ASCII"));
            
            // Comments length
            fOut.write(0x00);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DeflaterOutputStream dos = new DeflaterOutputStream(baos);
            // Data Tape
            dos.write(cartridge);
            
            // Data GAP
            dos.write(buffer.toByteArray());
//            dos.write(clockGap);
            
            dos.close();
            
            baos.writeTo(fOut);
            
            fOut.close();
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
    
    private ByteArrayOutputStream createCswBuffer() {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        System.out.println(String.format("First clockGap: %02X", clockGap[0]));
        byte value = clockGap[0];
        int counter = 0;
        int tot = 0;
        
        for (int pos = 0; pos < clockGap.length; pos++) {
            if (clockGap[pos] == value) {
                counter++;
            } else {
                System.out.println(String.format("value: %02X, ntimes: %d", value, counter));
                value = clockGap[pos];
                buffer.write(counter);
                buffer.write(counter >>> 8);
                tot += counter;
                counter = 1;
            }
        }
        
        System.out.println(String.format("value: %02X, ntimes: %d", value, counter));
        buffer.write(counter);
        buffer.write(counter >>> 8);
        
        tot += counter;
        
        System.out.println(String.format("Length: %d, Total # bytes: %d",
            clockGap.length, tot));
        
        return buffer;
    }
}
