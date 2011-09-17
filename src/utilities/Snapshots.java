/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import machine.MachineTypes;
import machine.Memory;

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
    private int last7ffd;
    private int flagsMode;
    private int lastfffd;
    private int last1ffd;
    int psgRegs[] = new int[16];
    private MachineTypes.CodeModel snapshotModel;
    private int border;
    private int tstates;
    private FileInputStream fIn;
    private FileOutputStream fOut;
    private boolean snapLoaded;
    private int error;
    private final String errorString[] = {"OPERATION_OK",
        "NOT_SNAPSHOT_FILE",
        "OPEN_FILE_ERROR",
        "FILE_SIZE_ERROR",
        "FILE_READ_ERROR",
        "UNSUPPORTED_SNAPSHOT",
        "FILE_WRITE_ERROR",
        "SNA_REGSP_ERROR",
        "SNAP_EXTENSION_ERROR"
    };

    public Snapshots() {
//        Ram = new int[0x10000];
        snapLoaded = false;
        error = 0;
    }

    public boolean isSnapLoaded() {
        return snapLoaded;
    }

    public MachineTypes.CodeModel getSnapshotModel() {
        return snapshotModel;
    }

    public void setSnapshotModel(MachineTypes.CodeModel model) {
        snapshotModel = model;
    }

    public int getRegAF() {
        return regAF;
    }

    public void setRegAF(int value) {
        regAF = value;
    }

    public int getRegBC() {
        return regBC;
    }

    public void setRegBC(int value) {
        regBC = value;
    }

    public int getRegDE() {
        return regDE;
    }

    public void setRegDE(int value) {
        regDE = value;
    }

    public int getRegHL() {
        return regHL;
    }

    public void setRegHL(int value) {
        regHL = value;
    }

    public int getRegAFalt() {
        return regAFalt;
    }

    public void setRegAFalt(int value) {
        regAFalt = value;
    }

    public int getRegBCalt() {
        return regBCalt;
    }

    public void setRegBCalt(int value) {
        regBCalt = value;
    }

    public int getRegDEalt() {
        return regDEalt;
    }

    public void setRegDEalt(int value) {
        regDEalt = value;
    }

    public int getRegHLalt() {
        return regHLalt;
    }

    public void setRegHLalt(int value) {
        regHLalt = value;
    }

    public int getRegIX() {
        return regIX;
    }

    public void setRegIX(int value) {
        regIX = value;
    }

    public int getRegIY() {
        return regIY;
    }

    public void setRegIY(int value) {
        regIY = value;
    }

    public int getRegSP() {
        return regSP;
    }

    public void setRegSP(int value) {
        regSP = value;
    }

    public int getRegPC() {
        return regPC;
    }

    public void setRegPC(int value) {
        regPC = value;
    }

    public int getRegI() {
        return regI;
    }

    public void setRegI(int value) {
        regI = value;
    }

    public int getRegR() {
        return regR;
    }

    public void setRegR(int value) {
        regR = value;
    }

    public int getModeIM() {
        return modeIM;
    }

    public void setModeIM(int value) {
        modeIM = value;
    }

    public boolean getIFF1() {
        return iff1;
    }

    public void setIFF1(boolean value) {
        iff1 = value;
    }

    public boolean getIFF2() {
        return iff2;
    }

    public void setIFF2(boolean value) {
        iff2 = value;
    }

    public int getPort7ffd() {
        return last7ffd;
    }

    public void setPort7ffd(int port7ffd) {
        last7ffd = port7ffd;
    }

    public int getPort1ffd() {
        return last1ffd;
    }

    public void setPort1ffd(int port1ffd) {
        last1ffd = port1ffd;
    }

    public int getPortFffd() {
        return lastfffd;
    }

    public void setPortfffd(int portfffd) {
        lastfffd = portfffd;
    }

    public int getPsgReg(int reg) {
        return psgRegs[reg];
    }

    public void setPsgReg(int reg, int value) {
        psgRegs[reg] = value;
    }

    public boolean getAYEnabled() {
        return (flagsMode & 0x04) == 0 ? false : true;
    }

    public void setAyEnabled(boolean ayEnabled) {
        flagsMode &= 0xfb;
        if (ayEnabled) {
            flagsMode |= 0x04;
        }
    }

    public int getBorder() {
        return border;
    }

    public void setBorder(int value) {
        border = value;
    }

    public int getTstates() {
        return tstates;
    }

    public void setTstates(int value) {
        tstates = value;
    }

    public String getErrorString() {
        return java.util.ResourceBundle.getBundle("utilities/Bundle").getString(
            errorString[error]);
    }

    public boolean loadSnapshot(File filename, Memory memory) {
        if (filename.getName().toLowerCase().endsWith(".sna")) {
            return loadSNA(filename, memory);
        }
        if (filename.getName().toLowerCase().endsWith(".z80")) {
            return loadZ80(filename, memory);
        }
        error = 1;
        return false;
    }

    public boolean saveSnapshot(File filename, Memory memory) {
        if (filename.getName().toLowerCase().endsWith(".sna")) {
            return saveSNA(filename, memory);
        }
//        if( filename.getName().toLowerCase().endsWith(".z80") )
//            return loadZ80(filename);
        error = 8;
        return false;
    }

    private boolean loadSNA(File filename, Memory memory) {
        try {
            try {
                fIn = new FileInputStream(filename);
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            int snaLen = fIn.available();
            switch (snaLen) {
                case 49179: // 48K
                    snapshotModel = MachineTypes.CodeModel.SPECTRUM48K;
                    memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                    memory.reset();
                    break;
                case 131103: // 128k
                    snapshotModel = MachineTypes.CodeModel.SPECTRUM128K;
                    memory.setSpectrumModel(MachineTypes.SPECTRUM128K);
                    memory.reset();
                    break;
                default:
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
            if ((iff2EI & 0x04) != 0) {
                iff2 = true;
            }

            if ((iff2EI & 0x02) != 0) {
                iff1 = true;
            }

            regR = fIn.read();
            regAF = fIn.read() | (fIn.read() << 8) & 0xffff;
            regSP = fIn.read() | (fIn.read() << 8) & 0xffff;
            modeIM = fIn.read() & 0xff;

            border = fIn.read() & 0x07;

            boolean loaded[] = {false, false, true, false,
                false, true, false, false};
            int highPage[] = new int[0x4000];
            if (snaLen == 49179) { // 48K
                for (int addr = 0x4000; addr < 0x10000; addr++) {
                    memory.writeByte(addr, (int) fIn.read() & 0xff);
                }
                regPC = 0x72; // dirección de RETN en la ROM
            } else {
                for (int addr = 0x4000; addr < 0xC000; addr++) {
                    memory.writeByte(addr, (int) fIn.read() & 0xff);
                }

                // Hasta que leamos el último valor del puerto 0x7ffd no sabemos
                // en qué página hay que poner los últimos 16K. Los leemos en
                // un buffer temporal y luego los copiamos (que remedio!!!)
                for (int addr = 0x0000; addr < 0x4000; addr++) {
                    highPage[addr] = (int) fIn.read() & 0xff;
                }
                
                regPC = fIn.read() | (fIn.read() << 8) & 0xffff;
                last7ffd = fIn.read() | (fIn.read() << 8) & 0xffff;
                memory.setPort7ffd(last7ffd);
                for (int addr = 0; addr < 0x4000; addr++) {
                    memory.writeByte(addr + 0xC000, highPage[addr]);
                }
                loaded[last7ffd & 0x07] = true;
                int trDos = fIn.read();
                
                for (int page = 0; page < 8; page++) {
                    if (!loaded[page]) {
                        memory.setPort7ffd(page);
                        for (int addr = 0xC000; addr < 0x10000; addr++) {
                            memory.writeByte(addr, (int) fIn.read() & 0xff);
                        }
                    }
                    // El formato SNA no guarda los registros del AY
                    // Los ponemos a cero y que se apañe....
                    Arrays.fill(psgRegs, 0);
                    lastfffd = 0;
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

    private boolean saveSNA(File filename, Memory memory) {

        if (regSP < 0x4002) {
            error = 7;
            return false;
        }

        try {
            try {
                fOut = new FileOutputStream(filename);
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            fOut.write(regI);
            fOut.write(regHLalt);
            fOut.write(regHLalt >>> 8);
            fOut.write(regDEalt);
            fOut.write(regDEalt >>> 8);
            fOut.write(regBCalt);
            fOut.write(regBCalt >>> 8);
            fOut.write(regAFalt);
            fOut.write(regAFalt >>> 8);
            fOut.write(regHL);
            fOut.write(regHL >>> 8);
            fOut.write(regDE);
            fOut.write(regDE >>> 8);
            fOut.write(regBC);
            fOut.write(regBC >>> 8);
            fOut.write(regIY);
            fOut.write(regIY >>> 8);
            fOut.write(regIX);
            fOut.write(regIX >>> 8);

            int iff2EI = 0;
            if (iff1) {
                iff2EI |= 0x02;
            }
            if (iff2) {
                iff2EI |= 0x04;
            }
            fOut.write(iff2EI);

            fOut.write(regR);
            fOut.write(regAF);
            fOut.write(regAF >>> 8);

            regSP = (regSP - 1) & 0xffff;
            memory.writeByte(regSP, regPC >>> 8);
            regSP = (regSP - 1) & 0xffff;
            memory.writeByte(regSP, regPC & 0xff);

            fOut.write(regSP);
            fOut.write(regSP >>> 8);
            fOut.write(modeIM);
            fOut.write(border);

            for (int addr = 0x4000; addr < 0x10000; addr++) {
                fOut.write(memory.readByte(addr));
            }

            fOut.close();

        } catch (IOException ex) {
            error = 6;
            return false;
        }

        return true;
    }

    private boolean uncompressZ80(Memory memory, int address, int length) {
//        System.out.println(String.format("Addr: %04X, len = %d", address, length));
        try {
            int endAddr = address + length;
            while (fIn.available() > 0 && address < endAddr) {
                int mem = fIn.read() & 0xff;
                if (mem != 0xED) {
                    memory.writeByte(address++, mem);
                } else {
                    int mem2 = fIn.read() & 0xff;
                    if (mem2 != 0xED) {
                        memory.writeByte(address++, 0xED);
                        memory.writeByte(address++, mem2);
                    } else {
                        int nreps = fIn.read() & 0xff;
                        int value = fIn.read() & 0xff;
                        while (nreps-- > 0) {
                            memory.writeByte(address++, value);
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

    private boolean loadZ80(File filename, Memory memory) {
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
            if ((byte12 & 0x01) != 0) {
                regR |= 0x80;
            }
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

            // Si regPC == 0, es un z80 v1.0
            if (regPC != 0) {
                snapshotModel = MachineTypes.CodeModel.SPECTRUM48K;
                memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                memory.reset();
                if ((byte12 & 0x20) == 0) { // el bloque no está comprimido
                    int address;
                    for (address = 0x4000; address < 0x10000; address++) {
                        memory.writeByte(address, fIn.read() & 0xff);
                    }
                } else {
                    uncompressZ80(memory, 0x4000, 0xC000);
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
                if (hdrLen == 23) { // Z80 v2
                    switch (hwMode) {
                        case 0: // 48k
                            snapshotModel = MachineTypes.CodeModel.SPECTRUM48K;
                            memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                            memory.reset();
                            break;
                        case 3: // 128k
                            snapshotModel = MachineTypes.CodeModel.SPECTRUM128K;
                            memory.setSpectrumModel(MachineTypes.SPECTRUM128K);
                            memory.reset();
                            break;
                        default:
                            error = 5;
                            fIn.close();
                            return false;
                    }
                } else { // Z80 v3
                    switch (hwMode) {
                        case 0: // 48k
                            snapshotModel = MachineTypes.CodeModel.SPECTRUM48K;
                            memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                            memory.reset();
                            break;
                        case 4: // 128k
                            snapshotModel = MachineTypes.CodeModel.SPECTRUM128K;
                            memory.setSpectrumModel(MachineTypes.SPECTRUM128K);
                            memory.reset();
                            break;
                        default:
                            error = 5;
                            fIn.close();
                            return false;
                    }
                }

                last7ffd = fIn.read() & 0xff;
                int if1Paged = fIn.read() & 0xff;
                flagsMode = fIn.read() & 0xff;
                lastfffd = fIn.read() & 0xff;
                for (int idx = 0; idx < 16; idx++) {
                    psgRegs[idx] = fIn.read() & 0xff;
                }

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
                    last1ffd = 0;
                    if (hdrLen == 55) {
                        last1ffd = fIn.read() & 0xff;
                    }
                }

                while (fIn.available() > 0) {
                    int addr;
                    int blockLen = fIn.read() | (fIn.read() << 8) & 0xffff;
                    int ramPage = fIn.read() & 0xff;
                    if (snapshotModel == MachineTypes.CodeModel.SPECTRUM48K) {
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
                    } else { // snapshotModel == 128K
                        if (ramPage >= 3 && ramPage <= 10) {
                            memory.setPort7ffd(ramPage - 3);
                            addr = 0xC000;
                        } else {
                            addr = 0;
                        }
                    }
                    if (blockLen == 0xffff) { // uncompressed data
                        for (int count = 0; count < 0x4000; count++) {
                            memory.writeByte(addr + count, fIn.read() & 0xff);
                        }
                    } else {
                        uncompressZ80(memory, addr, 0x4000);
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
