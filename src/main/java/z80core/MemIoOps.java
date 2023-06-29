/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package z80core;

/**
 *
 * @author jsanchez
 */
public class MemIoOps {
    private byte z80Ram[] = null;
    private byte z80Ports[] = null;

    private long tstates = 0;

    public MemIoOps() {
        z80Ram = new byte[0x10000];
        z80Ports = new byte[0x10000];
    }

    public MemIoOps(int ramSize, int portSize) {
        if (ramSize < 0 || ramSize > 0xFFFF)
            throw new IndexOutOfBoundsException("ramSize Out of Range [0x0000 - 0xFFFF");

        if (ramSize > 0) {
            z80Ram = new byte[ramSize];
        }

        if (portSize < 0 || portSize > 0xFFFF)
            throw new IndexOutOfBoundsException("portSize Out of Range [0x0000 - 0xFFFF");

        if (portSize > 0) {
            z80Ports = new byte[portSize];
        }
    }

    public void setRam(byte ram[]) {
        z80Ram = ram;
    }

    public void setPorts(byte ports[]) {
        z80Ports = ports;
    }

    public int fetchOpcode(int address) {
        // 3 clocks to fetch opcode from RAM and 1 execution clock
        tstates += 4;
        return z80Ram[address] & 0xff;
    }

    public int peek8(int address) {
        tstates += 3; // 3 clocks for read byte from RAM
        return z80Ram[address] & 0xff;
    }

    public void poke8(int address, int value) {
        tstates += 3; // 3 clocks for write byte to RAM
        z80Ram[address] = (byte)value;
    }

    public int peek16(int address) {
        int lsb = peek8(address);
        int msb = peek8(address + 1);
        return (msb << 8) | lsb;
    }

    public void poke16(int address, int word) {
        poke8(address, word);
        poke8(address + 1, word >>> 8);
    }

    public int inPort(int port) {
        tstates += 4; // 4 clocks for read byte from bus
        return z80Ports[port] & 0xff;
    }

    public void outPort(int port, int value) {
        tstates += 4; // 4 clocks for write byte to bus
        z80Ports[port] = (byte)value;
    }

    public void addressOnBus(int address, int tstates) {
        // Additional clocks to be added on some instructions
        // Not to be changed, really.
        this.tstates += tstates;
    }

    public void interruptHandlingTime(int tstates) {
        // Additional clocks to be added on INT & NMI
        // Not to be changed, really.
        this.tstates += tstates;
    }

    public boolean isActiveINT() {
        return false;
    }

    public long getTstates() {
        return tstates;
    }

    public void reset() {
        tstates = 0;
    }
}
