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

/**
 *
 * @author jsanchez
 */
public class Microdrive {
    
    private static final int GAP = 0x04;
    private static final int SYNC = 0x02;
    private static final int GAP_SYNC_MASK = GAP | SYNC;
    private static final int WRITE_PROT_MASK = 0xfe;
    private static final int GAP_SYNC_SIZE = 10;
    private static final int PREAMBLE_SIZE = 12;
    private static final int HEADER_SIZE = 15;
    private static final int DATA_SIZE = 528; // 15 + 512 + 1 
    private static final int SECTOR_SIZE = HEADER_SIZE + DATA_SIZE; // 543
    
    private int cartridgePos;
    private byte cartridge[];
    private int status;
    private int gapSyncCounter;
    private int nBytes;
    private int lastValue;
    private boolean isCartridge;
    private boolean modified;
    private boolean writeProtected;
    
    private int preamLen;
    private int preambleData[] = new int[12];
    
    private boolean headerSync[] = new boolean[255];
    private boolean dataSync[] = new boolean[255];
    private boolean preamble[];
    
    private FileInputStream mdrFileIn;
    private FileOutputStream mdrFileOut;
    private File filename;
    
    public Microdrive() {
        // A microdrive cartridge can have 254 sectors of 543 bytes length
        isCartridge = false;
        preambleData[10] = preambleData[11] = 0xff;
    }
    
    public void selected() {
        cartridgePos = 0;
        status = 0xff & ~GAP;
        nBytes = HEADER_SIZE;
        preamLen = 0;
        gapSyncCounter = 0;
        preamble = headerSync;
    }
    
    public int readStatus() {
        
        int sector = cartridgePos / SECTOR_SIZE;
        int offset = cartridgePos % SECTOR_SIZE;
        
//        System.out.println(String.format(
//            "readStatus: pos (sec/off): %d (%d/%d), status: %02x, gapSync: %d, nBytes: %d",
//            cartridgePos, sector, offset, status, gapSyncCounter, nBytes));
        
        
        if (offset == 0 && !headerSync[sector]) {
            if (writeProtected)
                status &= WRITE_PROT_MASK;
            return status;
        }
        
        if (offset == 15 && !dataSync[sector]) {
            if (writeProtected)
                status &= WRITE_PROT_MASK;
            return status;
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
        
        if (writeProtected)
            status &= WRITE_PROT_MASK;
        
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
            out &= lastValue;
        }
        
        return out;
    }
    
    public void writeControl(int value) {
        
//        int sector = cartridgePos / SECTOR_SIZE;
        int offset = cartridgePos % SECTOR_SIZE;
        
//        System.out.println(String.format(
//            "writeControl: pos (sec/off): %d (%d/%d), nBytes: %d, value: %02x, erase: %b, r/w: %b",
//            cartridgePos, sector, offset, nBytes, value, (value & 0x08) != 0,
//                (value & 0x04) != 0));
        
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
        gapSyncCounter = GAP_SYNC_SIZE - 3;
        // Si writeControl acaba con status GAP en lugar de SYNC, el formateo
        // desde el MF128 no funciona....
        status = 0xff & ~SYNC;
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
            modified = true;
        }
        lastValue = value;
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
        cartridge[cartridge.length - 1] = flag ? (byte)0x01 : (byte)0x00;
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
        
        cartridge = new byte[numSectors * SECTOR_SIZE + 1];
        
        Arrays.fill(cartridge, (byte)0xff);
        cartridge[cartridge.length - 1] = 0x00; // No WR protected
        
        Arrays.fill(headerSync, false);
        Arrays.fill(dataSync, false);
        
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
        updateSync();
        filename = fileName;
//        testMDR();
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
    
    public int getPreambleRem() {
        return PREAMBLE_SIZE - preamLen;
    }
    
    public void setPreambleRem(int offset) {
        if (offset < 0 || offset > PREAMBLE_SIZE)
            offset = PREAMBLE_SIZE;
        
        preamLen = PREAMBLE_SIZE - offset;
    }
    
    private void testMDR() {
        int length = (cartridge.length - 1) / SECTOR_SIZE;
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
