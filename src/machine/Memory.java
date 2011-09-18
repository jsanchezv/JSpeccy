/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import configuration.JSpeccySettingsType;
import configuration.MemoryType;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jsanchez
 */
public final class Memory {
    private final int PAGE_SIZE = 0x2000;
    
    private byte[][] Rom48k = new byte[2][PAGE_SIZE];
    private byte[][] IF2Rom = new byte[2][PAGE_SIZE];
    private byte[][] Rom128k = new byte[4][PAGE_SIZE];
    private byte[][] RomPlus2 = new byte[4][PAGE_SIZE];
    private byte[][] RomPlus3 = new byte[8][PAGE_SIZE];
    // 8 páginas de RAM
    private byte[][] Ram = new byte[16][PAGE_SIZE];
    // RAM falsa para dejar que escriba en páginas de ROM sin afectar a la
    // ROM de verdad. Esto evita tener que comprobar en cada escritura si la
    // página es de ROM o de RAM.
    private byte[] fakeROM = new byte[PAGE_SIZE];
    // punteros a las 4 páginas
    private byte[][] readPages = new byte[8][];
    private byte[][] writePages = new byte[8][];
    // Roms de los Multiface ([0]=MF1, [1]=MF128, [2]=MFPLUS3
    private byte[][] mfROM = new byte[3][PAGE_SIZE];
    // Ram del Multiface 8K para todos
    private byte[] mfRAM = new byte[PAGE_SIZE];
    // Número de página de RAM de donde sale la pantalla activa
    private int screenPage, highPage, bankM, bankP;
    private boolean IF2RomEnabled;
    private boolean model128k, pagingLocked, plus3RamMode, mfPagedIn, mfLocked;
    private MachineTypes spectrumModel;
    private JSpeccySettingsType settings;
    private Random random;

    public Memory(JSpeccySettingsType memSettings) {
        spectrumModel = MachineTypes.SPECTRUM48K;
        settings = memSettings;
        random = new Random();
        hardReset();
        loadRoms();
        IF2RomEnabled = false;
    }

    public void setSpectrumModel(MachineTypes model) {
        spectrumModel = model;
        reset();
    }

    public byte readScreenByte(int address) {
        return Ram[screenPage][address & 0x1fff];
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
            return Ram[page +  1][address & 0x1fff];
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
        byte target[] = Ram[page];
        System.arraycopy(buffer, 0, target, 0, PAGE_SIZE);
        target = Ram[page + 1];
        System.arraycopy(buffer, PAGE_SIZE, target, 0, PAGE_SIZE);
    }

    public void savePage(int page, byte[] buffer) {
        page <<= 1;
        byte source[] = Ram[page];
        System.arraycopy(source, 0, buffer, 0, PAGE_SIZE);
        source = Ram[page + 1];
        System.arraycopy(source, 0, buffer, PAGE_SIZE, PAGE_SIZE);
    }

    public void loadMFRam(byte[] buffer) {
        System.arraycopy(buffer, 0, mfRAM, 0, mfRAM.length);
    }

    public void saveMFRam(byte[] buffer) {
        System.arraycopy(mfRAM, 0, buffer, 0, buffer.length);
    }

    public void saveIF2Rom(byte[] buffer) {
        System.arraycopy(IF2Rom[0], 0, buffer, 0, IF2Rom[0].length);
        System.arraycopy(IF2Rom[1], 0, buffer, 0x2000, IF2Rom[1].length);
    }

    private void setMemoryMap16k() {
        if (IF2RomEnabled) {
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
        Arrays.fill(Ram[4], (byte)0xff);
        screenPage = 10;
        model128k = false;
        mfLocked = true;
    }

    private void setMemoryMap48k() {
        if (IF2RomEnabled) {
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
        mfLocked = true;
    }

    private void setMemoryMap128k() {
        readPages[0] = Rom128k[0];
        readPages[1] = Rom128k[1];
        writePages[0] = writePages[1] = fakeROM;

        readPages[2] = writePages[2] = Ram[10];
        readPages[3] = writePages[3] = Ram[11];

        readPages[4] = writePages[4] = Ram[4];
        readPages[5] = writePages[5] = Ram[5];

        readPages[6] = writePages[6] = Ram[0];
        readPages[7] = writePages[7] = Ram[1];

        screenPage = 10;
        highPage = 0;
        model128k = true;
        pagingLocked = plus3RamMode = false;
        bankM = 0;
        mfLocked = true;
    }

    private void setMemoryMapPlus2() {
        readPages[0] = RomPlus2[0];
        readPages[1] = RomPlus2[1];
        writePages[0] = writePages[1] = fakeROM;

        readPages[2] = writePages[2] = Ram[10];
        readPages[3] = writePages[3] = Ram[11];

        readPages[4] = writePages[4] = Ram[4];
        readPages[5] = writePages[5] = Ram[5];

        readPages[6] = writePages[6] = Ram[0];
        readPages[7] = writePages[7] = Ram[1];

        screenPage = 10;
        highPage = 0;
        model128k = true;
        pagingLocked = plus3RamMode = false;
        bankM = 0;
        mfLocked = true;
    }

    private void setMemoryMapPlus3() {
        readPages[0] = RomPlus3[0];
        readPages[1] = RomPlus3[1];
        writePages[0] = writePages[1] = fakeROM;

        readPages[2] = writePages[2] = Ram[10];
        readPages[3] = writePages[3] = Ram[11];

        readPages[4] = writePages[4] = Ram[4];
        readPages[5] = writePages[5] = Ram[5];

        readPages[6] = writePages[6] = Ram[0];
        readPages[7] = writePages[7] = Ram[1];

        screenPage = 10;
        highPage = 0;
        model128k = true;
        pagingLocked = plus3RamMode = false;
        bankM = bankP = 0;
        mfLocked = true;
    }

    public void setPort7ffd(int port7ffd) {

        port7ffd &= 0xff;
//        System.out.println(String.format("Port 0x7ffd: %02x", port7ffd));
        if (pagingLocked)
            return;

        bankM = port7ffd;

        // Set the page locking state
        pagingLocked = (port7ffd & 0x20) != 0;

        // Set the active screen
        screenPage = (port7ffd & 0x08) == 0 ? 10 : 14;

        // Si el +3 está en modo "all RAM" no se conmuta ROM ni RAM
        if (plus3RamMode)
            return;

        // Set the high page
        highPage = port7ffd & 0x07;
        readPages[6] = writePages[6] = Ram[highPage * 2];
        readPages[7] = writePages[7] = Ram[highPage * 2 + 1];

        // Si está funcionando el MF, no tocar la ROM
        if (mfPagedIn)
            return;
        
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
    }

    public void setPort1ffd(int port1ffd) {
        port1ffd &= 0x07;
//        System.out.println(String.format("Port 0x1ffd: %02x", port1ffd));
        if (pagingLocked)
            return;

        bankP = port1ffd;

        doPagingPlus3();
    }

    private void doPagingPlus3() {
        // Paging mode normal (bit0 of 0x1ffd to 0)
        if ((bankP & 0x01) == 0) {
            
            if (mfPagedIn)
                return;

            int rom = ((bankM & 0x10) >>> 3) | (bankP & 0x04);

            readPages[0] = RomPlus3[rom];
            readPages[1] = RomPlus3[rom + 1];
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
            // Special paging mode (all pages are RAM (bit0 of 0x1ffd to 1))
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
        if (mfPagedIn)
            return false;

        boolean res = false;
        switch(spectrumModel.codeModel) {
            case SPECTRUM48K:
                res = !IF2RomEnabled;
                break;
            case SPECTRUM128K:
                res = (bankM & 0x10) != 0;
                break;
            case SPECTRUMPLUS3:
                if (!plus3RamMode)
                    res = (((bankM & 0x10) >>> 3) | (bankP & 0x04)) == 6;
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
                    if (addr > 0x3FFF && addr < 0x5B00 && screenPage == 10) {
                        return true;
                    } else {
                        return false;
                    }
                case 6: // Pages 4, 7, 6, 3
                    if (addr > 0x3FFF && addr < 0x5B00 && screenPage == 14) {
                        return true;
                } else {
                        return false;
                }
            }
        }

        // El caso de todo RAM con páginas 4, 5, 6, 7 cae aquí
        switch (addr >>> 13) {
            case 2: // Address 0x4000-0x5fff
                if (addr < 0x5B00 && screenPage == 10) {
                    return true;
                }
                break;
            case 6: // Address 0xc000-0xdfff
                if (model128k && addr < 0xDB00 && (highPage << 1) == screenPage) {
                    return true;
                }
        }
        return false;
    }

    public void reset() {
        mfPagedIn = false;
        switch(spectrumModel) {
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

    public void hardReset() {
        reset();
        for (int page = 0; page < Ram.length; page++) {
            random.nextBytes(Ram[page]);
        }
        random.nextBytes(mfRAM);
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
     *
     *     * Multiface 3
     *       IN del puerto 0x3f, pagina el MF3
     *       IN del puerto 0xbf, despagina el MF3
     *       IN del puerto 0x1f3f, lectura del último valor enviado al puerto 0x1ffd
     *       IN del puerto 0x7f3f, lectura del último valor enviado el puerto 0x7ffd
     *
     *     Los dos últimos puertos del MF3, solo son legibles cuando el aparato está
     *     desbloqueado y, además, está paginada la ROM del MF3. Si no, devuelven
     *     0xFF como todo puerto que no esté asignado.
     *
     *     El MF128 y el MF3 controlan el "switch" de bloqueo con una escritura (OUT) al
     *     puerto 0x3f para activar el bloqueo y con un OUT al puerto 0xbf para desactivar
     *     el bloqueo (es así como funciona la opción de ON/OFF del menú del MF). Esta
     *     escritura funciona siempre que esté paginada la ROM del MF, el bloqueo
     ^     desactivado y, supuestamente, pero no lo he comprobado, que el PC esté
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
     */
    public void multifacePageIn() {
        if (mfPagedIn || plus3RamMode)
            return;

        mfPagedIn = true;
//        System.out.println("Multiface paged IN");
        switch (spectrumModel.codeModel) {
            case SPECTRUM48K:
                if (settings.getSpectrumSettings().isMf128On48K())
                    readPages[0] = mfROM[1];
                else
                    readPages[0] = mfROM[0];
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
     * paginación puede estar bloqueada (bit 5 de 0x7ffd). En ese
     * caso, la ROM que entra es, por obligación, la del Spectrum 48k
     * (ROM1 para el 128k/+2 y ROM3 para el +2A/+3.
     */
    public void multifacePageOut() {
        if (!mfPagedIn)
            return;
        
        mfPagedIn = false;
//        System.out.println("Multiface paged OUT");
        switch (spectrumModel) {
            case SPECTRUM16K:
            case SPECTRUM48K:
                readPages[0] = Rom48k[0];
                readPages[1] = Rom48k[1];
                writePages[1] = fakeROM;
                break;
            case SPECTRUM128K:
                writePages[1] = fakeROM;
                if (pagingLocked) {
                    readPages[0] = Rom128k[2];
                    readPages[1] = Rom128k[3];
                } else {
                    setPort7ffd(bankM);
                }
                break;
            case SPECTRUMPLUS2:
                writePages[1] = fakeROM;
                if (pagingLocked) {
                    readPages[0] = RomPlus2[2];
                    readPages[1] = RomPlus2[3];
                } else {
                    setPort7ffd(bankM);
                }
                break;
            case SPECTRUMPLUS2A:
            case SPECTRUMPLUS3:
                writePages[1] = fakeROM;
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
        return mfPagedIn;
    }

    public void setMultifaceLocked(boolean state) {
        mfLocked = state;
    }

    public boolean isMultifaceLocked() {
        return mfLocked;
    }

    public boolean insertIF2Rom(File filename) {
        if (!loadIF2Rom(filename)) {
            return false;
        }

        IF2RomEnabled = true;
        return true;
    }

    public void insertIF2RomFromSZX(byte[] rom) {
        System.arraycopy(rom, 0, IF2Rom[0], 0, IF2Rom[0].length);
        System.arraycopy(rom, 0x2000, IF2Rom[1], 0, IF2Rom[1].length);
        IF2RomEnabled = true;
    }

    public void ejectIF2Rom() {
        IF2RomEnabled = false;
        reset();
    }

    public boolean isIF2RomEnabled()  {
        return IF2RomEnabled;
    }

    public void loadRoms() {
        MemoryType conf = settings.getMemorySettings();
        String romsDirectory = conf.getRomsDirectory();

        if (!romsDirectory.isEmpty() && !romsDirectory.endsWith("/"))
            romsDirectory += "/";

        if (!loadRomAsFile(romsDirectory + conf.getRom48K(), Rom48k, 0, PAGE_SIZE * 2))
            loadRomAsResource("/roms/spectrum.rom", Rom48k, 0, PAGE_SIZE * 2);

        if (!loadRomAsFile(romsDirectory + conf.getRom128K0(), Rom128k, 0, PAGE_SIZE * 2))
            loadRomAsResource("/roms/128-0.rom", Rom128k, 0, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + conf.getRom128K1(), Rom128k, 2,PAGE_SIZE * 2))
            loadRomAsResource("/roms/128-1.rom", Rom128k, 2, PAGE_SIZE * 2);

        if (!loadRomAsFile(romsDirectory + conf.getRomPlus20(), RomPlus2, 0, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus2-0.rom", RomPlus2, 0, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus21(), RomPlus2, 2, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus2-1.rom", RomPlus2, 2, PAGE_SIZE * 2);

        if (!loadRomAsFile(romsDirectory + conf.getRomPlus30(), RomPlus3, 0, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus3-0.rom", RomPlus3, 0, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus31(), RomPlus3, 2, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus3-1.rom", RomPlus3, 2, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus32(), RomPlus3, 4, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus3-2.rom", RomPlus3, 4, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + conf.getRomPlus33(), RomPlus3, 6, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus3-3.rom", RomPlus3, 6, PAGE_SIZE * 2);

        if (!loadRomAsFile(romsDirectory + conf.getRomMF1(), mfROM, 0, PAGE_SIZE))
            loadRomAsResource("/roms/mf1.rom", mfROM, 0, PAGE_SIZE);
        if (!loadRomAsFile(romsDirectory + conf.getRomMF128(), mfROM, 1, PAGE_SIZE))
            loadRomAsResource("/roms/mf128.rom", mfROM, 1, PAGE_SIZE);
        if (!loadRomAsFile(romsDirectory + conf.getRomMFPlus3(), mfROM, 2, PAGE_SIZE))
            loadRomAsResource("/roms/mfplus3.rom", mfROM, 2, PAGE_SIZE);
    }

    private boolean loadRomAsResource(String filename, byte[][] rom, int page, int size) {

        InputStream inRom = Spectrum.class.getResourceAsStream(filename);
        boolean res = false;

        if (inRom == null) {
            String msg =
                    java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                    "RESOURCE_ROM_ERROR");
            System.out.println(String.format("%s: %s", msg, filename));
            return false;
        }

        try {
            for (int frag = 0; frag < size / PAGE_SIZE; frag++) {
                int count = 0;
                while (count != -1 && count < PAGE_SIZE) {
                    count += inRom.read(rom[page + frag], count, PAGE_SIZE - count);
                }

                if (count != PAGE_SIZE) {
                    String msg =
                            java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                            "ROM_SIZE_ERROR");
                    System.out.println(String.format("%s: %s", msg, filename));
                } else {
                    res = true;
                }
            }
        } catch (IOException ex) {
            String msg =
                java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                "RESOURCE_ROM_ERROR");
            System.out.println(String.format("%s: %s", msg, filename));
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                inRom.close();
            } catch (IOException ex) {
                Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (res) {
            String msg =
                    java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                    "ROM_RESOURCE_LOADED");
            System.out.println(String.format("%s: %s", msg, filename));
        }

        return res;
    }

    private boolean loadRomAsFile(String filename, byte[][] rom, int page, int size) {
        BufferedInputStream fIn = null;
        boolean res = false;

        try {
            try {
                fIn = new BufferedInputStream(new FileInputStream(filename));
            } catch (FileNotFoundException ex) {
                String msg =
                java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                "FILE_ROM_ERROR");
                System.out.println(String.format("%s: %s", msg, filename));
                //Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }

            for (int frag = 0; frag < size / PAGE_SIZE; frag++) {
                int count = 0;
                while (count != -1 && count < PAGE_SIZE) {
                    count += fIn.read(rom[page + frag], count, PAGE_SIZE - count);
                }

                if (count != PAGE_SIZE) {
                    String msg =
                            java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                            "ROM_SIZE_ERROR");
                    System.out.println(String.format("%s: %s", msg, filename));
                } else {
                    res = true;
                }
            }
        } catch (IOException ex) {
            String msg =
                java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                "FILE_ROM_ERROR");
            System.out.println(String.format("%s: %s", msg, filename));
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (fIn != null)
                    fIn.close();
            } catch (IOException ex) {
                Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (res) {
            String msg =
                    java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                    "ROM_FILE_LOADED");
            System.out.println(String.format("%s: %s", msg, filename));
        }

        return res;
    }

    private boolean loadIF2Rom(File filename) {
        BufferedInputStream fIn = null;
        try {
            try {
                fIn = new BufferedInputStream(new FileInputStream(filename));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }

            if (fIn.available() != 0x4000)
                return false;
            
            int readed = fIn.read(IF2Rom[0]);
            if (readed != 0x2000) {
                return false;
            }

            readed = fIn.read(IF2Rom[1]);
            if (readed != 0x2000) {
                return false;
            }

        } catch (IOException ex) {
            Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fIn.close();
            } catch (IOException ex) {
                Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        return true;
    }
}
