/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package snapshots;

import machine.Keyboard.JoystickModel;
import machine.MachineTypes;
import z80core.Z80.IntMode;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 *
 * @author jsanchez
 */
public class SnapshotSP implements SnapshotFile {
    private SpectrumState spectrum;
    private Z80State z80;
    private MemoryState memory;

    @Override
    public SpectrumState load(File filename) throws SnapshotException {
        spectrum = new SpectrumState();

        try (BufferedInputStream  fIn = new BufferedInputStream(new FileInputStream(filename))){

            int spLen = fIn.available();
            switch (spLen) {
                case 16422: // 16K
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUM16K);
                    break;
                case 49190: // 48k
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUM48K);
                    break;
                default:
                    throw new SnapshotException("FILE_SIZE_ERROR");
            }

            byte[] spHeader = new byte[38];
            int count = 0;
            while (count != -1 && count < spHeader.length) {
                count += fIn.read(spHeader, count, spHeader.length - count);
            }

            if (count != spHeader.length || spHeader[0] != 'S' || spHeader[1] != 'P') {
                throw new SnapshotException("FILE_SIZE_ERROR");
            }

            spLen = (spHeader[2] & 0xff) | ((spHeader[3] & 0xff) << 8);
            int start = (spHeader[4] & 0xff) | ((spHeader[5] & 0xff) << 8);
            if ((spLen != 16384 && spLen != 49152) || start != 16384) {
                throw new SnapshotException("FILE_SIZE_ERROR");
            }

            z80 = new Z80State();
            spectrum.setZ80State(z80);

            z80.setRegC(spHeader[6]);
            z80.setRegB(spHeader[7]);
            z80.setRegE(spHeader[8]);
            z80.setRegD(spHeader[9]);
            z80.setRegL(spHeader[10]);
            z80.setRegH(spHeader[11]);
            z80.setRegF(spHeader[12]);
            z80.setRegA(spHeader[13]);
            z80.setRegIX((spHeader[14] & 0xff) | (spHeader[15] << 8));
            z80.setRegIY((spHeader[16] & 0xff) | (spHeader[17] << 8));
            z80.setRegCx(spHeader[18]);
            z80.setRegBx(spHeader[19]);
            z80.setRegEx(spHeader[20]);
            z80.setRegDx(spHeader[21]);
            z80.setRegLx(spHeader[22]);
            z80.setRegHx(spHeader[23]);
            z80.setRegFx(spHeader[24]);
            z80.setRegAx(spHeader[25]);
            z80.setRegR(spHeader[26]);
            z80.setRegI(spHeader[27]);
            z80.setRegSP((spHeader[28] & 0xff) | (spHeader[29] << 8));
            z80.setRegPC((spHeader[30] & 0xff) | (spHeader[31] << 8));
            // bytes 32 & 33 unused
            spectrum.setBorder(spHeader[34] & 0xff);
            // byte 35 unused
            z80.setIFF1((spHeader[36] & 0x01) != 0);
            z80.setIFF2((spHeader[36] & 0x04) != 0);
            if ((spHeader[36] & 0x08) != 0) {
                z80.setIM(IntMode.IM0);
            } else {
                if ((spHeader[36] & 0x02) != 0) {
                    z80.setIM(IntMode.IM2);
                } else {
                    z80.setIM(IntMode.IM1);
                }
            }
            // byte 37 unused

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


            if (spLen == 49152) { // 48K
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
            }

            spectrum.setEnabledAY(false);
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
        throw new SnapshotException("FILE_WRITE_ERROR");
    }

}
