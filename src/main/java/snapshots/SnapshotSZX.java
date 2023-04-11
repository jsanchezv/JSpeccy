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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 *
 * @author jsanchez
 */
public class SnapshotSZX implements SnapshotFile {

    private SpectrumState spectrum;
    private Z80State z80;
    private MemoryState memory;
    private AY8912State ay8912;

    private boolean tapeEmbedded, tapeLinked;
    private byte tapeData[];
    private int tapeBlock;
    private String tapeName, tapeExtension;

    // SZX Definitions
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
    private static final int ZXSTZF_FSET = 4;  // new flag for v1.5 spec.
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

    // LEC RAM Extension (v1.5 SZX Spec)
    private static final int ZXSTBID_LEC = 0x0043454C;      // LEC\0
    private static final int ZXSTBID_LECRAMPAGE = 0x5052434C;  // LCRP
    private static final int ZXSTLCRPF_COMPRESSED = 0x01;

    // SpectraNET blocks (v1.5 SZX Spec)
    private static final int ZXSTBID_SPECTRANET = 0x54454E53; // SNET
    private static final int ZXSTSNETF_PAGED = 0x0001;
    private static final int ZXSTSNETF_PAGED_VIA_IO = 0x0002;
    private static final int ZXSTSNETF_PROGRAMMABLE_TRAP_ACTIVE = 0x0004;
    private static final int ZXSTSNETF_PROGRAMMABLE_TRAP_MSB = 0x0008;
    private static final int ZXSTSNETF_ALL_DISABLED = 0x0010;
    private static final int ZXSTSNETF_RST8_DISABLED = 0x0020;
    private static final int ZXSTSNETF_DENY_DOWNSTREAM_A15 = 0x0040;
    private static final int ZXSTSNETF_FLASH_COMPRESSED = 0x0080;
    private static final int ZXSTSNETF_RAM_COMPRESSED = 0x0100;

    /**
     * @return the tapeEmbedded
     */
    public boolean isTapeEmbedded() {
        return tapeEmbedded;
    }

    /**
     * @param tapeEmbedded the tapeEmbedded to set
     */
    public void setTapeEmbedded(boolean tapeEmbedded) {
        this.tapeEmbedded = tapeEmbedded;
    }

    /**
     * @return the tapeLinked
     */
    public boolean isTapeLinked() {
        return tapeLinked;
    }

    /**
     * @param tapeLinked the tapeLinked to set
     */
    public void setTapeLinked(boolean tapeLinked) {
        this.tapeLinked = tapeLinked;
    }

    /**
     * @return the tapeData
     */
    public byte[] getTapeData() {
        return tapeData;
    }

    /**
     * @param tapeData the tapeData to set
     */
    public void setTapeData(byte[] tapeData) {
        this.tapeData = tapeData;
    }

    /**
     * @return the tapeBlock
     */
    public int getTapeBlock() {
        return tapeBlock;
    }

    /**
     * @param tapeBlock the tapeBlock to set
     */
    public void setTapeBlock(int tapeBlock) {
        this.tapeBlock = tapeBlock & 0xffff;
    }

    /**
     * @return the tapeName
     */
    public String getTapeName() {
        return tapeName;
    }

    /**
     * @param tapeName the tapeName to set
     */
    public void setTapeName(String tapeName) {
        this.tapeName = tapeName;
    }

    /**
     * @return the tapeExtension
     */
    public String getTapeExtension() {
        return tapeExtension;
    }

    /**
     * @param tapeExtension the tapeExtension to set
     */
    public void setTapeExtension(String tapeExtension) {
        this.tapeExtension = tapeExtension;
    }

    private int dwMagicToInt(byte[] dwMagic) {
        int value0 = dwMagic[0] & 0xff;
        int value1 = (dwMagic[1] & 0xff) << 8;
        int value2 = (dwMagic[2] & 0xff) << 16;
        int value3 = (dwMagic[3] & 0xff) << 24;

        return (value3 | value2 | value1 | value0);
    }

    @Override
    public SpectrumState load(File filename) throws SnapshotException {

        byte dwMagic[] = new byte[4];
        byte dwSize[] = new byte[4];
        byte szxMajorVer, szxMinorVer;
        int readed, szxId, szxLen;
        ByteArrayInputStream bais;
        InflaterInputStream iis;
        byte chData[];

        spectrum = new SpectrumState();

        spectrum.setJoystick(JoystickModel.NONE);

        try (BufferedInputStream fIn = new BufferedInputStream(new FileInputStream(filename))) {

            readed = fIn.read(dwMagic);
            if (readed != dwMagic.length || dwMagicToInt(dwMagic) != ZXST_HEADER) {
                throw new SnapshotException("NOT_SNAPSHOT_FILE");
            }

            readed = fIn.read(dwSize);
            if (readed != dwSize.length) {
                throw new SnapshotException("NOT_SNAPSHOT_FILE");
            }

            szxMajorVer = dwSize[0];
            szxMinorVer = dwSize[1];
            switch (dwSize[2] & 0xff) {
                case ZXSTMID_16K:
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUM16K);
                    break;
                case ZXSTMID_48K:
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUM48K);
                    break;
                case ZXSTMID_128K:
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUM128K);
                    break;
                case ZXSTMID_PLUS2:
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2);
                    break;
                case ZXSTMID_PLUS2A:
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS2A);
                    break;
                case ZXSTMID_PLUS3:
                    spectrum.setSpectrumModel(MachineTypes.SPECTRUMPLUS3);
                    break;
                default:
                    throw new SnapshotException("UNSUPPORTED_SNAPSHOT");
            }

            while (fIn.available() > 0) {
                readed = fIn.read(dwMagic);
                readed += fIn.read(dwSize);
                if (readed != 8) {
                    throw new SnapshotException("FILE_READ_ERROR");
                }

                szxId = dwMagicToInt(dwMagic);
//                System.out.println(new String(dwMagic));
                szxLen = dwMagicToInt(dwSize);
                if (szxLen < 1) {
                    throw new SnapshotException("FILE_READ_ERROR");
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
                                throw new SnapshotException("FILE_READ_ERROR");
                            }
//                            System.out.println(String.format("Creator Data: %s",
//                                new String(chData)));
                        }
                        break;
                    case ZXSTBID_Z80REGS:
                        if (szxLen != 37) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        byte z80Regs[] = new byte[szxLen];
                        readed = fIn.read(z80Regs);
                        if (readed != szxLen) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        z80 = new Z80State();
                        spectrum.setZ80State(z80);

                        z80.setRegF(z80Regs[0]);
                        z80.setRegA(z80Regs[1]);
                        z80.setRegC(z80Regs[2]);
                        z80.setRegB(z80Regs[3]);
                        z80.setRegE(z80Regs[4]);
                        z80.setRegD(z80Regs[5]);
                        z80.setRegL(z80Regs[6]);
                        z80.setRegH(z80Regs[7]);
                        z80.setRegFx(z80Regs[8]);
                        z80.setRegAx(z80Regs[9]);
                        z80.setRegCx(z80Regs[10]);
                        z80.setRegBx(z80Regs[11]);
                        z80.setRegEx(z80Regs[12]);
                        z80.setRegDx(z80Regs[13]);
                        z80.setRegLx(z80Regs[14]);
                        z80.setRegHx(z80Regs[15]);
                        z80.setRegIX((z80Regs[16] & 0xff) | (z80Regs[17] << 8));
                        z80.setRegIY((z80Regs[18] & 0xff) | (z80Regs[19] << 8));
                        z80.setRegSP((z80Regs[20] & 0xff) | (z80Regs[21] << 8));
                        z80.setRegPC((z80Regs[22] & 0xff) | (z80Regs[23] << 8));
                        z80.setRegI(z80Regs[24]);
                        z80.setRegR(z80Regs[25]);
                        z80.setIFF1((z80Regs[26] & 0x01) != 0);
                        z80.setIFF2((z80Regs[27] & 0x01) != 0);

                        switch (z80Regs[28] & 0x03) {
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

                        spectrum.setTstates((int) (((z80Regs[32] & 0xff) << 24) | ((z80Regs[31] & 0xff) << 16)
                                | ((z80Regs[30] & 0xff) << 8) | (z80Regs[29] & 0xff)));

                        // ignore the chHoldIntReqCycles z80Regs[33]

                        switch (z80Regs[34]) {
                            case ZXSTZF_EILAST:
                                z80.setPendingEI(true);
                                break;
                            case ZXSTZF_HALTED:
                                z80.setHalted(true);
                                break;
                            default:
                                z80.setPendingEI(false);
                                z80.setHalted(false);
                        }

                        if (szxMajorVer == 1 && szxMinorVer > 3) {
                            z80.setMemPtr((z80Regs[35] & 0xff) | (z80Regs[36] << 8));
                        }

                        if (szxMajorVer == 1 && szxMinorVer > 4) {
                            z80.setFlagQ((z80Regs[34] & ZXSTZF_FSET) != 0);
                        }
                        break;
                    case ZXSTBID_SPECREGS:
                        if (szxLen != 8) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        byte specRegs[] = new byte[szxLen];
                        readed = fIn.read(specRegs);
                        if (readed != szxLen) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        spectrum.setBorder(specRegs[0]);
                        spectrum.setPort7ffd(specRegs[1]);
                        spectrum.setPort1ffd(specRegs[2]);
                        break;
                    case ZXSTBID_KEYBOARD:
                        if (szxLen != 5) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        byte keyb[] = new byte[szxLen];
                        readed = fIn.read(keyb);
                        if (readed != szxLen) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        spectrum.setIssue2((keyb[0] & ZXSTKF_ISSUE2) != 0);
                        switch (keyb[4] & 0xff) {
                            case ZXSKJT_KEMPSTON:
                                spectrum.setJoystick(JoystickModel.KEMPSTON);
                                break;
                            case ZXSKJT_FULLER:
                                spectrum.setJoystick(JoystickModel.FULLER);
                                break;
                            case ZXSKJT_CURSOR:
                                spectrum.setJoystick(JoystickModel.CURSOR);
                                break;
                            case ZXSKJT_SINCLAIR1:
                                spectrum.setJoystick(JoystickModel.SINCLAIR1);
                                break;
                            case ZXSKJT_SINCLAIR2:
                                spectrum.setJoystick(JoystickModel.SINCLAIR2);
                                break;
                            default:
                                spectrum.setJoystick(JoystickModel.NONE);
                        }
                        break;
                    case ZXSTBID_AY:
                        if (szxLen != 18) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        byte ayRegs[] = new byte[szxLen];
                        readed = fIn.read(ayRegs);
                        if (readed != szxLen) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        spectrum.setEnabledAY(true);
                        if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUM48K
                                && (ayRegs[0] & ZXSTAYF_128AY) == 0) {
                            spectrum.setEnabledAYon48k(false);
                        }

                        ay8912 = new AY8912State();
                        spectrum.setAY8912State(ay8912);

                        int regAY[] = new int[16];
                        ay8912.setAddressLatch(ayRegs[1]);
                        for (int idx = 0; idx < 16; idx++) {
                            regAY[idx] = ayRegs[2 + idx] & 0xff;
                        }
                        ay8912.setRegAY(regAY);
                        break;
                    case ZXSTBID_RAMPAGE:
                        if (memory == null) {
                            memory = new MemoryState();
                            spectrum.setMemoryState(memory);
                        }

                        byte ramPage[] = new byte[3];
                        readed = fIn.read(ramPage);
                        if (readed != ramPage.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        szxLen -= 3;
                        if (szxLen > 0x4000) {
                            throw new SnapshotException("SZX_RAMP_SIZE_ERROR");
                        }

                        chData = new byte[szxLen];
                        readed = fIn.read(chData);
                        if (readed != szxLen) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        if ((ramPage[0] & ZXSTRF_COMPRESSED) == 0) {
                            memory.setPageRam(ramPage[2] & 0x07, chData);
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
                            throw new SnapshotException("SZX_RAMP_SIZE_ERROR");
                        }
                        memory.setPageRam(ramPage[2] & 0x07, pageRAM);
                        break;
                    case ZXSTBID_MULTIFACE:
                        if (memory == null) {
                            memory = new MemoryState();
                            spectrum.setMemoryState(memory);
                        }

                        byte mf[] = new byte[2];
                        readed = fIn.read(mf);
                        if (readed != mf.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
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
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
//                        System.out.println("MF RAM readed bytes: " + readed);
                        if ((mf[1] & ZXSTMF_16KRAMMODE) != 0) {
                            spectrum.setMultiface(false);
                            break;  // Config. no soportada
                        }

                        spectrum.setMultiface(true);
                        if ((mf[0] & ZXSTMFM_128) != 0) {
                            memory.setMf128on48k(true);
                        }

                        if ((mf[1] & ZXSTMF_PAGEDIN) != 0) {
                            memory.setMultifacePaged(true);
                        }

                        if ((mf[1] & ZXSTMF_SOFTWARELOCKOUT) != 0) {
                            memory.setMultifaceLocked(true);
                        }

                        // MF RAM not commpressed
                        if ((mf[1] & ZXSTMF_COMPRESSED) == 0) {
                            if (chData.length == 8192) {
                                memory.setMultifaceRam(chData);
                            } else {
                                spectrum.setMultiface(false);
                            }
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
                            spectrum.setMultiface(false);
                            break;
                        }
                        memory.setMultifaceRam(mfRAM);
                        break;
                    case ZXSTBID_PALETTE:
                        if (szxLen != 66) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        spectrum.setULAPlusEnabled(true);
                        byte ULAplusRegs[] = new byte[szxLen];
                        readed = fIn.read(ULAplusRegs);
                        if (readed != szxLen) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        if (ULAplusRegs[0] == ZXSTPALETTE_ENABLED) {
                            spectrum.setULAPlusActive(true);
                        }

                        spectrum.setPaletteGroup(ULAplusRegs[1]);

                        int[] palette = new int[64];
                        for (int reg = 0; reg < 64; reg++) {
                            palette[reg] = ULAplusRegs[2 + reg] & 0xff;
                        }

                        spectrum.setULAPlusPalette(palette);
                        break;
                    case ZXSTBID_ZXTAPE:
                        byte tape[] = new byte[4];
                        readed = fIn.read(tape);
                        if (readed != tape.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        szxLen -= tape.length;
                        tapeBlock = ((tape[1] << 8) & 0xff00) | (tape[0] & 0xff);

                        byte qword[] = new byte[4];
                        readed = fIn.read(qword);
                        if (readed != qword.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        szxLen -= qword.length;
                        int uSize = dwMagicToInt(qword);

                        readed = fIn.read(qword);
                        if (readed != qword.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }
                        szxLen -= qword.length;
//                        int cSize = dwMagicToInt(qword);
//                        System.out.println(String.format("uSize: %d, cSize: %d",
//                                uSize, cSize));

                        byte szFileExtension[] = new byte[16];
                        readed = fIn.read(szFileExtension);
                        if (readed != szFileExtension.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
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
                                throw new SnapshotException("FILE_READ_ERROR");
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
                                throw new SnapshotException("FILE_READ_ERROR");
                            }
                            tapeName = new String(chData, 0, szxLen - 1);
//                            System.out.println("File linked: " + tapeName);
                            tapeLinked = true;
                        }
                        break;
                    case ZXSTBID_IF2ROM:
                        if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                            System.out.println(
                                    "SZX Error: +2a/+3 snapshot with IF-2 block. Skipping...");
                            while (szxLen > 0) {
                                szxLen -= fIn.skip(szxLen);
                            }
                            break;
                        }

                        if (memory == null) {
                            memory = new MemoryState();
                            spectrum.setMemoryState(memory);
                        }

                        byte dwCartSize[] = new byte[4];
                        readed = fIn.read(dwCartSize);
                        if (readed != dwCartSize.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
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
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        if (romLen == 0x4000) {
                            memory.setIF2RomPaged(true);
                            memory.setIF2Rom(chData);
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
                        memory.setIF2RomPaged(true);
                        memory.setIF2Rom(IF2Rom);
                        break;
                    case ZXSTBID_JOYSTICK:
                        if (szxLen != 6) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        while (szxLen > 0) {
                            szxLen -= fIn.skip(szxLen);
                        }
                        break;
                    case ZXSTBID_IF1:
                        if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUMPLUS3) {
                            System.out.println(
                                    "SZX Error: +2a/+3 snapshot with IF-1 block. Skipping...");
                            while (szxLen > 0) {
                                szxLen -= fIn.skip(szxLen);
                            }
                            break;
                        }

                        if (memory == null) {
                            memory = new MemoryState();
                            spectrum.setMemoryState(memory);
                        }

                        int if1Flags;
                        if1Flags = fIn.read() + fIn.read() * 256;
                        szxLen -= 2;
                        spectrum.setConnectedIF1((if1Flags & ZXSTIF1F_ENABLED) != 0);
                        memory.setIF1RomPaged((if1Flags & ZXSTIF1F_PAGED) != 0);

                        spectrum.setNumMicrodrives((byte)fIn.read());
                        szxLen--;

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
                    case ZXSTBID_LEC:
                        if (spectrum.getSpectrumModel() != MachineTypes.SPECTRUM48K) {
                            System.out.println(
                                    "SZX Error: LEC Block with a Spectrum model != 48k");
                            while (szxLen > 0) {
                                szxLen -= fIn.skip(szxLen);
                            }
                            break;
                        }

                        if (memory == null) {
                            memory = new MemoryState();
                            spectrum.setMemoryState(memory);
                        }

                        byte[] lecHeader = new byte[2];
                        readed = fIn.read(lecHeader);
                        if (readed != lecHeader.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        spectrum.setConnectedLec(true);
                        memory.setPortFD(lecHeader[0]);
                        break;
                    case ZXSTBID_LECRAMPAGE:
                        if (memory == null) {
                            memory = new MemoryState();
                            spectrum.setMemoryState(memory);
                        }

                        byte lecPage[] = new byte[3];
                        readed = fIn.read(lecPage);
                        if (readed != lecPage.length) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        szxLen -= 3;
                        if (szxLen > 0x8000) {
                            throw new SnapshotException("SZX_RAMP_SIZE_ERROR");
                        }

                        chData = new byte[szxLen];
                        readed = fIn.read(chData);
                        if (readed != szxLen) {
                            throw new SnapshotException("FILE_READ_ERROR");
                        }

                        if ((lecPage[0] & ZXSTLCRPF_COMPRESSED) == 0) {
                            memory.setLecPageRam(lecPage[2] & 0x0f, chData);
                            break;
                        }
                        // Compressed RAM page
                        bais = new ByteArrayInputStream(chData);
                        iis = new InflaterInputStream(bais);
                        byte lecRAM[] = new byte[0x8000];
                        readed = 0;
                        while (readed < lecRAM.length) {
                            int count = iis.read(lecRAM, readed, lecRAM.length - readed);
                            if (count == -1) {
                                break;
                            }
                            readed += count;
                        }
                        iis.close();
                        if (readed != 0x8000) {
                            throw new SnapshotException("SZX_RAMP_SIZE_ERROR");
                        }
                        memory.setLecPageRam(lecPage[2] & 0x0f, lecRAM);
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
                    case ZXSTBID_SPECTRANET:
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
                        String header = new String(dwMagic);
                        System.out.println(String.format("Unknown SZX block ID: %s", header));
                        throw new SnapshotException("FILE_READ_ERROR");
                }
            }
        } catch (final IOException ex) {
//            ex.printStackTrace();
            throw new SnapshotException("FILE_READ_ERROR");
        }

        return spectrum;
    }

    @Override
    public boolean save(File filename, SpectrumState state) throws SnapshotException {
        spectrum = state;
        z80 = spectrum.getZ80State();
        memory = spectrum.getMemoryState();
        ay8912 = spectrum.getAY8912State();

        try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filename))) {

            // SZX Header
            String blockID = "ZXST";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x01); // SZX major version
            fOut.write(0x05); // SZX minor version
            switch (spectrum.getSpectrumModel()) {
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
            blockID = "JSpeccy v0.95";
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
            z80r[0] = (byte) z80.getRegF();
            z80r[1] = (byte) z80.getRegA();
            z80r[2] = (byte) z80.getRegC();
            z80r[3] = (byte) z80.getRegB();
            z80r[4] = (byte) z80.getRegE();
            z80r[5] = (byte) z80.getRegD();
            z80r[6] = (byte) z80.getRegL();
            z80r[7] = (byte) z80.getRegH();
            z80r[8] = (byte) z80.getRegFx();
            z80r[9] = (byte) z80.getRegAx();
            z80r[10] = (byte) z80.getRegCx();
            z80r[11] = (byte) z80.getRegBx();
            z80r[12] = (byte) z80.getRegEx();
            z80r[13] = (byte) z80.getRegDx();
            z80r[14] = (byte) z80.getRegLx();
            z80r[15] = (byte) z80.getRegHx();
            z80r[16] = (byte) z80.getRegIX();
            z80r[17] = (byte) (z80.getRegIX() >>> 8);
            z80r[18] = (byte) z80.getRegIY();
            z80r[19] = (byte) (z80.getRegIY() >>> 8);
            z80r[20] = (byte) z80.getRegSP();
            z80r[21] = (byte) (z80.getRegSP() >>> 8);
            z80r[22] = (byte) z80.getRegPC();
            z80r[23] = (byte) (z80.getRegPC() >>> 8);
            z80r[24] = (byte) z80.getRegI();
            z80r[25] = (byte) z80.getRegR();
            if (z80.isIFF1()) {
                z80r[26] = 0x01;
            }
            if (z80.isIFF2()) {
                z80r[27] = 0x01;
            }
            z80r[28] = (byte) z80.getIM().ordinal();
            z80r[29] = (byte) spectrum.getTstates();
            z80r[30] = (byte) (spectrum.getTstates() >>> 8);
            z80r[31] = (byte) (spectrum.getTstates() >>> 16);
            z80r[32] = (byte) (spectrum.getTstates() >>> 24);

            // ignore the chHoldIntReqCycles z80r[33]

            if (z80.isPendingEI()) {
                z80r[34] = ZXSTZF_EILAST;
            }

            if (z80.isHalted()) {
                z80r[34] = ZXSTZF_HALTED;
            }

            if (z80.isFlagQ()) {
                z80r[34] |= ZXSTZF_FSET;
            }

            z80r[35] = (byte) z80.getMemPtr();
            z80r[36] = (byte) (z80.getMemPtr() >>> 8);
            fOut.write(z80r);

            // SZX SPECREGS block
            blockID = "SPCR";
            fOut.write(blockID.getBytes("US-ASCII"));
            fOut.write(0x08);
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);  // Z80R length block (8 bytes)
            byte[] specr = new byte[8];
            specr[0] = (byte) spectrum.getBorder();
            specr[1] = (byte) spectrum.getPort7ffd();
            specr[2] = (byte) spectrum.getPort1ffd();
            // ignore the chEff7 & chFe fields
            fOut.write(specr);

            boolean[] save = new boolean[8];
            switch (spectrum.getSpectrumModel()) {
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
            byte[] ram;
            ByteArrayOutputStream baos;
            DeflaterOutputStream dos;
            for (int page = 0; page < 8; page++) {
                if (save[page]) {
                    ram = memory.getPageRam(page);
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
            if (spectrum.isIssue2()) {
                fOut.write(ZXSTKF_ISSUE2);
            } else {
                fOut.write(0x00);
            }
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);
            switch (spectrum.getJoystick()) {
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
            if (spectrum.isEnabledAY()) {
                blockID = "AY\0\0";
                fOut.write(blockID.getBytes("US-ASCII"));
                fOut.write(0x12);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);  // AY length block (18 bytes)
                if (spectrum.getSpectrumModel().codeModel == MachineTypes.CodeModel.SPECTRUM48K) {
                    fOut.write(ZXSTAYF_128AY);
                } else {
                    fOut.write(0x00);
                }
                fOut.write(ay8912.getAddressLatch());
                int regAY[] = ay8912.getRegAY();
                for (int reg = 0; reg < 16; reg++) {
                    fOut.write(regAY[reg]);
                }
            }

            // SZX ULAplus block
            if (spectrum.isULAPlusEnabled()) {
                blockID = "PLTT";
                fOut.write(blockID.getBytes("US-ASCII"));
                fOut.write(0x42);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);  // PLTT length block (66 bytes)
                if (spectrum.isULAPlusActive()) {
                    fOut.write(ZXSTPALETTE_ENABLED);
                } else {
                    fOut.write(ZXSTPALETTE_DISABLED);
                }

                fOut.write(spectrum.getPaletteGroup());

                int[] palette = spectrum.getULAPlusPalette();
                for (int color = 0; color < 64; color++) {
                    fOut.write(palette[color]);
                }
            }

            // SZX Multiface block
            if (spectrum.isMultiface()) {
                blockID = "MFCE";
                fOut.write(blockID.getBytes("US-ASCII"));

                byte[] mfRam = memory.getMultifaceRam();
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

                if (memory.isMf128on48k()) {
                    fOut.write(ZXSTMFM_128);
                } else {
                    fOut.write(0x00);
                }

                int mfFlags = ZXSTMF_COMPRESSED;
                if (memory.isMultifacePaged()) {
                    mfFlags |= ZXSTMF_PAGEDIN;
                }
                if (memory.isMultifaceLocked()
                        && (spectrum.getSpectrumModel().codeModel != MachineTypes.CodeModel.SPECTRUM48K
                        || memory.isMultifacePaged())) {
                    mfFlags |= ZXSTMF_SOFTWARELOCKOUT;
                }
                fOut.write(mfFlags);
                baos.writeTo(fOut);
            }

            // SZX IF2 ROM Block
            if (memory.isIF2RomPaged()
                    && spectrum.getSpectrumModel().codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
                blockID = "IF2R";
                fOut.write(blockID.getBytes("US-ASCII"));

                byte[] if2Rom = memory.getIF2Rom();
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
                try (BufferedInputStream fIn = new BufferedInputStream(new FileInputStream(tapeName))) {
                    tapeData = new byte[fIn.available()];
                    fIn.read(tapeData);
                }
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
            if (spectrum.isConnectedIF1()
                    && spectrum.getSpectrumModel().codeModel != MachineTypes.CodeModel.SPECTRUMPLUS3) {
                blockID = "IF1\0";
                fOut.write(blockID.getBytes("US-ASCII"));

                fOut.write(0x28);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00);  // IF1 block length (40 bytes without ROM)

                byte if1Flag = ZXSTIF1F_ENABLED;
                if (memory.isIF1RomPaged()) {
                    if1Flag |= ZXSTIF1F_PAGED;
                }
                fOut.write(if1Flag);
                fOut.write(spectrum.getNumMicrodrives());
                byte reserved[] = new byte[38]; // 35 reserved + wRomSize + chRomData[1]
                fOut.write(reserved);
            }

            // LEC RAM Extension Block
            if (spectrum.isConnectedLec() && spectrum.getSpectrumModel() == MachineTypes.SPECTRUM48K) {
                blockID = "LEC\0";
                fOut.write(blockID.getBytes("US-ASCII"));
                fOut.write(0x02);
                fOut.write(0x00);
                fOut.write(0x00);
                fOut.write(0x00); // LEC block size (2 bytes)

                fOut.write(memory.getPortFD()); // active page

                fOut.write(0x10); // 16 Ram pages

                // LEC RAM Pages (0 to 15)
                blockID = "LCRP";
                for (int page = 0; page < 16; page++) {
                    if (spectrum.getMemoryState().getLecPageRam(page) != null) {
                        ram = memory.getLecPageRam(page);
                        baos = new ByteArrayOutputStream();
                        dos = new DeflaterOutputStream(baos);
                        dos.write(ram, 0, ram.length);
                        dos.close();
//                    System.out.println(String.format("Ram page: %d, compressed len: %d",
//                        page, baos.size()));
                        fOut.write(blockID.getBytes("US-ASCII"));
                        int pageLen = baos.size() + 3;
                        fOut.write(pageLen);
                        fOut.write(pageLen >>> 8);
                        fOut.write(pageLen >>> 16);
                        fOut.write(pageLen >>> 24);
                        fOut.write(ZXSTLCRPF_COMPRESSED);
                        fOut.write(0x00);
                        fOut.write(page);
                        baos.writeTo(fOut);
                    }
                }
            }
        } catch (final IOException ex) {
            throw new SnapshotException("FILE_WRITE_ERROR", ex);
        }

        return true;
    }
}
