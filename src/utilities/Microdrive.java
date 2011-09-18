/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utilities;

/**
 *
 * @author jsanchez
 */
public class Microdrive {
    // Definition of states for the Microdrive Machine States
    private enum MDR_STATE { GAP_HDR, PREAMBLE_HDR, HEADER_HDR,
    GAP_BLOCK, PREAMBLE_BLOCK, HEADER_BLOCK, DATA_BLOCK, DATA_CHECKSUM
    };
    
    private MDR_STATE mdrState;
    private int position;
    private byte cartridge[];
    private short nBlocks;
    
    public Microdrive() {
        position = 0;
        // A microdrive cartridge can have 254 sectors of 543 bytes length
        nBlocks = 254;
    }
}
