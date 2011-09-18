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
        GAP, SYNC, HEADER, DATA, CHECKSUM
    };
    
    private static final int GAP = 0x04;
    private static final int GAP_LEN = 12;
    private static final int SYNC = 0x02;
    private static final int SYNC_LEN = 10;
    private static final int HEADER_LEN = 15;
    private static final int DATA_LEN = 527; // 15 + 512
    
    private MDR_STATE mdrState;
    private int position;
    private byte cartridge[];
    private short nBlocks;
    private int status;
    private int nSync;
    private int nGap;
    private int nBytes;
    private boolean isCartridge;
    private boolean writeProtected;
    
    private FileInputStream mdrFile;
    private File filename;
    
    public Microdrive() {
        position = 0;
        // A microdrive cartridge can have 254 sectors of 543 bytes lenght
        nBlocks = 254;
        mdrState = MDR_STATE.GAP;
        isCartridge = true;
        status = 0xff;
        nBytes = HEADER_LEN;
        if (!insert(new File("/home/jsanchez/Spectrum/empty.mdr"))) {
            System.out.println("No se ha podido cargar el cartucho");
            isCartridge = false;
        }
        
        if (isCartridge) {
            status = 0xff & ~GAP;
            writeProtected = cartridge[cartridge.length - 1] != 0 ? true : false;
        }
    }
    
    public int getStatus() {
        if (!isCartridge)
            return status;
        
        if (mdrState == MDR_STATE.DATA) {
            mdrState = MDR_STATE.GAP;
            status &= ~GAP;
            position += nBytes + 1;
            if (position == cartridge.length - 1) {
                position = 0;
            }
            nBytes = HEADER_LEN;
        }
        
        switch (mdrState) {
            case GAP:
                if (nGap++ == GAP_LEN) {
                    nGap = 0;
                    status |= GAP;
                    status &= ~SYNC;
                    mdrState = MDR_STATE.SYNC;
                }
                break;
            case SYNC:
                if (nSync++ == SYNC_LEN) {
                    nSync = 0;
                    status &= ~GAP;
                    status |= SYNC;
                    mdrState = MDR_STATE.GAP;
                }
                break;
            default:
                System.out.println(String.format("getstatus: msdState = %s", mdrState.toString()));
        }
        
        return (status | (writeProtected ? 0x00 : 0x01)) & 0xff;
    }
    
    public int getByte() {
        
        if (mdrState == MDR_STATE.SYNC) {
            status |= SYNC;
            if (nBytes == HEADER_LEN) {
                mdrState = MDR_STATE.HEADER;
            }
            
            if (nBytes == DATA_LEN) {
                mdrState = MDR_STATE.DATA;
            }
        }
        
        int out = cartridge[position] & 0xff;
        
        switch (mdrState) {
            case HEADER:
                if (--nBytes == 0) {
                    mdrState = MDR_STATE.GAP;
                    status &= ~GAP;
                    nBytes = DATA_LEN;
                }
                position++;
                break;
            case DATA:
                if (--nBytes == 0) {
                    mdrState = MDR_STATE.CHECKSUM;
                }
                position++;
                break;
            case CHECKSUM:
                mdrState = MDR_STATE.GAP;
                status &= ~GAP;
                nBytes = HEADER_LEN;
                position++;
                if (position == cartridge.length - 1)
                    position = 0;
                break;
            default:
                System.out.println(String.format("getByte: msdState = %s", mdrState.toString()));
                out = 0xff;
        }
        
        return out;
    }
    
    public final boolean insert(File fileName) {
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
        return true;
    }
}
