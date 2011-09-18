/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utilities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import machine.MachineTypes;
import machine.Memory;
import machine.Spectrum.Joystick;

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
    private int last1ffd;
    private boolean enabledAY;
    // AY support
    private int lastfffd;
    private int psgRegs[] = new int[16];
    // Multiface support
    private boolean multiface, mfPagedIn, mf128on48k, mfLockout;
    // ULAplus support
    private boolean ULAplus, ULAplusEnabled;
    private int ULAplusRegister, ULAplusPalette[] = new int[64];
    // IF2 ROM support
    private boolean IF2RomPresent;
    // Tape Support
    private boolean tapeEmbedded, tapeLinked;
    private byte tapeData[];
    private int tapeBlock;
    private String tapeName, tapeExtension;

    private MachineTypes snapshotModel;
    private int border;
    private int tstates;
    private Joystick joystick;
    private boolean issue2;
    private BufferedInputStream fIn;
    private BufferedOutputStream fOut;
    private boolean snapLoaded;
    private int error;
    private final String errorString[] = {
        "OPERATION_OK",
        "NOT_SNAPSHOT_FILE",
        "OPEN_FILE_ERROR",
        "FILE_SIZE_ERROR",
        "FILE_READ_ERROR",
        "UNSUPPORTED_SNAPSHOT",
        "FILE_WRITE_ERROR",
        "SNA_REGSP_ERROR",
        "SNAP_EXTENSION_ERROR",
        "SNA_DONT_SUPPORT_PLUS3",
        "SZX_RAMP_SIZE_ERROR"
    };

    public Snapshots() {
        snapLoaded = false;
        error = 0;
    }

    public boolean isSnapLoaded() {
        return snapLoaded;
    }

    public MachineTypes getSnapshotModel() {
        return snapshotModel;
    }

    public void setSnapshotModel(MachineTypes model) {
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

    public int getPortfffd() {
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

    public boolean getEnabledAY() {
        return enabledAY;
    }

    public void setEnabledAY(boolean ayEnabled) {
        enabledAY = ayEnabled;
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

    public Joystick getJoystick() {
        return joystick;
    }

    public void setJoystick(Joystick model) {
        joystick = model;
    }

    public boolean isIssue2() {
        return issue2;
    }

    public void setIssue2(boolean version) {
        issue2 = version;
    }

    public boolean isMultiface() {
        return multiface;
    }

    public void setMultiface(boolean haveMF) {
        multiface = haveMF;
    }

    public boolean isMFPagedIn() {
        return mfPagedIn;
    }

    public void setMFPagedIn(boolean mf) {
        mfPagedIn = mf;
    }

    public boolean isMF128on48k() {
        return mf128on48k;
    }

    public void setMF128on48k(boolean mf128) {
        mf128on48k = mf128;
    }

    public boolean isMFLockout() {
        return mfLockout;
    }

    public void setMFlockout(boolean mf) {
        mfLockout = mf;
    }

    public boolean isULAplus() {
        return ULAplus;
    }

    public void setULAplus(boolean ulaplus) {
        ULAplus = ulaplus;
    }

    public boolean isULAplusEnabled() {
        return ULAplusEnabled;
    }

    public void setULAplusEnabled(boolean state) {
        ULAplusEnabled = state;
    }

    public int getULAplusRegister() {
        return ULAplusRegister;
    }

    public void setULAplusRegister(int register) {
        ULAplusRegister = register;
    }

    public int getULAplusColor(int register) {
        return ULAplusPalette[register];
    }

    public void setULAplusColor(int register, int color) {
        ULAplusPalette[register] = color;
    }

    public boolean isIF2RomPresent() {
        return IF2RomPresent;
    }

    public void setIF2RomPresent(boolean state) {
        IF2RomPresent = state;
    }

    public boolean isTapeEmbedded() {
        return tapeEmbedded;
    }

    public void setTapeEmbedded(boolean state) {
        tapeEmbedded = state;
    }

    public boolean isTapeLinked() {
        return tapeLinked;
    }

    public void setTapeLinked(boolean state) {
        tapeLinked = state;
    }

    public byte[] getTapeData() {
        return tapeData;
    }

    public int getTapeBlock() {
        return tapeBlock;
    }

    public void setTapeBlock(int block) {
        tapeBlock = block;
    }

    public String getTapeName() {
        return tapeName;
    }

    public void setTapeName(String filename) {
        tapeName = filename;
    }

    public String getTapeExtension() {
        return tapeExtension;
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

        if (filename.getName().toLowerCase().endsWith(".szx")) {
            return loadSZX(filename, memory);
        }
        error = 1;
        return false;
    }

    public boolean saveSnapshot(File filename, Memory memory) {
        if (filename.getName().toLowerCase().endsWith(".sna")) {
            return saveSNA(filename, memory);
        }
        if (filename.getName().toLowerCase().endsWith(".z80")) {
            return saveZ80(filename, memory);
        }

        if (filename.getName().toLowerCase().endsWith(".szx")) {
            return saveSZX(filename, memory);
        }
        error = 8;
        return false;
    }

    private boolean loadSNA(File filename, Memory memory) {
        try {
            try {
                fIn = new BufferedInputStream(new FileInputStream(filename));
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            int snaLen = fIn.available();
            switch (snaLen) {
                case 49179: // 48K
                    snapshotModel = MachineTypes.SPECTRUM48K;
                    memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                    memory.reset();
                    break;
                case 131103: // 128k
                case 147487: // snapshot de 128k con una página repetida
                    snapshotModel = MachineTypes.SPECTRUM128K;
                    memory.setSpectrumModel(MachineTypes.SPECTRUM128K);
                    memory.reset();
                    break;
                default:
                    error = 3;
                    fIn.close();
                    return false;
            }

            byte snaHeader[] = new byte[27];
            int count = 0;
            while (count != -1 && count < snaHeader.length) {
                count += fIn.read(snaHeader, count, snaHeader.length - count);
            }

            if (count != snaHeader.length) {
                error = 4;
                fIn.close();
                return false;
            }

            regI = snaHeader[0] & 0xff;
            regHLalt = (snaHeader[1] & 0xff) | (snaHeader[2] << 8) & 0xffff;
            regDEalt = (snaHeader[3] & 0xff) | (snaHeader[4] << 8) & 0xffff;
            regBCalt = (snaHeader[5] & 0xff) | (snaHeader[6] << 8) & 0xffff;
            regAFalt = (snaHeader[7] & 0xff) | (snaHeader[8] << 8) & 0xffff;
            regHL = (snaHeader[9] & 0xff) | (snaHeader[10] << 8) & 0xffff;
            regDE = (snaHeader[11] & 0xff) | (snaHeader[12] << 8) & 0xffff;
            regBC = (snaHeader[13] & 0xff) | (snaHeader[14] << 8) & 0xffff;
            regIY = (snaHeader[15] & 0xff) | (snaHeader[16] << 8) & 0xffff;
            regIX = (snaHeader[17] & 0xff) | (snaHeader[18] << 8) & 0xffff;

            iff1 = iff2 = false;
            if ((snaHeader[19] & 0x04) != 0) {
                iff2 = true;
            }

            if ((snaHeader[19] & 0x02) != 0) {
                iff1 = true;
            }

            regR = snaHeader[20] & 0xff;
            regAF = (snaHeader[21] & 0xff) | (snaHeader[22] << 8) & 0xffff;
            regSP = (snaHeader[23] & 0xff) | (snaHeader[24] << 8) & 0xffff;
            modeIM = snaHeader[25] & 0xff;

            border = snaHeader[26] & 0x07;

            byte buffer[] = new byte[0x4000];

            // Cargamos la página de la pantalla 0x4000-0x7FFF (5)
            count = 0;
            while (count != -1 && count < 0x4000) {
                count += fIn.read(buffer, count, 0x4000 - count);
            }

            if (count != 0x4000) {
                error = 4;
                fIn.close();
                return false;
            }
            memory.loadPage(5, buffer);


            // Cargamos la página 0x8000-0xBFFF (2)
            count = 0;
            while (count != -1 && count < 0x4000) {
                count += fIn.read(buffer, count, 0x4000 - count);
            }

            if (count != 0x4000) {
                error = 4;
                fIn.close();
                return false;
            }
            memory.loadPage(2, buffer);

            if (snaLen == 49179) { // 48K
                // Cargamos la página 0xC000-0XFFF (0)
                count = 0;
                while (count != -1 && count < 0x4000) {
                    count += fIn.read(buffer, count, 0x4000 - count);
                }

                if (count != 0x4000) {
                    error = 4;
                    fIn.close();
                    return false;
                }
                memory.loadPage(0, buffer);

                regPC = 0x72; // dirección de RETN en la ROM
            } else {
                boolean loaded[] = new boolean[8];

                // Hasta que leamos el último valor del puerto 0x7ffd no sabemos
                // en qué página hay que poner los últimos 16K. Los leemos en
                // un buffer temporal y luego los copiamos (que remedio!!!)
                count = 0;
                while (count != -1 && count < 0x4000) {
                    count += fIn.read(buffer, count, 0x4000 - count);
                }

                if (count != 0x4000) {
                    error = 4;
                    fIn.close();
                    return false;
                }

                // En modo 128, la página 5 está en 0x4000 y la 2 en 0x8000.
                loaded[2] = loaded[5] = true;
                regPC = fIn.read() | (fIn.read() << 8) & 0xffff;
                last7ffd = fIn.read() & 0xff;
                // Si la página de memoria en 0xC000 era la 2 o la 5, ésta se
                // habrá grabado dos veces, y esta segunda copia es redundante.
                int page = last7ffd & 0x07;
                if (page != 2 && page != 5) {
                    memory.loadPage(page, buffer);
                    loaded[page] = true;
                }

                int trDos = fIn.read();
                // Si la ROM del TR-DOS estaba paginada, mal vamos...
                if (trDos == 0x01) {
                    error = 1;
                    fIn.close();
                    return false;
                }

                for (page = 0; page < 8; page++) {
                    if (!loaded[page]) {
                        count = 0;
                        while (count != -1 && count < 0x4000) {
                            count += fIn.read(buffer, count, 0x4000 - count);
                        }
                        if (count != 0x4000) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        memory.loadPage(page, buffer);
//                        for (int addr = 0; addr < 0x4000; addr++) {
//                            memory.writeByte(page, addr, (byte)fIn.read());
//                        }
                    }
                    // El formato SNA no guarda los registros del AY
                    // Los ponemos a cero y que se apañe....
                    Arrays.fill(psgRegs, 0);
                    lastfffd = 0;
                }

            }

            fIn.close();

            issue2 = false; // esto no se guarda en los SNA, algo hay que poner...
            joystick = Joystick.NONE; // idem
            tstates = 0;
            mfPagedIn = mf128on48k = false;

        } catch (IOException ex) {
            error = 4;
            return false;
        }

        return true;
    }

    private boolean saveSNA(File filename, Memory memory) {

        // Si la pila está muy baja, no hay donde almacenar el registro SP
        if (snapshotModel == MachineTypes.SPECTRUM48K && regSP < 0x4002) {
            error = 7;
            return false;
        }

        if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
            error = 9;
            return false;
        }

        try {
            try {
                fOut = new BufferedOutputStream(new FileOutputStream(filename));
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            byte snaHeader[] = new byte[27];
            snaHeader[0] = (byte) regI;
            snaHeader[1] = (byte) regHLalt;
            snaHeader[2] = (byte) (regHLalt >>> 8);
            snaHeader[3] = (byte) regDEalt;
            snaHeader[4] = (byte) (regDEalt >>> 8);
            snaHeader[5] = (byte) regBCalt;
            snaHeader[6] = (byte) (regBCalt >>> 8);
            snaHeader[7] = (byte) regAFalt;
            snaHeader[8] = (byte) (regAFalt >>> 8);
            snaHeader[9] = (byte) regHL;
            snaHeader[10] = (byte) (regHL >>> 8);
            snaHeader[11] = (byte) regDE;
            snaHeader[12] = (byte) (regDE >>> 8);
            snaHeader[13] = (byte) regBC;
            snaHeader[14] = (byte) (regBC >>> 8);
            snaHeader[15] = (byte) regIY;
            snaHeader[16] = (byte) (regIY >>> 8);
            snaHeader[17] = (byte) regIX;
            snaHeader[18] = (byte) (regIX >>> 8);

            if (iff1) {
                snaHeader[19] |= 0x02;
            }
            if (iff2) {
                snaHeader[19] |= 0x04;
            }

            snaHeader[20] = (byte) regR;
            snaHeader[21] = (byte) regAF;
            snaHeader[22] = (byte) (regAF >>> 8);

            if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                regSP = (regSP - 1) & 0xffff;
                memory.writeByte(regSP, (byte) (regPC >>> 8));
                regSP = (regSP - 1) & 0xffff;
                memory.writeByte(regSP, (byte) regPC);
            }

            snaHeader[23] = (byte) regSP;
            snaHeader[24] = (byte) (regSP >>> 8);
            snaHeader[25] = (byte) modeIM;
            snaHeader[26] = (byte) border;

            fOut.write(snaHeader, 0, snaHeader.length);

            byte buffer[] = new byte[0x4000];

            // Salvamos la página de la pantalla 0x4000-0x7FFF (5)
            memory.savePage(5, buffer);
            fOut.write(buffer, 0, buffer.length);

            // Salvamos la página 0x8000-0xBFFF (2)
            memory.savePage(2, buffer);
            fOut.write(buffer, 0, buffer.length);

            if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                // Salvamos la página 0xC000-0xFFFF (0)
                memory.savePage(0, buffer);
                fOut.write(buffer, 0, buffer.length);
            } else {
                // Salvamos la página en 0xC000-0xFFFF
                memory.savePage((last7ffd & 0x07), buffer);
                fOut.write(buffer, 0, buffer.length);

                boolean saved[] = new boolean[8];
                saved[2] = saved[5] = true;
                fOut.write(regPC);
                fOut.write(regPC >>> 8);
                fOut.write(last7ffd);
                fOut.write(0x00); // La ROM del TR-DOS no está paginada
                saved[last7ffd & 0x07] = true;
                for (int page = 0; page < 8; page++) {
                    if (!saved[page]) {
                        memory.savePage(page, buffer);
                        fOut.write(buffer, 0, buffer.length);
                    }
                }
            }

            fOut.close();

        } catch (IOException ex) {
            error = 6;
            return false;
        }

        return true;
    }

    private int uncompressZ80(byte buffer[], int length) {
//        System.out.println(String.format("Addr: %04X, len = %d", address, length));
        int address = 0;
        try {
//            int endAddr = address + length;
            while (fIn.available() > 0 && address < length) {
                int mem = fIn.read() & 0xff;
                if (mem != 0xED) {
                    buffer[address++] = (byte) mem;
                } else {
                    int mem2 = fIn.read() & 0xff;
                    if (mem2 != 0xED) {
                        buffer[address++] = (byte) 0xED;
                        buffer[address++] = (byte) mem2;
                    } else {
                        int nreps = fIn.read() & 0xff;
                        int value = fIn.read() & 0xff;
                        while (nreps-- > 0) {
                            buffer[address++] = (byte) value;
                        }
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Snapshots.class.getName()).log(Level.SEVERE, null, ex);
            return 0;
        }
        return address;
    }

    private int countRepeatedByte(Memory memory, int page, int address, byte value) {
        int count = 0;

        while (address < 0x4000 && count < 254
            && value == memory.readByte(page, address++)) {
            count++;
        }

        return count;
    }

    private int compressPageZ80(Memory memory, byte buffer[], int page) {
        int address = 0;
        int addrDst = 0;
        int nReps;
        byte value;

        while (address < 0x4000) {
            value = memory.readByte(page, address++);
            nReps = countRepeatedByte(memory, page, address, value);
            if (value == (byte) 0xED) {
                if (nReps == 0) {
                    // El byte que sigue a ED siempre se representa sin
                    // comprimir, aunque hayan repeticiones.
                    buffer[addrDst++] = (byte) 0xED;
                    buffer[addrDst++] = memory.readByte(page, address++);
                } else {
                    // Varios ED consecutivos siempre se comprimen, aunque
                    // hayan menos de 5.
                    buffer[addrDst++] = (byte) 0xED;
                    buffer[addrDst++] = (byte) 0xED;
                    buffer[addrDst++] = (byte) (nReps + 1);
                    buffer[addrDst++] = (byte) 0xED;
                    address += nReps;
                }
            } else {
                if (nReps < 4) {
                    // Si hay menos de 5 valores consecutivos iguales
                    // no se comprimen.
                    buffer[addrDst++] = (byte) value;
                } else {
                    buffer[addrDst++] = (byte) 0xED;
                    buffer[addrDst++] = (byte) 0xED;
                    buffer[addrDst++] = (byte) (nReps + 1);
                    buffer[addrDst++] = (byte) value;
                    address += nReps;
                }
            }
        }
        return addrDst;
    }

    private boolean loadZ80(File filename, Memory memory) {
        try {
            try {
                fIn = new BufferedInputStream(new FileInputStream(filename));
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            if (fIn.available() < 30) {
                error = 3;
                fIn.close();
                return false;
            }

            byte z80Header1[] = new byte[30];
            int count = 0;
            while (count != -1 && count < z80Header1.length) {
                count += fIn.read(z80Header1, count, z80Header1.length - count);
            }

            if (count != z80Header1.length) {
                error = 4;
                fIn.close();
                return false;
            }

            regAF = (z80Header1[1] & 0xff) | (z80Header1[0] << 8) & 0xffff;
            regBC = (z80Header1[2] & 0xff) | (z80Header1[3] << 8) & 0xffff;
            regHL = (z80Header1[4] & 0xff) | (z80Header1[5] << 8) & 0xffff;
            regPC = (z80Header1[6] & 0xff) | (z80Header1[7] << 8) & 0xffff;
            regSP = (z80Header1[8] & 0xff) | (z80Header1[9] << 8) & 0xffff;
            regI = z80Header1[10] & 0xff;
            regR = z80Header1[11] & 0x7f;
            if ((z80Header1[12] & 0x01) != 0) {
                regR |= 0x80;
            }
            border = (z80Header1[12] >>> 1) & 0x07;
            regDE = (z80Header1[13] & 0xff) | (z80Header1[14] << 8) & 0xffff;
            regBCalt = (z80Header1[15] & 0xff) | (z80Header1[16] << 8) & 0xffff;
            regDEalt = (z80Header1[17] & 0xff) | (z80Header1[18] << 8) & 0xffff;
            regHLalt = (z80Header1[19] & 0xff) | (z80Header1[20] << 8) & 0xffff;
            regAFalt = (z80Header1[22] & 0xff) | (z80Header1[21] << 8) & 0xffff;
            regIY = (z80Header1[23] & 0xff) | (z80Header1[24] << 8) & 0xffff;
            regIX = (z80Header1[25] & 0xff) | (z80Header1[26] << 8) & 0xffff;
            iff1 = z80Header1[27] != 0;
            iff2 = z80Header1[28] != 0;
            modeIM = z80Header1[29] & 0x03;
            issue2 = (z80Header1[29] & 0x04) != 0;
            switch (z80Header1[29] >>> 6) {
                case 0: // Cursor/AGF/Protek Joystick
                    // No es lo que dice la especificación, pero lo prefiero...
                    joystick = Joystick.NONE;
                    break;
                case 1: // Kempston joystick
                    joystick = Joystick.KEMPSTON;
                    break;
                case 2:
                    joystick = Joystick.SINCLAIR1;
                    break;
                case 3:
                    joystick = Joystick.SINCLAIR2;
            }

            // Si regPC != 0, es un z80 v1.0
            if (regPC != 0) {
                byte pageBuffer[] = new byte[0x4000];
                snapshotModel = MachineTypes.SPECTRUM48K;
                memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                if ((z80Header1[12] & 0x20) == 0) { // el bloque no está comprimido

                    // Cargamos la página de la pantalla 0x4000-0x7FFF (5)
                    count = 0;
                    while (count != -1 && count < 0x4000) {
                        count += fIn.read(pageBuffer, count, 0x4000 - count);
                    }

                    if (count != 0x4000) {
                        error = 4;
                        fIn.close();
                        return false;
                    }
                    memory.loadPage(5, pageBuffer);


                    // Cargamos la página 0x8000-0xBFFF (2)
                    count = 0;
                    while (count != -1 && count < 0x4000) {
                        count += fIn.read(pageBuffer, count, 0x4000 - count);
                    }

                    if (count != 0x4000) {
                        error = 4;
                        fIn.close();
                        return false;
                    }
                    memory.loadPage(2, pageBuffer);

                    // Cargamos la página 0xC000-0xFFFF (0)
                    count = 0;
                    while (count != -1 && count < 0x4000) {
                        count += fIn.read(pageBuffer, count, 0x4000 - count);
                    }

                    if (count != 0x4000) {
                        error = 4;
                        fIn.close();
                        return false;
                    }
                    memory.loadPage(0, pageBuffer);
                } else {
                    byte buffer[] = new byte[0xC000];
                    int len = uncompressZ80(buffer, buffer.length);
                    if (len != 0xC000 || fIn.available() != 4) {
                        error = 4;
                        fIn.close();
                        return false;
                    }

                    // Como en Java no se puede apuntar hay que ir haciendo
                    // copias intermedias a un buffer más pequeño. Sigh!
//                    System.arraycopy(buffer, 0, pageBuffer, 0, 0x4000);
                    memory.loadPage(5, buffer);
                    System.arraycopy(buffer, 0x4000, pageBuffer, 0, 0x4000);
                    memory.loadPage(2, pageBuffer);
                    System.arraycopy(buffer, 0x8000, pageBuffer, 0, 0x4000);
                    memory.loadPage(0, pageBuffer);
                }
            } else {
                // Z80 v2 & v3
                int hdrLen = fIn.read() | (fIn.read() << 8) & 0xffff;
                if (hdrLen != 23 && hdrLen != 54 && hdrLen != 55) {
                    error = 3;
                    fIn.close();
                    return false;
                }

                byte z80Header2[] = new byte[hdrLen];
                count = 0;
                while (count != -1 && count < z80Header2.length) {
                    count += fIn.read(z80Header2, count, z80Header2.length - count);
                }

                if (count != z80Header2.length) {
                    error = 4;
                    fIn.close();
                    return false;
                }

                regPC = (z80Header2[0] & 0xff) | (z80Header2[1] << 8) & 0xffff;

                boolean modifiedHW = (z80Header2[5] & 0x80) != 0;
                if (hdrLen == 23) { // Z80 v2
                    switch (z80Header2[2]) {
                        case 0: // 48k
                        case 1: // 48k + IF.1
                            if (modifiedHW)
                                snapshotModel = MachineTypes.SPECTRUM16K;
                            else
                                snapshotModel = MachineTypes.SPECTRUM48K;
                            break;
                        case 3: // 128k
                        case 4: // 128k + IF.1
                            if (modifiedHW)
                                snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            else
                                snapshotModel = MachineTypes.SPECTRUM128K;
                            break;
                        case 7: // +3
                            if (modifiedHW)
                                snapshotModel = MachineTypes.SPECTRUMPLUS2A;
                            else
                                snapshotModel = MachineTypes.SPECTRUMPLUS3;
                            break;
                        case 12: // +2
                            snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            break;
                        case 13: // +2A
                            snapshotModel = MachineTypes.SPECTRUMPLUS2A;
                            break;
                        default:
                            error = 5;
                            fIn.close();
                            return false;
                    }
                } else { // Z80 v3
                    switch (z80Header2[2]) {
                        case 0: // 48k
                        case 1: // 48k + IF.1
                            if (modifiedHW)
                                snapshotModel = MachineTypes.SPECTRUM16K;
                            else
                                snapshotModel = MachineTypes.SPECTRUM48K;
                            break;
                        case 4: // 128k
                        case 5: // 128k + IF.1
                            if (modifiedHW)
                                snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            else
                                snapshotModel = MachineTypes.SPECTRUM128K;
                            break;
                        case 7: // +3
                            if (modifiedHW)
                                snapshotModel = MachineTypes.SPECTRUMPLUS2A;
                            else
                                snapshotModel = MachineTypes.SPECTRUMPLUS3;
                            break;
                        case 12: // +2
                            snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            break;
                        case 13: // +2A
                            snapshotModel = MachineTypes.SPECTRUMPLUS2A;
                            break;
                        default:
                            error = 5;
                            fIn.close();
                            return false;
                    }
                }

                memory.setSpectrumModel(snapshotModel);

                last7ffd = z80Header2[3] & 0xff;
                enabledAY = (z80Header2[5] & 0x04) != 0;
                lastfffd = z80Header2[6] & 0xff;
                for (int idx = 0; idx < 16; idx++) {
                    psgRegs[idx] = z80Header2[7 + idx] & 0xff;
                }

                last1ffd = 0;
                if (hdrLen == 55) {
                    last1ffd = z80Header2[54] & 0xff;
                }

                byte buffer[] = new byte[0x4000];
                while (fIn.available() > 0) {
                    int blockLen = fIn.read() | (fIn.read() << 8) & 0xffff;
                    int ramPage = fIn.read() & 0xff;
                    if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                        switch (ramPage) {
                            case 4:  // 0x8000-0xbfff
                                ramPage = 2;
                                break;
                            case 5:  // 0xC000-0xFFFF
                                ramPage = 0;
                                break;
                            case 8:  // 0x4000-0x7FFF
                                ramPage = 5;
                                break;
                        }
                    } else { // snapshotModel == 128K
                        if (ramPage < 3 || ramPage > 10) {
                            continue;
                        }
                        ramPage -= 3;
                    }

                    if (blockLen == 0xffff) { // uncompressed data
                        count = 0;
                        while (count != -1 && count < 0x4000) {
                            count += fIn.read(buffer, count, 0x4000 - count);
                        }

                        if (count != 0x4000) {
                            error = 4;
                            fIn.close();
                            return false;
                        }

                        memory.loadPage(ramPage, buffer);
                    } else {
                        int len = uncompressZ80(buffer, 0x4000);
                        if (len != 0x4000) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        memory.loadPage(ramPage, buffer);
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

    // Solo se graban Z80's versión 3
    private boolean saveZ80(File filename, Memory memory) {

        try {
            try {
                fOut = new BufferedOutputStream(new FileOutputStream(filename));
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            byte z80HeaderV3[] = new byte[87];
            z80HeaderV3[0] = (byte)(regAF >>> 8);
            z80HeaderV3[1] = (byte) regAF;
            z80HeaderV3[2] = (byte) regBC;
            z80HeaderV3[3] = (byte)(regBC >>> 8);
            z80HeaderV3[4] = (byte) regHL;
            z80HeaderV3[5] = (byte)(regHL >>> 8);
            // Bytes 6 y 7 se dejan a 0, si regPC==0, el Z80 es version 2 o 3
            z80HeaderV3[8] = (byte) regSP;
            z80HeaderV3[9] = (byte)(regSP >>> 8);
            z80HeaderV3[10] = (byte) regI;
            z80HeaderV3[11] = (byte)(regR & 0x7f);
            z80HeaderV3[12] = (byte)(border << 1);
            if (regR > 0x7f)
                z80HeaderV3[12] |= 0x01;
            z80HeaderV3[13] = (byte) regDE;
            z80HeaderV3[14] = (byte)(regDE >>> 8);
            z80HeaderV3[15] = (byte) regBCalt;
            z80HeaderV3[16] = (byte)(regBCalt >>> 8);
            z80HeaderV3[17] = (byte) regDEalt;
            z80HeaderV3[18] = (byte)(regDEalt >>> 8);
            z80HeaderV3[19] = (byte) regHLalt;
            z80HeaderV3[20] = (byte)(regHLalt >>> 8);
            z80HeaderV3[21] = (byte)(regAF >>> 8);
            z80HeaderV3[22] = (byte) regAF;
            z80HeaderV3[23] = (byte) regIY;
            z80HeaderV3[24] = (byte)(regIY >>> 8);
            z80HeaderV3[25] = (byte) regIX;
            z80HeaderV3[26] = (byte)(regIX >>> 8);
            z80HeaderV3[27] = (byte) (iff1 ? 0x01 : 0x00);
            z80HeaderV3[28] = (byte) (iff2 ? 0x01 : 0x00);
            z80HeaderV3[29] = (byte) modeIM;

            if (!issue2) {
                z80HeaderV3[29] |= 0x04;
            }
            switch (joystick) {
                case NONE:
                case CURSOR:
                    break;
                case KEMPSTON:
                    z80HeaderV3[29] |= 0x40;
                    break;
                case SINCLAIR1:
                    z80HeaderV3[29] |= 0x80;
                    break;
                case SINCLAIR2:
                    z80HeaderV3[29] |= 0xC0;
            }
            // Hasta aquí la cabecera v1.0, ahora viene lo propio de la v3.x
            z80HeaderV3[30] = 55; // Cabecera adicional de 55 bytes
            z80HeaderV3[32] = (byte) regPC;
            z80HeaderV3[33] = (byte)(regPC >>> 8);

            switch (snapshotModel) {
                case SPECTRUM16K:
                    z80HeaderV3[37] |= 0x80;
                    break;
                case SPECTRUM48K:
                    break;
                case SPECTRUM128K:
                    z80HeaderV3[34] = 4;
                    break;
                case SPECTRUMPLUS2:
                    z80HeaderV3[34] = 12;
                    break;
                case SPECTRUMPLUS2A:
                    z80HeaderV3[34] = 13;
                    break;
                case SPECTRUMPLUS3:
                    z80HeaderV3[34] = 7;
            }

            if (snapshotModel.codeModel != MachineTypes.CodeModel.SPECTRUM48K) {
                z80HeaderV3[35] = (byte) last7ffd;
            }

            if (enabledAY)
                z80HeaderV3[37] |= 0x04;
            
            z80HeaderV3[38] = (byte) lastfffd;

            for (int reg = 0; reg < 16; reg++) {
                z80HeaderV3[39 + reg] = (byte)psgRegs[reg];
            }
            
            if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                z80HeaderV3[54] = (byte)last1ffd;
            }

            fOut.write(z80HeaderV3, 0, z80HeaderV3.length);

            byte buffer[] = new byte[0x4000];
            int bufLen;
            if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                // Página 5, que corresponde a 0x4000-0x7FFF
                bufLen = compressPageZ80(memory, buffer, 5);
                if (bufLen == 0x4000) {
                    fOut.write(0xff);
                    fOut.write(0xff); // bloque sin compresión
                } else {
                    fOut.write(bufLen);
                    fOut.write(bufLen >>> 8);
                }
                fOut.write(8);
                fOut.write(buffer, 0, bufLen);

                // Página 2, que corresponde a 0x8000-0xBFFF
                bufLen = compressPageZ80(memory, buffer, 2);
                if (bufLen == 0x4000) {
                    fOut.write(0xff);
                    fOut.write(0xff); // bloque sin compresión
                } else {
                    fOut.write(bufLen);
                    fOut.write(bufLen >>> 8);
                }
                fOut.write(4);
                fOut.write(buffer, 0, bufLen);

                // Página 0, que corresponde a 0xC000-0xFFFF
                bufLen = compressPageZ80(memory, buffer, 0);
                if (bufLen == 0x4000) {
                    fOut.write(0xff);
                    fOut.write(0xff); // bloque sin compresión
                } else {
                    fOut.write(bufLen);
                    fOut.write(bufLen >>> 8);
                }
                fOut.write(5);
                fOut.write(buffer, 0, bufLen);
            } else { // Mode 128k
                for (int page = 0; page < 8; page++) {
                    bufLen = compressPageZ80(memory, buffer, page);
                    if (bufLen == 0x4000) {
                        fOut.write(0xff);
                        fOut.write(0xff); // bloque sin compresión
                    } else {
                        fOut.write(bufLen);
                        fOut.write(bufLen >>> 8);
                    }
                    fOut.write(page + 3);
                    fOut.write(buffer, 0, bufLen);
                }
            }

            fOut.close();

        } catch (IOException ex) {
            error = 6;
            return false;
        }

        return true;
    }

    // SZX Section
    private static final int ZXST_HEADER = 0x5453585A; // ZXST
    private static final int ZXSTMID_16K = 0;
    private static final int ZXSTMID_48K = 1;
    private static final int ZXSTMID_128K = 2;
    private static final int ZXSTMID_PLUS2 = 3;
    private static final int ZXSTMID_PLUS2A = 4;
    private static final int ZXSTMID_PLUS3 = 5;
    private static final int ZXSTMID_PLUS3E = 6;
    private static final int ZXSTMID_PENTAGON128 = 7;
    private static final int ZXSTMID_TC2048 = 8;
    private static final int ZXSTMID_TC2068 = 9;
    private static final int ZXSTMID_SCORPION = 10;
    private static final int ZXSTMID_SE = 11;
    private static final int ZXSTMID_TS2068 = 12;
    private static final int ZXSTMID_PENTAGON512 = 13;
    private static final int ZXSTMID_PENTAGON1024 = 14;

    private static final int ZXSTBID_ZXATASP = 0x5441585A;   // ZXAT
    private static final int ZXSTAF_UPLOADJUMPER = 1;
    private static final int ZXSTAF_WRITEPROTECT = 2;

    private static final int ZXSTBID_ATARAM = 0x50525441;    // ATRP
    private static final int ZXSTAF_COMPRESSED = 1;

    private static final int ZXSTBID_AY = 0x00005941;        // AY\0\0
    private static final int ZXSTAYF_FULLERBOX = 1;
    private static final int ZXSTAYF_128AY = 2;

    private static final int ZXSTBID_ZXCF = 0x4643585A;      // ZXCF
    private static final int ZXSTCF_UPLOADJUMPER = 1;

    private static final int ZXSTBID_CFRAM = 0x50524643;     // CFRP
    private static final int ZXSTCRF_COMPRESSED = 1;

    private static final int ZXSTBID_COVOX = 0x58564F43;     // COVX

    private static final int ZXSTBID_BETA128 = 0x38323142;   // B128
    private static final int ZXSTBETAF_CONNECTED = 1;
    private static final int ZXSTBETAF_CUSTOMROM = 2;
    private static final int ZXSTBETAF_PAGED = 4;
    private static final int ZXSTBETAF_AUTOBOOT = 8;
    private static final int ZXSTBETAF_SEEKLOWER = 16;
    private static final int ZXSTBETAF_COMPRESSED = 32;

    private static final int ZXSTBID_BETADISK = 0x4B534442;  // BDSK

    private static final int ZXSTBID_CREATOR = 0x52545243;   // CRTR

    private static final int ZXSTBID_DOCK = 0x4B434F44;      // DOCK

    private static final int ZXSTBID_DSKFILE = 0x004B5344;   // DSK\0
    private static final int ZXSTDSKF_COMPRESSED = 1;
    private static final int ZXSTDSKF_EMBEDDED = 2;

    private static final int ZXSTBID_GS = 0x00005347;        // GS\0\0

    private static final int ZXSTBID_GSRAMPAGE = 0x50525347; // GSRP

    private static final int ZXSTBID_KEYBOARD = 0x4259454B;  // KEYB
    private static final int ZXSTKF_ISSUE2 = 1;
    private static final int ZXSKJT_KEMPSTON = 0;
    private static final int ZXSKJT_FULLER = 1;
    private static final int ZXSKJT_CURSOR = 2;
    private static final int ZXSKJT_SINCLAIR1 = 3;
    private static final int ZXSKJT_SINCLAIR2 = 4;
    private static final int ZXSKJT_SPECTRUMPLUS = 5;
    private static final int ZXSKJT_TIMEX1 = 6;
    private static final int ZXSKJT_TIMEX2 = 7;
    private static final int ZXSKJT_NONE = 8;

    private static final int ZXSTBID_IF1 = 0x00314649;
    private static final int ZXSTIF1F_ENABLED = 1;
    private static final int ZXSTIF1F_COMPRESSED = 2;
    private static final int ZXSTIF1F_PAGED = 4;

    private static final int ZXSTBID_IF2ROM = 0x52324649;    // IF2R

    private static final int ZXSTBID_JOYSTICK = 0x00594F4A;  // JOY\0
    private static final int ZXSTJT_KEMPSTON = 0;
    private static final int ZXSTJT_FULLER = 1;
    private static final int ZXSTJT_CURSOR = 2;
    private static final int ZXSTJT_SINCLAIR1 = 3;
    private static final int ZXSTJT_SINCLAIR2 = 4;
    private static final int ZXSTJT_COMCOM = 5;
    private static final int ZXSTJT_TIMEX1 = 6;
    private static final int ZXSTJT_TIMEX2 = 7;
    private static final int ZXSTJT_DISABLED = 8;

    private static final int ZXSTBID_MICRODRIVE = 0x5652444D; // MDRV
    private static final int ZXSTMDF_COMPRESSED = 1;
    private static final int ZXSTMDF_EMBEDDED = 2;

    private static final int ZXSTBID_MOUSE = 0x4D584D41;     // AMXM

    private static final int ZXSTBID_MULTIFACE = 0x4543464D; // MFCE
    private static final int ZXSTMFM_1 = 0;
    private static final int ZXSTMFM_128 = 1;
    private static final int ZXSTMF_PAGEDIN = 0x01;
    private static final int ZXSTMF_COMPRESSED = 0x02;
    private static final int ZXSTMF_SOFTWARELOCKOUT = 0x04;
    private static final int ZXSTMF_REDBUTTONDISABLED = 0x08;
    private static final int ZXSTMF_DISABLED = 0x10;
    private static final int ZXSTMF_16KRAMMODE = 0x20;

    private static final int ZXSTBID_RAMPAGE = 0x504D4152;   // RAMP
    private static final int ZXSTRF_COMPRESSED = 1;

    private static final int ZXSTBID_PLUS3DISK = 0x0000332B; // +3\0\0

    private static final int ZXSTBID_PLUSD = 0x44534C50;     // PLSD

    private static final int ZXSTBID_PLUSDDISK = 0x4B534450; // PDSK

    private static final int ZXSTBID_ROM = 0x004D4F52;       // ROM\0

    private static final int ZXSTBID_TIMEXREGS = 0x444C4353; // SCLD

    private static final int ZXSTBID_SIMPLEIDE = 0x45444953; // SIDE

    private static final int ZXSTBID_SPECDRUM = 0x4D555244;  // DRUM

    private static final int ZXSTBID_SPECREGS = 0x52435053;  // SPCR

    private static final int ZXSTBID_ZXTAPE = 0x45504154;    // TAPE
    private static final int ZXSTTP_EMBEDDED = 1;
    private static final int ZXSTTP_COMPRESSED = 2;

    private static final int ZXSTBID_USPEECH = 0x45505355;   // USPE

    private static final int ZXSTBID_ZXPRINTER = 0x5250585A; // ZXPR

    private static final int ZXSTBID_Z80REGS = 0x5230385A;   // Z80R
    private static final int ZXSTZF_EILAST = 1;
    private static final int ZXSTZF_HALTED = 2;

    // This definition isn't in Spectaculator documentation page.
    // http://scratchpad.wikia.com/wiki/ZX_Spectrum_64_Colour_Mode
    private static final int ZXSTBID_PALETTE = 0x54544C50;    // PLTT
    private static final int ZXSTPALETTE_DISABLED = 0;
    private static final int ZXSTPALETTE_ENABLED = 1;

    


    private int dwMagicToInt(byte[] dwMagic) {
        int value0 = dwMagic[0] & 0xff;
        int value1 = dwMagic[1] & 0xff;
        int value2 = dwMagic[2] & 0xff;
        int value3 = dwMagic[3] & 0xff;

        return (value3 << 24) | (value2 << 16) | (value1 << 8) | value0;
    }

    private boolean loadSZX(File filename, Memory memory) {
        byte dwMagic[] = new byte[4];
        byte dwSize[] = new byte[4];
        int readed, szxId, szxLen;
        ByteArrayInputStream bais;
        InflaterInputStream iis;
        int addr = 0;
        byte chData[];

        joystick = Joystick.NONE;

        try {
            try {
                fIn = new BufferedInputStream(new FileInputStream(filename));
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            readed = fIn.read(dwMagic);
            if (dwMagicToInt(dwMagic) != ZXST_HEADER) {
                error = 1;
                fIn.close();
                return false;
            }

            readed = fIn.read(dwSize);
            switch (dwSize[2] & 0xff) {
                case ZXSTMID_16K:
                    snapshotModel = MachineTypes.SPECTRUM16K;
                    break;
                case ZXSTMID_48K:
                    snapshotModel = MachineTypes.SPECTRUM48K;
                    break;
                case ZXSTMID_128K:
                    snapshotModel = MachineTypes.SPECTRUM128K;
                    break;
                case ZXSTMID_PLUS2:
                    snapshotModel = MachineTypes.SPECTRUMPLUS2;
                    break;
                case ZXSTMID_PLUS2A:
                    snapshotModel = MachineTypes.SPECTRUMPLUS2A;
                    break;
                case ZXSTMID_PLUS3:
                    snapshotModel = MachineTypes.SPECTRUMPLUS3;
                    break;
                default:
                    error = 5;
                    fIn.close();
                    return false;
            }

            while (fIn.available() > 0) {
                readed = fIn.read(dwMagic);
                readed = fIn.read(dwSize);
                szxId = dwMagicToInt(dwMagic);
                szxLen = dwMagicToInt(dwSize);
                if (szxLen < 1) {
                    error = 4;
                    fIn.close();
                    return false;
                }

                switch (szxId) {
                    case ZXSTBID_CREATOR:
                        byte szCreator[] = new byte[32];
                        readed = fIn.read(szCreator);
//                        System.out.println(String.format("Creator: %s", new String(szCreator)));
                        int majorVersion = fIn.read() + fIn.read() * 256;
                        int minorVersion = fIn.read() + fIn.read() * 256;
//                        System.out.println(String.format("Creator Version %d.%d",
//                            majorVersion, minorVersion));
                        szxLen -= 36;
                        if (szxLen > 0) {
                            chData = new byte[szxLen];
                            readed = fIn.read(chData);
//                            System.out.println(String.format("Creator Data: %s",
//                                new String(chData)));
                        }
                        break;
                    case ZXSTBID_Z80REGS:
                        if (szxLen != 37) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        byte z80Regs[] = new byte[szxLen];
                        readed = fIn.read(z80Regs);
                        regAF = (z80Regs[0] & 0xff) | (z80Regs[1] << 8) & 0xffff;
                        regBC = (z80Regs[2] & 0xff) | (z80Regs[3] << 8) & 0xffff;
                        regDE = (z80Regs[4] & 0xff) | (z80Regs[5] << 8) & 0xffff;
                        regHL = (z80Regs[6] & 0xff) | (z80Regs[7] << 8) & 0xffff;
                        regAFalt = (z80Regs[8] & 0xff) | (z80Regs[9] << 8) & 0xffff;
                        regBCalt = (z80Regs[10] & 0xff) | (z80Regs[11] << 8) & 0xffff;
                        regDEalt = (z80Regs[12] & 0xff) | (z80Regs[13] << 8) & 0xffff;
                        regHLalt = (z80Regs[14] & 0xff) | (z80Regs[15] << 8) & 0xffff;
                        regIX = (z80Regs[16] & 0xff) | (z80Regs[17] << 8) & 0xffff;
                        regIY = (z80Regs[18] & 0xff) | (z80Regs[19] << 8) & 0xffff;
                        regSP = (z80Regs[20] & 0xff) | (z80Regs[21] << 8) & 0xffff;
                        regPC = (z80Regs[22] & 0xff) | (z80Regs[23] << 8) & 0xffff;
                        regI = z80Regs[24] & 0xff;
                        regR = z80Regs[25] & 0xff;
                        iff1 = (z80Regs[26] & 0xff )!= 0;
                        iff2 = (z80Regs[27] & 0xff )!= 0;
                        modeIM = z80Regs[28] & 0xff;
                        tstates = ((z80Regs[32] & 0xff) << 24) | ((z80Regs[31] & 0xff) << 16) |
                                ((z80Regs[30] & 0xff) << 8) | (z80Regs[29] & 0xff);
                        break;
                    case ZXSTBID_SPECREGS:
                        if (szxLen != 8) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        byte specRegs[] = new byte[szxLen];
                        readed = fIn.read(specRegs);
                        border = specRegs[0] & 0x07;
                        last7ffd = specRegs[1] & 0xff;
                        last1ffd = specRegs[2] & 0xff;
                        break;
                    case ZXSTBID_KEYBOARD:
                        if (szxLen != 5) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        byte keyb[] = new byte[szxLen];
                        readed = fIn.read(keyb);
                        issue2 = (keyb[0] & ZXSTKF_ISSUE2) != 0;
                        switch (keyb[4] & 0xff) {
                            case ZXSKJT_KEMPSTON:
                                joystick = Joystick.KEMPSTON;
                                break;
                            case ZXSKJT_FULLER:
                                joystick = Joystick.FULLER;
                                break;
                            case ZXSKJT_CURSOR:
                                joystick = Joystick.CURSOR;
                                break;
                            case ZXSKJT_SINCLAIR1:
                                joystick = Joystick.SINCLAIR1;
                                break;
                            case ZXSKJT_SINCLAIR2:
                                joystick = Joystick.SINCLAIR2;
                                break;
                            default:
                                joystick = Joystick.NONE;
                        }
                        break;
                    case ZXSTBID_AY:
                        if (szxLen != 18) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        byte ayRegs[] = new byte[szxLen];
                        readed = fIn.read(ayRegs);
                        enabledAY = true;
                        if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K &&
                                ayRegs[0] != ZXSTAYF_128AY)
                            enabledAY = false;
                        lastfffd = ayRegs[1] & 0xff;
                        for (int idx = 0; idx < 16; idx++) {
                            psgRegs[idx] = ayRegs[2 + idx] & 0xff;
                        }
                        break;
                    case ZXSTBID_RAMPAGE:
                        byte ramPage[] = new byte[3];
                        readed = fIn.read(ramPage);
                        szxLen -= 3;
                        if (szxLen > 0x4000) {
                            error = 10;
                            fIn.close();
                            return false;
                        }
                        chData = new byte[szxLen];
                        readed = fIn.read(chData);
                        if ((ramPage[0] & ZXSTRF_COMPRESSED) == 0) {
                            memory.loadPage(ramPage[2] & 0xff, chData);
                            break;
                        }
                        // Compressed RAM page
                        bais = new ByteArrayInputStream(chData);
                        iis = new InflaterInputStream(bais);
                        addr = 0;
                        while (addr < 0x4000) {
                            int value = iis.read();
                            if (value == -1)
                                break;
                            memory.writeByte(ramPage[2] & 0xff, addr++, (byte)value);
                        }
                        readed = iis.read();
                        iis.close();
                        if (addr != 0x4000 || readed != -1) {
                            error = 10;
                            fIn.close();
                            return false;
                        }
                        break;
                    case ZXSTBID_MULTIFACE:
                        byte mf[] = new byte[2];
                        readed = fIn.read(mf);
                        szxLen -= 2;
                        if (szxLen > 0x4000) {
                            while (szxLen > 0)
                                szxLen -= fIn.skip(szxLen);
                            break;
                        }

                        chData = new byte[szxLen];
                        readed = fIn.read(chData);
//                        System.out.println("MF RAM readed bytes: " + readed);
                        if ((mf[1] & ZXSTMF_16KRAMMODE) != 0) {
                            multiface = false;
                            break;  // Config. no soportada
                        }

                        multiface = true;
                        if ((mf[0] & ZXSTMFM_128) != 0) {
                            mf128on48k = true;
                        }

                        if ((mf[1] & ZXSTMF_PAGEDIN) != 0) {
                            mfPagedIn = true;
                        }

                        if ((mf[1] & ZXSTMF_SOFTWARELOCKOUT) != 0) {
                            mfLockout = true;
                        }

                        if ((mf[1] & ZXSTMF_COMPRESSED) == 0) {
                            memory.loadMFRam(chData);
                            break;
                        }
                        // MF RAM compressed
                        bais = new ByteArrayInputStream(chData);
                        iis = new InflaterInputStream(bais);
                        byte mfRAM[] = new byte[0x2000];
                        addr = 0;
                        while (addr < mfRAM.length) {
                            int value = iis.read();
                            if (value == -1)
                                break;
                            mfRAM[addr++] = (byte)value;
                        }
                        readed = iis.read();
                        iis.close();
                        if (addr != mfRAM.length || readed != -1) {
                            System.out.println("Multiface RAM uncompress error!");
                            multiface = false;
                            break;
                        }
                        memory.loadMFRam(mfRAM);
                        break;
                    case ZXSTBID_PALETTE:
                        if (szxLen != 66) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        ULAplus = true;
                        byte ULAplusRegs[] = new byte[szxLen];
                        readed = fIn.read(ULAplusRegs);

                        if (ULAplusRegs[0] == ZXSTPALETTE_ENABLED) {
                            ULAplusEnabled = true;
                        }

                        ULAplusRegister = ULAplusRegs[1] & 0xff;

                        for (int reg = 0; reg < 64; reg++) {
                            ULAplusPalette[reg] = ULAplusRegs[2 + reg] & 0xff;
                        }
                        break;
                    case ZXSTBID_ZXTAPE:
                        byte tape[] = new byte[4];
                        readed = fIn.read(tape);
                        szxLen -= tape.length;
//                        System.out.println(String.format("Tape Block #%d",
//                            (tape[0] & 0xff) + (tape[1] & 0xff) * 256));

                        byte qword[] = new byte[4];
                        readed = fIn.read(qword);
                        szxLen -= qword.length;
                        int uSize = dwMagicToInt(qword);

                        readed = fIn.read(qword);
                        szxLen -= qword.length;
                        int cSize = dwMagicToInt(qword);
//                        System.out.println(String.format("uSize: %d, cSize: %d",
//                                uSize, cSize));

                        byte szFileExtension[] = new byte[16];
                        readed = fIn.read(szFileExtension);
                        szxLen -= szFileExtension.length;

                        if ((tape[2] & ZXSTTP_EMBEDDED) != 0) {
                            // Hay que crear un String con la extensión sin
                            // "comerse" los ceros del final, porque luego no se
                            // ven, pero están....
                            int nChars = 0;
                            while(nChars < szFileExtension.length) {
                                if (szFileExtension[nChars] == 0)
                                    break;
                                 nChars++;
                            }
                            tapeExtension = new String(szFileExtension, 0, nChars);
                            tapeName = filename.getName();
//                            System.out.println(String.format("Tape embedded with extension: [%s]",
//                                    tapeExtension));

                            chData = new byte[szxLen];
                            readed = fIn.read(chData);
                            if ((tape[2] & ZXSTTP_COMPRESSED) != 0) {
//                                System.out.println("Tape compressed");
                                bais = new ByteArrayInputStream(chData);
                                iis = new InflaterInputStream(bais);
                                tapeData = new byte[uSize];
                                addr = 0;
                                while (addr < uSize) {
                                    int value = iis.read();
                                    if (value == -1) {
                                        break;
                                    }
                                    tapeData[addr++] = (byte) value;
                                }
                                readed = iis.read();
                                iis.close();
                                if (addr != uSize || readed != -1) {
                                    System.out.println("Tape uncompress error!");
                                    break;
                                }
                            } else {
                                tapeData = chData;
                            }
                            tapeEmbedded = true;
                        } else {
                            chData = new byte[szxLen];
                            readed = fIn.read(chData);
                            tapeName = new String(chData, 0, szxLen - 1);
//                            System.out.println("File linked: " + tapeName);
                            tapeLinked = true;
                        }
                        break;
                    case ZXSTBID_IF2ROM:
                        byte dwCartSize[] = new byte[4];
                        readed = fIn.read(dwCartSize);
                        int romLen = dwMagicToInt(dwCartSize);
//                        System.out.println(String.format("IF2 ROM present. Lenght: %d", romLen));
                        if (romLen > 0x4000) {
                            while (romLen > 0)
                            romLen -= fIn.skip(romLen);
                            break;
                        }

                        chData = new byte[romLen];
                        readed = fIn.read(chData);
                        if (romLen == 0x4000) {
                            memory.insertIF2RomFromSZX(chData);
                            IF2RomPresent = true;
                            break;
                        }

                        // ROM is compressed
                        bais = new ByteArrayInputStream(chData);
                        iis = new InflaterInputStream(bais);
                        byte IF2Rom[] = new byte[0x4000];
                        addr = 0;
                        while (addr < IF2Rom.length) {
                            int value = iis.read();
                            if (value == -1) {
                                break;
                            }
                            IF2Rom[addr++] = (byte) value;
                        }
                        readed = iis.read();
                        iis.close();
                        if (addr != IF2Rom.length || readed != -1) {
                            System.out.println("Rom uncompress error!");
                            break;
                        }
                        memory.insertIF2RomFromSZX(IF2Rom);
                        IF2RomPresent = true;
                        break;
                    case ZXSTBID_JOYSTICK:
                        if (szxLen != 6) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        
                        while (szxLen > 0)
                            szxLen -= fIn.skip(szxLen);
                        break;
                    case ZXSTBID_ZXATASP:
                    case ZXSTBID_ATARAM:
                    case ZXSTBID_ZXCF:
                    case ZXSTBID_CFRAM:
                    case ZXSTBID_COVOX:
                    case ZXSTBID_BETA128:
                    case ZXSTBID_BETADISK:
                    case ZXSTBID_DOCK:
                    case ZXSTBID_DSKFILE:
                    case ZXSTBID_GS:
                    case ZXSTBID_GSRAMPAGE:
                    case ZXSTBID_IF1:
                    case ZXSTBID_MICRODRIVE:
                    case ZXSTBID_MOUSE:
                    case ZXSTBID_PLUS3DISK:
                    case ZXSTBID_PLUSD:
                    case ZXSTBID_PLUSDDISK:
                    case ZXSTBID_ROM:
                    case ZXSTBID_TIMEXREGS:
                    case ZXSTBID_SIMPLEIDE:
                    case ZXSTBID_SPECDRUM:
                    case ZXSTBID_USPEECH:
                    case ZXSTBID_ZXPRINTER:
//                        chData = new byte[szxLen];
                        while (szxLen > 0)
                            szxLen -= fIn.skip(szxLen);

                        String blockID = new String(dwMagic);
                        System.out.println(String.format(
                            "SZX block ID '%s' readed but not emulated. Skipping...",
                            blockID));
                        break;
                    default:
//                        chData = new byte[szxLen];
//                        readed = fIn.read(chData);
//                        fIn.skip(szxLen);
                        String header = new String(dwMagic);
                        System.out.println(String.format("Unknown SZX block ID: %s", header));
                        fOut.close();
                        error = 4;
                        return false;
                }
            }
        } catch (IOException ex) {
//            ex.printStackTrace();
            error = 4;
            return false;
        }

        return true;
    }

    public boolean saveSZX(File filename, Memory memory) {

        try {
            try {
                fOut = new BufferedOutputStream(new FileOutputStream(filename));
            } catch (FileNotFoundException ex) {
                error = 2;
                return false;
            }

            // SZX Header
            String blockID = "ZXST";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x01); // SZX major version
            fOut.write(0x03); // SZX minor version
            switch (snapshotModel) {
                case SPECTRUM16K:
                    fOut.write(ZXSTMID_16K);
                    break;
                case SPECTRUM48K:
                    fOut.write(ZXSTMID_48K);
                    break;
                case SPECTRUM128K:
                    fOut.write(ZXSTMID_128K);
                    break;
                case SPECTRUMPLUS2:
                    fOut.write(ZXSTMID_PLUS2);
                    break;
                case SPECTRUMPLUS2A:
                    fOut.write(ZXSTMID_PLUS2A);
                    break;
                case SPECTRUMPLUS3:
                    fOut.write(ZXSTMID_PLUS3);
                    break;
            }
            fOut.write(0x01); // SZX reserved

            // SZX Creator block
            blockID = "CRTR";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x25);
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);  // CRTR lenght block
            blockID = "JSpeccy 0.87";
            byte[] szCreator = new byte[32];
            System.arraycopy(blockID.getBytes("US-ASCII"), 0, szCreator,
                0, blockID.getBytes("US-ASCII").length);
            fOut.write(szCreator);
            fOut.write(0x00);
            fOut.write(0x00); // JSpeccy major version (0)
            fOut.write(0x00);
            fOut.write(0x57); // JSpeccy minor version (87)
            fOut.write(0x00); // chData

            // SZX Z80REGS block
            blockID = "Z80R";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x25);
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);  // Z80R lenght block (37 bytes)
            byte[] z80r = new byte[37];
            z80r[0] = (byte) regAF;
            z80r[1] = (byte) (regAF >> 8);
            z80r[2] = (byte) regBC;
            z80r[3] = (byte) (regBC >> 8);
            z80r[4] = (byte) regDE;
            z80r[5] = (byte) (regDE >> 8);
            z80r[6] = (byte) regHL;
            z80r[7] = (byte) (regHL >> 8);
            z80r[8] = (byte) regAFalt;
            z80r[9] = (byte) (regAFalt >> 8);
            z80r[10] = (byte) regBCalt;
            z80r[11] = (byte) (regBCalt >> 8);
            z80r[12] = (byte) regDEalt;
            z80r[13] = (byte) (regDEalt >> 8);
            z80r[14] = (byte) regHLalt;
            z80r[15] = (byte) (regHLalt >> 8);
            z80r[16] = (byte) regIX;
            z80r[17] = (byte) (regIX >> 8);
            z80r[18] = (byte) regIY;
            z80r[19] = (byte) (regIY >> 8);
            z80r[20] = (byte) regSP;
            z80r[21] = (byte) (regSP >> 8);
            z80r[22] = (byte) regPC;
            z80r[23] = (byte) (regPC >> 8);
            z80r[24] = (byte) regI;
            z80r[25] = (byte) regR;
            if (iff1)
                z80r[26] = 0x01;
            if (iff2)
                z80r[27] = 0x01;
            z80r[28] = (byte) modeIM;
            z80r[29] = (byte) tstates;
            z80r[30] = (byte) (tstates >> 8);
            z80r[31] = (byte) (tstates >> 16);
            z80r[32] = (byte) (tstates >> 24);
            // ignore the chHoldIntReqCycles, chFlags & chBitReg fields
            fOut.write(z80r);

             // SZX SPECREGS block
            blockID = "SPCR";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x08);
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);  // Z80R lenght block (8 bytes)
            byte[] specr = new byte[8];
            specr[0] = (byte)border;
            specr[1] = (byte)last7ffd;
            specr[2] = (byte)last1ffd;
            // ignore the chEff7 & chFe fields
            fOut.write(specr);

            boolean[] save = new boolean[8];
            switch (snapshotModel) {
                case SPECTRUM16K:
                    save[5] = true;
                    break;
                case SPECTRUM48K:
                    save[0] = save[2] = save[5] = true;
                    break;
                default:
                    Arrays.fill(save, true);
            }

            // RAM pages
            byte[] ram = new byte[0x4000];
            ByteArrayOutputStream baos;
            DeflaterOutputStream dos;
            for (int page = 0; page < 8; page++) {
                if (save[page]) {
                    memory.savePage(page, ram);
                    baos = new ByteArrayOutputStream();
                    dos = new DeflaterOutputStream(baos);
                    dos.write(ram, 0, ram.length);
                    dos.close();
//                    System.out.println(String.format("Ram page: %d, compressed len: %d",
//                        page, baos.size()));
                    blockID = "RAMP";
                    fOut.write(blockID.getBytes("US-ASCII"));
                    int pageLen = baos.size() + 3;
                    fOut.write(pageLen);
                    fOut.write(pageLen >> 8);
                    fOut.write(pageLen >> 16);
                    fOut.write(pageLen >> 24);
                    fOut.write(ZXSTRF_COMPRESSED);
                    fOut.write(0x00);
                    fOut.write(page);
                    baos.writeTo(fOut);
                }
            }

             // SZX KEYB block
            blockID = "KEYB";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x05);
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);  // KEYB lenght block (5 bytes)
            if (issue2) {
                fOut.write(ZXSTKF_ISSUE2);
            } else {
                fOut.write(0x00);
            }
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);
            switch (joystick) {
                case NONE:
                    fOut.write(ZXSKJT_NONE);
                    break;
                case KEMPSTON:
                    fOut.write(ZXSKJT_KEMPSTON);
                    break;
                case SINCLAIR1:
                    fOut.write(ZXSKJT_SINCLAIR1);
                    break;
                case SINCLAIR2:
                    fOut.write(ZXSKJT_SINCLAIR2);
                    break;
                case CURSOR:
                    fOut.write(ZXSKJT_CURSOR);
                    break;
                case FULLER:
                    fOut.write(ZXSKJT_FULLER);
                    break;
            }

            // SZX AY block
            if (enabledAY) {
                byte[] ayID = new byte[4];
                ayID[0] = 'A';
                ayID[1] = 'Y';
                fOut.write(ayID);
                fOut.write(0x12);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);  // AY lenght block (18 bytes)
                if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                    fOut.write(ZXSTAYF_128AY);
                } else {
                    fOut.write(0x00);
                }
                fOut.write(lastfffd);
                for (int reg = 0; reg < 16; reg++) {
                    fOut.write(psgRegs[reg]);
                }
            }

            // SZX ULAplus block
            if (ULAplus) {
                blockID = "PLTT";
                fOut.write(blockID.getBytes("US-ASCII"));
                fOut.write(0x42);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);  // PLTT lenght block (66 bytes)
                if (ULAplusEnabled) {
                    fOut.write(ZXSTPALETTE_ENABLED);
                } else {
                    fOut.write(0x00);
                }
                fOut.write(ULAplusRegister);
                for (int color = 0; color < 64; color++) {
                    fOut.write(ULAplusPalette[color]);
                }
            }

            // SZX Multiface block
            if (multiface) {
                blockID = "MFCE";
                fOut.write(blockID.getBytes("US-ASCII"));

                byte[] mfRam = new byte[0x2000];
                memory.saveMFRam(mfRam);
                baos = new ByteArrayOutputStream();
                dos = new DeflaterOutputStream(baos);
                dos.write(mfRam, 0, mfRam.length);
                dos.close();
//                System.out.println("Compressed MF RAM: " + baos.size());
                int pageLen = baos.size() + 2;
                fOut.write(pageLen);
                fOut.write(pageLen >> 8);
                fOut.write(pageLen >> 16);
                fOut.write(pageLen >> 24);

                if (mf128on48k)
                    fOut.write(ZXSTMFM_128);
                else
                    fOut.write(0x00);

                int mfFlags = ZXSTMF_COMPRESSED;
                if (memory.isMultifacePaged()) {
                    mfFlags |= ZXSTMF_PAGEDIN;
                }
                if (memory.isMultifaceLocked()
                    && (snapshotModel.codeModel != MachineTypes.CodeModel.SPECTRUM48K
                    || mf128on48k)) {
                    mfFlags |= ZXSTMF_SOFTWARELOCKOUT;
                }
                fOut.write(mfFlags);
                baos.writeTo(fOut);
            }

            // SZX IF2 ROM Block
            if (memory.isIF2RomEnabled()) {
                blockID = "IF2R";
                fOut.write(blockID.getBytes("US-ASCII"));

                byte[] if2Rom = new byte[0x4000];
                memory.saveIF2Rom(if2Rom);
                baos = new ByteArrayOutputStream();
                dos = new DeflaterOutputStream(baos);
                dos.write(if2Rom, 0, if2Rom.length);
                dos.close();
//                System.out.println("Compressed IF2 ROM: " + baos.size());
                int pageLen = baos.size() + 4;
                fOut.write(pageLen);
                fOut.write(pageLen >> 8);
                fOut.write(pageLen >> 16);
                fOut.write(pageLen >> 24);
                pageLen = baos.size();
                fOut.write(pageLen);
                fOut.write(pageLen >> 8);
                fOut.write(pageLen >> 16);
                fOut.write(pageLen >> 24);
                baos.writeTo(fOut);
            }

            // SZX Tape Block
            if (tapeLinked && !tapeEmbedded) {
                blockID = "TAPE";
                fOut.write(blockID.getBytes("US-ASCII"));
                int blockLen = 28 + tapeName.length() + 1;
                fOut.write(blockLen);
                fOut.write(blockLen >> 8);
                fOut.write(blockLen >> 16);
                fOut.write(blockLen >> 24);

                fOut.write(tapeBlock);
                fOut.write(tapeBlock >> 8); // wCurrentBlockNo
                fOut.write(0x00);
                fOut.write(0x00); // wFlags
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00); // dwUncompressedSize
                blockLen = tapeName.length() + 1;
                fOut.write(blockLen);
                fOut.write(blockLen >> 8);
                fOut.write(blockLen >> 16);
                fOut.write(blockLen >> 24); // dwCompressedSize
                byte[] szFileExtension = new byte[16];
                fOut.write(szFileExtension);
                fOut.write(tapeName.getBytes("US-ASCII"));
                fOut.write(0x00); // zero-terminated strings
            }

            if (!tapeLinked && tapeEmbedded) {
                blockID = "TAPE";
                fOut.write(blockID.getBytes("US-ASCII"));
                File tapeFile = new File(tapeName);
                fIn = new BufferedInputStream(new FileInputStream(tapeFile));
                tapeData = new byte[fIn.available()];
                fIn.read(tapeData);
                baos = new ByteArrayOutputStream();
                dos = new DeflaterOutputStream(baos);
                dos.write(tapeData, 0, tapeData.length);
                dos.close();
//                System.out.println("Compressed IF2 ROM: " + baos.size());
                int blockLen = 28 + baos.size();
                fOut.write(blockLen);
                fOut.write(blockLen >> 8);
                fOut.write(blockLen >> 16);
                fOut.write(blockLen >> 24);

                fOut.write(tapeBlock);
                fOut.write(tapeBlock >> 8); // wCurrentBlockNo
                fOut.write(ZXSTTP_EMBEDDED | ZXSTTP_COMPRESSED); // wFlags
                fOut.write(0x00);
                fOut.write(tapeData.length);
                fOut.write(tapeData.length >> 8);
                fOut.write(tapeData.length >> 16);
                fOut.write(tapeData.length >> 24); // dwUncompressedSize
                blockLen = baos.size();
                fOut.write(blockLen);
                fOut.write(blockLen >> 8);
                fOut.write(blockLen >> 16);
                fOut.write(blockLen >> 24); // dwCompressedSize
                byte[] szFileExtension = new byte[16];
                szFileExtension[0] = 't';
                if (tapeName.toLowerCase().endsWith("tzx")) {
                    szFileExtension[1] = 'z';
                    szFileExtension[2] = 'x';
                } else {
                    szFileExtension[1] = 'a';
                    szFileExtension[2] = 'p';
                }
                fOut.write(szFileExtension);
                baos.writeTo(fOut);
            }
            fOut.close();

        } catch (IOException ex) {
            error = 6;
            return false;
        }

        return true;
    }
}
