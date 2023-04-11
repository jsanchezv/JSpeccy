/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package snapshots;

import machine.Keyboard.JoystickModel;
import machine.MachineTypes;
import z80core.Z80.IntMode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 * @author jsanchez
 */
public class SnapshotSNA implements SnapshotFile {

    private SpectrumState spectrum;
    private Z80State z80;
    private MemoryState memory;
    private AY8912State ay8912;

    @Override
    public SpectrumState load(File filename) throws SnapshotException {
        spectrum = new SpectrumState();

        try (BufferedInputStream fIn = new BufferedInputStream(new FileInputStream(filename))) {

            int snaLen = fIn.available();
            switch (snaLen) {
                case 49179: // 48K
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUM48K);
                    break;
                case 131103: // 128k
                case 147487: // snapshot de 128k con una página repetida
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUM128K);
                    break;
                default:
                    throw new SnapshotException("FILE_SIZE_ERROR");
            }

            byte[] snaHeader = new byte[27];
            int count = 0;
            while (count != -1 && count < snaHeader.length) {
                count += fIn.read(snaHeader, count, snaHeader.length - count);
            }

            if (count != snaHeader.length) {
                throw new SnapshotException("FILE_READ_ERROR");
            }

            z80 = new Z80State();
            spectrum.setZ80State(z80);

            z80.setRegI(snaHeader[0]);
            z80.setRegLx(snaHeader[1]);
            z80.setRegHx(snaHeader[2]);
            z80.setRegEx(snaHeader[3]);
            z80.setRegDx(snaHeader[4]);
            z80.setRegCx(snaHeader[5]);
            z80.setRegBx(snaHeader[6]);
            z80.setRegFx(snaHeader[7]);
            z80.setRegAx(snaHeader[8]);
            z80.setRegL(snaHeader[9]);
            z80.setRegH(snaHeader[10]);
            z80.setRegE(snaHeader[11]);
            z80.setRegD(snaHeader[12]);
            z80.setRegC(snaHeader[13]);
            z80.setRegB(snaHeader[14]);
            z80.setRegIY((snaHeader[15] & 0xff) | (snaHeader[16] << 8));
            z80.setRegIX((snaHeader[17] & 0xff) | (snaHeader[18] << 8));

            // From SNA specification:
            // When the registers have been loaded, a RETN command is required to start the program.
            // IFF2 is short for interrupt flip-flop 2, and for all practical purposes is the
            // interrupt-enabled flag. Set means enabled. 
            boolean intEnabled = (snaHeader[19] & 0x04) != 0;
            z80.setIFF1(intEnabled);
            z80.setIFF2(intEnabled);

            z80.setRegR(snaHeader[20]);
            z80.setRegF(snaHeader[21]);
            z80.setRegA(snaHeader[22]);
            z80.setRegSP((snaHeader[23] & 0xff) | (snaHeader[24] << 8));
            switch (snaHeader[25] & 0x03) {
                case 0:
                    z80.setIM(IntMode.IM0);
                    break;
                case 1:
                    z80.setIM(IntMode.IM1);
                    break;
                case 2:
                    z80.setIM(IntMode.IM2);
                    break;
            }

            spectrum.setBorder(snaHeader[26]);

            memory = new MemoryState();
            spectrum.setMemoryState(memory);

            byte[] buffer = new byte[0x4000];

            // Cargamos la página de la pantalla 0x4000-0x7FFF (5)
            count = 0;
            while (count != -1 && count < 0x4000) {
                count += fIn.read(buffer, count, 0x4000 - count);
            }

            if (count != 0x4000) {
                throw new SnapshotException("FILE_READ_ERROR");
            }
            memory.setPageRam(5, buffer);


            // Cargamos la página 0x8000-0xBFFF (2)
            buffer = new byte[0x4000];
            count = 0;
            while (count != -1 && count < 0x4000) {
                count += fIn.read(buffer, count, 0x4000 - count);
            }

            if (count != 0x4000) {
                throw new SnapshotException("FILE_READ_ERROR");
            }
            memory.setPageRam(2, buffer);

            if (snaLen == 49179) { // 48K
                // Cargamos la página 0xC000-0XFFF (0)
                buffer = new byte[0x4000];
                count = 0;
                while (count != -1 && count < 0x4000) {
                    count += fIn.read(buffer, count, 0x4000 - count);
                }

                if (count != 0x4000) {
                    throw new SnapshotException("FILE_READ_ERROR");
                }
                memory.setPageRam(0, buffer);

                z80.setRegPC(0x72); // dirección de RETN en la ROM
                spectrum.setEnabledAY(false);
            } else {
                boolean loaded[] = new boolean[8];

                // Hasta que leamos el último valor del puerto 0x7ffd no sabemos
                // en qué página hay que poner los últimos 16K. Los leemos en
                // un buffer temporal y luego los copiamos (que remedio!!!)
                buffer = new byte[0x4000];
                count = 0;
                while (count != -1 && count < 0x4000) {
                    count += fIn.read(buffer, count, 0x4000 - count);
                }

                if (count != 0x4000) {
                    throw new SnapshotException("FILE_READ_ERROR");
                }

                // En modo 128, la página 5 está en 0x4000 y la 2 en 0x8000.
                loaded[2] = loaded[5] = true;
                z80.setRegPC(fIn.read() | (fIn.read() << 8));
                spectrum.setPort7ffd(fIn.read());
                // Si la página de memoria en 0xC000 era la 2 o la 5, ésta se
                // habrá grabado dos veces, y esta segunda copia es redundante.
                int page = spectrum.getPort7ffd() & 0x07;
                if (page != 2 && page != 5) {
                    memory.setPageRam(page, buffer);
                    loaded[page] = true;
                }

                int trDos = fIn.read();
                // Si la ROM del TR-DOS estaba paginada, mal vamos...
                if (trDos == 0x01) {
                    throw new SnapshotException("NOT_SNAPSHOT_FILE");
                }

                for (page = 0; page < 8; page++) {
                    if (!loaded[page]) {
                        buffer = new byte[0x4000];
                        count = 0;
                        while (count != -1 && count < 0x4000) {
                            count += fIn.read(buffer, count, 0x4000 - count);
                        }
                        if (count != 0x4000) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        memory.setPageRam(page, buffer);
                    }
                    // El formato SNA no guarda los registros del AY
                    // Los ponemos a cero y que se apañe....
                    spectrum.setEnabledAY(true);
                    spectrum.setEnabledAYon48k(false);
                    int regAY[] = new int[16];
                    ay8912 = new AY8912State();
                    spectrum.setAY8912State(ay8912);
                    ay8912.setAddressLatch(0);
                    ay8912.setRegAY(regAY);
                }

            }

            spectrum.setIssue2(false); // esto no se guarda en los SNA, algo hay que poner...
            spectrum.setJoystick(JoystickModel.NONE); // idem
            spectrum.setTstates(0);
        } catch (final IOException ioExcpt) {
            throw new SnapshotException("FILE_READ_ERROR", ioExcpt);
        }

        return spectrum;
    }

    @Override
    public boolean save(File filename, SpectrumState state) throws SnapshotException {
        spectrum = state;
        z80 = spectrum.getZ80State();
        memory = spectrum.getMemoryState();

        // Si la pila está muy baja, no hay donde almacenar el registro SP
        if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUM48K
                && z80.getRegSP() < 0x4002) {
            throw new SnapshotException("SNA_REGSP_ERROR");
        }

        // The SNA format can handle Spectrum 16/48k and 128k (not +2)
        if (spectrum.getSpectrumModel().codeModel != MachineTypes.CodeModel.SPECTRUM48K &&
                spectrum.getSpectrumModel() != MachineTypes.SPECTRUM128K) {
            throw new SnapshotException("SNA_DONT_SUPPORT_PLUS3");
        }

        try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filename))) {

            byte snaHeader[] = new byte[27];
            snaHeader[0] = (byte) z80.getRegI();
            snaHeader[1] = (byte) z80.getRegLx();
            snaHeader[2] = (byte) z80.getRegHx();
            snaHeader[3] = (byte) z80.getRegEx();
            snaHeader[4] = (byte) z80.getRegDx();
            snaHeader[5] = (byte) z80.getRegCx();
            snaHeader[6] = (byte) z80.getRegBx();
            snaHeader[7] = (byte) z80.getRegFx();
            snaHeader[8] = (byte) z80.getRegAx();
            snaHeader[9] = (byte) z80.getRegL();
            snaHeader[10] = (byte) z80.getRegH();
            snaHeader[11] = (byte) z80.getRegE();
            snaHeader[12] = (byte) z80.getRegD();
            snaHeader[13] = (byte) z80.getRegC();
            snaHeader[14] = (byte) z80.getRegB();
            snaHeader[15] = (byte) z80.getRegIY();
            snaHeader[16] = (byte) (z80.getRegIY() >>> 8);
            snaHeader[17] = (byte) z80.getRegIX();
            snaHeader[18] = (byte) (z80.getRegIX() >>> 8);

            snaHeader[19] = (byte) (z80.isIFF2() ?  0x04 : 0x00);

            snaHeader[20] = (byte) z80.getRegR();
            snaHeader[21] = (byte) z80.getRegF();
            snaHeader[22] = (byte) z80.getRegA();

            int regSP = z80.getRegSP();
            if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                regSP = (regSP - 2) & 0xffff;
            }

            snaHeader[23] = (byte) regSP;
            snaHeader[24] = (byte) (regSP >>> 8);
            snaHeader[25] = (byte) z80.getIM().ordinal();
            snaHeader[26] = (byte) spectrum.getBorder();

            fOut.write(snaHeader, 0, snaHeader.length);

            byte[] buffer;
            if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                buffer = new byte[0xC000];
                System.arraycopy(memory.getPageRam(5), 0, buffer, 0, 0x4000);
                System.arraycopy(memory.getPageRam(2), 0, buffer, 0x4000, 0x4000);
                System.arraycopy(memory.getPageRam(0), 0, buffer, 0x8000, 0x4000);

                regSP -= 0x4000;
                buffer[regSP] = (byte) z80.getRegPC();
                regSP = (regSP + 1) & 0xffff;
                buffer[regSP] = (byte) (z80.getRegPC() >>> 8);
                fOut.write(buffer, 0, buffer.length);
            }

            if (spectrum.getSpectrumModel() == MachineTypes.SPECTRUM128K) {
                // Salvamos la página de la pantalla 0x4000-0x7FFF (5)
                buffer = memory.getPageRam(5);
                fOut.write(buffer, 0, buffer.length);
                // Salvamos la página 0x8000-0xBFFF (2)
                buffer = memory.getPageRam(2);
                fOut.write(buffer, 0, buffer.length);
                // Salvamos la página en 0xC000-0xFFFF
                buffer = memory.getPageRam((spectrum.getPort7ffd() & 0x07));
                fOut.write(buffer, 0, buffer.length);

                boolean saved[] = new boolean[8];
                saved[2] = saved[5] = true;
                fOut.write(z80.getRegPC());
                fOut.write(z80.getRegPC() >>> 8);
                fOut.write(spectrum.getPort7ffd());
                fOut.write(0x00); // La ROM del TR-DOS no está paginada
                saved[spectrum.getPort7ffd() & 0x07] = true;
                for (int page = 0; page < 8; page++) {
                    if (!saved[page]) {
                        buffer = memory.getPageRam(page);
                        fOut.write(buffer, 0, buffer.length);
                    }
                }
            }

        } catch (final IOException ex) {
            throw new SnapshotException("FILE_WRITE_ERROR", ex);
        }
        return true;
    }

}
