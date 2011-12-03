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
import snapshots.AY8912State;
import snapshots.Z80State;
import z80core.Z80.IntMode;

/**
 *
 * @author jsanchez
 */
public class Snapshots {

    private Z80State z80state;
    private int last7ffd;
    private int last1ffd;
    private boolean enabledAY;
    // AY support
    private AY8912State ay8912state;
    // Multiface support
    private boolean multiface, mfPagedIn, mf128on48k, mfLockout;
    // ULAplus support
    private boolean ULAplus, ULAplusEnabled;
    private int ULAplusRegister, ULAplusPalette[] = new int[64];
    // IF1 support;
    private boolean IF1Enabled, IF1RomPaged;
    private byte numDrives;
    private int driveRunning;
//    private int cartridgePos[] = new int[8];
//    private int preambleRem[] = new int[8];
//    private String cartridgeFile[] = new String[8];
    // IF2 ROM support
    private boolean IF2RomPresent;
    // Tape Support
    private boolean tapeEmbedded, tapeLinked;
    private byte tapeData[];
    private int tapeBlock;
    private String tapeName, tapeExtension;
    private MachineTypes snapshotModel;
    private int border;
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

    public final Z80State getZ80State() {
        return z80state;
    }
    
    public final void setZ80State(Z80State state) {
        z80state = state;
    }
    
    public AY8912State getAY8912State() {
        return ay8912state;
    }
    
    public void setAY8912State(AY8912State state) {
        ay8912state = state;
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

    public boolean isIF1Enabled() {
        return IF1Enabled;
    }

    public void setIF1Present(boolean state) {
        IF1Enabled = state;
    }

    public boolean isIF1RomPaged() {
        return IF1RomPaged;
    }

    public void setIF1RomPaged(boolean state) {
        IF1RomPaged = state;
    }

    public boolean isIF2RomPaged() {
        return IF2RomPresent;
    }

    public void setIF2RomPaged(boolean state) {
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

    public byte getNumDrives() {
        return numDrives;
    }

    public void setNumDrives(byte drives) {
        numDrives = drives;
    }

    public int getDriveRunning() {
        return driveRunning;
    }

    public void setDriveRunning(int drive) {
        driveRunning = drive;
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

            z80state = new Z80State();
            z80state.setRegI(snaHeader[0]);
            z80state.setRegLx(snaHeader[1]);
            z80state.setRegHx(snaHeader[2]);
            z80state.setRegEx(snaHeader[3]);
            z80state.setRegDx(snaHeader[4]);
            z80state.setRegCx(snaHeader[5]);
            z80state.setRegBx(snaHeader[6]);
            z80state.setRegFx(snaHeader[7]);
            z80state.setRegAx(snaHeader[8]);
            z80state.setRegL(snaHeader[9]);
            z80state.setRegH(snaHeader[10]);
            z80state.setRegE(snaHeader[11]);
            z80state.setRegD(snaHeader[12]);
            z80state.setRegC(snaHeader[13]);
            z80state.setRegB(snaHeader[14]);
            z80state.setRegIY((snaHeader[15] & 0xff) | (snaHeader[16] << 8));
            z80state.setRegIX((snaHeader[17] & 0xff) | (snaHeader[18] << 8));

            z80state.setIFF1((snaHeader[19] & 0x02) != 0);
            z80state.setIFF2((snaHeader[19] & 0x04) != 0);

            z80state.setRegR(snaHeader[20]);
            z80state.setRegF(snaHeader[21]);
            z80state.setRegA(snaHeader[22]);
            z80state.setRegSP((snaHeader[23] & 0xff) | (snaHeader[24] << 8));
            switch (snaHeader[25] & 0x03) {
                case 0:
                    z80state.setIM(IntMode.IM0);
                    break;
                case 1:
                    z80state.setIM(IntMode.IM1);
                    break;
                case 2:
                    z80state.setIM(IntMode.IM2);
                    break;
            }

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

                z80state.setRegPC(0x72); // dirección de RETN en la ROM
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
                z80state.setRegPC(fIn.read() | (fIn.read() << 8));
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
                    }
                    // El formato SNA no guarda los registros del AY
                    // Los ponemos a cero y que se apañe....
                    int regAY[] = new int[16];
                    ay8912state = new AY8912State();
                    ay8912state.setAddressLatch(0);
                    ay8912state.setRegAY(regAY);
                }

            }

            fIn.close();

            issue2 = false; // esto no se guarda en los SNA, algo hay que poner...
            joystick = Joystick.NONE; // idem
            z80state.setTstates(0);
            mfPagedIn = mf128on48k = false;

        } catch (IOException ex) {
            error = 4;
            return false;
        }

        return true;
    }

    private boolean saveSNA(File filename, Memory memory) {

        // Si la pila está muy baja, no hay donde almacenar el registro SP
        if (snapshotModel == MachineTypes.SPECTRUM48K && z80state.getRegSP() < 0x4002) {
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
            snaHeader[0] = (byte) z80state.getRegI();
            snaHeader[1] = (byte) z80state.getRegLx();
            snaHeader[2] = (byte) z80state.getRegHx();
            snaHeader[3] = (byte) z80state.getRegEx();
            snaHeader[4] = (byte) z80state.getRegDx();
            snaHeader[5] = (byte) z80state.getRegCx();
            snaHeader[6] = (byte) z80state.getRegBx();
            snaHeader[7] = (byte) z80state.getRegFx();
            snaHeader[8] = (byte) z80state.getRegAx();
            snaHeader[9] = (byte) z80state.getRegL();
            snaHeader[10] = (byte) z80state.getRegH();
            snaHeader[11] = (byte) z80state.getRegE();
            snaHeader[12] = (byte) z80state.getRegD();
            snaHeader[13] = (byte) z80state.getRegC();
            snaHeader[14] = (byte) z80state.getRegB();
            snaHeader[15] = (byte) z80state.getRegIY();
            snaHeader[16] = (byte) (z80state.getRegIY() >>> 8);
            snaHeader[17] = (byte) z80state.getRegIX();
            snaHeader[18] = (byte) (z80state.getRegIX() >>> 8);

            if (z80state.isIFF1()) {
                snaHeader[19] |= 0x02;
            }
            if (z80state.isIFF2()) {
                snaHeader[19] |= 0x04;
            }

            snaHeader[20] = (byte) z80state.getRegR();
            snaHeader[21] = (byte) z80state.getRegF();
            snaHeader[22] = (byte) z80state.getRegA();

            int regSP = z80state.getRegSP();
            if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                regSP = (regSP - 1) & 0xffff;
                memory.writeByte(regSP, (byte) (z80state.getRegPC() >>> 8));
                regSP = (regSP - 1) & 0xffff;
                memory.writeByte(regSP, (byte) z80state.getRegPC());
            }

            snaHeader[23] = (byte) regSP;
            snaHeader[24] = (byte) (regSP >>> 8);
            snaHeader[25] = (byte) z80state.getIM().ordinal();
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
                fOut.write(z80state.getRegPC());
                fOut.write(z80state.getRegPC() >>> 8);
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

            z80state = new Z80State();
            z80state.setRegA(z80Header1[0]);
            z80state.setRegF(z80Header1[1]);
            z80state.setRegC(z80Header1[2]);
            z80state.setRegB(z80Header1[3]);
            z80state.setRegL(z80Header1[4]);
            z80state.setRegH(z80Header1[5]);
            z80state.setRegPC((z80Header1[6] & 0xff) | (z80Header1[7] << 8));
            z80state.setRegSP((z80Header1[8] & 0xff) | (z80Header1[9] << 8));
            z80state.setRegI(z80Header1[10]);

            int regR = z80Header1[11] & 0x7f;
            if ((z80Header1[12] & 0x01) != 0) {
                regR |= 0x80;
            }
            z80state.setRegR(regR);

            border = (z80Header1[12] >>> 1) & 0x07;

            z80state.setRegE(z80Header1[13]);
            z80state.setRegD(z80Header1[14]);
            z80state.setRegCx(z80Header1[15]);
            z80state.setRegBx(z80Header1[16]);
            z80state.setRegEx(z80Header1[17]);
            z80state.setRegDx(z80Header1[18]);
            z80state.setRegLx(z80Header1[19]);
            z80state.setRegHx(z80Header1[20]);
            z80state.setRegAx(z80Header1[21]);
            z80state.setRegFx(z80Header1[22]);
            z80state.setRegIY((z80Header1[23] & 0xff) | (z80Header1[24] << 8));
            z80state.setRegIX((z80Header1[25] & 0xff) | (z80Header1[26] << 8));
            z80state.setIFF1(z80Header1[27] != 0);
            z80state.setIFF2(z80Header1[28] != 0);

            switch (z80Header1[29] & 0x03) {
                case 0:
                    z80state.setIM(IntMode.IM0);
                    break;
                case 1:
                    z80state.setIM(IntMode.IM1);
                    break;
                case 2:
                    z80state.setIM(IntMode.IM2);
                    break;
            }

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
            if (z80state.getRegPC() != 0) {
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

                z80state.setRegPC((z80Header2[0] & 0xff) | (z80Header2[1] << 8));

                boolean modifiedHW = (z80Header2[5] & 0x80) != 0;
                if (hdrLen == 23) { // Z80 v2
                    switch (z80Header2[2]) {
                        case 0: // 48k
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUM16K;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUM48K;
                            }

                            IF1Enabled = false;
                            break;
                        case 1: // 48k + IF.1
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUM16K;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUM48K;
                            }

                            IF1Enabled = true;
                            if (z80Header2[4] == 0xff) {
                                IF1RomPaged = true;
                            }
                            break;
                        case 3: // 128k
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUM128K;
                            }

                            IF1Enabled = false;
                            break;
                        case 4: // 128k + IF.1
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUM128K;
                            }

                            IF1Enabled = true;
                            if (z80Header2[4] == 0xff) {
                                IF1RomPaged = true;
                            }
                            break;
                        case 7: // +3
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUMPLUS2A;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUMPLUS3;
                            }
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
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUM16K;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUM48K;
                            }

                            IF1Enabled = false;
                            break;
                        case 1: // 48k + IF.1
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUM16K;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUM48K;
                            }

                            IF1Enabled = true;
                            if (z80Header2[4] == 0xff) {
                                IF1RomPaged = true;
                            }
                            break;
                        case 4: // 128k
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUM128K;
                            }

                            IF1Enabled = false;
                            break;
                        case 5: // 128k + IF.1
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUM128K;
                            }

                            IF1Enabled = true;
                            if (z80Header2[4] == 0xff) {
                                IF1RomPaged = true;
                            }
                            break;
                        case 7: // +3
                            if (modifiedHW) {
                                snapshotModel = MachineTypes.SPECTRUMPLUS2A;
                            } else {
                                snapshotModel = MachineTypes.SPECTRUMPLUS3;
                            }
                            break;
                        case 12: // +2
                            snapshotModel = MachineTypes.SPECTRUMPLUS2;
                            break;
                        case 13: // +2A
                            snapshotModel = MachineTypes.SPECTRUMPLUS2A;
                            break;
                        default:
//                            System.out.println(String.format("SnapshotV3 model: %d",
//                                    z80Header2[2]));
                            error = 5;
                            fIn.close();
                            return false;
                    }
                }

                memory.setSpectrumModel(snapshotModel);

                last7ffd = z80Header2[3] & 0xff;
                enabledAY = (z80Header2[5] & 0x04) != 0;
                
                ay8912state = new AY8912State();
                int regAY[] = new int[16];
                ay8912state.setAddressLatch(z80Header2[6]);
                for (int idx = 0; idx < 16; idx++) {
                    regAY[idx] = z80Header2[7 + idx] & 0xff;
                }
                ay8912state.setRegAY(regAY);

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

            z80state.setTstates(0);

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
            z80HeaderV3[0] = (byte) z80state.getRegA();
            z80HeaderV3[1] = (byte) z80state.getRegF();
            z80HeaderV3[2] = (byte) z80state.getRegC();
            z80HeaderV3[3] = (byte) z80state.getRegB();
            z80HeaderV3[4] = (byte) z80state.getRegL();
            z80HeaderV3[5] = (byte) z80state.getRegH();
            // Bytes 6 y 7 se dejan a 0, si regPC==0, el Z80 es version 2 o 3
            z80HeaderV3[8] = (byte) z80state.getRegSP();
            z80HeaderV3[9] = (byte) (z80state.getRegSP() >>> 8);
            z80HeaderV3[10] = (byte) z80state.getRegI();
            z80HeaderV3[11] = (byte) (z80state.getRegR() & 0x7f);
            z80HeaderV3[12] = (byte) (border << 1);
            if (z80state.getRegR() > 0x7f) {
                z80HeaderV3[12] |= 0x01;
            }
            z80HeaderV3[13] = (byte) z80state.getRegE();
            z80HeaderV3[14] = (byte) z80state.getRegD();
            z80HeaderV3[15] = (byte) z80state.getRegCx();
            z80HeaderV3[16] = (byte) z80state.getRegBx();
            z80HeaderV3[17] = (byte) z80state.getRegEx();
            z80HeaderV3[18] = (byte) z80state.getRegDx();
            z80HeaderV3[19] = (byte) z80state.getRegLx();
            z80HeaderV3[20] = (byte) z80state.getRegHx();
            z80HeaderV3[21] = (byte) z80state.getRegAx();
            z80HeaderV3[22] = (byte) z80state.getRegFx();
            z80HeaderV3[23] = (byte) z80state.getRegIY();
            z80HeaderV3[24] = (byte) (z80state.getRegIY() >>> 8);
            z80HeaderV3[25] = (byte) z80state.getRegIX();
            z80HeaderV3[26] = (byte) (z80state.getRegIX() >>> 8);
            z80HeaderV3[27] = (byte) (z80state.isIFF1() ? 0x01 : 0x00);
            z80HeaderV3[28] = (byte) (z80state.isIFF2() ? 0x01 : 0x00);
            z80HeaderV3[29] = (byte) z80state.getIM().ordinal();

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
            z80HeaderV3[32] = (byte) z80state.getRegPC();
            z80HeaderV3[33] = (byte) (z80state.getRegPC() >>> 8);

            switch (snapshotModel) {
                case SPECTRUM16K:
                    z80HeaderV3[37] |= 0x80; // modified HW, 48k --> 16k
//                    break;
                case SPECTRUM48K:
                    z80HeaderV3[34] = (byte) (IF1Enabled ? 1 : 0);
                    break;
                case SPECTRUM128K:
                    z80HeaderV3[34] = (byte) (IF1Enabled ? 5 : 4);
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

            if (IF1RomPaged) {
                z80HeaderV3[36] = (byte) 0xff;
            }

            if (enabledAY) {
                z80HeaderV3[37] |= 0x04;
                z80HeaderV3[38] = (byte) ay8912state.getAddressLatch();

                int regAY[] = ay8912state.getRegAY();
                for (int reg = 0; reg < 16; reg++) {
                    z80HeaderV3[39 + reg] = (byte) regAY[reg];
                }
            }

            if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                z80HeaderV3[86] = (byte) last1ffd;
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
    private static final int ZXSTMID_NTSC48K = 15;
    private static final int ZXSTMID_128KE = 16;
    private static final int ZXSTMF_ALTERNATETIMINGS = 1;
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
    private static final int ZXSTDSKF_SIDEB = 4;
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
    private static final int ZXSTBID_IF1 = 0x00314649;      // IF1\0
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
    // New blocks from SZX v1.4 (04/06/2011)
    private static final int ZXSTBID_OPUS = 0x5355504F;   // OPUS
    // Flags
    private static final int ZXSTOPUSF_PAGED = 1;
    private static final int ZXSTOPUSF_COMPRESSED = 2;
    private static final int ZXSTOPUSF_SEEKLOWER = 4;
    private static final int ZXSTOPUSF_CUSTOMROM = 8;
    private static final int ZXSTBID_ODSK = 0x4B53444F;   // ODSK
    // Disk image types
    private static final int ZXSTOPDT_OPD = 0;
    private static final int ZXSTOPDT_OPU = 1;
    private static final int ZXSTOPDT_FLOPPY0 = 2;
    private static final int ZXSTOPDT_FLOPPY1 = 3;
    // Flags
    private static final int ZXSTOPDF_EMBEDDED = 1;
    private static final int ZXSTOPDF_COMPRESSED = 2;
    private static final int ZXSTOPDF_WRITEPROTECT = 4;

    private int dwMagicToInt(byte[] dwMagic) {
        int value0 = dwMagic[0] & 0xff;
        int value1 = (dwMagic[1] & 0xff) << 8;
        int value2 = (dwMagic[2] & 0xff) << 16;
        int value3 = (dwMagic[3] & 0xff) << 24;

        return (value3 | value2 | value1 | value0);
    }

    private boolean loadSZX(File filename, Memory memory) {
        byte dwMagic[] = new byte[4];
        byte dwSize[] = new byte[4];
        byte szxMajorVer, szxMinorVer;
        int readed, szxId, szxLen;
        ByteArrayInputStream bais;
        InflaterInputStream iis;
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
            if (readed != dwMagic.length || dwMagicToInt(dwMagic) != ZXST_HEADER) {
                error = 1;
                fIn.close();
                return false;
            }

            readed = fIn.read(dwSize);
            if (readed != dwSize.length) {
                error = 1;
                fIn.close();
                return false;
            }

            szxMajorVer = dwSize[0];
            szxMinorVer = dwSize[1];
            switch (dwSize[2]) {
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
                readed += fIn.read(dwSize);
                if (readed != 8) {
                    error = 4;
                    fIn.close();
                    return false;
                }

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
                            if (readed != szxLen) {
                                error = 4;
                                fIn.close();
                                return false;
                            }
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
                        if (readed != szxLen) {
                            error = 4;
                            fIn.close();
                            return false;
                        }

                        z80state = new Z80State();
                        z80state.setRegF(z80Regs[0]);
                        z80state.setRegA(z80Regs[1]);
                        z80state.setRegC(z80Regs[2]);
                        z80state.setRegB(z80Regs[3]);
                        z80state.setRegE(z80Regs[4]);
                        z80state.setRegD(z80Regs[5]);
                        z80state.setRegL(z80Regs[6]);
                        z80state.setRegH(z80Regs[7]);
                        z80state.setRegFx(z80Regs[8]);
                        z80state.setRegAx(z80Regs[9]);
                        z80state.setRegCx(z80Regs[10]);
                        z80state.setRegBx(z80Regs[11]);
                        z80state.setRegEx(z80Regs[12]);
                        z80state.setRegDx(z80Regs[13]);
                        z80state.setRegLx(z80Regs[14]);
                        z80state.setRegHx(z80Regs[15]);
                        z80state.setRegIX((z80Regs[16] & 0xff) | (z80Regs[17] << 8));
                        z80state.setRegIY((z80Regs[18] & 0xff) | (z80Regs[19] << 8));
                        z80state.setRegSP((z80Regs[20] & 0xff) | (z80Regs[21] << 8));
                        z80state.setRegPC((z80Regs[22] & 0xff) | (z80Regs[23] << 8));
                        z80state.setRegI(z80Regs[24]);
                        z80state.setRegR(z80Regs[25]);
                        z80state.setIFF1((z80Regs[26] & 0x01) != 0);
                        z80state.setIFF1((z80Regs[27] & 0x01) != 0);

                        switch (z80Regs[28] & 0x03) {
                            case 0:
                                z80state.setIM(IntMode.IM0);
                                break;
                            case 1:
                                z80state.setIM(IntMode.IM1);
                                break;
                            case 2:
                                z80state.setIM(IntMode.IM2);
                                break;
                        }
                        
                        z80state.setTstates((int) (((z80Regs[32] & 0xff) << 24) | ((z80Regs[31] & 0xff) << 16)
                                | ((z80Regs[30] & 0xff) << 8) | (z80Regs[29] & 0xff)));

                        if (szxMajorVer == 1 && szxMinorVer > 3) {
                            z80state.setMemPtr((z80Regs[35] & 0xff) | (z80Regs[36] << 8));
                        }
                        break;
                    case ZXSTBID_SPECREGS:
                        if (szxLen != 8) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        byte specRegs[] = new byte[szxLen];
                        readed = fIn.read(specRegs);
                        if (readed != szxLen) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
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
                        if (readed != szxLen) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
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
                        if (readed != szxLen) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        
                        enabledAY = true;
                        if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K
                                && ayRegs[0] != ZXSTAYF_128AY) {
                            enabledAY = false;
                        }
                        
                        ay8912state = new AY8912State();
                        int regAY[] = new int[16];
                        ay8912state.setAddressLatch(ayRegs[1]);
                        for (int idx = 0; idx < 16; idx++) {
                            regAY[idx] = ayRegs[2 + idx] & 0xff;
                        }
                        ay8912state.setRegAY(regAY);
                        break;
                    case ZXSTBID_RAMPAGE:
                        byte ramPage[] = new byte[3];
                        readed = fIn.read(ramPage);
                        if (readed != ramPage.length) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        szxLen -= 3;
                        if (szxLen > 0x4000) {
                            error = 10;
                            fIn.close();
                            return false;
                        }

                        chData = new byte[szxLen];
                        readed = fIn.read(chData);
                        if (readed != szxLen) {
                            error = 4;
                            fIn.close();
                            return false;
                        }

                        if ((ramPage[0] & ZXSTRF_COMPRESSED) == 0) {
                            memory.loadPage(ramPage[2] & 0xff, chData);
                            break;
                        }
                        // Compressed RAM page
                        bais = new ByteArrayInputStream(chData);
                        iis = new InflaterInputStream(bais);
                        byte pageRAM[] = new byte[0x4000];
                        readed = 0;
                        while (readed < pageRAM.length) {
                            int count = iis.read(pageRAM, readed, pageRAM.length - readed);
                            if (count == -1) {
                                break;
                            }
                            readed += count;
                        }
                        iis.close();
                        if (readed != 0x4000) {
                            error = 10;
                            fIn.close();
                            return false;
                        }
                        memory.loadPage(ramPage[2] & 0xff, pageRAM);
                        break;
                    case ZXSTBID_MULTIFACE:
                        byte mf[] = new byte[2];
                        readed = fIn.read(mf);
                        if (readed != mf.length) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        szxLen -= 2;
                        if (szxLen > 0x4000) {
                            while (szxLen > 0) {
                                szxLen -= fIn.skip(szxLen);
                            }
                            break;
                        }

                        chData = new byte[szxLen];
                        readed = fIn.read(chData);
                        if (readed != szxLen) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
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
                        readed = 0;
                        while (readed < mfRAM.length) {
                            int count = iis.read(mfRAM, readed, mfRAM.length - readed);
                            if (count == -1) {
                                break;
                            }
                            readed += count;
                        }
                        iis.close();
                        if (readed != mfRAM.length) {
                            System.out.println("Multiface RAM uncompress error!: " + readed);
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
                        if (readed != szxLen) {
                            error = 4;
                            fIn.close();
                            return false;
                        }

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
                        if (readed != tape.length) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        szxLen -= tape.length;
//                        System.out.println(String.format("Tape Block #%d",
//                            (tape[0] & 0xff) + (tape[1] & 0xff) * 256));

                        byte qword[] = new byte[4];
                        readed = fIn.read(qword);
                        if (readed != qword.length) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        szxLen -= qword.length;
                        int uSize = dwMagicToInt(qword);

                        readed = fIn.read(qword);
                        if (readed != qword.length) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        szxLen -= qword.length;
                        int cSize = dwMagicToInt(qword);
//                        System.out.println(String.format("uSize: %d, cSize: %d",
//                                uSize, cSize));

                        byte szFileExtension[] = new byte[16];
                        readed = fIn.read(szFileExtension);
                        if (readed != szFileExtension.length) {
                            error = 4;
                            fIn.close();
                            return false;
                        }
                        szxLen -= szFileExtension.length;

                        if ((tape[2] & ZXSTTP_EMBEDDED) != 0) {
                            // Hay que crear un String con la extensión sin
                            // "comerse" los ceros del final, porque luego no se
                            // ven, pero están....
                            int nChars = 0;
                            while (nChars < szFileExtension.length) {
                                if (szFileExtension[nChars] == 0) {
                                    break;
                                }
                                nChars++;
                            }
                            tapeExtension = new String(szFileExtension, 0, nChars);
                            tapeName = filename.getName();
//                            System.out.println(String.format("Tape embedded with extension: [%s]",
//                                    tapeExtension));

                            chData = new byte[szxLen];
                            readed = fIn.read(chData);
                            if (readed != szxLen) {
                                error = 4;
                                fIn.close();
                                return false;
                            }

                            if ((tape[2] & ZXSTTP_COMPRESSED) != 0) {
//                                System.out.println("Tape compressed");
                                bais = new ByteArrayInputStream(chData);
                                iis = new InflaterInputStream(bais);
                                tapeData = new byte[uSize];
                                readed = 0;
                                while (readed < tapeData.length) {
                                    int count = iis.read(tapeData, readed, tapeData.length - readed);
                                    if (count == -1) {
                                        break;
                                    }
                                    readed += count;
                                }
                                iis.close();
                                if (readed != uSize) {
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
                            if (readed != szxLen) {
                                error = 4;
                                fIn.close();
                                return false;
                            }
                            tapeName = new String(chData, 0, szxLen - 1);
//                            System.out.println("File linked: " + tapeName);
                            tapeLinked = true;
                        }
                        break;
                    case ZXSTBID_IF2ROM:
                        byte dwCartSize[] = new byte[4];
                        readed = fIn.read(dwCartSize);
                        if (readed != dwCartSize.length) {
                            error = 4;
                            fIn.close();
                            return false;
                        }

                        int romLen = dwMagicToInt(dwCartSize);
//                        System.out.println(String.format("IF2 ROM present. Length: %d", romLen));
                        if (romLen > 0x4000) {
                            while (romLen > 0) {
                                romLen -= fIn.skip(romLen);
                            }
                            break;
                        }

                        chData = new byte[romLen];
                        readed = fIn.read(chData);
                        if (readed != chData.length) {
                            error = 4;
                            fIn.close();
                            return false;
                        }

                        if (romLen == 0x4000) {
                            memory.insertIF2RomFromSZX(chData);
                            IF2RomPresent = true;
                            break;
                        }

                        // ROM is compressed
                        bais = new ByteArrayInputStream(chData);
                        iis = new InflaterInputStream(bais);
                        byte IF2Rom[] = new byte[0x4000];
                        readed = 0;
                        while (readed < IF2Rom.length) {
                            int count = iis.read(IF2Rom, readed, IF2Rom.length - readed);
                            if (count == -1) {
                                break;
                            }
                            readed += count;
                        }
                        iis.close();
                        if (readed != IF2Rom.length) {
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

                        while (szxLen > 0) {
                            szxLen -= fIn.skip(szxLen);
                        }
                        break;
                    case ZXSTBID_IF1:
                        if (snapshotModel.codeModel
                                == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                            System.out.println(
                                    "SZX Error: +2a/+3 snapshot with IF1 block. Skipping");
                            while (szxLen > 0) {
                                szxLen -= fIn.skip(szxLen);
                            }
                            break;
                        }

                        int if1Flags;
                        if1Flags = fIn.read() + fIn.read() * 256;
                        szxLen -= 2;
                        IF1Enabled = (if1Flags & ZXSTIF1F_ENABLED) != 0;
                        IF1RomPaged = (if1Flags & ZXSTIF1F_PAGED) != 0;

                        numDrives = (byte) fIn.read();
                        szxLen--;
                        if (numDrives < 1 || numDrives > 8) {
                            numDrives = 8;
                        }
//                        szxLen -= fIn.skip(35);
//                        int if1RomLen;
//                        if1RomLen = fIn.read() + fIn.read() * 256;
//                        szxLen -= 2;
                        while (szxLen > 0) {
                            szxLen -= fIn.skip(szxLen);
                        }
                        break;
                    case ZXSTBID_MICRODRIVE:
                        // The MDRV have some design problems, so is ignored now.
                        while (szxLen > 0) {
                            szxLen -= fIn.skip(szxLen);
                        }
//                        if (snapshotModel.codeModel ==
//                            MachineTypes.CodeModel.SPECTRUMPLUS3) {
//                            System.out.println(
//                                "SZX Error: +2a/+3 snapshot with MDRV block. Skipping");
//                            while (szxLen > 0)
//                                szxLen -= fIn.skip(szxLen);
//                            break;
//                        }
//                        int mdrvFlags;
//                        mdrvFlags = fIn.read() + fIn.read() * 256;
//                        szxLen -= 2;
//                        
//                        int driveNum = fIn.read();
//                        szxLen--;
//                        if (driveNum < 1 || driveNum > 8) {
//                            System.out.println(
//                                String.format("MDRV %d block error. Skipping",
//                                driveNum));
//                        }
//                        
//                        int motor = fIn.read() & 0xff;
//                        szxLen--;
//                        if (motor == 1)
//                            driveRunning = driveNum;
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
                    case ZXSTBID_OPUS:
                    case ZXSTBID_ODSK:
//                        chData = new byte[szxLen];
                        while (szxLen > 0) {
                            szxLen -= fIn.skip(szxLen);
                        }

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
            fOut.write(0x04); // SZX minor version
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
            fOut.write(0x00); // Flags (No ZXSTMF_ALTERNATETIMINGS)

            // SZX Creator block
            blockID = "CRTR";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x25);
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);  // CRTR length block
            blockID = "JSpeccy 0.89 (15/07/2011)";
            byte[] szCreator = new byte[32];
            System.arraycopy(blockID.getBytes("US-ASCII"), 0, szCreator,
                    0, blockID.getBytes("US-ASCII").length);
            fOut.write(szCreator);
            fOut.write(0x00);
            fOut.write(0x00); // JSpeccy major version (0)
            fOut.write(0x00);
            fOut.write(0x59); // JSpeccy minor version (89)
            fOut.write(0x00); // chData

            // SZX Z80REGS block
            blockID = "Z80R";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x25);
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);  // Z80R length block (37 bytes)
            byte[] z80r = new byte[37];
            z80r[0] = (byte) z80state.getRegF();
            z80r[1] = (byte) z80state.getRegA();
            z80r[2] = (byte) z80state.getRegC();
            z80r[3] = (byte) z80state.getRegB();
            z80r[4] = (byte) z80state.getRegE();
            z80r[5] = (byte) z80state.getRegD();
            z80r[6] = (byte) z80state.getRegL();
            z80r[7] = (byte) z80state.getRegH();
            z80r[8] = (byte) z80state.getRegFx();
            z80r[9] = (byte) z80state.getRegAx();
            z80r[10] = (byte) z80state.getRegCx();
            z80r[11] = (byte) z80state.getRegBx();
            z80r[12] = (byte) z80state.getRegEx();
            z80r[13] = (byte) z80state.getRegDx();
            z80r[14] = (byte) z80state.getRegLx();
            z80r[15] = (byte) z80state.getRegHx();
            z80r[16] = (byte) z80state.getRegIX();
            z80r[17] = (byte) (z80state.getRegIX() >>> 8);
            z80r[18] = (byte) z80state.getRegIY();
            z80r[19] = (byte) (z80state.getRegIY() >>> 8);
            z80r[20] = (byte) z80state.getRegSP();
            z80r[21] = (byte) (z80state.getRegSP() >>> 8);
            z80r[22] = (byte) z80state.getRegPC();
            z80r[23] = (byte) (z80state.getRegPC() >>> 8);
            z80r[24] = (byte) z80state.getRegI();
            z80r[25] = (byte) z80state.getRegR();
            if (z80state.isIFF1()) {
                z80r[26] = 0x01;
            }
            if (z80state.isIFF2()) {
                z80r[27] = 0x01;
            }
            z80r[28] = (byte) z80state.getIM().ordinal();
            z80r[29] = (byte) z80state.getTstates();
            z80r[30] = (byte) (z80state.getTstates() >>> 8);
            z80r[31] = (byte) (z80state.getTstates() >>> 16);
            z80r[32] = (byte) (z80state.getTstates() >>> 24);
            // ignore the chHoldIntReqCycles & chFlags fields
            z80r[35] = (byte) z80state.getMemPtr();
            z80r[36] = (byte) (z80state.getMemPtr() >>> 8);
            fOut.write(z80r);

            // SZX SPECREGS block
            blockID = "SPCR";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x08);
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);  // Z80R length block (8 bytes)
            byte[] specr = new byte[8];
            specr[0] = (byte) border;
            specr[1] = (byte) last7ffd;
            specr[2] = (byte) last1ffd;
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
                    fOut.write(pageLen >>> 8);
                    fOut.write(pageLen >>> 16);
                    fOut.write(pageLen >>> 24);
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
            fOut.write(0x00);  // KEYB length block (5 bytes)
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
                blockID = "AY\0\0";
                fOut.write(blockID.getBytes("US-ASCII"));
                fOut.write(0x12);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);  // AY length block (18 bytes)
                if (snapshotModel.codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                    fOut.write(ZXSTAYF_128AY);
                } else {
                    fOut.write(0x00);
                }
                fOut.write(ay8912state.getAddressLatch());
                int regAY[] = ay8912state.getRegAY();
                for (int reg = 0; reg < 16; reg++) {
                    fOut.write(regAY[reg]);
                }
            }

            // SZX ULAplus block
            if (ULAplus) {
                blockID = "PLTT";
                fOut.write(blockID.getBytes("US-ASCII"));
                fOut.write(0x42);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);  // PLTT length block (66 bytes)
                if (ULAplusEnabled) {
                    fOut.write(ZXSTPALETTE_ENABLED);
                } else {
                    fOut.write(ZXSTPALETTE_DISABLED);
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

                if (mf128on48k) {
                    fOut.write(ZXSTMFM_128);
                } else {
                    fOut.write(0x00);
                }

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
            if (memory.isIF2RomPaged()) {
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
                fOut.write(pageLen >>> 8);
                fOut.write(pageLen >>> 16);
                fOut.write(pageLen >>> 24);
                pageLen = baos.size();
                fOut.write(pageLen);
                fOut.write(pageLen >>> 8);
                fOut.write(pageLen >>> 16);
                fOut.write(pageLen >>> 24);
                baos.writeTo(fOut);
            }

            // SZX Tape Block
            if (tapeLinked && !tapeEmbedded) {
                blockID = "TAPE";
                fOut.write(blockID.getBytes("US-ASCII"));
                int blockLen = 28 + tapeName.length() + 1;
                fOut.write(blockLen);
                fOut.write(blockLen >>> 8);
                fOut.write(blockLen >>> 16);
                fOut.write(blockLen >>> 24);

                fOut.write(tapeBlock);
                fOut.write(tapeBlock >>> 8); // wCurrentBlockNo
                fOut.write(0x00);
                fOut.write(0x00); // wFlags
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00); // dwUncompressedSize
                blockLen = tapeName.length() + 1;
                fOut.write(blockLen);
                fOut.write(blockLen >>> 8);
                fOut.write(blockLen >>> 16);
                fOut.write(blockLen >>> 24); // dwCompressedSize
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
                fOut.write(blockLen >>> 8);
                fOut.write(blockLen >>> 16);
                fOut.write(blockLen >>> 24);

                fOut.write(tapeBlock);
                fOut.write(tapeBlock >>> 8); // wCurrentBlockNo
                fOut.write(ZXSTTP_EMBEDDED | ZXSTTP_COMPRESSED); // wFlags
                fOut.write(0x00);
                fOut.write(tapeData.length);
                fOut.write(tapeData.length >>> 8);
                fOut.write(tapeData.length >>> 16);
                fOut.write(tapeData.length >>> 24); // dwUncompressedSize
                blockLen = baos.size();
                fOut.write(blockLen);
                fOut.write(blockLen >>> 8);
                fOut.write(blockLen >>> 16);
                fOut.write(blockLen >>> 24); // dwCompressedSize
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

            // IF1 SZX Block
            if (IF1Enabled
                    && snapshotModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
                blockID = "IF1\0";
                fOut.write(blockID.getBytes("US-ASCII"));

                fOut.write(0x28);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);  // IF1 block length (40 bytes without ROM)

                byte if1Flag = ZXSTIF1F_ENABLED;
                if (IF1RomPaged) {
                    if1Flag |= ZXSTIF1F_PAGED;
                }
                fOut.write(if1Flag);
                fOut.write(numDrives);
                byte reserved[] = new byte[38]; // 35 reserved + wRomSize + chRomData[1]
                fOut.write(reserved);
            }

        } catch (IOException ex) {
            error = 6;
            return false;
        } finally {
            try {
                fOut.close();
            } catch (IOException ex) {
                Logger.getLogger(Snapshots.class.getName()).log(Level.SEVERE, null, ex);
                error = 6;
                return false;
            }
        }

        return true;
    }
}
