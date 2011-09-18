/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import configuration.MemoryType;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jsanchez
 */
public final class Memory {
    private final int PAGE_SIZE = 0x2000;
    
    private byte[][] Rom48k = new byte[2][PAGE_SIZE];
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
    // Número de página de RAM de donde sale la pantalla activa
    private int screenPage, highPage, bankM, bankP;
    private boolean model128k, pagingLocked, plus3RamMode;
    MachineTypes spectrumModel;
    MemoryType settings;

    public Memory(MemoryType memSettings) {
        spectrumModel = MachineTypes.SPECTRUM48K;
        settings = memSettings;
        reset();
        loadRoms();
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

    private void setMemoryMap16k() {
        readPages[0] = Rom48k[0];
        readPages[1] = Rom48k[1];
        writePages[0] = writePages[1] = fakeROM;
        readPages[2] = writePages[2] = Ram[10];
        readPages[3] = writePages[3] = Ram[11];
        readPages[4] = readPages[5] = Ram[4];
        readPages[6] = readPages[7] = Ram[4];
        writePages[4] = writePages[5] = fakeROM;
        writePages[6] = writePages[7] = fakeROM;
        Arrays.fill(Ram[4], (byte)0xff);
        screenPage = 10;
        model128k = false;
    }

    private void setMemoryMap48k() {
        readPages[0] = Rom48k[0];
        readPages[1] = Rom48k[1];
        writePages[0] = writePages[1] = fakeROM;
        readPages[2] = writePages[2] = Ram[10];
        readPages[3] = writePages[3] = Ram[11];
        readPages[4] = writePages[4] = Ram[4];
        readPages[5] = writePages[5] = Ram[5];
        readPages[6] = writePages[6] = Ram[0];
        readPages[7] = writePages[7] = Ram[1];
        screenPage = 10;
        model128k = false;
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
    }

    public void setPort7ffd(int port7ffd) {

        port7ffd &= 0xff;
        if (pagingLocked || port7ffd == bankM)
            return;

        bankM = port7ffd;

        // Set the page locking state
        pagingLocked = (port7ffd & 0x20) == 0 ? false : true;

        // Set the active screen
        screenPage = (port7ffd & 0x08) == 0 ? 10 : 14;

        if (plus3RamMode)
            return;

        // Set the high page
        highPage = port7ffd & 0x07;
        readPages[6] = writePages[6] = Ram[highPage * 2];
        readPages[7] = writePages[7] = Ram[highPage * 2 + 1];

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
        if (pagingLocked || port1ffd == bankP)
            return;

        bankP = port1ffd;

        doPagingPlus3();
    }

    private void doPagingPlus3() {
        // Paging mode normal (bit0 of 0x1ffd to 0)
        if ((bankP & 0x01) == 0) {
            int rom = ((bankM & 0x10) >>> 4) | ((bankP & 0x04) >>> 1);

            readPages[0] = RomPlus3[rom * 2];
            readPages[1] = RomPlus3[rom * 2 + 1];
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
        boolean res = false;

        switch(spectrumModel.codeModel) {
            case SPECTRUM48K:
                res = true;
                break;
            case SPECTRUM128K:
                res = (bankM & 0x10) != 0;
                break;
            case SPECTRUMPLUS3:
                if (!plus3RamMode)
                    res = (((bankM & 0x10) >>> 4) | ((bankP & 0x04) >>> 1)) == 3;
        }
        return res;
    }

    // "La Abadía del Crimen" pone la página 7 en 0xC000 y selecciona la
    // pantalla de la página 7. A partir de ahí, la modifica en 0xC000 en
    // lugar de hacerlo en 0x4000, donde siempre está la página 5.
    public boolean isScreenByte(int addr) {
        if (plus3RamMode) {
            switch (bankP = 0x06) {
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

    public void loadRoms() {
        String romsDirectory = settings.getRomsDirectory();
        if (!romsDirectory.isEmpty() && !romsDirectory.endsWith("/"))
            romsDirectory += "/";

        if (!loadRomAsFile(romsDirectory + settings.getRom48K(), Rom48k, 0, PAGE_SIZE * 2))
            loadRomAsResource("/roms/spectrum.rom", Rom48k, 0, PAGE_SIZE * 2);

        if (!loadRomAsFile(romsDirectory + settings.getRom128K0(), Rom128k, 0, PAGE_SIZE * 2))
            loadRomAsResource("/roms/128-0.rom", Rom128k, 0, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + settings.getRom128K1(), Rom128k, 2,PAGE_SIZE * 2))
            loadRomAsResource("/roms/128-1.rom", Rom128k, 2, PAGE_SIZE * 2);

        if (!loadRomAsFile(romsDirectory + settings.getRomPlus20(), RomPlus2, 0, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus2-0.rom", RomPlus2, 0, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + settings.getRomPlus21(), RomPlus2, 2, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus2-1.rom", RomPlus2, 2, PAGE_SIZE * 2);

        if (!loadRomAsFile(romsDirectory + settings.getRomPlus30(), RomPlus3, 0, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus3-0.rom", RomPlus3, 0, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + settings.getRomPlus31(), RomPlus3, 2, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus3-1.rom", RomPlus3, 2, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + settings.getRomPlus32(), RomPlus3, 4, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus3-2.rom", RomPlus3, 4, PAGE_SIZE * 2);
        if (!loadRomAsFile(romsDirectory + settings.getRomPlus33(), RomPlus3, 6, PAGE_SIZE * 2))
            loadRomAsResource("/roms/plus3-3.rom", RomPlus3, 6, PAGE_SIZE * 2);

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

            for (int frag = 0; frag < size / PAGE_SIZE; page++) {
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
}
