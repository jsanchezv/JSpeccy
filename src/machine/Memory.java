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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jsanchez
 */
public final class Memory {
    private final int PAGE_SIZE = 0x4000;
    
    private byte[] Rom48k = new byte[PAGE_SIZE];
    private byte[][] Rom128k = new byte[2][PAGE_SIZE];
    private byte[][] RomPlus2 = new byte[2][PAGE_SIZE];
    private byte[][] RomPlus3 = new byte[4][PAGE_SIZE];
    // 8 páginas de RAM
    private byte[][] Ram = new byte[8][PAGE_SIZE];
    // RAM falsa para dejar que escriba en páginas de ROM sin afectar a la
    // ROM de verdad. Esto evita tener que comprobar en cada escritura si la
    // página es de ROM o de RAM.
    private byte[] fakeROM = new byte[PAGE_SIZE];
    // punteros a las 4 páginas
    private byte[][] readPages = new byte[4][];
    private byte[][] writePages = new byte[4][];
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
        return Ram[screenPage][address & 0x3fff];
    }

    public byte readByte(int address) {
        return readPages[address >>> 14][address & 0x3fff];
    }

    public void writeByte(int address, byte value) {
        writePages[address >>> 14][address & 0x3fff] = value;
    }

    public byte readByte(int page, int address) {
        return Ram[page][address & 0x3fff];
    }

    public void writeByte(int page, int address, byte value) {
        Ram[page][address & 0x3fff] = value;
    }

    private void setMemoryMap48k() {
        readPages[0] = Rom48k;
        readPages[1] = Ram[5];
        readPages[2] = Ram[2];
        readPages[3] = Ram[0];

        writePages[0] = fakeROM;
        writePages[1] = Ram[5];
        writePages[2] = Ram[2];
        writePages[3] = Ram[0];

        screenPage = 5;
        model128k = false;
    }

    private void setMemoryMap128k() {
        readPages[0] = Rom128k[0];
        writePages[0] = fakeROM;

        readPages[1] = writePages[1] = Ram[5];
        readPages[2] = writePages[2] = Ram[2];
        readPages[3] = writePages[3] = Ram[0];

        screenPage = 5;
        highPage = 0;
        model128k = true;
        pagingLocked = plus3RamMode = false;
        bankM = 0;
    }

    private void setMemoryMapPlus2() {
        readPages[0] = RomPlus2[0];
        writePages[0] = fakeROM;

        readPages[1] = writePages[1] = Ram[5];
        readPages[2] = writePages[2] = Ram[2];
        readPages[3] = writePages[3] = Ram[0];

        screenPage = 5;
        highPage = 0;
        model128k = true;
        pagingLocked = plus3RamMode = false;
        bankM = 0;
    }

    private void setMemoryMapPlus3() {
        readPages[0] = RomPlus3[0];
        writePages[0] = fakeROM;

        readPages[1] = writePages[1] = Ram[5];
        readPages[2] = writePages[2] = Ram[2];
        readPages[3] = writePages[3] = Ram[0];

        screenPage = 5;
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
        screenPage = (port7ffd & 0x08) == 0 ? 5 : 7;

        if (plus3RamMode)
            return;

        // Set the high page
        highPage = port7ffd & 0x07;
        readPages[3] = writePages[3] = Ram[highPage];

        // Set the active ROM
        switch (spectrumModel) {
            case SPECTRUM128K:
                readPages[0] = (port7ffd & 0x10) == 0 ? Rom128k[0] : Rom128k[1];
                break;
            case SPECTRUMPLUS2:
                readPages[0] = (port7ffd & 0x10) == 0 ? RomPlus2[0] : RomPlus2[1];
                break;
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

            readPages[0] = RomPlus3[rom];
            writePages[0] = fakeROM;
            plus3RamMode = false;
        } else {
            // Special paging mode (all pages are RAM)
            plus3RamMode = true;
            switch ((bankP & 0x06) >>> 1) {
                case 0:
                    readPages[0] = Ram[0];
                    writePages[0] = Ram[0];
                    readPages[1] = Ram[1];
                    writePages[1] = Ram[1];
                    readPages[2] = Ram[2];
                    writePages[2] = Ram[2];
                    readPages[3] = Ram[3];
                    writePages[3] = Ram[3];
                    highPage = 3;
                    break;
                case 1:
                    readPages[0] = Ram[4];
                    writePages[0] = Ram[4];
                    readPages[1] = Ram[5];
                    writePages[1] = Ram[5];
                    readPages[2] = Ram[6];
                    writePages[2] = Ram[6];
                    readPages[3] = Ram[7];
                    writePages[3] = Ram[7];
                    highPage = 7;
                    break;
                case 2:
                    readPages[0] = Ram[4];
                    writePages[0] = Ram[4];
                    readPages[1] = Ram[5];
                    writePages[1] = Ram[5];
                    readPages[2] = Ram[6];
                    writePages[2] = Ram[6];
                    readPages[3] = Ram[3];
                    writePages[3] = Ram[3];
                    highPage = 3;
                    break;
                case 3:
                    readPages[0] = Ram[4];
                    writePages[0] = Ram[4];
                    readPages[1] = Ram[7];
                    writePages[1] = Ram[7];
                    readPages[2] = Ram[6];
                    writePages[2] = Ram[6];
                    readPages[3] = Ram[3];
                    writePages[3] = Ram[3];
                    highPage = 3;
                    break;
            }
        }
    }

    public int getPlus3HighPage() {
        return highPage;
    }

//    public int getPlus3ScreenPage() {
//        return screenPage;
//    }

    public boolean isPlus3RamMode() {
        return plus3RamMode;
    }

    public boolean isPagingLocked() {
        return pagingLocked;
    }

    // "La Abadía del Crimen" pone la página 7 en 0xC000 y selecciona la
    // pantalla de la página 7. A partir de ahí, la modifica en 0xC000 en
    // lugar de hacerlo en 0x4000, donde siempre está la página 5.
    public boolean isScreenByte(int addr) {
        switch(addr >>> 14) {
            case 0: // Address 0x0000-0x3fff
            case 2: // Address 0x8000-0xbfff
                return false;
            case 1: // Address 0x4000-0x7fff
                if (addr > 0x5aff)
                    return false;
                break;
            case 3: // Address 0xc000-0xffff
                if (!model128k || highPage != screenPage || addr > 0xdaff)
                    return false;
        }
        return true;
    }

    public void setScreenPage(int nPage) {
        screenPage = nPage;
    }

    public void reset() {
        switch(spectrumModel) {
            case SPECTRUM48K:
                setMemoryMap48k();
                break;
            case SPECTRUM128K:
                setMemoryMap128k();
                break;
            case SPECTRUMPLUS2:
                setMemoryMapPlus2();
                break;
            case SPECTRUMPLUS3:
                setMemoryMapPlus3();
        }
    }

    public void loadRoms() {
        String romsDirectory = settings.getRomsDirectory();
        if (!romsDirectory.isEmpty() && !romsDirectory.endsWith("/"))
            romsDirectory += "/";

        if (!loadRomAsFile(romsDirectory + settings.getRom48K(), Rom48k))
            loadRomAsResource("/roms/spectrum.rom", Rom48k);

        if (!loadRomAsFile(romsDirectory + settings.getRom128K0(), Rom128k[0]))
            loadRomAsResource("/roms/128-0.rom", Rom128k[0]);
        if (!loadRomAsFile(romsDirectory + settings.getRom128K1(), Rom128k[1]))
            loadRomAsResource("/roms/128-1.rom", Rom128k[1]);

        if (!loadRomAsFile(romsDirectory + settings.getRomPlus20(), RomPlus2[0]))
            loadRomAsResource("/roms/plus2-0.rom", RomPlus2[0]);
        if (!loadRomAsFile(romsDirectory + settings.getRomPlus21(), RomPlus2[1]))
            loadRomAsResource("/roms/plus2-1.rom", RomPlus2[1]);

        if (!loadRomAsFile(romsDirectory + settings.getRomPlus30(), RomPlus3[0]))
            loadRomAsResource("/roms/plus3-0.rom", RomPlus3[0]);
        if (!loadRomAsFile(romsDirectory + settings.getRomPlus31(), RomPlus3[1]))
            loadRomAsResource("/roms/plus3-1.rom", RomPlus3[1]);
        if (!loadRomAsFile(romsDirectory + settings.getRomPlus32(), RomPlus3[2]))
            loadRomAsResource("/roms/plus3-2.rom", RomPlus3[2]);
        if (!loadRomAsFile(romsDirectory + settings.getRomPlus33(), RomPlus3[3]))
            loadRomAsResource("/roms/plus3-3.rom", RomPlus3[3]);

    }

    private boolean loadRomAsResource(String filename, byte[] page) {
        InputStream inRom;

        try {
            inRom = Spectrum.class.getResourceAsStream(filename);
            if (inRom == null) {
                String msg =
                    java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                    "RESOURCE_ROM_ERROR");
                System.out.println(String.format("%s: %s", msg, filename));
                return false;
            }

            int count = 0;
            while (count != -1 && count < 0x4000)
                count += inRom.read(page, count, 0x4000 - count);

            if (count != 0x4000) {
                String msg =
                    java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                    "ROM_SIZE_ERROR");
                System.out.println(String.format("%s: %s", msg, filename));
                return false;
            }

            inRom.close();
        } catch (IOException ex) {
            String msg =
                java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                "RESOURCE_ROM_ERROR");
            System.out.println(String.format("%s: %s", msg, filename));
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }
        String msg =
            java.util.ResourceBundle.getBundle("machine/Bundle").getString(
            "ROM_RESOURCE_LOADED");
        System.out.println(String.format("%s: %s", msg, filename));

        return true;
    }

    private boolean loadRomAsFile(String filename, byte[] page) {
        BufferedInputStream fIn;

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

            int count = 0;
            while (count != -1 && count < 0x4000)
                count += fIn.read(page, count, 0x4000 - count);

            if (count != 0x4000) {
                String msg =
                    java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                    "ROM_SIZE_ERROR");
                System.out.println(String.format("%s: %s", msg, filename));
                return false;
            }

            fIn.close();
        } catch (IOException ex) {
            String msg =
                java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                "FILE_ROM_ERROR");
            System.out.println(String.format("%s: %s", msg, filename));
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }

        String msg =
            java.util.ResourceBundle.getBundle("machine/Bundle").getString(
            "ROM_FILE_LOADED");
        System.out.println(String.format("%s: %s", msg, filename));

        return true;
    }
}
