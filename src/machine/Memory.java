/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

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
public class Memory {
    final int PAGE_SIZE = 0x4000;
    
    int[] Rom48k = new int[PAGE_SIZE];
    int[][] Rom128k = new int[2][PAGE_SIZE];
    int[][] RomPlus3 = new int[4][PAGE_SIZE];
    // 8 páginas de RAM
    int[][] Ram = new int[8][PAGE_SIZE];
    // RAM falsa para dejar que escriba en páginas de ROM sin afectar a la
    // ROM de verdad. Esto evita tener que comprobar en cada escritura si la
    // página es de ROM o de RAM.
    int[] fakeROM = new int[PAGE_SIZE];
    // punteros a las 4 páginas
    int[][] readPages = new int[4][];
    int[][] writePages = new int[4][];
    int screenPage;
    //boolean[] ROMpage = new boolean[4];

    public int getScreenByte(int address) {
        return Ram[screenPage][address & 0x3fff];
    }

    public int readByte(int address) {
        return readPages[address >>> 14][address & 0x3fff];
    }

    public void writeByte(int address, int value) {
        writePages[address >>> 14][address & 0x3fff] = value;
    }

    public void setMemMap48k() {
        readPages[0] = Rom48k;
        readPages[1] = Ram[5];
        readPages[2] = Ram[1];
        readPages[3] = Ram[2];

        writePages[0] = fakeROM;
        writePages[1] = Ram[5];
        writePages[2] = Ram[1];
        writePages[3] = Ram[2];

        screenPage = 5;
//        ROMpage[0] = true;
//        ROMpage[1] = false;
//        ROMpage[2] = false;
//        ROMpage[3] = false;
    }

    public void setScreenPage(int nPage) {
        screenPage = nPage;
    }

    public void loadRoms() {
        if (!loadRomAsFile("spectrum.rom", Rom48k))
            loadRomAsResource("/roms/spectrum.rom", Rom48k);
    }

    private boolean loadRomAsResource(String filename, int[] page) {
        InputStream inRom;

        try {
            inRom = Spectrum.class.getResourceAsStream(filename);
            if (inRom == null) {
                System.out.println(
                    java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                    "NO_SE_PUDO_LEER_LA_ROM_DESDE_/ROMS/SPECTRUM.ROM"));
                return false;
            }

            int count, value;
            for (count = 0; count < 0x4000; count++) {
                value = inRom.read();
                if (value == -1)
                    break;
                page[count] = value & 0xff;
            }

            if (count != 0x4000) {
                System.out.println(
                    java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                    "NO_SE_PUDO_CARGAR_LA_ROM"));
                return false;
            }

            inRom.close();
        } catch (IOException ex) {
            System.out.println(
                java.util.ResourceBundle.getBundle("machine/Bundle").getString(
                "NO_SE_PUDO_LEER_EL_FICHERO_SPECTRUM.ROM"));
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println(
            java.util.ResourceBundle.getBundle("machine/Bundle").getString("ROM_CARGADA"));

        return true;
    }

    private boolean loadRomAsFile(String filename, int[] page) {
        FileInputStream fIn;

        try {
            try {
                fIn = new FileInputStream(filename);
            } catch (FileNotFoundException ex) {
                System.out.println("No se pudo abrir el fichero " + filename);
                //Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }

            int count, value;
            for (count = 0; count < 0x4000; count++) {
                value = fIn.read();
                if (value == -1)
                    break;
                page[count] = value  & 0xff;
            }

//            if (count != 0x4000) {
//                System.out.println("No se pudo cargar la ROM");
//                return false;
//            }

            fIn.close();
        } catch (IOException ex) {
            System.out.println("No se pudo leer el fichero " + filename);
            Logger.getLogger(Spectrum.class.getName()).log(Level.SEVERE, null, ex);
        }

        return true;
    }
}
