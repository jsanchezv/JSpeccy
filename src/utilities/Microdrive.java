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

/**
 *
 * @author jsanchez
 */
public class Microdrive {
    // Definition of states for the Microdrive Machine States
    private enum MDR_STATE {
        GAP, SYNC, HEADER, DATA
    };
    
    private static final int GAP = 0x04;
    private static final int GAP_LEN = 12;
    private static final int SYNC = 0x02;
    private static final int SYNC_LEN = 10;
    private static final int HEADER_LEN = 15;
    private static final int DATA_LEN = 528; // 15 + 512 + 1 
    private static final int PREAMBLE_LEN = 12;
    
    private MDR_STATE mdrState;
    private int position;
    private byte cartridge[];
    private short nBlocks;
    private int status;
    private int nSync;
    private int nGap;
    private int nBytes, nWritten;
    private boolean isCartridge;
    private int writeProtected;
    private int preamLen;
    private int preambleData[] = new int[12];
    
    private FileInputStream mdrFile;
    private File filename;
    
    public Microdrive() {
        // A microdrive cartridge can have 254 sectors of 543 bytes lenght
        nBlocks = 254;
        mdrState = MDR_STATE.GAP;
        isCartridge = false;
        status = 0xff;
        nBytes = HEADER_LEN;
        preambleData[10] = preambleData[11] = 0xff;
    }
    
    public void start() {
        position = 0;
        mdrState = MDR_STATE.GAP;
        status = 0xff & ~GAP;
        nBytes = HEADER_LEN;
        preamLen = nWritten = 0;
    }
    
    public int readStatus() {
        if (!isCartridge)
            return status;
        
        System.out.println(String.format(
            "readStatus: Block: %d, offset: %d, status: %02x, mdrState: %s",
            position / 543, position % 543, status, mdrState.toString()));
        
        switch (mdrState) {
            case GAP:
                if (nGap++ == GAP_LEN) {
                    nGap = 0;
//                    status &= ~(GAP | SYNC);
                    status |= GAP;
                    status &= ~SYNC;
                    mdrState = MDR_STATE.SYNC;
                }
                break;
            case SYNC:
                if (nSync++ == SYNC_LEN) {
                    nSync = 0;
//                    status |= GAP | SYNC;
                    status |= SYNC;
                    status &= ~GAP;
                    mdrState = MDR_STATE.GAP;
                }
                break;
            case HEADER:
                mdrState = MDR_STATE.GAP;
//                status |= GAP | SYNC;
                status |= SYNC;
                status &= ~GAP;
                position += nBytes;
                if (position == cartridge.length - 1) {
                    position = 0;
                }
                nBytes = DATA_LEN;
                break;
            case DATA:
                mdrState = MDR_STATE.GAP;
//                status |= GAP | SYNC;
                status |= SYNC;
                status &= ~GAP;
                position += nBytes;
                if (position == cartridge.length - 1) {
                    position = 0;
                }
                nBytes = HEADER_LEN;
                break;
            default:
                System.out.println(String.format("readStatus: mdrState = %s, status = %02x",
                    mdrState.toString(), status));
        }
        
        if (isWriteProtected())
            status &= 0xfe;
        
        return status;
    }
    
    public int readData() {
        
        System.out.println(String.format(
            "readData: Block: %d, offset: %d, nBytes: %d, status: %02x, mdrState: %s",
            position / 543, position % 543, nBytes, status, mdrState.toString()));
        
        if (mdrState == MDR_STATE.GAP || mdrState == MDR_STATE.SYNC) {
            status |= GAP | SYNC;
            nGap = nSync = 0;
            if (nBytes == HEADER_LEN) {
                mdrState = MDR_STATE.HEADER;
            }
            
            if (nBytes == DATA_LEN) {
                mdrState = MDR_STATE.DATA;
            }
        }
        
        int out = cartridge[position++] & 0xff;
        if (position == cartridge.length - 1) {
            position = 0;
        }
        
        switch (mdrState) {
            case HEADER:
                if (--nBytes == 0) {
                    mdrState = MDR_STATE.GAP;
                    status &= ~GAP;
                    nBytes = DATA_LEN;
                }
                break;
            case DATA:
                if (--nBytes == 0) {
                    mdrState = MDR_STATE.GAP;
                    status &= ~GAP;
                    nBytes = HEADER_LEN;
                }
                break;
            default:
                System.out.println(String.format("readData: mdrState = %s, status = %02x",
                    mdrState.toString(), status));
                out = 0xff;
        }
        
        return out;
    }
    
    public void writeData(int value) {
//        System.out.println(String.format(
//            "writeData: Block: %d, offset: %d, nBytes: %d, nWritten: %d, status: %02x",
//            position / 543, position % 543, nBytes, nWritten, status));
        
        if (nGap != 0 || nSync != 0) {
            status &= ~GAP;
            status |= SYNC;
            nGap = nSync = 0;
        }
        
        if (preamLen < PREAMBLE_LEN) {
            if (preambleData[preamLen] != value) {
                System.out.println(String.format(
                    "writeData: ERROR en preámbulo. preamLen: %d", preamLen));
            }
            preamLen++;
            return;
        }
        
        cartridge[position++] = (byte)value;
        if (position == cartridge.length - 1)
            position = 0;
        
        if (++nWritten == nBytes) {
            nBytes = nBytes == HEADER_LEN ? DATA_LEN : HEADER_LEN;
            preamLen = nWritten = 0;
        }
        
        System.out.println(String.format(
            "writeData: Block: %d, offset: %d, nBytes: %d, nWritten: %d, status: %02x",
            position / 543, position % 543, nBytes, nWritten, status));
        
//        System.out.println(String.format("writeData: %d", value));
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
}
