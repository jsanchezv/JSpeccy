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

    SPECTRUM48K(CodeModel.SPECTRUM48K),
    SPECTRUM128K(CodeModel.SPECTRUM128K),
    SPECTRUMPLUS2(CodeModel.SPECTRUMPLUS2),
    SPECTRUMPLUS3(CodeModel.SPECTRUMPLUS3);

    static public enum CodeModel { SPECTRUM48K, SPECTRUM128K, SPECTRUMPLUS2, SPECTRUMPLUS3 };
    public CodeModel codeModel; // Código de modelo
    private String modelName;   // Nombre de la máquina
    public int tstatesFrame;    // t-states por cuadro de la imagen
    public int tstatesLine;     // t-states por línea de imagen
    public int upBorderWidth;   // Número de líneas del borde superior
    public int scanLines;       // Número de líneas de imagen
    public int firstScrByte;    // t-states hasta la pantalla
    public int firstScrUpdate;  // t-states primera actualización de la pantalla
    public int lastScrUpdate;   // t-states última actualización de la pantalla
    public int outOffset;       //
    public int lengthINT;       // Duración en t-states de la señal INT
    private boolean hasAY8912;  // Tiene un AY-3-8912?
    private boolean hasDisk;    // Tiene un controlador de disco y disquetera?

    MachineTypes(CodeModel model) {
        switch (model) {
            case SPECTRUM48K: // Spectrum 48K
                this.modelName = "48K";
                this.tstatesFrame = 69888;
                this.tstatesLine = 224;
                this.upBorderWidth = 64;
                this.scanLines = 312;
                this.lengthINT = 32;
                this.firstScrByte = 14336;
                this.firstScrUpdate = 14328;
                this.lastScrUpdate = 57237;
                this.outOffset = 3;
                this.hasAY8912 = false;
                this.hasDisk = false;
                this.codeModel = CodeModel.SPECTRUM48K;
                break;
            case SPECTRUM128K: // Spectrum 128K
                this.modelName = "128K";
                this.tstatesFrame = 70908;
                this.tstatesLine = 228;
                this.upBorderWidth = 63;
                this.scanLines = 311;
                this.lengthINT = 36;
                this.firstScrByte = 14364;
                this.firstScrUpdate = 14356;
                this.lastScrUpdate = 58040;
                this.outOffset = 1;
                this.hasAY8912 = true;
                this.hasDisk = false;
                this.codeModel = CodeModel.SPECTRUM128K;
                break;
            case SPECTRUMPLUS2: // Spectrum +2A/B
                this.modelName = "+2A/B";
                this.tstatesFrame = 70908;
                this.tstatesLine = 228;
                this.upBorderWidth = 63;
                this.scanLines = 311;
                this.lengthINT = 36;
                this.firstScrByte = 14364;
                this.firstScrUpdate = 14356;
                this.lastScrUpdate = 58040;
                this.outOffset = 1;
                this.hasAY8912 = true;
                this.hasDisk = false;
                this.codeModel = CodeModel.SPECTRUMPLUS2;
                break;
            case SPECTRUMPLUS3: // Spectrum +3
                this.modelName = "+3";
                this.tstatesFrame = 70908;
                this.tstatesLine = 228;
                this.upBorderWidth = 63;
                this.scanLines = 311;
                this.lengthINT = 36;
                this.firstScrByte = 14364;
                this.firstScrUpdate = 14356;
                this.lastScrUpdate = 58040;
                this.outOffset = 1;
                this.hasAY8912 = true;
                this.hasDisk = true;
                this.codeModel = CodeModel.SPECTRUMPLUS3;
                break;
        }
    }

    public String getModelName() {
        return modelName;
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
