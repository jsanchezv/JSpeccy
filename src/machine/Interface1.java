/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

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
    
    private byte mdrSelected;
    private byte numMicrodrives;
    private Microdrive microdrive[];
    
    public Interface1() {
        mdrSelected = 0;
        numMicrodrives = 8;
        microdrive = new Microdrive[8];
        microdrive[0] = new Microdrive();
    }
    
    public int readControlPort() {
        return microdrive[mdrSelected].getStatus();
    }
    
    public int readDataPort() {
        return microdrive[mdrSelected].getByte();
    }
}
