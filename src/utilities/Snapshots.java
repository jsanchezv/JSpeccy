/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utilities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
    private int flagsMode;
    private int lastfffd;
    private int last1ffd;
    int psgRegs[] = new int[16];
    private MachineTypes snapshotModel;
    private int border;
    private int tstates;
    private Joystick joystick;
    private boolean issue3;
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
        "SNA_DONT_SUPPORT_PLUS3"
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

    public Joystick getJoystick() {
        return joystick;
    }

    public void setJoystick(Joystick model) {
        joystick = model;
    }

    public boolean isIssue3() {
        return issue3;
    }

    public void setIssue3(boolean version) {
        issue3 = version;
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
        if( filename.getName().toLowerCase().endsWith(".z80") )
            return saveZ80(filename, memory);
        
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

            if (snaLen == 49179) { // 48K
                for (int addr = 0x4000; addr < 0x10000; addr++) {
                    memory.writeByte(addr, (byte)fIn.read());
                }
                regPC = 0x72; // dirección de RETN en la ROM
            } else {
                boolean loaded[] = new boolean[8];
                byte highPage[] = new byte[0x4000];
                for (int addr = 0x4000; addr < 0xC000; addr++) {
                    memory.writeByte(addr, (byte)fIn.read());
                }

                // Hasta que leamos el último valor del puerto 0x7ffd no sabemos
                // en qué página hay que poner los últimos 16K. Los leemos en
                // un buffer temporal y luego los copiamos (que remedio!!!)
                int count = 0;
                while (count != -1 && count < 0x4000)
                    count += fIn.read(highPage, count, 0x4000 - count);

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
                    for (int addr = 0; addr < 0x4000; addr++) {
                        memory.writeByte(page, addr, highPage[addr]);
                    }
                    loaded[last7ffd & 0x07] = true;
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
                        for (int addr = 0; addr < 0x4000; addr++) {
                            memory.writeByte(page, addr, (byte)fIn.read());
                        }
                    }
                    // El formato SNA no guarda los registros del AY
                    // Los ponemos a cero y que se apañe....
                    Arrays.fill(psgRegs, 0);
                    lastfffd = 0;
                }

            }

            fIn.close();

            issue3 = true; // esto no se guarda en los SNA, algo hay que poner...
            joystick = Joystick.NONE; // idem
            tstates = 0;

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

        if (snapshotModel == MachineTypes.SPECTRUMPLUS3) {
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

            if (snapshotModel == MachineTypes.SPECTRUM48K) {
                regSP = (regSP - 1) & 0xffff;
                memory.writeByte(regSP, (byte)(regPC >>> 8));
                regSP = (regSP - 1) & 0xffff;
                memory.writeByte(regSP, (byte)regPC);
            }

            fOut.write(regSP);
            fOut.write(regSP >>> 8);
            fOut.write(modeIM);
            fOut.write(border);

            for (int addr = 0x4000; addr < 0x10000; addr++) {
                fOut.write(memory.readByte(addr));
            }
            
            if (snapshotModel != MachineTypes.SPECTRUM48K) {
                boolean saved[] = new boolean[8];
                saved[2] = saved[5] = true;
                fOut.write(regPC);
                fOut.write(regPC >>> 8);
                fOut.write(last7ffd);
                fOut.write(0x00); // La ROM del TR-DOS no está paginada
                saved[last7ffd & 0x07] = true;
                for (int page = 0; page < 8; page++) {
                    if (!saved[page]) {
                        for (int addr = 0; addr < 0x4000; addr++) {
                            fOut.write(memory.readByte(page, addr));
                        }
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
                    buffer[address++] = (byte)mem;
                } else {
                    int mem2 = fIn.read() & 0xff;
                    if (mem2 != 0xED) {
                        buffer[address++] = (byte)0xED;
                        buffer[address++] = (byte)mem2;
                    } else {
                        int nreps = fIn.read() & 0xff;
                        int value = fIn.read() & 0xff;
                        while (nreps-- > 0) {
                            buffer[address++] = (byte)value;
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

    private int countRepeatedByte(Memory memory, int page, int address, int value) {
        int count = 0;

        while(address < 0x4000 && count < 254 &&
                value == memory.readByte(page, address++))
            count++;

        return count;
    }
    
    private int compressPageZ80(Memory memory, int buffer[], int page) {
        int address = 0;
        int addrDst = 0;
        int value, nReps;

        while (address < 0x4000) {
            value = memory.readByte(page, address++);
            nReps = countRepeatedByte(memory, page, address, value);
            if (value == 0xED) {
                if(nReps == 0) {
                    // El byte que sigue a ED siempre se representa sin
                    // comprimir, aunque hayan repeticiones.
                    buffer[addrDst++] = 0xED;
                    buffer[addrDst++] = memory.readByte(page, address++);
                } else {
                    // Varios ED consecutivos siempre se comprimen, aunque
                    // hayan menos de 5.
                    buffer[addrDst++] = 0xED;
                    buffer[addrDst++] = 0xED;
                    buffer[addrDst++] = nReps + 1;
                    buffer[addrDst++] = 0xED;
                    address += nReps;
                }
            } else {
                if (nReps < 4) {
                    // Si hay menos de 5 valores consecutivos iguales
                    // no se comprimen.
                    buffer[addrDst++] = value;
                } else {
                    buffer[addrDst++] = 0xED;
                    buffer[addrDst++] = 0xED;
                    buffer[addrDst++] = nReps + 1;
                    buffer[addrDst++] = value;
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
            issue3 = (byte29 & 0x04) == 0 ? true : false;
            switch (byte29 >>> 6) {
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
                snapshotModel = MachineTypes.SPECTRUM48K;
                memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                memory.reset();
                if ((byte12 & 0x20) == 0) { // el bloque no está comprimido
                    // Segmento 0x4000-0x7FFF a página 5
                    for (int address = 0; address < 0x4000; address++) {
                        memory.writeByte(5, address, (byte)fIn.read());
                    }
                    // Segmento 0x8000-0xBFFF a página 2
                    for (int address = 0; address < 0x4000; address++) {
                        memory.writeByte(2, address, (byte)fIn.read());
                    }
                    // Segmento 0xC000-0xFFFF a página 0
                    for (int address = 0; address < 0x4000; address++) {
                        memory.writeByte(0, address, (byte)fIn.read());
                    }
                } else {
                    byte buffer[] = new byte[0xC000];
                    int len = uncompressZ80(buffer, buffer.length);
                    if (len != 0xC000 || fIn.available() != 4) {
                        error = 4;
                        fIn.close();
                        return false;
                    }

                    for (int address = 0; address < buffer.length; address++) {
                        memory.writeByte(address + 0x4000, buffer[address]);
                    }
                }
            } else {
                // Z80 v2 & v3
                int hdrLen = fIn.read() | (fIn.read() << 8) & 0xffff;
                if (hdrLen != 23 && hdrLen != 54 && hdrLen != 55) {
                    error = 3;
                    fIn.close();
                    return false;
                }

                regPC = fIn.read() | (fIn.read() << 8) & 0xffff;
                int hwMode = fIn.read() & 0xff;
                if (hdrLen == 23) { // Z80 v2
                    switch (hwMode) {
                        case 0: // 48k
                            snapshotModel = MachineTypes.SPECTRUM48K;
                            memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                            memory.reset();
                            break;
                        case 3: // 128k
                            snapshotModel = MachineTypes.SPECTRUM128K;
                            memory.setSpectrumModel(MachineTypes.SPECTRUM128K);
                            memory.reset();
                            break;
                        case 7: // +3
                            snapshotModel = MachineTypes.SPECTRUMPLUS3;
                            memory.setSpectrumModel(MachineTypes.SPECTRUMPLUS3);
                            memory.reset();
                            break;
                        case 12: // Plus 2
                            snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            memory.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                            memory.reset();
                            break;
                        case 13: // +2A
                            snapshotModel = MachineTypes.SPECTRUMPLUS3;
                            memory.setSpectrumModel(MachineTypes.SPECTRUMPLUS3);
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
                            snapshotModel = MachineTypes.SPECTRUM48K;
                            memory.setSpectrumModel(MachineTypes.SPECTRUM48K);
                            memory.reset();
                            break;
                        case 4: // 128k
                            snapshotModel = MachineTypes.SPECTRUM128K;
                            memory.setSpectrumModel(MachineTypes.SPECTRUM128K);
                            memory.reset();
                            break;
                        case 7: // +3
                            snapshotModel = MachineTypes.SPECTRUMPLUS3;
                            memory.setSpectrumModel(MachineTypes.SPECTRUMPLUS3);
                            memory.reset();
                            break;
                        case 12: // Plus 2
                            snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            memory.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                            memory.reset();
                            break;
                        case 13: // +2A
                            snapshotModel = MachineTypes.SPECTRUMPLUS3;
                            memory.setSpectrumModel(MachineTypes.SPECTRUMPLUS3);
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

                byte buffer[] = new byte[0x4000];
                while (fIn.available() > 0) {
                    int blockLen = fIn.read() | (fIn.read() << 8) & 0xffff;
                    int ramPage = fIn.read() & 0xff;
                    if (snapshotModel == MachineTypes.SPECTRUM48K) {
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
                        for (int addr = 0; addr < 0x4000; addr++) {
                            memory.writeByte(ramPage, addr, (byte)fIn.read());
                        }
                    } else {
                        int len = uncompressZ80(buffer, 0x4000);
                        if (len != 0x4000) {
                            error = 5;
                            fIn.close();
                            return false;
                        }

                        for (int address = 0; address < buffer.length; address++) {
                            memory.writeByte(ramPage, address, buffer[address]);
                        }
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

            fOut.write(regAF >>> 8);
            fOut.write(regAF);
            fOut.write(regBC);
            fOut.write(regBC >>> 8);
            fOut.write(regHL);
            fOut.write(regHL >>> 8);
            fOut.write(0x00);
            fOut.write(0x00); // Si regPC==0, el Z80 es version 2 o 3
            fOut.write(regSP);
            fOut.write(regSP >>> 8);
            fOut.write(regI);
            fOut.write(regR);
            int flag = border << 1;
            flag |= regR > 0x7f ? 0x01 : 0x00;
            fOut.write(flag);
            fOut.write(regDE);
            fOut.write(regDE >>> 8);
            fOut.write(regBCalt);
            fOut.write(regBCalt >>> 8);
            fOut.write(regDEalt);
            fOut.write(regDEalt >>> 8);
            fOut.write(regHLalt);
            fOut.write(regHLalt >>> 8);
            fOut.write(regAFalt >>> 8);
            fOut.write(regAFalt);
            fOut.write(regIY);
            fOut.write(regIY >>> 8);
            fOut.write(regIX);
            fOut.write(regIX >>> 8);
            int iff = iff1 ? 0x01 : 0x00;
            fOut.write(iff);
            iff = iff2 ? 0x01 : 0x00;
            fOut.write(iff);
            if (!issue3)
                modeIM |= 0x04;
            switch (joystick) {
                case NONE:
                case CURSOR:
                    break;
                case KEMPSTON:
                    modeIM |= 0x40;
                    break;
                case SINCLAIR1:
                    modeIM |= 0x80;
                    break;
                case SINCLAIR2:
                    modeIM |= 0xC0;
            }
            fOut.write(modeIM);
            // Hasta aquí la cabecera v1.0, ahora viene lo propio de la v3.x
            fOut.write(55);
            fOut.write(0x00);  // Cabecera de 55 bytes (v3.x)
            fOut.write(regPC);
            fOut.write(regPC >>> 8);

            byte hwMode = 0;
            switch (snapshotModel) {
                case SPECTRUM48K:
                    break;
                case SPECTRUM128K:
                    hwMode = 4;
                    break;
                case SPECTRUMPLUS2:
                    hwMode = 12;
                    break;
                case SPECTRUMPLUS3:
                    hwMode = 13; // +2A ID
            }
            fOut.write(hwMode);

            if (snapshotModel != MachineTypes.SPECTRUM48K) {
                fOut.write(last7ffd);
            } else {
                fOut.write(0x00);
            }
            fOut.write(0x00);  // byte 36
            flagsMode |= 0x03; //  R register & LDIR emulation on
            fOut.write(flagsMode);
            fOut.write(lastfffd);
            for (int reg = 0; reg < 16; reg++) {
                fOut.write(psgRegs[reg]);
            }
            fOut.write(0x00); // lsb low T state counter
            fOut.write(0x00); // msb low T state counter
            fOut.write(0x00); // Hi T state counter
            fOut.write(0x00); // flag for Spectator
            fOut.write(0x00); // M.G.T. ROM paged
            fOut.write(0x00); // Multiface ROM paged
            fOut.write(0xff); // 0-8191 is ROM
            fOut.write(0xff); // 8192-16383 is ROM
            byte keymaps[] = new byte[20];
            fOut.write(keymaps); // keymaps & ASCII words
            fOut.write(0x00); // M.G.T. type
            fOut.write(0x00); // Disciple inhibit button status
            fOut.write(0xff); // Disciple inhibit flag
            if (snapshotModel != MachineTypes.SPECTRUMPLUS3)
                last1ffd = 0;
            fOut.write(last1ffd);

            int buffer[] = new int[0x4000];
            int bufLen;
            if (snapshotModel == MachineTypes.SPECTRUM48K) {
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
                for (int addr = 0; addr < bufLen; addr++) {
                    fOut.write(buffer[addr]);
                }

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
                for (int addr = 0; addr < bufLen; addr++) {
                    fOut.write(buffer[addr]);
                }

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
                for (int addr = 0; addr < bufLen; addr++) {
                    fOut.write(buffer[addr]);
                }
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
                    for (int addr = 0; addr < bufLen; addr++) {
                        fOut.write(buffer[addr]);
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
}
