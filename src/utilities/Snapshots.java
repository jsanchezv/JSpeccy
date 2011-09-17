/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
                                           "128K_Z80_SNAPSHOT"
    };

    public Snapshots() {
        Ram = new int[0x10000];
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
        return Ram[address];
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

            for (int addr = 0x4000; addr < 0x10000; addr++) {
                Ram[addr] = (int) fIn.read() & 0xff;
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

    private boolean uncompressZ80(int address, int length) {
//        System.out.println(String.format("Addr: %04X, len = %d", address, length));
        try {
            int endAddr = address + length;
            while (fIn.available() > 0 && address < endAddr) {
                int mem = fIn.read() & 0xff;
                if (mem != 0xED) {
                    Ram[address++] = mem;
                } else {
                    int mem2 = fIn.read() & 0xff;
                    if (mem2 != 0xED) {
                        Ram[address++] = 0xED;
                        Ram[address++] = mem2;
                    } else {
                        int nreps = fIn.read() & 0xff;
                        int value = fIn.read() & 0xff;
                        while (nreps-- > 0) {
                            Ram[address++] = value;
                        }
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Snapshots.class.getName()).log(Level.SEVERE, null, ex);
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

            if (regPC != 0) {
                if ((byte12 & 0x20) == 0) { // el bloque no está comprimido
                    int address;
                    for (address = 0x4000; address < 0x10000; address++) {
                        Ram[address] = fIn.read() & 0xff;
                    }
                } else {
                    uncompressZ80(0x4000, 0xC000);
                    if (fIn.available() != 4) {
                        error = 4;
                        fIn.close();
                        return false;
                    }
                }
            } else {
                // Z80 v2 & v3
                int hdrLen = fIn.read() | (fIn.read() << 8) & 0xffff;
//                if (hdrLen > 23)
//                    System.out.println("Z80 v3. Header: " + hdrLen);
//                else
//                    System.out.println("Z80 v2");
                
                regPC = fIn.read() | (fIn.read() << 8) & 0xffff;
                int hwMode = fIn.read() & 0xff;
                if (hwMode > 1) {  // de momento, solo se soporta el 48k
                    error = 5;
                    fIn.close();
                    return false;
                }
                int last7ffd = fIn.read() & 0xff;
                int if1Paged = fIn.read() & 0xff;
                int flagsMode = fIn.read() & 0xff;
                int lastfffd = fIn.read() & 0xff;
                int psgRegs[] = new int[16];
                for (int idx = 0; idx < 16; idx++)
                    psgRegs[idx] = fIn.read() & 0xff;

                if (hdrLen > 23) { // Z80 v3.x
                    int lowTstate = fIn.read() | (fIn.read() << 8) & 0xffff;
                    int highTstate = fIn.read() & 0xff;
                    int fooByte = fIn.read() & 0xff; // Spectator flag (always 0)
                    int mgtRom = fIn.read() & 0xff;
                    int multifaceRom = fIn.read() & 0xff;
                    int rom0_8k = fIn.read() & 0xff;
                    int rom8_16k = fIn.read() & 0xff;
                    int keymaps[] = new int[5];
                    for (int idx = 0; idx < 5; idx++) {
                        keymaps[idx] = fIn.read() & 0xff;
                        fooByte = fIn.read(); // always 0
                    }
                    int keyASCII[] = new int[5];
                    for (int idx = 0; idx < 5; idx++) {
                        keyASCII[idx] = fIn.read() | (fIn.read() << 8) & 0xffff;
                    }
                    int mgtType = fIn.read() & 0xff;
                    int discipleButton = fIn.read() & 0xff;
                    int discipleFlag = fIn.read() & 0xff;
                    int last1ffd = 0;
                    if (hdrLen == 55)
                        last1ffd = fIn.read() & 0xff;
                }

                while (fIn.available() > 0) {
                    int addr;
                    int blockLen = fIn.read() | (fIn.read() << 8) & 0xffff;
                    int ramPage = fIn.read() & 0xff;
                    switch (ramPage) {
                        case 4:  // 0x8000-0xbfff
                            addr = 0x8000;
                            break;
                        case 5:  // 0xC000-0xFFFF
                            addr = 0xC000;
                            break;
                        case 8:  // 0x4000-0x7FFF
                            addr = 0x4000;
                            break;
                        default:
                            addr = 0;
                    }
                    if (blockLen == 0xffff) { // uncompressed data
                        for(int count = 0; count < 0x4000; count++)
                            Ram[addr + count] = fIn.read() & 0xff;
                    } else {
                        uncompressZ80(addr, 0x4000);
                    }
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