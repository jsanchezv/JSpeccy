/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utilities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private boolean haveMultiface, mfPagedIn, mf128on48k, mfLockout;
    // ULAplus support
    private boolean ULAplus, ULAplusEnabled;
    private int ULAplusRegister, ULAplusPalette[] = new int[64];

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
        return haveMultiface;
    }

    public void setMultiface(boolean haveMF) {
        haveMultiface = haveMF;
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

    private static final int ZXSTBID_CREATOR = 0x52545243;   // CRTR

    private static final int ZXSTBID_Z80REGS = 0x5230385A;   // Z80R
    private static final int ZXSTZF_EILAST = 1;
    private static final int ZXSTZF_HALTED = 2;

    private static final int ZXSTBID_SPECREGS = 0x52435053;  // SPCR

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
    private static final int ZXSKJT_DISABLED = 8;

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

    private static final int ZXSTBID_AY = 0x00005941;        // AY\0\0
    private static final int ZXSTAYF_FULLERBOX = 1;
    private static final int ZXSTAYF_128AY = 2;

    private static final int ZXSTBID_RAMPAGE = 0x504D4152;   // RAMP
    private static final int ZXSTRF_COMPRESSED = 1;

    private static final int ZXSTBID_MULTIFACE = 0x4543464D; // MFCE
    private static final int ZXSTMFM_1 = 0;
    private static final int ZXSTMFM_128 = 1;
    private static final int ZXSTMF_PAGEDIN = 0x01;
    private static final int ZXSTMF_COMPRESSED = 0x02;
    private static final int ZXSTMF_SOFTWARELOCKOUT = 0x04;
    private static final int ZXSTMF_REDBUTTONDISABLED = 0x08;
    private static final int ZXSTMF_DISABLED = 0x10;
    private static final int ZXSTMF_16KRAMMODE = 0x20;

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
                        byte szCreator[] = new byte[szxLen];
                        readed = fIn.read(szCreator);
                        break;
                    case ZXSTBID_Z80REGS:
                        byte zxRegs[] = new byte[szxLen];
                        readed = fIn.read(zxRegs);
                        regAF = (zxRegs[0] & 0xff) | (zxRegs[1] << 8) & 0xffff;
                        regBC = (zxRegs[2] & 0xff) | (zxRegs[3] << 8) & 0xffff;
                        regDE = (zxRegs[4] & 0xff) | (zxRegs[5] << 8) & 0xffff;
                        regHL = (zxRegs[6] & 0xff) | (zxRegs[7] << 8) & 0xffff;
                        regAFalt = (zxRegs[8] & 0xff) | (zxRegs[9] << 8) & 0xffff;
                        regBCalt = (zxRegs[10] & 0xff) | (zxRegs[11] << 8) & 0xffff;
                        regDEalt = (zxRegs[12] & 0xff) | (zxRegs[13] << 8) & 0xffff;
                        regHLalt = (zxRegs[14] & 0xff) | (zxRegs[15] << 8) & 0xffff;
                        regIX = (zxRegs[16] & 0xff) | (zxRegs[17] << 8) & 0xffff;
                        regIY = (zxRegs[18] & 0xff) | (zxRegs[19] << 8) & 0xffff;
                        regSP = (zxRegs[20] & 0xff) | (zxRegs[21] << 8) & 0xffff;
                        regPC = (zxRegs[22] & 0xff) | (zxRegs[23] << 8) & 0xffff;
                        regI = zxRegs[24] & 0xff;
                        regR = zxRegs[25] & 0xff;
                        iff1 = (zxRegs[26] & 0xff )!= 0;
                        iff2 = (zxRegs[27] & 0xff )!= 0;
                        modeIM = zxRegs[28] & 0xff;
                        tstates = ((zxRegs[32] & 0xff) << 24) | ((zxRegs[31] & 0xff) << 16) |
                                ((zxRegs[30] & 0xff) << 8) | (zxRegs[29] & 0xff);
                        break;
                    case ZXSTBID_SPECREGS:
                        byte spRegs[] = new byte[szxLen];
                        readed = fIn.read(spRegs);
                        border = spRegs[0] & 0x07;
                        last7ffd = spRegs[1] & 0xff;
                        last1ffd = spRegs[2] & 0xff;
                        break;
                    case ZXSTBID_KEYBOARD:
                        byte keyb[] = new byte[szxLen];
                        readed = fIn.read(keyb);
                        issue2 = (keyb[0] & ZXSTKF_ISSUE2) != 0;
                        switch (keyb[4] & 0xff) {
                            case ZXSKJT_KEMPSTON:
                                joystick = Joystick.KEMPSTON;
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
                    case ZXSTBID_JOYSTICK:
                        byte joy[] = new byte[szxLen];
                        readed = fIn.read(joy);
                        break;
                    case ZXSTBID_AY:
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
                        byte ramp[] = new byte[3];
                        readed = fIn.read(ramp);
                        szxLen -= 3;
                        byte page[] = new byte[szxLen];
                        readed = fIn.read(page);
                        if ((ramp[0] & ZXSTRF_COMPRESSED) == 0) {
                            memory.loadPage(ramp[2] & 0xff, page);
                            break;
                        }
                        // Compressed RAM page
                        bais = new ByteArrayInputStream(page);
                        iis = new InflaterInputStream(bais);
                        addr = 0;
                        while (addr < 0x4000) {
                            int value = iis.read();
                            if (value == -1)
                                break;
                            memory.writeByte(ramp[2] & 0xff, addr++, (byte)value);
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
                        byte mfBuf[] = new byte[szxLen];
                        readed = fIn.read(mfBuf);
                        if ((mf[1] & ZXSTMF_16KRAMMODE) != 0) {
                            haveMultiface = false;
                            break;  // Config. no soportada
                        }

                        haveMultiface = true;
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
                            memory.loadMFRam(mfBuf);
                            break;
                        }
                        // MF RAM compressed
                        bais = new ByteArrayInputStream(mfBuf);
                        iis = new InflaterInputStream(bais);
                        byte mfRAM[] = new byte[0x2000];
                        addr = 0;
                        while (addr < 0x4000) {
                            int value = iis.read();
                            if (value == -1)
                                break;
                            mfRAM[addr++] = (byte)value;
                        }
                        readed = iis.read();
                        iis.close();
                        if (addr != 0x2000 || readed != -1) {
                            haveMultiface = false;
                            break;
                        }
                        memory.loadMFRam(mfRAM);
                        break;
                    case ZXSTBID_PALETTE:
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
                    default:
                        byte unknown[] = new byte[szxLen];
                        readed = fIn.read(unknown);
                        String header = new String(dwMagic);
                        System.out.println(String.format("Unknown SZX block ID: %s", header));
                }
            }
        } catch (IOException ex) {
//            ex.printStackTrace();
            error = 4;
            return false;
        }

        return true;
    }
}
