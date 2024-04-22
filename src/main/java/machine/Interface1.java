/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import configuration.Interface1Type;
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
    private final Microdrive microdrive[];
    private boolean commsClk;
    private final Interface1Type settings;
    private int lan;

    private final ArrayList<Interface1DriveListener> driveListeners = new ArrayList<>();

    public Interface1(Interface1Type if1settings) {
        settings = if1settings;
        mdrFlipFlop = 0;
        mdrSelected = 0;
        numMicrodrives = settings.getMicrodriveUnits();

        microdrive = new Microdrive[8];
        for (int mdr = 0; mdr < 8; mdr++)
            microdrive[mdr] = new Microdrive();

        commsClk = false;
        lan = 0;
    }

    /**
     * Adds a new event listener to the list of event listeners.
     *
     * @param listener The new event listener.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     */
    public void addInterface1DriveListener(final Interface1DriveListener listener) {

        Objects.requireNonNull(listener, "Internal Error: Listener can't be null");

        // Avoid duplicates
        if (!driveListeners.contains(listener)) {
            driveListeners.add(listener);
        }
    }

    /**
     * Remove a new event listener from the list of event listeners.
     *
     * @param listener The event listener to remove.
     *
     * @throws NullPointerException Thrown if the listener argument is null.
     * @throws IllegalArgumentException Thrown if the listener wasn't registered.
     */
    public void removeInterface1DriveListener(final Interface1DriveListener listener) {

        Objects.requireNonNull(listener, "Internal Error: Listener can't be null");

        if (!driveListeners.remove(listener)) {
            throw new IllegalArgumentException("Internal Error: Listener was not listening on object");
        }
    }

    public void fireDriveSelected(final int unit) {
        for (final Interface1DriveListener listener : driveListeners) {
            listener.driveSelected(unit);
        }
    }

    public void fireDriveWrited(final int unit) {
        for (final Interface1DriveListener listener : driveListeners) {
            listener.driveModified(unit);
        }
    }

    public int readControlPort() {
        if (mdrFlipFlop != 0 && microdrive[mdrSelected].isCartridge()) {
//            System.out.println(String.format("readControlPort: Unit %d selected",
//                mdrSelected));
            return microdrive[mdrSelected].readStatus();
        } else {
            return 0xf4; // WR-Prot active
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
            }

            if (mdrFlipFlop != 0) {
                microdrive[mdrSelected].selected();
                fireDriveSelected(mdrSelected + 1);
//                System.out.println(String.format("MDR %d [%d] selected",
//                    mdrFlipFlop, mdrSelected));
            } else {
//                System.out.println("All MDR are stopped");
                fireDriveSelected(0);
            }
        }

        if (mdrFlipFlop != 0)
            microdrive[mdrSelected].writeControl(value);

        commsClk = (value & CTRL_OUT_COMMSCLK) != 0;
//        System.out.println(String.format("erase: %b, r/w: %b",
//            (value & CTRL_OUT_ERASE) != 0, (value & CTRL_OUT_RW) != 0));
    }

    public void writeDataPort(int value) {
        if (mdrFlipFlop != 0 && microdrive[mdrSelected].isCartridge()) {
            if (!microdrive[mdrSelected].isModified()) {
                fireDriveWrited(mdrSelected + 1);
            }

            microdrive[mdrSelected].writeData(value);
        }
    }

    public void writeLanPort(int value) {
        lan = value;
    }

    public void reset() {
        mdrFlipFlop = 0;
        fireDriveSelected(0);
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

        return mdrSelected == drive;
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

//        return microdrive[drive].getPreambleRem();
        return -1;
    }
}
