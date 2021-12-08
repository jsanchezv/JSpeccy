/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package snapshots;

/**
 *
 * @author jsanchez
 */
public class MemoryState {
    private final byte ram[][] = new byte[8][];
    private byte IF2Rom[];
    private byte mfRam[];
    private final byte lecRam[][] = new byte[16][];
    private int portFD;
    private boolean IF1RomPaged, IF2RomPaged;
    private boolean multifacePaged, multifaceLocked, mf128on48k;

    public MemoryState () {
    }

    public byte[] getPageRam(int page) {
        return ram[page];
    }

    public void setPageRam(int page, byte[] memory) {
        ram[page] = memory;
    }

    public byte[] getIF2Rom() {
        return IF2Rom;
    }

    public void setIF2Rom(byte[] memory) {
        IF2Rom = memory;
    }

    public byte[] getMultifaceRam() {
        return mfRam;
    }

    public void setMultifaceRam(byte[] memory) {
        mfRam = memory;
    }

    /**
     * @return the IF2RomPaged
     */
    public boolean isIF2RomPaged() {
        return IF2RomPaged;
    }

    /**
     * @param IF2RomPaged the IF2RomPaged to set
     */
    public void setIF2RomPaged(boolean IF2RomPaged) {
        this.IF2RomPaged = IF2RomPaged;
    }

    /**
     * @return the multifacePaged
     */
    public boolean isMultifacePaged() {
        return multifacePaged;
    }

    /**
     * @param multifacePaged the multifacePaged to set
     */
    public void setMultifacePaged(boolean multifacePaged) {
        this.multifacePaged = multifacePaged;
    }

    /**
     * @return the multifaceLocked
     */
    public boolean isMultifaceLocked() {
        return multifaceLocked;
    }

    /**
     * @param multifaceLocked the multifaceLocked to set
     */
    public void setMultifaceLocked(boolean multifaceLocked) {
        this.multifaceLocked = multifaceLocked;
    }

    /**
     * @return the mf128on48k
     */
    public boolean isMf128on48k() {
        return mf128on48k;
    }

    /**
     * @param mf128on48k the mf128on48k to set
     */
    public void setMf128on48k(boolean mf128on48k) {
        this.mf128on48k = mf128on48k;
    }

    /**
     * @return the IF1RomPaged
     */
    public boolean isIF1RomPaged() {
        return IF1RomPaged;
    }

    /**
     * @param IF1RomPaged the IF1RomPaged to set
     */
    public void setIF1RomPaged(boolean IF1RomPaged) {
        this.IF1RomPaged = IF1RomPaged;
    }

    // método de conveniencia para los snapshots Z80
    public byte readByte(int page, int address) {
            return ram[page][address];
    }

    /**
     * @return the pageLEC
     */
    public int getPortFD() {
        return portFD;
    }

    /**
     * @param pageLEC the pageLEC to set
     */
    public void setPortFD(int pageLEC) {
        this.portFD = pageLEC;
    }

    /**
     * @return the lecPaged
     */
    public boolean isLecPaged() {
        return (portFD & 0x80) != 0;
    }

    public byte[] getLecPageRam(int page) {
        if (lecRam[page] == null) {
            return null;
        }

        return lecRam[page];
    }

    public void setLecPageRam(int page, byte[] ram) {
        lecRam[page] = ram;
    }
}
