/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jsanchez
 */
public class Microdrive {
    // Definition of states for the Microdrive Machine States
    
    private static final int GAP = 0x04;
    private static final int SYNC = 0x02;
    private static final int GAP_SYNC_MASK = GAP | SYNC;
    private static final int GAP_SYNC_SIZE = 10;
    private static final int PREAMBLE_SIZE = 12;
    private static final int HEADER_SIZE = 15;
    private static final int DATA_SIZE = 528; // 15 + 512 + 1 
    private static final int SECTOR_SIZE = HEADER_SIZE + DATA_SIZE; // 543
    
    private int cartridgePos;
    private byte cartridge[];
    private short nBlocks;
    private int status;
    private int gapSyncCounter;
    private int nBytes;
    private int lastOut;
    private boolean isCartridge;
    private int writeProtected;
    
    private int preamLen;
    private int preambleData[] = new int[12];
    
    private boolean headerSync[] = new boolean[255];
    private boolean dataSync[] = new boolean[255];
    private boolean preamble[];
    
    private FileInputStream mdrFile;
    private File filename;
    
    public Microdrive() {
        // A microdrive cartridge can have 254 sectors of 543 bytes lenght
        nBlocks = 254;
        isCartridge = false;
        preambleData[10] = preambleData[11] = 0xff;
    }
    
    public void start() {
        cartridgePos = 0;
        status = 0xff & ~GAP;
        nBytes = HEADER_SIZE;
        preamLen = 0;
        gapSyncCounter = 0;
        preamble = headerSync;
    }
    
    public int readStatus() {
        
        if (!isCartridge)
            return 0xff;
        
        int sector = cartridgePos / SECTOR_SIZE;
        int offset = cartridgePos % SECTOR_SIZE;
        
//        System.out.println(String.format(
//            "readStatus: pos (sec/off): %d (%d/%d), status: %02x, gapSync: %d, nBytes: %d",
//            cartridgePos, sector, offset, status, gapSyncCounter, nBytes));
        
        
        if (offset == 0 && !headerSync[sector]) {
            return isWriteProtected() ? 0xfe : 0xff;
        }
        
        if (offset == 15 && !dataSync[sector]) {
            return isWriteProtected() ? 0xfe : 0xff;
        }
        
        if (gapSyncCounter++ == GAP_SYNC_SIZE) {
            gapSyncCounter = 0;
            status ^= GAP_SYNC_MASK;
        }
        
        switch (offset) {
            case 0: // Sector start
                nBytes = HEADER_SIZE;
                preamble = headerSync;
                break;
            case 15: // DATA start
                nBytes = DATA_SIZE;
                preamble = dataSync;
                break;
            default:
                status = 0xff & ~GAP;
                gapSyncCounter = 0;
                cartridgePos += nBytes;
                if (cartridgePos == cartridge.length - 1)
                    cartridgePos = 0;
        }
        
        if (isWriteProtected())
            status &= 0xfe;
        
        return status;
    }
    
    public int readData() {
        
//        System.out.println(String.format(
//            "readData: pos (sec/off): %d (%d/%d), status: %02x, nBytes: %d",
//            cartridgePos, cartridgePos / SECTOR_SIZE, cartridgePos % SECTOR_SIZE,
//            status, nBytes));
        
        int out = 0xff;
        
        if (nBytes > 0) {
            out &= cartridge[cartridgePos++];
            if (cartridgePos == cartridge.length - 1)
                cartridgePos = 0;
        
            nBytes--;
        } else {
            out &= lastOut;
        }
        
        return out;
    }
    
    public void writeControl(int value) {
        
        int sector = cartridgePos / SECTOR_SIZE;
        int offset = cartridgePos % SECTOR_SIZE;
        
//        System.out.println(String.format(
//            "writeControl: pos (sec/off): %d (%d/%d), nBytes: %d, value: %02x",
//            cartridgePos, sector, offset, nBytes, value));
        
        switch (offset) {
            case 0: // Sector start
                nBytes = HEADER_SIZE;
                preamble = headerSync;
                break;
            case 15: // DATA start
                nBytes = DATA_SIZE;
                preamble = dataSync;
                break;
            default:
                cartridgePos += nBytes;
                if (cartridgePos == cartridge.length - 1)
                    cartridgePos = 0;
        }
        
        preamLen = 0;  
        gapSyncCounter = 0;
        status = 0xff & ~GAP;
    }
    
    public void writeData(int value) {
        
        int sector = cartridgePos / SECTOR_SIZE;
        
//        System.out.println(String.format(
//            "writeData: pos (sec/off): %d (%d/%d), nBytes: %d, pream: %d, value: %02x",
//            cartridgePos, sector, cartridgePos % SECTOR_SIZE, nBytes, preamLen, value));

        if (preamLen < PREAMBLE_SIZE) {
            if (preambleData[preamLen++] != value) {
                preamble[sector] = false;
                System.out.println(String.format(
                    "writeData: ERROR en preámbulo. preamLen: %d", preamLen));
            } else {
                preamble[sector] = true;
            }
            return;
        }
        
        if (nBytes > 0) {
            cartridge[cartridgePos++] = (byte)value;
            if (cartridgePos == cartridge.length - 1)
                cartridgePos = 0;
            
            nBytes--;
        }

        lastOut = value;
    }
    
    public boolean isWriteProtected() {
        return writeProtected == 0x00 ? true : false;
    }
    
    public boolean isCartridge() {
        return isCartridge;
    }
    
    public final boolean insert(File fileName) {
        
        if (isCartridge)
            return false;
        
        try {
            mdrFile = new FileInputStream(fileName);
        } catch (FileNotFoundException fex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, fex);
            return false;
        }

        try {
            cartridge = new byte[mdrFile.available()];
            mdrFile.read(cartridge);
            mdrFile.close();
            filename = fileName;
        } catch (IOException ex) {
            Logger.getLogger(Tape.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        
        isCartridge = true;
        status = 0xff & ~GAP;
        writeProtected = cartridge[cartridge.length - 1] != 0 ? 0x00 : 0x01;
        updateSync();
//        testMDR();
        return true;
    }
    
    private void testMDR() {
        int length = (cartridge.length - 1) / 543;
        boolean sectors[] = new boolean[256];
        
        System.out.println(String.format("Cartridge %s", filename.getName()));
        System.out.println(String.format("# sectors %d", length));
        for (int idx = 0; idx < length; idx++ ) {
            int nSector = cartridge[idx * 543 + 1] & 0xff;
            sectors[nSector] = true;
            System.out.println(String.format("Sector %d present", idx));
        }
        
        for (int idx = length; idx > 0; idx-- ) {
            if (sectors[idx] == true) {
                System.out.println(String.format("Max sector: %d", idx));
                break;
            }
        }
        System.out.println("-----------------------------");
    }
    
    private void updateSync() {
        
        Arrays.fill(headerSync, false);
        Arrays.fill(dataSync, false);
        
        int nsectors = (cartridge.length -1) / SECTOR_SIZE;
        for (int sector = 0; sector < nsectors; sector++) {
            if (cartridge[sector * SECTOR_SIZE + 14] != (byte)0xff)
                headerSync[sector] = true;
            if (cartridge[sector * SECTOR_SIZE + 542] != (byte)0xff)
                dataSync[sector] = true;
        }
    }
}
