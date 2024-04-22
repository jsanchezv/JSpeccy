/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

import configuration.JSpeccySettings;
import configuration.MemoryType;
import lombok.extern.slf4j.Slf4j;
import snapshots.MemoryState;
import tv.porst.jhexview.IDataChangedListener;

/**
 *
 * @author jsanchez
 */
@Slf4j
public final class Memory {

    private final int PAGE_SIZE = 0x2000;
    private final byte[][] Rom48k = new byte[2][PAGE_SIZE];
    private final byte[][] IF2Rom = new byte[2][PAGE_SIZE];
    private final byte[][] Rom128k = new byte[4][PAGE_SIZE];
    private final byte[][] RomPlus2 = new byte[4][PAGE_SIZE];
    private final byte[][] RomPlus2a = new byte[8][PAGE_SIZE];
    private final byte[][] RomPlus3 = new byte[8][PAGE_SIZE];
    // ROM del Interfaz I
    private final byte[][] IF1Rom = new byte[1][PAGE_SIZE];
    // 8 páginas de RAM
    private final byte[][] Ram = new byte[16][PAGE_SIZE];
    // RAM falsa para dejar que escriba en páginas de ROM sin afectar a la
    // ROM de verdad. Esto evita tener que comprobar en cada escritura si la
    // página es de ROM o de RAM.
    private final byte[] fakeROM = new byte[PAGE_SIZE];
    // punteros a las 4 páginas
    private final byte[][] readPages = new byte[8][];
    private final byte[][] writePages = new byte[8][];
    // Roms de los Multiface ([0]=MF1, [1]=MF128, [2]=MFPLUS3)
    private final byte[][] mfROM = new byte[3][PAGE_SIZE];
    // Ram del Multiface 8K para todos
    private final byte[] mfRAM = new byte[PAGE_SIZE];
    // Ram del la expansión de memoria LEC
    private byte[][] lecRam;
    // Número de página de RAM de donde sale la pantalla activa
    private int screenPage;
    private int highPage, bankM, bankP, portFD, activePageLEC;
    private boolean IF1RomPaged, IF2RomPaged;
    private boolean model128k, pagingLocked, plus3RamMode;
    private boolean multifacePaged, multifaceLocked;
    private MachineTypes spectrumModel;
    private final JSpeccySettings settings;
    private final Random random;

    public Memory(JSpeccySettings memSettings) {
        spectrumModel = null;
        settings = memSettings;
        random = new Random();
        loadRoms();
        IF2RomPaged = false;
        reset(MachineTypes.SPECTRUM48K);
    }

    public MachineTypes getSpectrumModel() {
        return spectrumModel;
    }

    public MemoryState getMemoryState() {
        MemoryState state = new MemoryState();

        switch (spectrumModel) {
            case SPECTRUM16K:
                state.setPageRam(5, savePage(5));
                break;
            case SPECTRUM48K:
                state.setPageRam(5, savePage(5));
                state.setPageRam(2, savePage(2));
                state.setPageRam(0, savePage(0));
                if (lecRam != null) {
                    state.setPortFD(portFD);
                    for (int page = 0; page < 16; page++) {
                        state.setLecPageRam(page, savePageLec(page));
                    }
                }
                break;
            default:
                for (int page = 0; page < 8; page++) {
                    state.setPageRam(page, savePage(page));
                }
        }

        state.setIF2RomPaged(IF2RomPaged);
        if (IF2RomPaged) {
            state.setIF2Rom(saveIF2Rom());
        }

        state.setMf128on48k(settings.getSpectrumSettings().isMf128On48K());
        state.setMultifaceRam(saveMFRam());
        state.setMultifacePaged(multifacePaged);
        state.setMultifaceLocked(multifaceLocked);

        state.setIF1RomPaged(IF1RomPaged);

        return state;
    }

    public void setMemoryState(MemoryState state) {

        if (IF2RomPaged) {
            extractIF2Rom();
        }

        switch (spectrumModel) {
            case SPECTRUM16K:
                loadPage(5, state.getPageRam(5));
                break;
            case SPECTRUM48K:
                loadPage(5, state.getPageRam(5));
                loadPage(2, state.getPageRam(2));
                loadPage(0, state.getPageRam(0));
                if (state.getLecPageRam(0) != null) {
                    setConnectedLEC(true);
                    for (int page = 0; page < 16; page++) {
                        loadPageLec(page, state.getLecPageRam(page));
                 }
                } else {
                    setConnectedLEC(false);
                }
                break;
            default:
                for (int page = 0; page < 8; page++) {
                    loadPage(page, state.getPageRam(page));
                }
        }

        if (state.isIF2RomPaged() && spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
            loadIF2Rom(state.getIF2Rom());
            readPages[0] = IF2Rom[0];
            readPages[1] = IF2Rom[1];
        }

        settings.getSpectrumSettings().setMf128On48K(state.isMf128on48k());
        if (state.getMultifaceRam() != null) {
            loadMFRam(state.getMultifaceRam());
        }

        if (state.isMultifacePaged()) {
            pageMultiface();
        }

        multifaceLocked = state.isMultifaceLocked();

        if (spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3 && state.isIF1RomPaged()) {
            pageIF1Rom();
        }
    }

    public byte readScreenByte(int address) {
        return Ram[screenPage][address];
    }

    public byte readByte(int address) {
        return readPages[address >>> 13][address & 0x1fff];
    }

    public void writeByte(int address, byte value) {
        writePages[address >>> 13][address & 0x1fff] = value;
    }

    public byte readByte(int page, int address) {
        page <<= 1;
        if (address < PAGE_SIZE) {
            return Ram[page][address];
        } else {
            return Ram[page + 1][address & 0x1fff];
        }
    }

    public void writeByte(int page, int address, byte value) {
        page <<= 1;
        if (address < PAGE_SIZE) {
            Ram[page][address] = value;
        } else {
            Ram[page + 1][address & 0x1fff] = value;
        }
    }

    public void loadPage(int page, byte[] buffer) {
        page <<= 1;
        System.arraycopy(buffer, 0, Ram[page], 0, PAGE_SIZE);
        System.arraycopy(buffer, PAGE_SIZE, Ram[page + 1], 0, PAGE_SIZE);
    }

    public byte[] savePage(int page) {
        byte[] buffer = new byte[PAGE_SIZE * 2];
        page <<= 1;
        System.arraycopy(Ram[page], 0, buffer, 0, PAGE_SIZE);
        System.arraycopy(Ram[page + 1], 0, buffer, PAGE_SIZE, PAGE_SIZE);
        return buffer;
    }

    public void loadMFRam(byte[] buffer) {
        System.arraycopy(buffer, 0, mfRAM, 0, mfRAM.length);
    }

    public byte[] saveMFRam() {
        byte[] buffer = new byte[PAGE_SIZE];
        System.arraycopy(mfRAM, 0, buffer, 0, buffer.length);
        return buffer;
    }

    public void loadIF2Rom(byte[] rom) {
        System.arraycopy(rom, 0, IF2Rom[0], 0, IF2Rom[0].length);
        System.arraycopy(rom, PAGE_SIZE, IF2Rom[1], 0, IF2Rom[1].length);
        IF2RomPaged = true;
    }

    public byte[] saveIF2Rom() {
        byte[] buffer = new byte[PAGE_SIZE * 2];
        System.arraycopy(IF2Rom[0], 0, buffer, 0, IF2Rom[0].length);
        System.arraycopy(IF2Rom[1], 0, buffer, PAGE_SIZE, IF2Rom[1].length);
        return buffer;
    }

    public void loadPageLec(int page, byte[] ram) {
        Objects.requireNonNull(lecRam, "No reserved LEC ram pages!");

        page <<= 2;
        System.arraycopy(ram, 0, lecRam[page], 0, lecRam[0].length);
        System.arraycopy(ram, 0x2000, lecRam[page + 1], 0, lecRam[0].length);
        System.arraycopy(ram, 0x4000, lecRam[page + 2], 0, lecRam[0].length);
        System.arraycopy(ram, 0x6000, lecRam[page + 3], 0, lecRam[0].length);
    }

    public byte[] savePageLec(int page) {
        Objects.requireNonNull(lecRam, "No reserved LEC ram pages!");

        byte[] ram = new byte[0x8000];

        page <<= 2;
        System.arraycopy(lecRam[page], 0, ram, 0, lecRam[0].length);
        System.arraycopy(lecRam[page + 1], 0, ram, 0x2000, lecRam[0].length);
        System.arraycopy(lecRam[page + 2], 0, ram, 0x4000, lecRam[0].length);
        System.arraycopy(lecRam[page + 3], 0, ram, 0x6000, lecRam[0].length);

        return ram;
    }

    private void setMemoryMap16k() {
        if (IF2RomPaged) {
            readPages[0] = IF2Rom[0];
            readPages[1] = IF2Rom[1];
        } else {
            readPages[0] = Rom48k[0];
            readPages[1] = Rom48k[1];
        }
        writePages[0] = writePages[1] = fakeROM;

        readPages[2] = writePages[2] = Ram[10]; // Página 5
        readPages[3] = writePages[3] = Ram[11];

        readPages[4] = readPages[5] = Ram[4];
        readPages[6] = readPages[7] = Ram[4];
        writePages[4] = writePages[5] = fakeROM;
        writePages[6] = writePages[7] = fakeROM;
        Arrays.fill(Ram[4], (byte) 0xff);
        screenPage = 10;
        model128k = false;
    }

    private void setMemoryMap48k() {
        if (IF2RomPaged) {
            readPages[0] = IF2Rom[0];
            readPages[1] = IF2Rom[1];
        } else {
            readPages[0] = Rom48k[0];
            readPages[1] = Rom48k[1];
        }
        writePages[0] = writePages[1] = fakeROM;

        readPages[2] = writePages[2] = Ram[10]; // Página 5
        readPages[3] = writePages[3] = Ram[11];

        readPages[4] = writePages[4] = Ram[4];  // Página 2
        readPages[5] = writePages[5] = Ram[5];

        readPages[6] = writePages[6] = Ram[0];  // Página 0
        readPages[7] = writePages[7] = Ram[1];
        screenPage = 10;
        model128k = false;
    }

    private void setMemoryMap128k() {
        if (IF2RomPaged) {
            readPages[0] = IF2Rom[0];
            readPages[1] = IF2Rom[1];
        } else {
            readPages[0] = Rom128k[0];
            readPages[1] = Rom128k[1];
        }
        writePages[0] = writePages[1] = fakeROM;

        readPages[2] = writePages[2] = Ram[10]; // Página 5
        readPages[3] = writePages[3] = Ram[11];

        readPages[4] = writePages[4] = Ram[4];  // Página 2
        readPages[5] = writePages[5] = Ram[5];

        readPages[6] = writePages[6] = Ram[0];  // Página 0
        readPages[7] = writePages[7] = Ram[1];

        screenPage = 10;
        highPage = 0;
        model128k = true;
        bankM = 0;
    }

    private void setMemoryMapPlus2() {
        if (IF2RomPaged) {
            readPages[0] = IF2Rom[0];
            readPages[1] = IF2Rom[1];
        } else {
            readPages[0] = RomPlus2[0];
            readPages[1] = RomPlus2[1];
        }
        writePages[0] = writePages[1] = fakeROM;

        readPages[2] = writePages[2] = Ram[10]; // Página 5
        readPages[3] = writePages[3] = Ram[11];

        readPages[4] = writePages[4] = Ram[4];  // Página 2
        readPages[5] = writePages[5] = Ram[5];

        readPages[6] = writePages[6] = Ram[0];  // Página 0
        readPages[7] = writePages[7] = Ram[1];

        screenPage = 10;
        highPage = 0;
        model128k = true;
        bankM = 0;
    }

    private void setMemoryMapPlus3() {
        if (spectrumModel == MachineTypes.SPECTRUMPLUS3) {
            readPages[0] = RomPlus3[0];
            readPages[1] = RomPlus3[1];
        } else {
            readPages[0] = RomPlus2a[0];
            readPages[1] = RomPlus2a[1];
        }
        writePages[0] = writePages[1] = fakeROM;

        readPages[2] = writePages[2] = Ram[10]; // Página 5
        readPages[3] = writePages[3] = Ram[11];

        readPages[4] = writePages[4] = Ram[4];  // Página 2
        readPages[5] = writePages[5] = Ram[5];

        readPages[6] = writePages[6] = Ram[0];  // Página 0
        readPages[7] = writePages[7] = Ram[1];

        screenPage = 10;
        highPage = 0;
        model128k = true;
        bankM = bankP = 0;
    }

    /*
     * Bits 0-2: RAM page (0-7) to map into memory at 0xc000.
     * Bit 3: Select normal (0) or shadow (1) screen to be displayed.
     *        The normal screen is in bank 5, whilst the shadow screen is in bank 7.
     *        Note that this does not affect the memory between 0x4000 and 0x7fff,
     *        which is always bank 5.
     * Bit 4: ROM select. ROM 0 is the 128k editor and menu system; ROM 1 contains 48K BASIC.
     * Bit 5: If set, memory paging will be disabled and further output to this
     *        port will be ignored until the computer is reset.
     */
    public void setPort7ffd(int port7ffd) {

//        System.out.println(String.format("Port 0x7ffd: %02x", port7ffd));
        if (pagingLocked) {
            return;
        }

        bankM = port7ffd & 0xff;

        // Set the active screen
        screenPage = (port7ffd & 0x08) == 0 ? 10 : 14;

        // Si el +3 está en modo "all RAM" no se conmuta ROM ni RAM
        if (plus3RamMode) {
            return;
        }

        // Set the high page
        highPage = port7ffd & 0x07;
        readPages[6] = writePages[6] = Ram[highPage << 1];
        readPages[7] = writePages[7] = Ram[(highPage << 1) + 1];

        // Si está funcionando el IF1, el IF2 o el MF, no tocar la ROM
        if (IF1RomPaged || multifacePaged
                || (IF2RomPaged && spectrumModel.codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3)) {
            return;
        }

        // Set the active ROM
        switch (spectrumModel) {
            case SPECTRUM128K:
                if ((port7ffd & 0x10) == 0) {
                    readPages[0] = Rom128k[0];
                    readPages[1] = Rom128k[1];
                } else {
                    readPages[0] = Rom128k[2];
                    readPages[1] = Rom128k[3];
                }
                break;
            case SPECTRUMPLUS2:
                if ((port7ffd & 0x10) == 0) {
                    readPages[0] = RomPlus2[0];
                    readPages[1] = RomPlus2[1];
                } else {
                    readPages[0] = RomPlus2[2];
                    readPages[1] = RomPlus2[3];
                }
                break;
            case SPECTRUMPLUS2A:
            case SPECTRUMPLUS3:
                doPagingPlus3();
                break;
        }

        // Set the page locking state
        pagingLocked = (port7ffd & 0x20) != 0;
    }

    /*
     * Bit 0: Paging mode. 0=normal, 1=special
     * Bit 1: In normal mode, ignored.
     * Bit 2: In normal mode, high bit of ROM selection. The four ROMs are:
     *        ROM 0: 128k editor, menu system and self-test program
     *        ROM 1: 128k syntax checker
     *        ROM 2: +3DOS
     *        ROM 3: 48 BASIC
     * Bit 3: Disk motor; 1=on, 0=off
     * Bit 4: Printer port strobe.
     */
    public void setPort1ffd(int port1ffd) {
//        System.out.println(String.format("Port 0x1ffd: %02x", port1ffd));
        if (pagingLocked) {
            return;
        }

        bankP = port1ffd & 0x07;

        doPagingPlus3();
    }

    private void doPagingPlus3() {

        // Normal paging mode (bit0 of 0x1ffd to 0)
        if ((bankP & 0x01) == 0) {

            if (multifacePaged) {
                return;
            }

            int rom = ((bankM & 0x10) >>> 3) | (bankP & 0x04);

            if (spectrumModel == MachineTypes.SPECTRUMPLUS3) {
                readPages[0] = RomPlus3[rom];
                readPages[1] = RomPlus3[rom + 1];
            } else {
                readPages[0] = RomPlus2a[rom];
                readPages[1] = RomPlus2a[rom + 1];
            }
            writePages[0] = writePages[1] = fakeROM;
            // Si estamos en Plus3RamMode es que estamos saliendo del modo
            // "all RAM" y hay que reponer las páginas a su lugar
            if (plus3RamMode) {
                readPages[2] = writePages[2] = Ram[10];
                readPages[3] = writePages[3] = Ram[11];

                readPages[4] = writePages[4] = Ram[4];
                readPages[5] = writePages[5] = Ram[5];

                highPage = bankM & 0x07;
                readPages[6] = writePages[6] = Ram[highPage * 2];
                readPages[7] = writePages[7] = Ram[highPage * 2 + 1];
                plus3RamMode = false;
            }
        } else {
            // Special paging mode (all RAM pages (bit0 of 0x1ffd to 1))
            plus3RamMode = true;
            switch (bankP & 0x06) {
                case 0:
                    readPages[0] = writePages[0] = Ram[0]; // Page 0
                    readPages[1] = writePages[1] = Ram[1];

                    readPages[2] = writePages[2] = Ram[2]; // Page 1
                    readPages[3] = writePages[3] = Ram[3];

                    readPages[4] = writePages[4] = Ram[4]; // Page 2
                    readPages[5] = writePages[5] = Ram[5];

                    readPages[6] = writePages[6] = Ram[6]; // Page 3
                    readPages[7] = writePages[7] = Ram[7];
                    highPage = 3;
                    break;
                case 2:
                    readPages[0] = writePages[0] = Ram[8]; // Page 4
                    readPages[1] = writePages[1] = Ram[9];

                    readPages[2] = writePages[2] = Ram[10]; // Page 5
                    readPages[3] = writePages[3] = Ram[11];

                    readPages[4] = writePages[4] = Ram[12]; // Page 6
                    readPages[5] = writePages[5] = Ram[13];

                    readPages[6] = writePages[6] = Ram[14]; // Page 7
                    readPages[7] = writePages[7] = Ram[15];
                    highPage = 7;
                    break;
                case 4:
                    readPages[0] = writePages[0] = Ram[8]; // Page 4
                    readPages[1] = writePages[1] = Ram[9];

                    readPages[2] = writePages[2] = Ram[10]; // Page 5
                    readPages[3] = writePages[3] = Ram[11];

                    readPages[4] = writePages[4] = Ram[12]; // Page 6
                    readPages[5] = writePages[5] = Ram[13];

                    readPages[6] = writePages[6] = Ram[6]; // Page 3
                    readPages[7] = writePages[7] = Ram[7];
                    highPage = 3;
                    break;
                case 6:
                    readPages[0] = writePages[0] = Ram[8]; // Page 4
                    readPages[1] = writePages[1] = Ram[9];

                    readPages[2] = writePages[2] = Ram[14]; // Page 7
                    readPages[3] = writePages[3] = Ram[15];

                    readPages[4] = writePages[4] = Ram[12]; // Page 6
                    readPages[5] = writePages[5] = Ram[13];

                    readPages[6] = writePages[6] = Ram[6]; // Page 3
                    readPages[7] = writePages[7] = Ram[7];
                    highPage = 3;
                    break;
            }
        }
    }

    public int getPlus3HighPage() {
        return highPage;
    }

    public boolean isPlus3RamMode() {
        return plus3RamMode;
    }

    public boolean isPagingLocked() {
        return pagingLocked;
    }

    public boolean isSpectrumRom() {
        if (multifacePaged || IF1RomPaged || IF2RomPaged) {
            return false;
        }

        boolean res = false;
        switch (spectrumModel.codeModel) {
            case SPECTRUM48K:
                res = !isLecPaged();
                break;
            case SPECTRUM128K:
                res = (bankM & 0x10) != 0;
                break;
            case SPECTRUMPLUS3:
                if (!plus3RamMode) {
                    res = (((bankM & 0x10) >>> 3) | (bankP & 0x04)) == 6;
                }
        }
        return res;
    }

    // "La Abadía del Crimen" pone la página 7 en 0xC000 y selecciona la
    // pantalla de la página 7. A partir de ahí, la modifica en 0xC000 en
    // lugar de hacerlo en 0x4000, donde siempre está la página 5.
    // Livingstone Supongo II, versión +2A/+3, usa las combinaciones "all RAM"
    // de páginas 4, 5, 6, 3 y 4, 7, 6, 3.
    public boolean isScreenByte(int addr) {
        if (plus3RamMode) {
            switch (bankP & 0x06) {
                case 0: // Pages 0, 1, 2, 3
                    return false;
                case 4: // Pages 4, 5, 6, 3
                    return (addr > 0x3FFF && addr < 0x5B00 && screenPage == 10);
                case 6: // Pages 4, 7, 6, 3
                    return (addr > 0x3FFF && addr < 0x5B00 && screenPage == 14);
            }
        }

        // El caso de todo RAM con páginas 4, 5, 6, 7 cae aquí
        switch (addr >>> 13) {
            case 2: // Address 0x4000-0x5fff
                return (addr < 0x5B00 && screenPage == 10);
            case 6: // Address 0xc000-0xdfff
                return (addr < 0xDB00 && (highPage << 1) == screenPage);
        }

        return false;
    }

    public boolean isScreenByteModified(int address, byte value) {
        return (readPages[address >>> 13][address & 0x1fff] != value
            && isScreenByte(address));
    }

    public boolean isModel128k() {
        return model128k;
    }

    public void reset(MachineTypes model) {

        multifacePaged = false;
        IF1RomPaged = false;
        multifaceLocked = true;
        pagingLocked = plus3RamMode = false;
        portFD = activePageLEC = 0;

        if (spectrumModel != model) {
            spectrumModel = model;

            for (byte[] Ram1 : Ram) {
                random.nextBytes(Ram1);
            }
            random.nextBytes(mfRAM);
        }

        switch (spectrumModel) {
            case SPECTRUM16K:
                setMemoryMap16k();
                break;
            case SPECTRUM48K:
                setMemoryMap48k();
                break;
            case SPECTRUM128K:
                setMemoryMap128k();
                break;
            case SPECTRUMPLUS2:
                setMemoryMapPlus2();
                break;
            case SPECTRUMPLUS2A:
            case SPECTRUMPLUS3:
                setMemoryMapPlus3();
        }
    }

    /*
     * Existieron, básicamente, 3 modelos de Multiface, versión Spectrum:
     * el Multiface 1, para el Spectrum 48k, el Multiface 128, para el 128k/+2
     * y el Multiface 3, para el Spectrum +2A/+3. El MF128 podía funcionar
     * también en el 16k/48k y el MF1 podía funcionar en el 128k en el modo 48k
     * con paginación bloqueada. El MF3 solo funcionaba los +2A/+3.
     *
     * Todos tienen 8 KB de ROM que se paginan entre las direcciones 0x0000-0x1FFF
     * y 8 KB de RAM que se paginan entre 0x2000-0x3FFF. De esta forma, sustituyen
     * a la ROM del Spectrum en dos momentos principalmente:
     *
     * 1.- Cuando se pulsa el botón rojo del Multiface. Con ello se provoca una
     *     NMI a la vez que mediante la señal del bus de expansión ROMCS se deja
     *     a la ROM original en estado de alta impedancia. La NMI ejecuta entonces
     *     la rutina en 0x0066 de la ROM del Multiface. En el 128K se inhabilita
     *     el chip de la ROM (32 KB) y, por tanto, da igual cual de ellas estuviera
     *     paginada. En el +2A/+3 hay dos chips de ROM físicas (32+32 KB) y en el
     *     bus, en lugar de una señal ROMCS hay dos llamadas ROM 1 OE y ROM 2 OE. En
     *     este caso, el fallo es que el +3 tiene modos "all RAM" en los cuales no hay
     *     ROM que inhabilitar (si lo hay, pero da igual porque no se usan) y el
     *     MF3 no funciona.
     *     Además, en el MF128 y en el MF3 hay un "switch" interno que sirve de bloqueo
     *     del aparato, para que éste no sea detectado por otro hardware (excusa fácil)
     *     ni por el software. Este bloqueo se desactiva SIEMPRE al pulsar el botón rojo.
     *
     * 2.- Por programación, siempre que el aparato (MF128/MF3) no esté en estado de bloqueo.
     *     (Versiones posteriores del MF1 incorporaban un switch físico).
     *     Cada modelo de MF tiene un puerto de activación y otro de desactivación
     *     (ambos diferentes entre todos ellos). El método consiste en leer de un puerto
     *     determinado, lo que provoca la paginación/despaginación del aparato. La
     *     relación de puertos es:
     *
     *     * Multiface One
     *       IN del puerto 0x9f, pagina el MF
     *       IN del puerto 0x1f, despagina el MF
     *
     *     * Multiface 128
     *       IN del puerto 0xbf, pagina el MF128
     *       IN del puerto 0x3f, despagina el MF128
     *       IN del puerto 0x01bf, lectura del último valor enviado el puerto 0x7ffd
     *
     *     * Multiface 3
     *       IN del puerto 0x3f, pagina el MF3
     *       IN del puerto 0xbf, despagina el MF3
     *       IN del puerto 0x1f3f, lectura del último valor enviado al puerto 0x1ffd
     *       IN del puerto 0x7f3f, lectura del último valor enviado el puerto 0x7ffd
     *
     *     Los dos últimos puertos del MF3, solo son legibles cuando el aparato esté
     *     desbloqueado y, además, esté paginada la ROM del MF3. Si no, devuelven
     *     0xFF como todo puerto que no esté asignado.
     *
     *     El MF128 y el MF3 controlan el "switch" de bloqueo con una escritura (OUT) al
     *     puerto 0x3f para activar el bloqueo y con un OUT al puerto 0xbf para desactivar
     *     el bloqueo (es así como funciona la opción de ON/OFF del menú del MF). Esta
     *     escritura funciona siempre que esté paginada la ROM del MF, el bloqueo
     *     desactivado y, supuestamente, pero no lo he comprobado, que el PC esté
     *     en una dirección por debajo de 0x4000.
     *     El valor enviado al puerto parece que es indiferente. El amigo Woodster, del
     *     canal SPIN, me dijo el 12/09/2010:
     *
     *     [1:39pm] <Woodster> from the MF3 ROM when 'returning':
     *     [1:39pm] <Woodster>         LD   A,(#2006)
     *     [1:39pm] <Woodster>         BIT  2,A
     *     [1:39pm] <Woodster>         JR   Z,L06E6
     *     [1:39pm] <Woodster>         OUT  (#3F),A
     *     [1:39pm] <Woodster>         JR   #06E8
     *     [1:39pm] <Woodster> L06E6:  OUT  (#BF),A
     *
     *     La mayoría de información acerca de los detalles no evidentes proviene de él.
     *     Thanks Woodster. :)
     *
     *     http://www.worldofspectrum.org/forums/showthread.php?t=12294
     *
     */
    public void pageMultiface() {
        if (multifacePaged || plus3RamMode) {
            return;
        }

        multifacePaged = true;
//        System.out.println("Multiface paged IN");
        switch (spectrumModel.codeModel) {
            case SPECTRUM48K:
                if (settings.getSpectrumSettings().isMf128On48K()) {
                    readPages[0] = mfROM[1];
                } else {
                    readPages[0] = mfROM[0];
                }
                break;
            case SPECTRUM128K:
                readPages[0] = mfROM[1];
                break;
            case SPECTRUMPLUS3:
                readPages[0] = mfROM[2];
                break;
        }
        readPages[1] = writePages[1] = mfRAM;
    }

    /*
     * Al despaginar los MF128/MFP3 hay que tener en cuenta que la
     * paginacion puede estar bloqueada (bit 5 de 0x7ffd). En ese
     * caso, la ROM que entra es, por obligación, la del Spectrum 48k
     * (ROM1 para el 128k/+2 y ROM3 para el +2A/+3.
     */
    public void unpageMultiface() {
        if (!multifacePaged) {
            return;
        }

        multifacePaged = false;

        writePages[0] = writePages[1] = fakeROM;

        if (IF2RomPaged) {
            readPages[0] = IF2Rom[0];
            readPages[1] = IF2Rom[1];
            return;
        }

        if (IF1RomPaged) {
            readPages[0] = readPages[1] = IF1Rom[0];
            return;
        }

//        System.out.println("Multiface paged OUT");
        switch (spectrumModel) {
            case SPECTRUM16K:
            case SPECTRUM48K:
                readPages[0] = Rom48k[0];
                readPages[1] = Rom48k[1];
                break;
            case SPECTRUM128K:
                if (pagingLocked) {
                    readPages[0] = Rom128k[2];
                    readPages[1] = Rom128k[3];
                } else {
                    setPort7ffd(bankM);
                }
                break;
            case SPECTRUMPLUS2:
                if (pagingLocked) {
                    readPages[0] = RomPlus2[2];
                    readPages[1] = RomPlus2[3];
                } else {
                    setPort7ffd(bankM);
                }
                break;
            case SPECTRUMPLUS2A:
            case SPECTRUMPLUS3:
                if (pagingLocked) {
                    readPages[0] = RomPlus3[6];
                    readPages[1] = RomPlus3[7];
                } else {
                    setPort7ffd(bankM);
                }
                break;
        }
    }

    public boolean isMultifacePaged() {
        return multifacePaged;
    }

    public void setMultifaceLocked(boolean state) {
        multifaceLocked = state;
    }

    public boolean isMultifaceLocked() {
        return multifaceLocked;
    }

    public boolean insertIF2Rom(File filename) {
        if (!loadIF2Rom(filename)) {
            return false;
        }

        IF2RomPaged = true;
        return true;
    }

    public void extractIF2Rom() {
        IF2RomPaged = false;
    }

    public boolean isIF2RomPaged() {
        return IF2RomPaged;
    }

    /*
     * La ROM del IF1 es de 8K y cuando es paginada replica las direcciones
     * entre 0x0000-0x1FFF en 0x2000-0x3FFF. Gracias a mcleod_ideafix por
     * investigarlo. :)
     */
    public void pageIF1Rom() {
        if (IF1RomPaged) {
            return;
        }

        IF1RomPaged = true;
        readPages[0] = readPages[1] = IF1Rom[0];
        writePages[0] = writePages[1] = fakeROM;
    }

    public void unpageIF1Rom() {
        if (!IF1RomPaged) {
            return;
        }

        IF1RomPaged = false;

        if (IF2RomPaged) {
            readPages[0] = IF2Rom[0];
            readPages[1] = IF2Rom[1];
            writePages[0] = writePages[1] = fakeROM;
            return;
        }

        if (multifacePaged) {
            switch (spectrumModel.codeModel) {
                case SPECTRUM48K:
                    if (settings.getSpectrumSettings().isMf128On48K()) {
                        readPages[0] = mfROM[1];
                    } else {
                        readPages[0] = mfROM[0];
                    }
                    break;
                case SPECTRUM128K:
                    readPages[0] = mfROM[1];
                    break;
                case SPECTRUMPLUS3:
                    readPages[0] = mfROM[2];
                    break;
            }
            readPages[1] = writePages[1] = mfRAM;
            return;
        }

        writePages[0] = writePages[1] = fakeROM;
        switch (spectrumModel) {
            case SPECTRUM16K:
            case SPECTRUM48K:
                readPages[0] = Rom48k[0];
                readPages[1] = Rom48k[1];
                break;
            case SPECTRUM128K:
                if (pagingLocked) {
                    readPages[0] = Rom128k[2];
                    readPages[1] = Rom128k[3];
                } else {
                    setPort7ffd(bankM);
                }
                break;
            case SPECTRUMPLUS2:
                if (pagingLocked) {
                    readPages[0] = RomPlus2[2];
                    readPages[1] = RomPlus2[3];
                } else {
                    setPort7ffd(bankM);
                }
                break;
        }
    }

    public boolean isIF1RomPaged() {
        return IF1RomPaged;
    }

    public void setConnectedLEC(boolean state) {
        if (state && lecRam != null) {
            return;
        }

        if (state) {
            lecRam = new byte[64][];
                for (int page = 0; page < 60; page++) {
                    lecRam[page] = new byte[PAGE_SIZE];
                }

                lecRam[60] = Ram[4]; // Page 2
                lecRam[61] = Ram[5];
                lecRam[62] = Ram[0]; // Page 0
                lecRam[63] = Ram[1];
        } else {
            lecRam = null;
        }
    }

    public boolean isConnectedLEC() {
        return lecRam != null;
    }

    public void setPortFD(int value) {
        if (lecRam == null) {
            return;
        }

        portFD = value;
        if ((value & 0x80) == 0) {
//            System.out.println("LEC unpaged!");

            activePageLEC = 0;

            setMemoryMap48k();

            if (IF1RomPaged) {
                IF1RomPaged = false;
                pageIF1Rom();
            }

            if (multifacePaged) {
                multifacePaged = false;
                pageMultiface();
            }
        } else {

            value &= 0x78;

            // The page # is in b3b6b5b4
            activePageLEC = (((value & 0x70) >>> 4) | (value & 0x08)) & 0x0f;

//            System.out.println("LEC page " + activePageLEC);

            value = activePageLEC <<= 2;
            readPages[0] = writePages[0] = lecRam[value++];
            readPages[1] = writePages[1] = lecRam[value++];
            readPages[2] = writePages[2] = lecRam[value++];
            readPages[3] = writePages[3] = lecRam[value];
        }
    }

    /**
     * @return the lecPaged
     */
    public boolean isLecPaged() {
        return (portFD & 0x80) != 0;
    }

    /**
     * @return the pageLEC selected
     */
    public int getPortFD() {
        return portFD;
    }

    public void loadRoms() {
        MemoryType conf = settings.getMemorySettings();
        String romsDirectory = conf.getRomsDirectory();

        if (!romsDirectory.isEmpty() && !romsDirectory.endsWith("/")) {
            romsDirectory += "/";
        }

        if (!loadRomAsFile(romsDirectory + conf.getRom48K(), Rom48k, 0, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/spectrum.rom", Rom48k, 0, PAGE_SIZE * 2);
        }

        if (!loadRomAsFile(romsDirectory + conf.getRomIF1(), IF1Rom, 0, PAGE_SIZE)) {
            loadRomAsResource("/roms/if1.rom", IF1Rom, 0, PAGE_SIZE);
        }

        if (!loadRomAsFile(romsDirectory + conf.getRom128K0(), Rom128k, 0, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/128-0.rom", Rom128k, 0, PAGE_SIZE * 2);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRom128K1(), Rom128k, 2, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/128-1.rom", Rom128k, 2, PAGE_SIZE * 2);
        }

        if (!loadRomAsFile(romsDirectory + conf.getRomPlus20(), RomPlus2, 0, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus2-0.rom", RomPlus2, 0, PAGE_SIZE * 2);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus21(), RomPlus2, 2, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus2-1.rom", RomPlus2, 2, PAGE_SIZE * 2);
        }

        if (!loadRomAsFile(romsDirectory + conf.getRomPlus2A0(), RomPlus2a, 0, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus2a-0.rom", RomPlus2a, 0, PAGE_SIZE * 2);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus2A1(), RomPlus2a, 2, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus2a-1.rom", RomPlus2a, 2, PAGE_SIZE * 2);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus2A2(), RomPlus2a, 4, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus2a-2.rom", RomPlus2a, 4, PAGE_SIZE * 2);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus2A3(), RomPlus2a, 6, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus2a-3.rom", RomPlus2a, 6, PAGE_SIZE * 2);
        }

        if (!loadRomAsFile(romsDirectory + conf.getRomPlus30(), RomPlus3, 0, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus3-0.rom", RomPlus3, 0, PAGE_SIZE * 2);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus31(), RomPlus3, 2, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus3-1.rom", RomPlus3, 2, PAGE_SIZE * 2);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus32(), RomPlus3, 4, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus3-2.rom", RomPlus3, 4, PAGE_SIZE * 2);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus33(), RomPlus3, 6, PAGE_SIZE * 2)) {
            loadRomAsResource("/roms/plus3-3.rom", RomPlus3, 6, PAGE_SIZE * 2);
        }

        if (!loadRomAsFile(romsDirectory + conf.getRomMF1(), mfROM, 0, PAGE_SIZE)) {
            loadRomAsResource("/roms/mf1.rom", mfROM, 0, PAGE_SIZE);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomMF128(), mfROM, 1, PAGE_SIZE)) {
            loadRomAsResource("/roms/mf128.rom", mfROM, 1, PAGE_SIZE);
        }
        if (!loadRomAsFile(romsDirectory + conf.getRomMFPlus3(), mfROM, 2, PAGE_SIZE)) {
            loadRomAsResource("/roms/mfplus3.rom", mfROM, 2, PAGE_SIZE);
        }
    }

    private boolean loadRomAsResource(String filename, byte[][] rom, int page, int size) {

        boolean res = false;

        try (InputStream inRom = Spectrum.class.getResourceAsStream(filename)) {
            if (inRom == null) {
                String msg = java.util.ResourceBundle.getBundle("machine/Bundle").getString("RESOURCE_ROM_ERROR");
                log.warn("{}: {}", msg, filename);
                return false;
            }

            for (int frag = 0; frag < size / PAGE_SIZE; frag++) {
                int count = 0;
                while (count != -1 && count < PAGE_SIZE) {
                    count += inRom.read(rom[page + frag], count, PAGE_SIZE - count);
                }

                if (count != PAGE_SIZE) {
                    String msg = java.util.ResourceBundle.getBundle("machine/Bundle").getString("ROM_SIZE_ERROR");
                    log.error("{}: {}", msg, filename);
                } else {
                    res = true;
                }
            }
        } catch (IOException ex) {
            String msg = java.util.ResourceBundle.getBundle("machine/Bundle").getString("RESOURCE_ROM_ERROR");
            log.error("{}: {}", msg, filename, ex);
        }

        if (res) {
            String msg = java.util.ResourceBundle.getBundle("machine/Bundle").getString("ROM_RESOURCE_LOADED");
            log.info("{}: {}", msg, filename);
        }

        return res;
    }

    private boolean loadRomAsFile(String filename, byte[][] rom, int page, int size) {
        boolean res = false;

        try (BufferedInputStream fIn = new BufferedInputStream(new FileInputStream(filename))) {

            for (int frag = 0; frag < size / PAGE_SIZE; frag++) {
                int count = 0;
                while (count != -1 && count < PAGE_SIZE) {
                    count += fIn.read(rom[page + frag], count, PAGE_SIZE - count);
                }

                if (count != PAGE_SIZE) {
                    String msg = java.util.ResourceBundle.getBundle("machine/Bundle").getString("ROM_SIZE_ERROR");
                    log.error("{}: {}", msg, filename);
                } else {
                    res = true;
                }
            }
        } catch (FileNotFoundException ex) {
                String msg = java.util.ResourceBundle.getBundle("machine/Bundle").getString("FILE_ROM_ERROR");
                log.warn("{}: {}", msg, filename);
                return false;
        } catch (IOException ex) {
            String msg = java.util.ResourceBundle.getBundle("machine/Bundle").getString("FILE_ROM_ERROR");
            log.error("{}: {}", msg, filename);
        }

        if (res) {
            String msg = java.util.ResourceBundle.getBundle("machine/Bundle").getString("ROM_FILE_LOADED");
            log.info("{}: {}", msg, filename);
        }

        return res;
    }

    private boolean loadIF2Rom(File filename) {

        try (BufferedInputStream fIn = new BufferedInputStream(new FileInputStream(filename))) {

            if (fIn.available() > 0x4000) {
                return false;
            }

            Arrays.fill(IF2Rom[0], (byte) 0xff);
            Arrays.fill(IF2Rom[1], (byte) 0xff);
            int readed = fIn.read(IF2Rom[0], 0, 0x2000);
            if (readed == -1) {
                return false;
            }

            if (readed < 0x2000) {
                return true;
            }

            if (fIn.available() > 0) {
                readed = fIn.read(IF2Rom[1], 0, 0x2000);

                if (readed == -1) {
                    return false;
                }
            }

        } catch (FileNotFoundException ex) {
            log.error("{}", filename, ex);
            return false;
        } catch (IOException ex) {
            log.error("{}", filename, ex);
        }

        return true;
    }

    private int pageModeBrowser = 0;  // RAM Page = 0-7, Lineal Mode >= 8
    public void setPageModeBrowser(int page) {
        pageModeBrowser = page;
    }

    private MemoryDataProvider memoryDataProvider;
    public MemoryDataProvider getMemoryDataProvider() {

        if (memoryDataProvider == null) {
            memoryDataProvider =  new MemoryDataProvider();
        }

        return memoryDataProvider;
    }

    private class MemoryDataProvider implements tv.porst.jhexview.IDataProvider {
        // Interface implementation for JHexView

        @Override
        public void addListener(IDataChangedListener hexView) {
        }

        @Override
        public byte[] getData() {
            byte ram[];

            if (pageModeBrowser > 7) {
                ram = new byte[0x10000];
                System.arraycopy(readPages[0], 0, ram, 0, PAGE_SIZE);
                System.arraycopy(readPages[1], 0, ram, 0x2000, PAGE_SIZE);
                System.arraycopy(readPages[2], 0, ram, 0x4000, PAGE_SIZE);
                System.arraycopy(readPages[3], 0, ram, 0x6000, PAGE_SIZE);
                System.arraycopy(readPages[4], 0, ram, 0x8000, PAGE_SIZE);
                System.arraycopy(readPages[5], 0, ram, 0xA000, PAGE_SIZE);
                System.arraycopy(readPages[6], 0, ram, 0xC000, PAGE_SIZE);
                System.arraycopy(readPages[7], 0, ram, 0xD000, PAGE_SIZE);
            } else {
                ram = new byte[PAGE_SIZE << 1];
                System.arraycopy(Ram[pageModeBrowser << 1], 0, ram, 0, PAGE_SIZE);
                System.arraycopy(Ram[(pageModeBrowser << 1) + 1], 0, ram, 0x2000, PAGE_SIZE);
            }

            return ram;
        }

        @Override
        public byte[] getData(long offset, int length) {
            byte ram[] = new byte[length];

            if (pageModeBrowser > 7) {
                for (int addr = 0; addr < length; addr++) {
                    ram[addr] = readByte((int) (addr + offset));
                }
            } else {
                for (int addr = 0; addr < length; addr++) {
                    ram[addr] = readByte(pageModeBrowser, (int) (addr + offset));
                }
            }

            return ram;
        }

        @Override
        public int getDataLength() {
            return pageModeBrowser > 7 ? 0x10000 : 0x4000;
        }

        @Override
        public boolean hasData(long start, int length) {
            return true;
        }

        @Override
        public boolean isEditable() {
            return true;
        }

        @Override
        public boolean keepTrying() {
            return false;
        }

        @Override
        public void removeListener(IDataChangedListener listener) {
        }

        @Override
        public void setData(long offset, byte[] data) {
            if (pageModeBrowser > 7) {
                for (int addr = 0; addr < data.length; addr++) {
                    writeByte((int) (addr + offset), data[addr]);
                }
            } else {
                for (int addr = 0; addr < data.length; addr++) {
                    writeByte(pageModeBrowser, (int) (addr + offset), data[addr]);
                }
            }
        }
    }
}
