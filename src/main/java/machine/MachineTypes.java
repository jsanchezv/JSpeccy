/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package machine;

/**
 *
 * @author jsanchez
 */
public enum MachineTypes {

    SPECTRUM16K(0),
    SPECTRUM48K(1),
    SPECTRUM128K(2),
    SPECTRUMPLUS2(3),
    SPECTRUMPLUS2A(4),
    SPECTRUMPLUS3(5);

    static public enum CodeModel { SPECTRUM48K, SPECTRUM128K, SPECTRUMPLUS3 };
    public final CodeModel codeModel; // Código de modelo
    private final String longModelName;   // Nombre largo del modelo de Spectrum
    private final String shortModelName;   // Nombre corto del modelo de Spectrum
    public final int clockFreq;       // Clock frequency
    public final int tstatesFrame;    // t-states por cuadro de la imagen
    public final int tstatesLine;     // t-states por línea de imagen
    public final int upBorderWidth;   // Número de líneas del borde superior
    public final int scanLines;       // Número de líneas de imagen
    public final int firstScrByte;    // t-states hasta el primer byte de la pantalla
    public final int lastScrUpdate;   // t-states última actualización de la pantalla
    public final int outOffset;       //
    public final int outBorderOffset; //
    public final int lengthINT;       // Duración en t-states de la señal INT
    private final boolean hasAY8912;  // Tiene un AY-3-8912?
    private final boolean hasDisk;    // Tiene un controlador de disco y disquetera?

    MachineTypes(int model) {
        switch (model) {
            case 0: // Spectrum 16K
                this.longModelName = "ZX Spectrum 16K";
                this.shortModelName = "16k";
                this.clockFreq = 3500000;
                this.tstatesFrame = 69888;
                this.tstatesLine = 224;
                this.upBorderWidth = 64;
                this.scanLines = 312;
                this.lengthINT = 32;
                this.firstScrByte = 14336;
                this.lastScrUpdate = 57246;
                this.outOffset = 3;
                this.outBorderOffset = 0;
                this.hasAY8912 = false;
                this.hasDisk = false;
                this.codeModel = CodeModel.SPECTRUM48K;
                break;
            case 1: // Spectrum 48K
            default:
                this.longModelName = "ZX Spectrum 48K";
                this.shortModelName = "48k";
                this.clockFreq = 3500000;
                this.tstatesFrame = 69888;
                this.tstatesLine = 224;
                this.upBorderWidth = 64;
                this.scanLines = 312;
                this.lengthINT = 32;
                this.firstScrByte = 14336;
                this.lastScrUpdate = 57246;
                this.outOffset = 3;
                this.outBorderOffset = 0;
                this.hasAY8912 = false;
                this.hasDisk = false;
                this.codeModel = CodeModel.SPECTRUM48K;
                break;
            case 2: // Spectrum 128K
                this.longModelName = "ZX Spectrum 128K";
                this.shortModelName = "128";
                this.clockFreq = 3546900;
                this.tstatesFrame = 70908;
                this.tstatesLine = 228;
                this.upBorderWidth = 63;
                this.scanLines = 311;
                this.lengthINT = 36;
                this.firstScrByte = 14362;
                this.lastScrUpdate = 58040;
                this.outOffset = 1;
                this.outBorderOffset = 2;
                this.hasAY8912 = true;
                this.hasDisk = false;
                this.codeModel = CodeModel.SPECTRUM128K;
                break;
            case 3: // Spectrum +2
                this.longModelName = "Amstrad ZX Spectrum +2";
                this.shortModelName = "  +2";
                this.clockFreq = 3546900;
                this.tstatesFrame = 70908;
                this.tstatesLine = 228;
                this.upBorderWidth = 63;
                this.scanLines = 311;
                this.lengthINT = 36;
                this.firstScrByte = 14362;
                this.lastScrUpdate = 58040;
                this.outOffset = 1;
                this.outBorderOffset = 2;
                this.hasAY8912 = true;
                this.hasDisk = false;
                this.codeModel = CodeModel.SPECTRUM128K;
                break;
            case 4: // Spectrum +2A
                this.longModelName = "ZX Spectrum +2A";
                this.shortModelName = "+2A";
                this.clockFreq = 3546900;
                this.tstatesFrame = 70908;
                this.tstatesLine = 228;
                this.upBorderWidth = 63;
                this.scanLines = 311;
                this.lengthINT = 32;
                this.firstScrByte = 14364;
                this.lastScrUpdate = 58044;
                this.outOffset = 1;
                this.outBorderOffset = -1;
                this.hasAY8912 = true;
                this.hasDisk = false;
                this.codeModel = CodeModel.SPECTRUMPLUS3;
                break;
            case 5: // Spectrum +3
                this.longModelName = "ZX Spectrum +3";
                this.shortModelName = "  +3";
                this.clockFreq = 3546900;
                this.tstatesFrame = 70908;
                this.tstatesLine = 228;
                this.upBorderWidth = 63;
                this.scanLines = 311;
                this.lengthINT = 32;
                this.firstScrByte = 14364;
                this.lastScrUpdate = 58044;
                this.outOffset = 1;
                this.outBorderOffset = -1;
                this.hasAY8912 = true;
                this.hasDisk = true;
                this.codeModel = CodeModel.SPECTRUMPLUS3;
                break;
        }
    }

    public String getLongModelName() {
        return longModelName;
    }

    public String getShortModelName() {
        return shortModelName;
    }

    public int getTstatesFrame() {
        return tstatesFrame;
    }

    public int getTstatesLine() {
        return tstatesLine;
    }

    public int getBorderLines() {
        return upBorderWidth;
    }

    public int getScanLines() {
        return scanLines;
    }

    public int getTstatesINT() {
        return lengthINT;
    }

    public boolean hasAY8912() {
        return hasAY8912;
    }

    public boolean hasDisk() {
        return hasDisk;
    }

}
