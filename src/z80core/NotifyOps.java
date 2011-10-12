/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package z80core;

/**
 *
 * @author jsanchez
 */
public interface NotifyOps {
    int atAddress(int address, int opcode);
    void execDone(int tstates);
}
