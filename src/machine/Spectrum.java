/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import gui.JSpeccyScreen;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import z80core.Z80;

/**
 *
 * @author jsanchez
 */
public class Spectrum implements z80core.MemIoOps {
    private Z80 z80;
    private int z80Ram[] = new int[0x10000];
    private int puertos[] = new int[0x10000];

    private FileInputStream fIn;

    private int frameStart;

    public static final int FRAMES48k = 70000;
    private static final byte delayTstates[] = new byte[FRAMES48k];
    static {
        Arrays.fill(delayTstates, (byte)0x00);
        for( int idx = 14336; idx < 57344; idx += 224  ) {
            for( int ndx = 0; ndx < 128; ndx += 8) {
                int frame = idx + ndx;
                delayTstates[frame++] = 6;
                delayTstates[frame++] = 5;
                delayTstates[frame++] = 4;
                delayTstates[frame++] = 3;
                delayTstates[frame++] = 2;
                delayTstates[frame++] = 1;
                delayTstates[frame++] = 0;
                delayTstates[frame++] = 0;
            }
        }
    }

    public Spectrum() {
        z80 = new Z80(this);
        loadRom();
        frameStart = 0;
        for( int addr = 0; addr < z80Ram.length; addr++ )
            z80Ram[addr] = (int)Math.abs(Math.random()) & 0xff;
    }

    public void execFrame() {

        z80.setTEstados(frameStart);
        z80.setINTLine(true);
        while( z80.getTEstados() < 69888 ) {
            z80.execInstruction();
            if( z80.getTEstados() > 28 )
                z80.setINTLine(false);
        }
        z80.setINTLine(true);
        System.out.println("execFrame: ejecutados " + z80.getTEstados());
        frameStart = z80.getTEstados() - 69888;
    }

    private void loadRom() {
        try {
            try {
                fIn = new FileInputStream("spectrum.rom");
            } catch (FileNotFoundException ex) {
                System.out.println("No se pudo abrir el fichero spectrum.rom");
                Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            int count;
            for (count = 0; count < 16384; count++) {
                z80Ram[count] = (int) fIn.read() & 0xff;
            }
            if (count != 16384) {
                System.out.println("No se pudo cargar la ROM");
                return;
            }

            fIn.close();
        } catch (IOException ex) {
            System.out.println("No se pudo leer el fichero spectrum.rom");
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("ROM cargada");
    }

    public int[] getSpectrumMem() {
        return z80Ram;
    }

//    public void run() {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }

    public int getOpcode(int address) {
        int delay = 4;

        //System.out.println(String.format(strMC, z80.getTEstados(), address));

        if( (address & 0xC000) == 0x4000 ) {
            delay += delayTstates[z80.getTEstados()];
                //System.out.println("R(" + dire +"): ");
        }
        z80.addTEstados(delay);
        //System.out.println(String.format(strMR, z80.getTEstados(), address, z80Ram[address]));
        return z80Ram[address & 0xffff];
    }

    public int peek8(int address) {
        int delay = 3;

        //System.out.println(String.format(strMC, z80.getTEstados(), address));

        if( (address & 0xC000) == 0x4000 ) {
            delay += delayTstates[z80.getTEstados()];
                //System.out.println("R(" + dire +"): ");
        }
        z80.addTEstados(delay);

        //System.out.println(String.format(strMR, z80.getTEstados(), address, z80Ram[address]));
        return z80Ram[address & 0xffff];
    }

    public void poke8(int address, int value) {
        int delay = 3;

        //System.out.println(String.format(strMC, z80.getTEstados(), address));

        address &= 0xffff;
        if( address < 0x4000 ) // en la rom no se escribe (o no sería ROM)
            return;

        if( (address & 0xC000) == 0x4000 ) {
            delay += delayTstates[z80.getTEstados()];
                //System.out.println("R(" + dire +"): ");
        }
        z80.addTEstados(delay);
        z80Ram[address] = value & 0xff;
        //System.out.println(String.format(strMW, z80.getTEstados(), address, z80Ram[address]));
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
        int res = port >>> 8;
        preIO(port);
        System.out.println(String.format("%5d PR %04x %02x", z80.getTEstados(), port, res));
        postIO(port);
        return res;
    }

    public void outPort(int port, int value) {
        preIO(port);
        System.out.println(String.format("%5d PW %04x %02x", z80.getTEstados(), port, value));
        postIO(port);
    }

    public int MREQstates(int address, int tstates) {
        int delay = 0;

        address &= 0xffff;
        if( (address & 0xC000) == 0x4000 ) {
            int tEstados = z80.getTEstados();
            for( int idx = 0; idx < tstates; idx++ ) {
                System.out.println(address + " " + (tEstados+delay+idx));
                //System.out.println(String.format(strMC, tEstados+delay+idx, address));
                delay += delayTstates[(tEstados + delay)];

            }
        }
        return (tstates + delay);
    }

    private void preIO(int port) {
        if( ( port & 0xc000 ) == 0x4000 ) {
            //System.out.println(String.format(strPC, z80.getTEstados(), port));
        }
        z80.addTEstados(1);
    }

    private void postIO(int port) {
        if( (port & 0x0001) != 0 ) {
            if( ( port & 0xc000 ) == 0x4000 ) {
                //System.out.println(String.format(strPC, z80.getTEstados(), port));
                z80.addTEstados(1);
                //System.out.println(String.format(strPC, z80.getTEstados(), port));
                z80.addTEstados(1);
                //System.out.println(String.format(strPC, z80.getTEstados(), port));
                z80.addTEstados(1);
            } else {
                z80.addTEstados(3);
            }
        } else {
            //System.out.println(String.format(strPC, z80.getTEstados(), port));
            z80.addTEstados(3);
        }
    }
}
