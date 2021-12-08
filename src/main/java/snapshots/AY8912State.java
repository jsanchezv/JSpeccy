/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package snapshots;

/**
 *
 * @author jsanchez
 */
public class AY8912State {
    // AY register index
    private int addressLatch;
    // AY register set
    private int regAY[];

    public AY8912State() {
    }

    public int getAddressLatch() {
        return addressLatch;
    }

    public void setAddressLatch(int value) {
        addressLatch = value & 0x0f;
    }

    public int[] getRegAY() {
        return regAY;
    }

    public void setRegAY(int reg[]) {
        regAY = reg;
    }
}
