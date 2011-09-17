/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 *
 * @author jsanchez
 */
public class Snapshots {
    private int regAF, regBC, regDE, regHL;
    private int regAFalt, regBCalt, regDEalt, regHLalt;
    private int regIX, regIY, regPC, regSP;
    private int regI, regR, modeIM;
    private boolean iff1, iff2;
    private int border;
    private int tstates;
    private int Ram[];
    private FileInputStream fIn;
    private boolean snapLoaded;
    private int error;
    private final String errorString[] = { "OPERACIÓN_CORRECTA",
                                           "EL_ARCHIVO_NO_ES_UNA_IMAGEN",
                                           "NO_SE_PUDO_ABRIR_EL_ARCHIVO",
                                           "LA_IMAGEN_TIENE_UNA_LONGITUD_INCORRECTA",
                                           "ERROR_DE_LECTURA_DEL_ARCHIVO",
                                           "LA_VERSIÓN_DEL_Z80_ES_MAYOR_DE_1"
    };

    public Snapshots() {
        Ram = new int[0xC000];
        snapLoaded = false;
        error = 0;
    }

    public boolean isSnapLoaded() {
        return snapLoaded;
    }

    public int getRegAF() {
        return regAF;
    }

    public int getRegBC() {
        return regBC;
    }

    public int getRegDE() {
        return regDE;
    }

    public int getRegHL() {
        return regHL;
    }

    public int getRegAFalt() {
        return regAFalt;
    }

    public int getRegBCalt() {
        return regBCalt;
    }

    public int getRegDEalt() {
        return regDEalt;
    }

    public int getRegHLalt() {
        return regHLalt;
    }

    public int getRegIX() {
        return regIX;
    }

    public int getRegIY() {
        return regIY;
    }

    public int getRegSP() {
        return regSP;
    }

    public int getRegPC() {
        return regPC;
    }

    public int getRegI() {
        return regI;
    }

    public int getRegR() {
        return regR;
    }

    public int getModeIM() {
        return modeIM;
    }

    public boolean getIFF1() {
        return iff1;
    }

    public boolean getIFF2() {
        return iff2;
    }

    public int getBorder() {
        return border;
    }

    public int getTstates(){
        return tstates;
    }

    public int getRamAddr(int address) {
        return Ram[address - 0x4000];
    }

    public String getErrorString() {
        return java.util.ResourceBundle.getBundle("utilities/Bundle").getString(
            errorString[error]);
    }

    public boolean loadSnapshot(File filename) {
        if( filename.getName().toLowerCase().endsWith(".sna") )
            return loadSNA(filename);
        if( filename.getName().toLowerCase().endsWith(".z80") )
            return loadZ80(filename);
        error = 1;
        return false;
    }

    private boolean loadSNA(File filename) {
        try {
            try {
                fIn = new FileInputStream(filename);
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            if (fIn.available() != 49179) {
                error = 3;
                fIn.close();
                return false;
            }

            regI = fIn.read();
            regHLalt = fIn.read() | (fIn.read() << 8) & 0xffff;
            regDEalt = fIn.read() | (fIn.read() << 8) & 0xffff;
            regBCalt = fIn.read() | (fIn.read() << 8) & 0xffff;
            regAFalt = fIn.read() | (fIn.read() << 8) & 0xffff;
            regHL = fIn.read() | (fIn.read() << 8) & 0xffff;
            regDE = fIn.read() | (fIn.read() << 8) & 0xffff;
            regBC = fIn.read() | (fIn.read() << 8) & 0xffff;
            regIY = fIn.read() | (fIn.read() << 8) & 0xffff;
            regIX = fIn.read() | (fIn.read() << 8) & 0xffff;

            iff1 = iff2 = false;
            int iff2EI = fIn.read() & 0xff;
            if ((iff2EI & 0x02) != 0) {
                iff2 = true;
            }

            if ((iff2EI & 0x01) != 0) {
                iff1 = true;
            }

            regR = fIn.read();
            regAF = fIn.read() | (fIn.read() << 8) & 0xffff;
            regSP = fIn.read() | (fIn.read() << 8) & 0xffff;
            modeIM = fIn.read() & 0xff;

            border = fIn.read() & 0x07;

            int count;
            for (count = 0; count < 0xC000; count++) {
                Ram[count] = (int) fIn.read() & 0xff;
            }

            fIn.close();

            regPC = 0x72;  // código de RETN en la ROM
            tstates = 0;

        } catch (IOException ex) {
            error = 4;
            return false;
        }

        return true;
    }

    private boolean loadZ80(File filename) {
        try {
            try {
                fIn = new FileInputStream(filename);
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            if (fIn.available() < 30) {
                error = 3;
                fIn.close();
                return false;
            }

            regAF = (fIn.read() << 8) | fIn.read() & 0xffff;
            regBC = fIn.read() | (fIn.read() << 8) & 0xffff;
            regHL = fIn.read() | (fIn.read() << 8) & 0xffff;
            regPC = fIn.read() | (fIn.read() << 8) & 0xffff;
            if( regPC == 0) {  // de momento, solo se soporta el Z80 v1.0
                error = 5;
                fIn.close();
                return false;
            }
            regSP = fIn.read() | (fIn.read() << 8) & 0xffff;
            regI = fIn.read() & 0xff;
            regR = fIn.read() & 0x7f;

            int byte12 = fIn.read() & 0xff;
            if ((byte12 & 0x01) != 0 )
                regR |= 0x80;
            border = (byte12 >>> 1) & 0x07;

            regDE = fIn.read() | (fIn.read() << 8) & 0xffff;
            regBCalt = fIn.read() | (fIn.read() << 8) & 0xffff;
            regDEalt = fIn.read() | (fIn.read() << 8) & 0xffff;
            regHLalt = fIn.read() | (fIn.read() << 8) & 0xffff;
            regAFalt = (fIn.read() << 8) | fIn.read() & 0xffff;
            regIY = fIn.read() | (fIn.read() << 8) & 0xffff;
            regIX = fIn.read() | (fIn.read() << 8) & 0xffff;
            iff1 = fIn.read() != 0 ? true : false;
            iff2 = fIn.read() != 0 ? true : false;
            int byte29 = fIn.read() & 0xff;
            modeIM = byte29 & 0x03;

            if ((byte12 & 0x20) == 0) { // el bloque no está comprimido
                int address;
                for (address = 0; address < 0xC000; address++) {
                    Ram[address] = fIn.read() & 0xff;
                }
            } else {
                int address = 0;
                while (fIn.available() > 0 && address < 0xC000) {
                    int mem = fIn.read() & 0xff;
                    if (mem != 0xED) {
                        Ram[address++] = mem;
                    } else {
                        int mem2 = fIn.read() & 0xff;
                        if( mem2 != 0xED ) {
                            Ram[address++] = 0xED;
                            Ram[address++] = mem2;
                        } else {
                            int nreps = fIn.read() & 0xff;
                            int value = fIn.read() & 0xff;
                            while(nreps-- > 0)
                                Ram[address++] = value;
                        }
                    }
                }
//                System.out.println(String.format("Leídos %d bytes de RAM", address));
//                System.out.println(String.format("Quedan %d bytes por leer", fIn.available()));
//                while (fIn.available() > 0)
//                    System.out.println(String.format("Byte: %02x", fIn.read()));
                if (fIn.available() != 4 || address < 0xC000) {
                    error = 4;
                    fIn.close();
                    return false;
                }
            }

            fIn.close();

            tstates = 0;

        } catch (IOException ex) {
            error = 4;
            return false;
        }

        return true;
    }
}
