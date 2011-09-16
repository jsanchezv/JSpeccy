/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package z80core;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jsanchez
 */
public class Z80test implements MemIoOps {

    private Z80 z80;
    //private Memoria ram;
    //private InputOutput inOut;
    private static final int copiaRam[] = new int[0x10000];
    private BufferedReader in = null;
    private int tstatesTest;
    private int z80Ram[] = new int[0x10000];
    private int puertos[] = new int[0x10000];

    public static final int FRAMES48k = 71000;

    private static final byte delayTstates[] = new byte[FRAMES48k];
    static {
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
    
    public Z80test() {
        z80 = new Z80(this);
    }

    /**
     * @param args the command line arguments
     */
    
    private void printRegs(int tEstados) {
        //System.out.println("-AF-\t-BC-\t-DE-\t-HL-\t-AF'\t-BC'\t-DE'\t-HL'\t-IX-\t-IY-");
        System.out.print(String.format("%04x ", z80.getRegAF()));
        System.out.print(String.format("%04x ", z80.getRegBC()));
        System.out.print(String.format("%04x ", z80.getRegDE()));
        System.out.print(String.format("%04x ", z80.getRegHL()));
        System.out.print(String.format("%04x ", z80.getRegAFalt()));
        System.out.print(String.format("%04x ", z80.getRegBCalt()));
        System.out.print(String.format("%04x ", z80.getRegDEalt()));
        System.out.print(String.format("%04x ", z80.getRegHLalt()));
        System.out.print(String.format("%04x ", z80.getRegIX()));
        System.out.print(String.format("%04x ", z80.getRegIY()));
        //System.out.println("-SP-\t-PC-\tIFF1\tIFF2\t-I\t-R\tT-Estados");
        System.out.print(String.format("%04x ", z80.getRegSP()));
        System.out.println(String.format("%04x", z80.getRegPC()));
        
        System.out.print(String.format("%02x ", z80.getRegI()));
        System.out.print(String.format("%02x ", z80.getRegR()));
        System.out.print((z80.isIFF1() ? "1 " : "0 "));
        System.out.print((z80.isIFF2() ? "1 " : "0 "));
        System.out.println(z80.getIM() + " 0 " + tEstados);     
    }

    public void initRam() {
        for( int idx = 0; idx < 0x10000; idx += 4 ) {
            z80Ram[idx] = 0xde;
            z80Ram[idx+1] = 0xad;
            z80Ram[idx+2] = 0xbe;
            z80Ram[idx+3] = 0xef;
        }
    }

    public void test() {
        try {
            in = new BufferedReader( new FileReader("tests.in") );
        } catch (FileNotFoundException ex) {
            System.out.println("No puedo abrir el fichero para lectura");
            ex.printStackTrace();
            Logger.getLogger(Z80test.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        z80.reset();
        initRam();
        
        while( leeTest() )
        {
            System.arraycopy(z80Ram, 0, copiaRam, 0, 0x10000);
            //z80.tEstados = 0;
            z80.setTEstados(0);
            while( z80.getTEstados() < tstatesTest ) {
                //z80.setTEstados(0);
                z80.execInstruction();
            }
            printRegs(z80.getTEstados());
            dumpMem();
            System.out.println("");
            z80.reset();
            initRam();
        }
    }
    
    private void dumpMem() {
        for( int addr = 0; addr < 0x10000; addr++ ) {
            if( copiaRam[addr] == z80Ram[addr] )
                continue;
            System.out.print(String.format("%04x ", addr));
            while( addr < 0x10000 && copiaRam[addr] != z80Ram[addr] )
                System.out.print(String.format("%02x ", z80Ram[addr++]));
            
            System.out.println("-1");
        }
    }
    
    private boolean leeTest() {
        String linea;
        StringTokenizer st;
        try {
            do {
                linea = in.readLine();
                if( linea == null )
                    return false;
                st = new StringTokenizer(linea);
            } while( st.countTokens() == 0 );
            System.out.println(linea);
            
            linea = in.readLine();
            if( linea == null )
                return false;
            
            st = new StringTokenizer(linea);
            if( st.countTokens() != 12 ) {
                System.out.println("La línea de registros no es correcta");
            }
            z80.setRegAF(Integer.valueOf(st.nextToken(), 16));
            z80.setRegBC(Integer.valueOf(st.nextToken(), 16));
            z80.setRegDE(Integer.valueOf(st.nextToken(), 16));
            z80.setRegHL(Integer.valueOf(st.nextToken(), 16));
            z80.setRegAFalt(Integer.valueOf(st.nextToken(), 16));
            z80.setRegBCalt(Integer.valueOf(st.nextToken(), 16));
            z80.setRegDEalt(Integer.valueOf(st.nextToken(), 16));
            z80.setRegHLalt(Integer.valueOf(st.nextToken(), 16));
            z80.setRegIX(Integer.valueOf(st.nextToken(), 16));
            z80.setRegIY(Integer.valueOf(st.nextToken(), 16));
            z80.setRegSP(Integer.valueOf(st.nextToken(), 16));
            z80.setRegPC(Integer.valueOf(st.nextToken(), 16));
            
            linea = in.readLine();
            if( linea == null )
                return false;

            st = new StringTokenizer(linea);
            if( st.countTokens() != 7 ) {
                System.out.println("La línea de I, R, etc no es correcta");
            }
            z80.setRegI(Integer.valueOf(st.nextToken(), 16));
            z80.setRegR(Integer.valueOf(st.nextToken(), 16));
            z80.setIFF1((st.nextToken().equals("1") ? true : false));
            z80.setIFF2((st.nextToken().equals("1") ? true : false));
            z80.setIM(Integer.valueOf(st.nextToken()));
            st.nextToken(); // me salto el HALTED
            tstatesTest = Integer.valueOf(st.nextToken());
            //System.out.println("Test a ejecutar durante " + tstatesTest + " t-estados");
            
            linea = in.readLine();

            while( linea != null && !linea.equals("-1") ) {
                pokeMemoria(linea);
                linea = in.readLine();
            }
        } catch( IOException ioExcpt ) {
            System.out.println("Error al leer el fichero de test");
            try {
                in.close();
                return false;
            } catch (IOException ex) {
                Logger.getLogger(Z80test.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return true;
    }
    
    private void pokeMemoria(String linea) {
        StringTokenizer st = new StringTokenizer(linea);
        if( st.countTokens() < 2 ) {
            System.out.println("Linea de pokes erronea");
            return;
        }
        
        int address = Integer.valueOf(st.nextToken(), 16);
        String valor = st.nextToken();
        while( !valor.equals("-1") ) {
            z80Ram[address++] = Integer.valueOf(valor, 16);
            valor = st.nextToken();
        }
    }
    
    private void intTest() {
        int tEstados = 0;
        z80.reset();
        z80.setRegHL(0x1234);
        z80.setRegPC(0x0000);
        z80.setIM(z80.IM1);
        poke8(0x0000,0xFB); //EI
        poke8(0x0001,0x76); //HALT
        poke8(0x0038,0x21); // LD HL,AA55
        poke16(0x0039, 0xAA55);
        
        printRegs(tEstados);
        tEstados = z80.execInstruction(); //el EI
        printRegs(tEstados);
        tEstados = z80.execInstruction();  //el HALT
        printRegs(tEstados);
        z80.setINTLine(true);
        tEstados = z80.execInstruction(); //el HALT y la INT
        printRegs(tEstados);
        tEstados = z80.execInstruction(); //el LD HL,AA55
        printRegs(tEstados);
    }
    
    public void speedTest() {
        long startTime, stopTime;
        //int tEstados = 0;
        z80.reset();
        startTime = System.currentTimeMillis();
        stopTime = startTime;
        while( (stopTime - startTime) < 10000 ) {
            z80.execInstruction();
            stopTime = System.currentTimeMillis();
        }
        
        System.out.println("Tiempo transcurrido: " + (stopTime-startTime));
        printRegs(z80.getTEstados());
    }
    
    public void testFrame(int nFrames) {
        z80.reset();
        Arrays.fill(z80Ram, 0x86);

        long start = System.currentTimeMillis();
        int count = 0;
        while( z80.getTEstados() < nFrames ) {
            z80.execInstruction();
            count++;
            //System.out.println(z80.getTEstados());
        }
        long stop = System.currentTimeMillis();

        System.out.println("Ejecutadas " + count + " instrucciones en " +
                  z80.getTEstados() + " t-estados en " + (stop-start) + " ms.");
        printRegs(z80.getTEstados());
        //System.out.println("Longitud del frame: " + frame);
    }

    public static void main(String[] args) {
        // TODO code application logic here
        Z80test Test = new Z80test();
        Test.test();
        //Test.testFrame(6988800);
        //Test.testFrame(69888);
        //Test.speedTest();
    }

    String strMC = "%5d MC %04x";
    String strMR = "%5d MR %04x %02x";
    String strMW = "%5d MW %04x %02x";
    public int getOpcode(int address) {
        int delay = 4;

        System.out.println(String.format(strMC, z80.getTEstados(), address));

        if( (address & 0xC000) == 0x4000 ) {
            delay += delayTstates[z80.getTEstados()%69888];
                //System.out.println("R(" + dire +"): ");
        }
        //z80.tEstados += delay;
        z80.addTEstados(delay);
        System.out.println(String.format(strMR, z80.getTEstados(), address, z80Ram[address]));
        return z80Ram[address & 0xffff];
    }

    public int peek8(int address) {
        int delay = 3;

        System.out.println(String.format(strMC, z80.getTEstados(), address));

        if( (address & 0xC000) == 0x4000 ) {
            delay += delayTstates[z80.getTEstados()%69888];
                //System.out.println("R(" + dire +"): ");
        }
        //z80.tEstados += delay;
        z80.addTEstados(delay);

        System.out.println(String.format(strMR, z80.getTEstados(), address, z80Ram[address]));
        return z80Ram[address & 0xffff];
    }

    public void poke8(int address, int value) {
        int delay = 3;

        System.out.println(String.format(strMC, z80.getTEstados(), address));

        if( (address & 0xC000) == 0x4000 ) {
            delay += delayTstates[z80.getTEstados()%69888];
                //System.out.println("R(" + dire +"): ");
        }
        //z80.tEstados += delay;
        z80.addTEstados(delay);
        z80Ram[address & 0xffff] = value & 0xff;
        System.out.println(String.format(strMW, z80.getTEstados(), address, z80Ram[address]));
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
        //if( (address & 0xC000) == 0x4000 ) {
            int tEstados = z80.getTEstados();
            for( int idx = 0; idx < tstates; idx++ ) {
                System.out.println(String.format(strMC, tEstados+delay+idx, address));
                delay += delayTstates[(tEstados + delay)%69888];

          //  }
        }
        return (tstates + delay);
    }

    String strPC = "%5d PC %04x";
    private void preIO(int port) {
        if( ( port & 0xc000 ) == 0x4000 ) {
            System.out.println(String.format(strPC, z80.getTEstados(), port));
        }
        z80.addTEstados(1);
    }

    private void postIO(int port) {
        if( (port & 0x0001) != 0 ) {
            if( ( port & 0xc000 ) == 0x4000 ) {
                System.out.println(String.format(strPC, z80.getTEstados(), port));
                //z80.tEstados++;
                z80.addTEstados(1);
                System.out.println(String.format(strPC, z80.getTEstados(), port));
                //z80.tEstados++;
                z80.addTEstados(1);
                System.out.println(String.format(strPC, z80.getTEstados(), port));
                //z80.tEstados++;
                z80.addTEstados(1);
            } else {
                z80.addTEstados(3);
            }
        } else {
            System.out.println(String.format(strPC, z80.getTEstados(), port));
            z80.addTEstados(3);
        }
    }
}