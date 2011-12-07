/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import configuration.Interface1Type;
import java.io.File;
import javax.swing.SwingUtilities;
import utilities.Microdrive;

/**
 *
 * @author jsanchez
 */
public class Interface1 {
    // Definitions for ZX Interface I Control Port 0xEF
    // For INning
    private static final int CTRL_IN_WRPROT = 0x01;
    private static final int CTRL_IN_SYNC = 0x02;
    private static final int CTRL_IN_GAP = 0x04;
    private static final int CTRL_IN_DTR = 0x08;
    // For OUTing
    private static final int CTRL_OUT_COMMSDATA = 0x01;
    private static final int CTRL_OUT_COMMSCLK = 0x02;
    private static final int CTRL_OUT_RW = 0x04;
    private static final int CTRL_OUT_ERASE = 0x08;
    private static final int CTRL_OUT_CTS = 0x10;
    private static final int CTRL_OUT_WAIT = 0x20;
    
    // Definitions for ZX Interface I RS232/Network Port 0xF7
    // For INning
    private static final int RSN_IN_NET = 0x01;
    private static final int RSN_IN_TXDATA = 0x80;
    // For OUTing
    private static final int RSN_OUT_NET_RXDATA = 0x01;
    
    private int mdrFlipFlop, mdrSelected;
    private byte numMicrodrives;
    private Microdrive microdrive[];
    private boolean commsClk;
    private TimeCounters clock;
    private Interface1Type settings;
    private int lan;
    
    public Interface1(TimeCounters clock, Interface1Type if1settings) {
        this.clock = clock;
        settings = if1settings;
        mdrFlipFlop = 0;
        mdrSelected = 0;
        numMicrodrives = settings.getMicrodriveUnits();
        
        microdrive = new Microdrive[8];
        for (int mdr = 0; mdr < 8; mdr++)
            microdrive[mdr] = new Microdrive(clock);
        
        commsClk = false;
        lan = 0;
    }
    
    public int readControlPort() {
        if (mdrFlipFlop != 0 && microdrive[mdrSelected].isCartridge()) {
//            System.out.println(String.format("readControlPort: Unit %d selected",
//                mdrSelected));
            return microdrive[mdrSelected].readStatus();
        } else {
            return 0xfe; // WR-Prot active
        }
    }
    
    public int readDataPort() {
        if (mdrFlipFlop != 0 && microdrive[mdrSelected].isCartridge()) {
//            System.out.println(String.format("readDataPort: Unit %d selected",
//                mdrSelected));
            return microdrive[mdrSelected].readData();
        } else {
            return 0xff;
        }
    }
    
    public int readLanPort() {
        return lan;
    }
    
    public void writeControlPort(int value) {
        if ((value & CTRL_OUT_COMMSCLK) == 0 && commsClk) {
            mdrFlipFlop <<= 1;
            if ((value & CTRL_OUT_COMMSDATA) == 0) {
                mdrFlipFlop |= 0x01;
            }
            
            mdrFlipFlop &= 0xff;
            if (mdrFlipFlop != 0) {
                switch (mdrFlipFlop) {
                    case 1:
                        mdrSelected = 0;
                        break;
                    case 2:
                        mdrSelected = 1;
                        break;
                    case 4:
                        mdrSelected = 2;
                        break;
                    case 8:
                        mdrSelected = 3;
                        break;
                    case 16:
                        mdrSelected = 4;
                        break;
                    case 32:
                        mdrSelected = 5;
                        break;
                    case 64:
                        mdrSelected = 6;
                        break;
                    case 128:
                        mdrSelected = 7;
                        break;
                }
            }
            
            if (mdrFlipFlop != 0 && mdrSelected > numMicrodrives - 1) {
                mdrFlipFlop = 0;
//                System.out.println("All MDR are stopped");
            }
            
            if (mdrFlipFlop != 0) {
                microdrive[mdrSelected].selected();
//                System.out.println(String.format("MDR %d [%d] selected",
//                    mdrFlipFlop, mdrSelected));
            }
            
            boolean motorOn = mdrFlipFlop != 0;
            
            if (mdrvIcon.isEnabled() != motorOn)
                updateMdrvIcon();
        }
        
        if (mdrFlipFlop != 0)
            microdrive[mdrSelected].writeControl(value);
        
        commsClk = (value & CTRL_OUT_COMMSCLK) != 0;
//        System.out.println(String.format("erase: %b, r/w: %b",
//            (value & CTRL_OUT_ERASE) != 0, (value & CTRL_OUT_RW) != 0));
    }
    
    public void writeDataPort(int value) {
        if (mdrFlipFlop != 0 && microdrive[mdrSelected].isCartridge()) {
//            System.out.println(String.format("readDataPort: Unit %d selected",
//                mdrSelected));
            microdrive[mdrSelected].writeData(value);
        }
    }
    
    public void writeLanPort(int value) {
        lan = value;
        return;
    }
    
    public void reset() {
        mdrFlipFlop = 0;
        updateMdrvIcon();
    }
    
    public boolean isCartridge(int drive) {
        if (drive < 0 || drive > 7)
            return false;
        
        return microdrive[drive].isCartridge();
    }
    
    public boolean isModified(int drive) {
        if (drive < 0 || drive > 7)
            return false;
        
        return microdrive[drive].isModified();
    }
    
    public byte getNumDrives() {
        return numMicrodrives;
    }
    
    public void setNumDrives(byte drives) {
        if (drives < 1 || drives > 8)
            drives = 8;
        
        numMicrodrives = drives;
    }
    
    public boolean isWriteProtected(int drive) {
        if (drive < 0 || drive > 7)
            return false;
        
        return microdrive[drive].isWriteProtected();
    }
    
    public void setWriteProtected(int drive, boolean state) {
        if (drive < 0 || drive > 7)
            return;
        
        microdrive[drive].setWriteProtected(state);
    }
    
    public String getFilename(int drive) {
        if (drive < 0 || drive > 7)
            return null;
        
//        System.out.println(String.format("filename: %s", microdrive[drive].getFilename()));
        return microdrive[drive].getFilename();
    }
    
    public String getAbsolutePath(int drive) {
        if (drive < 0 || drive > 7)
            return null;
        
//        System.out.println(String.format("filename: %s", microdrive[drive].getFilename()));
        return microdrive[drive].getAbsolutePath();
    }
    
    public boolean insertNew(int drive) {
        if (drive <  0 || drive > 7)
            return false;
        
        return microdrive[drive].insertNew(settings.getCartridgeSize());
    }
    
    
    public boolean insertFile(int drive, File filename) {
        if (drive <  0 || drive > 7)
            return false;
        
        return microdrive[drive].insertFromFile(filename);
    }
    
    public boolean eject(int drive) {
        if (drive <  0 || drive > 7)
            return false;
        
        return microdrive[drive].eject();
    }
    
    public boolean save(int drive) {
        if (drive <  0 || drive > 7)
            return false;
        
        return microdrive[drive].save();
    }
    
    public boolean save(int drive, File name) {
        if (drive <  0 || drive > 7)
            return false;
        
        return microdrive[drive].save(name);
    }
    
    public boolean hasDirtyCartridges() {
        boolean res = false;
        
        for (int drive = 0; drive < numMicrodrives; drive++) {
            if (microdrive[drive].isModified()) {
                res = true;
                break;
            }
        }
        return res;
    }
    
    public boolean isDriveRunning(int drive) {
        if (drive <  0 || drive > 7 || mdrFlipFlop == 0)
            return false;
        
        return mdrSelected == drive ? true : false;
    }
    
    public int getDriveRunning() {
        if (mdrFlipFlop == 0)
            return 0;
        
        return mdrSelected;
    }
    
    public void setDriveRunning(int drive, boolean state) {
        if (drive <  0 || drive > 7 || !state)
            return;
        
        mdrFlipFlop = (0x01 << drive);
        mdrSelected = drive;
    }
    
    public int getDrivePos(int drive) {
        if (drive <  0 || drive > 7)
            return -1;
        
        return microdrive[drive].getCartridgePos();
    }
    
    public void setDrivePos(int drive, int offset) {
        if (drive <  0 || drive > 7)
            return;
        
        microdrive[drive].setCartridgePos(offset);
    }
    
    public int getPreambleRem(int drive) {
        if (drive <  0 || drive > 7)
            return -1;
        
        return microdrive[drive].getPreambleRem();
    }
    
    public void setPreambleRem(int drive, int offset) {
        if (drive <  0 || drive > 7)
            return;
        
        microdrive[drive].setPreambleRem(offset);
    }
    
    private javax.swing.JLabel mdrvIcon;
    public void setMdrvIcon(javax.swing.JLabel tapeLabel) {
        mdrvIcon = tapeLabel;
        updateMdrvIcon();
    }
    
    private void updateMdrvIcon() {
        if (mdrvIcon == null) {
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                if (mdrFlipFlop == 0) {
                    mdrvIcon.setEnabled(false);
                } else {
                    mdrvIcon.setEnabled(true);
            }
            }
        });
    }
}
