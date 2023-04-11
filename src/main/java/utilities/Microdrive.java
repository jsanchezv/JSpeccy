/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 * 
 * MDVT file format: http://www.worldofspectrum.org/forums/showthread.php?t=37039
 *
 */
package utilities;

import machine.SpectrumClock;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author jsanchez
 */
@Slf4j
public class Microdrive {

    private static final int RAW_SECTOR_SIZE = 792;  // empirically calculated (sigh!)
    private static final int MDR_SECTOR_SIZE = 587;  // 543 + 2 * PREAMBLE + 2 * 10 (GAPs)
    private static final int MDR_HEADER_SIZE = 15;
    private static final int MDR_RECORD_SIZE = 528;
    private static final int MDR_GAP_LENGTH = 10;
    private static final String mdvtID = "MDVT";

    private static final int timeTransfer = 168;     // 12 usec/bit * 8 at 3.5 Mhz

    private static final int MDVT_WR_PROT = 0x01;       // Cartridge protected flag
    private static final int MDVT_COMPRESSED = 0x02;    // Cartridge data is compressed
    private static final int MDVT_MDR_CONVERTED = 0x04; // Cartridge converted from MDR
    private static final int MDVT_UNFORMATTED = 0x08;   // Cartridge created but unformatted

    private static final byte GAP = 0x04;
    private static final byte SYNC = 0x02;
    private static final byte WRITE_PROT_MASK = (byte)0xfe;
    private static final byte CLOCK_GAP = 0x04;
    private static final byte CLOCK_DATA = 0x00;
    private static final byte DATA_FILLER = 0x5A;
    private static final byte preamble[] = { 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x00, 0x00, 0x00, 0x00, 0x00,
                                             (byte)0xFF, (byte)0xFF };

    private byte cartridge[];
    private byte clockGap[];
    private byte lastData;
    private int status;
    private int cartridgePos;
    private int numSectors;
    private int mdrSectors;
    private int sectorSize;
    private boolean isCartridge;
    private boolean modified, unformatted;
    private boolean writeProtected;
    private boolean wrGapPending;
    private boolean mdrFile;

    private File filename;
    private final SpectrumClock clock;
    private long startGap;

    public Microdrive() {

        clock = SpectrumClock.INSTANCE;
        isCartridge = mdrFile = false;
        writeProtected = true;
        cartridgePos = 0;
    }

    public void selected() {

        if (!isCartridge)
            return;

        wrGapPending = false;

        // Find a GAP to start from a known position
        int pos = cartridgePos - 1;
        if (pos < 0)
            pos = clockGap.length - 1;

        while(clockGap[cartridgePos] == CLOCK_DATA && cartridgePos != pos) {
            if (++cartridgePos >= clockGap.length)
                cartridgePos = 0;
        }

        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;
    }

    public int readStatus() {

        if (!isCartridge)
            return 0xf5;

        if (wrGapPending)
            return 0xfb;

        status = 0xf9; // NO GAP, NO SYNC

        // Is a GAP?
        if (clockGap[cartridgePos] != CLOCK_DATA) {
            status |= GAP;
        }

        // When isn't a GAP, can be needed to assert SYNC
        if (clockGap[cartridgePos] == CLOCK_DATA && (lastData & cartridge[cartridgePos]) == 0)
            status |= SYNC;

        lastData = cartridge[cartridgePos];

        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        if (writeProtected)
            status &= WRITE_PROT_MASK;

//        System.out.println(String.format("readStatus at pos: %d, status: %02x, cartridge: %02x, clockGAP: %02x",
//                cartridgePos, status, cartridge[cartridgePos] & 0xff, clockGap[cartridgePos]& 0xff));

        return status & 0xff;
    }

    public int readData() {

//        System.out.println(String.format("readData at pos: %d, status: %02x, cartridge: %02x, clockGAP: %02x",
//                cartridgePos, status, cartridge[cartridgePos] & 0xff, clockGap[cartridgePos]& 0xff));

        if (!isCartridge)
            return 0xff;

        lastData = cartridge[cartridgePos];

        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        clock.addTstates(timeTransfer);

        return lastData & 0xff;
    }

    public void writeControl(int value) {

        if (!isCartridge)
            return;

//        System.out.println(String.format("writeControl (%02x) at : pos: %d, status: %02x, cartridge: %02x, wrGapPending: %b",
//                value & 0xff, cartridgePos, clockGap[cartridgePos]  & 0xff, cartridge[cartridgePos] & 0xff, wrGapPending));

        int op = value & 0x0C;
        // ERASE: ON, WRITE: OFF
        if (op == 0x04 && !wrGapPending) {
            startGap = clock.getAbsTstates();
            wrGapPending = true;
            return;
        }

        // ERASE: ON, WRITE: ON or ERASE: OFF, WRITE: OFF
        if ((op == 0x00 || op == 0x0C) && wrGapPending) {
            long gapLen = (clock.getAbsTstates() - startGap) / timeTransfer;
            while (gapLen-- > 0) {
                cartridge[cartridgePos] = DATA_FILLER;
                clockGap[cartridgePos] = CLOCK_GAP;
                if (++cartridgePos >= clockGap.length)
                    cartridgePos = 0;
            }
            wrGapPending = false;
        }
    }

    public void writeData(int value) {

        if (!isCartridge)
            return;
//        System.out.println(String.format("writeData (%02x) at: pos: %d, status: %02x, cartridge: %02x, wrGapPending: %b",
//                value & 0xff, cartridgePos, clockGap[cartridgePos] & 0xff, cartridge[cartridgePos] & 0xff, wrGapPending));

        clockGap[cartridgePos] = CLOCK_DATA;
        cartridge[cartridgePos] = (byte) value;
        modified = true;
        unformatted = false;

        if (++cartridgePos >= clockGap.length)
            cartridgePos = 0;

        clock.addTstates(timeTransfer);
    }

    public boolean isWriteProtected() {

        if (!isCartridge)
            return true;

        return writeProtected;
    }

    public void setWriteProtected(boolean flag) {

        if (!isCartridge)
            return;

        writeProtected = flag;
        modified = true;
    }

    public boolean isCartridge() {
        return isCartridge;
    }

    public boolean isModified() {
        return modified;
    }

    public final boolean insertNew(int numSectors) {

        if (isCartridge)
            return false;

        cartridge = new byte[numSectors * RAW_SECTOR_SIZE];
        clockGap = new byte[numSectors * RAW_SECTOR_SIZE];

        Arrays.fill(cartridge, DATA_FILLER);
        Arrays.fill(clockGap, CLOCK_GAP);

        isCartridge = true;
        writeProtected = mdrFile = false;
        modified = unformatted = true;
        filename = null;
        this.numSectors = numSectors;
        cartridgePos = 0;

        return true;
    }

    public final boolean insertFromFile(File fileName) {

        if (isCartridge)
            return false;

        isCartridge = false;

        try (BufferedInputStream fIn = new BufferedInputStream(new FileInputStream(fileName))) {

            if (fileName.getName().toLowerCase().endsWith(".mdr")) {
                mdrFile = true;

                int mdrLen = fIn.available();
                int nsectors = mdrSectors = (mdrLen - 1) / (MDR_HEADER_SIZE + MDR_RECORD_SIZE);
                int pos = 0;

                // 587 = 543 + 2 * 10 (GAP) + 2 * 12 (PREAMBLE)
                clockGap = new byte[nsectors * MDR_SECTOR_SIZE];
                cartridge = new byte[nsectors * MDR_SECTOR_SIZE];
                while (nsectors-- > 0) {
                    // Create header GAP
                    for(int gap = 0; gap < MDR_GAP_LENGTH; gap++) {
                        cartridge[pos] = DATA_FILLER;
                        clockGap[pos++] = CLOCK_GAP;
                    }

                    // Create header preamble (10 * 0x00 + 2 * 0xFF)
                    for(int pream = 0; pream < preamble.length; pream++) {
                        cartridge[pos] = preamble[pream];
                        clockGap[pos++] = CLOCK_DATA;
                    }

                    // Read header
                    for(int header = 0; header < MDR_HEADER_SIZE; header++) {
                        cartridge[pos] = (byte)fIn.read();
                        clockGap[pos++] = CLOCK_DATA;
                    }

                    // Create data GAP
                    for(int gap = 0; gap < MDR_GAP_LENGTH; gap++) {
                        cartridge[pos] = DATA_FILLER;
                        clockGap[pos++] = CLOCK_GAP;
                    }

                    // Create data preamble (10 * 0x00 + 2 * 0xFF)
                    for(int pream = 0; pream < preamble.length; pream++) {
                        cartridge[pos] = preamble[pream];
                        clockGap[pos++] = CLOCK_DATA;
                    }

                    // Read data
                    for(int data = 0; data < MDR_RECORD_SIZE; data++) {
                        cartridge[pos] = (byte)fIn.read();
                        clockGap[pos++] = CLOCK_DATA;
                    }
                }

                writeProtected = true;
            } else {
                // Read and check the file signature
                byte[] blockID = new byte[4];
                int readed = fIn.read(blockID);
                if (readed != blockID.length)
                    return false;

                String readedID = new String(blockID, "US-ASCII");
                if (!mdvtID.equals(readedID))
                    return false;

                // Read header length
                readed = fIn.read(blockID); // reuse blockID variable
                if (readed != blockID.length)
                    return false;

                int headerSize = (blockID[0] & 0xff) | ((blockID[1] << 8) & 0xff00)
                    | ((blockID[2] << 16) & 0xff0000) | ((blockID[3] << 24) & 0xff000000);
                byte[] header = new byte[headerSize];
                readed = fIn.read(header);
                if (readed != header.length)
                    return false;

                mdrFile = (header[2] & MDVT_MDR_CONVERTED) != 0;
                writeProtected = (header[2] & MDVT_WR_PROT) != 0;
                unformatted = (header[2] & MDVT_UNFORMATTED) != 0;
                sectorSize = (header[4] & 0xff) | ((header[5] << 8) & 0xff00);
                numSectors = (header[6] & 0xff) | ((header[7] << 8) & 0xff00);
                int gapEntries = (header[10] & 0xff) | ((header[11] << 8) & 0xff00);

                // Creator field
                int lenTextField = fIn.read() & 0xff;
                if (lenTextField > 0) {
                    if (fIn.skip(lenTextField) != lenTextField)
                        return false;
                }

                // Description field
                lenTextField = fIn.read() & 0xff;
                if (lenTextField > 0) {
                    if (fIn.skip(lenTextField) != lenTextField)
                        return false;
                }

                // Comments field
                lenTextField = fIn.read() & 0xff;
                if (lenTextField > 0) {
                    if (fIn.skip(lenTextField) != lenTextField)
                        return false;
                }

                if ((header[2] & MDVT_UNFORMATTED) != 0) {
                    cartridge = new byte[sectorSize * numSectors];
                    clockGap = new byte[sectorSize * numSectors];

                     Arrays.fill(cartridge, DATA_FILLER);
                     Arrays.fill(clockGap, CLOCK_GAP);

                    unformatted = isCartridge = true;
                    filename = fileName;
                    cartridgePos = 0;
                    return true;
                }

                if ((header[2] & MDVT_COMPRESSED) != 0) {
                    byte[] cswGaps;
                    // Block is compressed
                    try (InflaterInputStream iis = new InflaterInputStream(fIn)) {
                        cartridge = new byte[sectorSize * numSectors];
                        readed = 0;
                        while (readed < cartridge.length) {
                            int count = iis.read(cartridge, readed, cartridge.length - readed);
                            if (count == -1) {
                                break;
                            }
                            readed += count;
                        }
                        if (readed != cartridge.length) {
                            iis.close();
                            return false;
                        }   // The Gap entries are WORDs
                        cswGaps = new byte[gapEntries << 1];
                        readed = 0;
                        while (readed < cswGaps.length) {
                            int count = iis.read(cswGaps, readed, cswGaps.length - readed);
                            if (count == -1) {
                                break;
                            }
                            readed += count;
                        }
                    }
                    if (readed != cswGaps.length) {
                        return false;
                    }

                    clockGap = convertCswToGaps(header[9], cswGaps);
                    if (clockGap == null) {
                        return false;
                    }

                } else {
                    // Not compressed data
                    cartridge = new byte[sectorSize * numSectors];
                    readed = 0;
                    while (readed < cartridge.length) {
                        int count = fIn.read(cartridge, readed, cartridge.length - readed);
                        if (count == -1) {
                            break;
                        }
                        readed += count;
                    }

                    if (readed != cartridge.length) {
                        return false;
                    }

                    // The Gap entries are WORDs
                    byte[] cswGaps = new byte[gapEntries << 1];
                    readed = 0;
                    while (readed < cswGaps.length) {
                        int count = fIn.read(cswGaps, readed, cswGaps.length - readed);
                        if (count == -1) {
                            break;
                        }
                        readed += count;
                    }

                    if (readed != cswGaps.length) {
                        return false;
                    }

                    clockGap = convertCswToGaps(header[9], cswGaps);
                    if (clockGap == null) {
                        return false;
                    }
                }
            }
        } catch (final FileNotFoundException fnfExcpt) {
            log.error("File {} not found: ", fileName, fnfExcpt);
            return false;
        } catch (final IOException ex) {
            log.error("IOException: ", ex);
            return false;
        }

        cartridgePos = 0;
        isCartridge = true;
        modified = false;
        filename = fileName;
        return true;
    }

    public final boolean eject() {

        if (!isCartridge)
            return true;

        isCartridge = false;
        modified = false;
        writeProtected = true;
        filename = null;
        cartridgePos = 0;
        return true;
    }

    public final String getFilename() {
        if (filename == null)
            return null;
        else
            return filename.getName();
    }

    public final String getAbsolutePath() {
        return filename == null ? null : filename.getAbsolutePath();
    }

    public final boolean save() {

        if (!isCartridge || !modified) {
            return true;
        }

        try (BufferedOutputStream fOut = new BufferedOutputStream(new FileOutputStream(filename))) {

            // MDVT Header
            fOut.write(mdvtID.getBytes("US-ASCII"));
            fOut.write(12);   // MDVT 1.0 header length (QWORD)
            fOut.write(0x00);
            fOut.write(0x00);
            fOut.write(0x00);

            fOut.write(0x01); // MDV major version
            fOut.write(0x00); // MDV minor version

            // Flags (WORD)
            int flags = 0x00;
            if (writeProtected)
                flags |= MDVT_WR_PROT;
            if (mdrFile)
                flags |= MDVT_MDR_CONVERTED;
            if (unformatted)
                flags |= MDVT_UNFORMATTED;
            else
                flags |= MDVT_COMPRESSED;

            fOut.write(flags);
            fOut.write(flags >>> 8);

            if (mdrFile) {
                 // Raw sector size (WORD)
                fOut.write(MDR_SECTOR_SIZE);
                fOut.write(MDR_SECTOR_SIZE >>> 8);
                // Num sectors (QWORD)
                fOut.write(mdrSectors);
                fOut.write(mdrSectors >>> 8);
                // GAP size used (in converted mdr files)
                fOut.write(MDR_GAP_LENGTH);
            } else {
                 // Raw sector size (WORD)
                fOut.write(RAW_SECTOR_SIZE);
                fOut.write(RAW_SECTOR_SIZE >>> 8);
                // Num sectors (QWORD)
                fOut.write(numSectors);
                fOut.write(numSectors >>> 8);
                // No GAP size predefined
                fOut.write(0x00);
            }

            // First GAP value
            fOut.write(clockGap[0]);

            // GAP array length (WORD)
            ByteArrayOutputStream buffer = null;
            if (unformatted) {
                fOut.write(0x00);
                fOut.write(0x00);
            } else {
                buffer = createCswBuffer();
                int buflen = buffer.size();
                fOut.write(buflen >>> 1);
                fOut.write(buflen >>> 9);
            }

            // Creator ID
            String creatorID = "JSpeccy 0.94";
            // Creator Length
            byte[] fieldText = creatorID.getBytes("UTF-8");
            int fieldLen = fieldText.length < 256 ? fieldText.length : 255;
            fOut.write(fieldLen);
            // Creator message
            fOut.write(fieldText, 0, fieldLen);

            // Desription length
            fOut.write(0x00);

            // Comments length
            fOut.write(0x00);

            if (!unformatted) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
                    // Sectors Data
                    dos.write(cartridge);

                    // GAP DATA
                    dos.write(buffer.toByteArray());
                }

                baos.writeTo(fOut);
            }

        } catch (final IOException ex) {
            log.error("IOException: ", ex);
            return false;
        }

        modified = false;
        return true;
    }

    public final boolean save(File newName) {
        filename = newName;
        modified = true;
        return save();
    }

    public int getCartridgePos() {
        return cartridgePos;
    }

    public void setCartridgePos(int offset) {

        if (cartridge == null || offset < 0 || offset > cartridge.length - 1)
            offset = 0;

        cartridgePos = offset;
    }

    private ByteArrayOutputStream createCswBuffer() {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte value = clockGap[0];
        int counter = 0;

        for (int pos = 0; pos < clockGap.length; pos++) {
            if (clockGap[pos] == value) {
                counter++;
            } else {
                value = clockGap[pos];
                buffer.write(counter);
                buffer.write(counter >>> 8);
                counter = 1;
            }
        }

        buffer.write(counter);
        buffer.write(counter >>> 8);

        return buffer;
    }

    private byte[] convertCswToGaps(byte firstGap, byte[] cswGaps) {
        byte[] dataGaps = new byte[cartridge.length];

        int nGap = 0, idx = 0;
        firstGap = firstGap == 0 ? CLOCK_DATA : CLOCK_GAP;

        while (nGap < cswGaps.length) {
            int gapLen = (cswGaps[nGap] & 0xff) | ((cswGaps[nGap + 1] << 8) & 0xff00);
            nGap += 2;

            while(gapLen-- > 0) {
                dataGaps[idx++] = firstGap;
            }

            firstGap = firstGap == 0 ? CLOCK_GAP : CLOCK_DATA;
        }

        if (idx != dataGaps.length) {
            log.warn("Error regenerating GAP info!");
            return null;
        }

        return dataGaps;
    }
}
