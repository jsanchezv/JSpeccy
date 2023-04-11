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
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jsanchez
 */
public class SnapshotZ80 implements SnapshotFile {

    private SpectrumState spectrum;
    private Z80State z80;
    private MemoryState memory;
    private AY8912State ay8912;

    private int uncompressZ80(BufferedInputStream fIn, byte buffer[], int length) {
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
        } catch (final IOException ex) {
            Logger.getLogger(SnapshotZ80.class.getName()).log(Level.SEVERE, null, ex);
            return 0;
        }
        return address;
    }

    private int countRepeatedByte(int page, int address, byte value) {
        int count = 0;

        while (address < 0x4000 && count < 254
                && value == memory.readByte(page, address++)) {
            count++;
        }

        return count;
    }

    private int compressPageZ80(byte buffer[], int page) {
        int address = 0;
        int addrDst = 0;
        int nReps;
        byte value;

        while (address < 0x4000) {
            value = memory.readByte(page, address++);
            nReps = countRepeatedByte(page, address, value);
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

    @Override
    public SpectrumState load(File filename) throws SnapshotException {
        spectrum = new SpectrumState();

        try (BufferedInputStream fIn = new BufferedInputStream(new FileInputStream(filename))) {

            if (fIn.available() < 30) {
                throw new SnapshotException("FILE_SIZE_ERROR");
            }

            byte z80Header1[] = new byte[30];
            int count = 0;
            while (count != -1 && count < z80Header1.length) {
                count += fIn.read(z80Header1, count, z80Header1.length - count);
            }

            if (count != z80Header1.length) {
                throw new SnapshotException("FILE_READ_ERROR");
            }

            z80 = new Z80State();
            spectrum.setZ80State(z80);

            z80.setRegA(z80Header1[0]);
            z80.setRegF(z80Header1[1]);
            z80.setRegC(z80Header1[2]);
            z80.setRegB(z80Header1[3]);
            z80.setRegL(z80Header1[4]);
            z80.setRegH(z80Header1[5]);
            z80.setRegPC((z80Header1[6] & 0xff) | (z80Header1[7] << 8));
            z80.setRegSP((z80Header1[8] & 0xff) | (z80Header1[9] << 8));
            z80.setRegI(z80Header1[10]);

            int regR = z80Header1[11] & 0x7f;
            if ((z80Header1[12] & 0x01) != 0) {
                regR |= 0x80;
            }
            z80.setRegR(regR);

            spectrum.setBorder((z80Header1[12] >>> 1) & 0x07);

            z80.setRegE(z80Header1[13]);
            z80.setRegD(z80Header1[14]);
            z80.setRegCx(z80Header1[15]);
            z80.setRegBx(z80Header1[16]);
            z80.setRegEx(z80Header1[17]);
            z80.setRegDx(z80Header1[18]);
            z80.setRegLx(z80Header1[19]);
            z80.setRegHx(z80Header1[20]);
            z80.setRegAx(z80Header1[21]);
            z80.setRegFx(z80Header1[22]);
            z80.setRegIY((z80Header1[23] & 0xff) | (z80Header1[24] << 8));
            z80.setRegIX((z80Header1[25] & 0xff) | (z80Header1[26] << 8));
            z80.setIFF1(z80Header1[27] != 0);
            z80.setIFF2(z80Header1[28] != 0);

            switch (z80Header1[29] & 0x03) {
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

            spectrum.setIssue2((z80Header1[29] & 0x04) != 0);

            switch ((z80Header1[29] & 0xC0)) {
                case 0: // Cursor/AGF/Protek Joystick
                    spectrum.setJoystick(JoystickModel.CURSOR);
                    break;
                case 0x40: // Kempston joystick
                    spectrum.setJoystick(JoystickModel.KEMPSTON);
                    break;
                case 0x80:
                    spectrum.setJoystick(JoystickModel.SINCLAIR1);
                    break;
                case 0xC0:
                    spectrum.setJoystick(JoystickModel.SINCLAIR2);
                    break;
                default:
                    spectrum.setJoystick(JoystickModel.NONE);
            }

            memory = new MemoryState();
            spectrum.setMemoryState(memory);

            // Si regPC != 0, es un z80 v1.0
            if (z80.getRegPC() != 0) {
                byte[] pageBuffer = new byte[0x4000];
                spectrum.setSpectrumModel(MachineTypes.SPECTRUM48K);
                if ((z80Header1[12] & 0x20) == 0) { // el bloque no está comprimido

                    // Cargamos la página de la pantalla 0x4000-0x7FFF (5)
                    count = 0;
                    while (count != -1 && count < 0x4000) {
                        count += fIn.read(pageBuffer, count, 0x4000 - count);
                    }

                    if (count != 0x4000) {
                        throw new SnapshotException("FILE_READ_ERROR");
                    }
                    memory.setPageRam(5, pageBuffer);


                    // Cargamos la página 0x8000-0xBFFF (2)
                    pageBuffer = new byte[0x4000];
                    count = 0;
                    while (count != -1 && count < 0x4000) {
                        count += fIn.read(pageBuffer, count, 0x4000 - count);
                    }

                    if (count != 0x4000) {
                        throw new SnapshotException("FILE_READ_ERROR");
                    }
                    memory.setPageRam(2, pageBuffer);

                    // Cargamos la página 0xC000-0xFFFF (0)
                    pageBuffer = new byte[0x4000];
                    count = 0;
                    while (count != -1 && count < 0x4000) {
                        count += fIn.read(pageBuffer, count, 0x4000 - count);
                    }

                    if (count != 0x4000) {
                        throw new SnapshotException("FILE_READ_ERROR");
                    }

                    memory.setPageRam(0, pageBuffer);
                } else {
                    byte buffer[] = new byte[0xC000];
                    int len = uncompressZ80(fIn, buffer, buffer.length);
                    if (len != 0xC000 || fIn.available() != 4) {
                        throw new SnapshotException("FILE_READ_ERROR");
                    }

                    memory.setPageRam(5, Arrays.copyOfRange(buffer, 0, 0x4000));
                    memory.setPageRam(2, Arrays.copyOfRange(buffer, 0x4000, 0x8000));
                    memory.setPageRam(0, Arrays.copyOfRange(buffer, 0x8000, 0xC000));
                }
            } else {
                // Z80 v2 & v3
                int hdrLen = fIn.read() | (fIn.read() << 8) & 0xffff;
                if (hdrLen != 23 && hdrLen != 54 && hdrLen != 55) {
                    throw new SnapshotException("FILE_SIZE_ERROR");
                }

                byte z80Header2[] = new byte[hdrLen];
                count = 0;
                while (count != -1 && count < z80Header2.length) {
                    count += fIn.read(z80Header2, count, z80Header2.length - count);
                }

                if (count != z80Header2.length) {
                    throw new SnapshotException("FILE_READ_ERROR");
                }

                z80.setRegPC((z80Header2[0] & 0xff) | (z80Header2[1] << 8));

                boolean modifiedHW = (z80Header2[5] & 0x80) != 0;
                if (hdrLen == 23) { // Z80 v2
                    switch (z80Header2[2] & 0xff) {
                        case 0: // 48k
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM16K);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM48K);
                            }

                            spectrum.setConnectedIF1(false);
                            break;
                        case 1: // 48k + IF.1
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM16K);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM48K);
                            }

                            spectrum.setConnectedIF1(true);
                            if ((z80Header2[4] & 0xff) == 0xff) {
                                memory.setIF1RomPaged(true);
                            }
                            break;
                        case 3: // 128k
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM128K);
                            }

                            spectrum.setConnectedIF1(false);
                            break;
                        case 4: // 128k + IF.1
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM128K);
                            }

                            spectrum.setConnectedIF1(true);
                            if ((z80Header2[4] & 0xff) == 0xff) {
                                memory.setIF1RomPaged(true);
                            }
                            break;
                        case 7: // +3
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2A);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS3);
                            }
                            break;
                        case 12: // +2
                            spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                            break;
                        case 13: // +2A
                            spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2A);
                            break;
                        default:
                            throw new SnapshotException("UNSUPPORTED_SNAPSHOT");
                    }
                } else { // Z80 v3
                    switch (z80Header2[2] & 0xff) {
                        case 0: // 48k
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM16K);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM48K);
                            }

                            spectrum.setConnectedIF1(false);
                            break;
                        case 1: // 48k + IF.1
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM16K);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM48K);
                            }

                            spectrum.setConnectedIF1(true);
                            if ((z80Header2[4] & 0xff) == 0xff) {
                                memory.setIF1RomPaged(true);
                            }
                            break;
                        case 4: // 128k
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM128K);
                            }

                            spectrum.setConnectedIF1(false);
                            break;
                        case 5: // 128k + IF.1
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUM128K);
                            }

                            spectrum.setConnectedIF1(true);
                            if ((z80Header2[4] & 0xff) == 0xff) {
                                memory.setIF1RomPaged(true);
                            }
                            break;
                        case 7: // +3
                            if (modifiedHW) {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2A);
                            } else {
                                spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS3);
                            }
                            break;
                        case 12: // +2
                            spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                            break;
                        case 13: // +2A
                            spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2A);
                            break;
                        default:
                            throw new SnapshotException("UNSUPPORTED_SNAPSHOT");
//                            System.out.println(String.format("SnapshotV3 model: %d",
//                                    z80Header2[2]));
                    }
                }

                spectrum.setPort7ffd(z80Header2[3]);

                spectrum.setEnabledAY(true);
                if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                    spectrum.setEnabledAYon48k((z80Header2[5] & 0x04) != 0);
                    spectrum.setEnabledAY(spectrum.isEnabledAYon48k());
                }

                spectrum.setEnabledAYon48k((z80Header2[5] & 0x04) != 0);

                ay8912 = new AY8912State();
                spectrum.setAY8912State(ay8912);

                ay8912.setAddressLatch(z80Header2[6]);
                int regAY[] = new int[16];
                for (int idx = 0; idx < 16; idx++) {
                    regAY[idx] = z80Header2[7 + idx] & 0xff;
                }
                ay8912.setRegAY(regAY);

                spectrum.setPort1ffd(hdrLen == 55 ? z80Header2[54] : 0);

                byte[] buffer;
                while (fIn.available() > 0) {
                    buffer = new byte[0x4000];
                    int blockLen = fIn.read() | (fIn.read() << 8) & 0xffff;
                    int ramPage = fIn.read() & 0xff;
                    if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
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
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        memory.setPageRam(ramPage, buffer);
                    } else {
                        int len = uncompressZ80(fIn, buffer, 0x4000);
                        if (len != 0x4000) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        memory.setPageRam(ramPage, buffer);
                    }
                }
            }

            spectrum.setTstates(0);

        } catch (final IOException ex) {
            throw new SnapshotException("FILE_READ_ERROR", ex);
        }

        return spectrum;
    }

    // Solo se graban Z80's versión 3
    @Override
    public boolean save(File filename, SpectrumState state) throws SnapshotException {
        spectrum = state;
        z80 = spectrum.getZ80State();
        memory = spectrum.getMemoryState();
        ay8912 = spectrum.getAY8912State();

        try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filename))) {

            byte z80HeaderV3[] = new byte[87];
            z80HeaderV3[0] = (byte) z80.getRegA();
            z80HeaderV3[1] = (byte) z80.getRegF();
            z80HeaderV3[2] = (byte) z80.getRegC();
            z80HeaderV3[3] = (byte) z80.getRegB();
            z80HeaderV3[4] = (byte) z80.getRegL();
            z80HeaderV3[5] = (byte) z80.getRegH();
            // Bytes 6 y 7 se dejan a 0, si regPC==0, el Z80 es version 2 o 3
            z80HeaderV3[8] = (byte) z80.getRegSP();
            z80HeaderV3[9] = (byte) (z80.getRegSP() >>> 8);
            z80HeaderV3[10] = (byte) z80.getRegI();
            z80HeaderV3[11] = (byte) (z80.getRegR() & 0x7f);
            z80HeaderV3[12] = (byte) (spectrum.getBorder() << 1);
            if (z80.getRegR() > 0x7f) {
                z80HeaderV3[12] |= 0x01;
            }
            z80HeaderV3[13] = (byte) z80.getRegE();
            z80HeaderV3[14] = (byte) z80.getRegD();
            z80HeaderV3[15] = (byte) z80.getRegCx();
            z80HeaderV3[16] = (byte) z80.getRegBx();
            z80HeaderV3[17] = (byte) z80.getRegEx();
            z80HeaderV3[18] = (byte) z80.getRegDx();
            z80HeaderV3[19] = (byte) z80.getRegLx();
            z80HeaderV3[20] = (byte) z80.getRegHx();
            z80HeaderV3[21] = (byte) z80.getRegAx();
            z80HeaderV3[22] = (byte) z80.getRegFx();
            z80HeaderV3[23] = (byte) z80.getRegIY();
            z80HeaderV3[24] = (byte) (z80.getRegIY() >>> 8);
            z80HeaderV3[25] = (byte) z80.getRegIX();
            z80HeaderV3[26] = (byte) (z80.getRegIX() >>> 8);
            z80HeaderV3[27] = (byte) (z80.isIFF1() ? 0x01 : 0x00);
            z80HeaderV3[28] = (byte) (z80.isIFF2() ? 0x01 : 0x00);
            z80HeaderV3[29] = (byte) z80.getIM().ordinal();

            if (spectrum.isIssue2()) {
                z80HeaderV3[29] |= 0x04;
            }

            switch (spectrum.getJoystick()) {
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
            z80HeaderV3[32] = (byte) z80.getRegPC();
            z80HeaderV3[33] = (byte) (z80.getRegPC() >>> 8);

            switch (spectrum.getSpectrumModel()) {
                case SPECTRUM16K:
                    z80HeaderV3[37] |= 0x80; // modified HW, 48k --> 16k
//                    break;
                case SPECTRUM48K:
                    z80HeaderV3[34] = (byte) (spectrum.isConnectedIF1() ? 1 : 0);
                    break;
                case SPECTRUM128K:
                    z80HeaderV3[34] = (byte) (spectrum.isConnectedIF1() ? 5 : 4);
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

            if (spectrum.getSpectrumModel().codeModel != MachineTypes.CodeModel.SPECTRUM48K) {
                z80HeaderV3[35] = (byte) spectrum.getPort7ffd();
            }

            if (memory.isIF1RomPaged()) {
                z80HeaderV3[36] = (byte) 0xff;
            }

            if (spectrum.isEnabledAY()) {
                z80HeaderV3[37] |= 0x04;
                z80HeaderV3[38] = (byte) ay8912.getAddressLatch();

                int regAY[] = ay8912.getRegAY();
                for (int reg = 0; reg < 16; reg++) {
                    z80HeaderV3[39 + reg] = (byte) regAY[reg];
                }
            }

            if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                z80HeaderV3[86] = (byte) spectrum.getPort1ffd();
            }

            fOut.write(z80HeaderV3, 0, z80HeaderV3.length);

            byte buffer[] = new byte[0x4000];
            int bufLen;
            if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                // Página 5, que corresponde a 0x4000-0x7FFF
                bufLen = compressPageZ80(buffer, 5);
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
                bufLen = compressPageZ80(buffer, 2);
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
                bufLen = compressPageZ80(buffer, 0);
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
                    bufLen = compressPageZ80(buffer, page);
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

        } catch (final IOException ex) {
            throw new SnapshotException("FILE_WRITE_ERROR", ex);
        }

        return true;
    }
}
