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
    
    SPECTRUM48K("Spectrum 48k", 69888, 224, 64, false, false, 48),
    SPECTRUM128K("Spectrum 128k", 70908, 228, 63, true, false, 128),
    SPECTRUMPLUS2A("Spectrum +2A/B", 70908, 228, 63, true, false, 255),
    SPECTRUMPLUS3("Spectrum +3", 70980, 228, 63, true, true, 255);
    
    private String name;        // Nombre de la máquina
    private int frames;         // t-estados por cuadro de la imagen
    private int tstatesLine;    // t-estados por línea de imagen
    private int borderLines;    // Número de líneas del borde superior
    private int scanLines;      // Número de líneas de imagen
    private boolean hasAY8912;  // Tiene un AY-3-8912?
    private boolean hasDisk;    // Tiene un controlador de disco y disquetera?
    private int model;          // Código de modelo
    private byte wStatesTable[]; // Tabla de estados de espera para este modelo
    private int floatingBusTable[]; // Tabla de conversión frame a dir. pantalla
    private static final byte wStatesTable48k[] = new byte[69888];
    private static final byte wStatesTable128k[] = new byte[70908];
    private static final byte wStatesTablePlus3[] = new byte[70908];
    private static final int floatingBusTable48k[] = new int[69888];
    private static final int floatingBusTable128k[] = new int[70908];
    private static final int floatingBusTablePlus3[] = new int[70908];
    static {
        /* Construcción de la tabla de estados de espera en cada frame para el
         * modelo de 48K */
        for( int idx = 14336; idx < 57344; idx += 224  ) {
            for( int ndx = 0; ndx < 128; ndx += 8) {
                int frame = idx + ndx;
                wStatesTable48k[frame++] = 6;
                wStatesTable48k[frame++] = 5;
                wStatesTable48k[frame++] = 4;
                wStatesTable48k[frame++] = 3;
                wStatesTable48k[frame++] = 2;
                wStatesTable48k[frame++] = 1;
                wStatesTable48k[frame++] = 0;
                wStatesTable48k[frame++] = 0;
            }
        }
        
        /* Construcción de la tabla de estados de espera en cada frame para el
         * modelo 128K original */
        for( int idx = 14365; idx < 58141; idx += 228  ) {
            for( int ndx = 0; ndx < 128; ndx += 8) {
                int frame = idx + ndx;
                wStatesTable128k[frame++] = 6;
                wStatesTable128k[frame++] = 5;
                wStatesTable128k[frame++] = 4;
                wStatesTable128k[frame++] = 3;
                wStatesTable128k[frame++] = 2;
                wStatesTable128k[frame++] = 1;
                wStatesTable128k[frame++] = 0;
                wStatesTable128k[frame++] = 0;
            }
        }
        
        /* Construcción de la tabla de estados de espera en cada frame para el
         * modelo +2A/B y +3 */
        for( int idx = 14365; idx < 58141; idx += 228  ) {
            for( int ndx = 0; ndx < 128; ndx += 8) {
                int frame = idx + ndx;
                wStatesTablePlus3[frame++] = 1;
                wStatesTablePlus3[frame++] = 0;
                wStatesTablePlus3[frame++] = 7;
                wStatesTablePlus3[frame++] = 6;
                wStatesTablePlus3[frame++] = 5;
                wStatesTablePlus3[frame++] = 4;
                wStatesTablePlus3[frame++] = 3;
                wStatesTablePlus3[frame++] = 2;
            }
        }
        
        /* Construcción de la tabla de conversión frame-pantalla para la
         * emulación del bus flotante del 48K
         * Para empezar, todos a 0xff, que es lo que da el bus flotante
         * cuando se lee un puerto inexistente y está dibujando el borde */
        for( int idx = 0; idx < floatingBusTable48k.length; idx++ )
            floatingBusTable48k[idx] = 0xff;
        
        int frame = 14347; // primer cuadro según RAMSOFT
    }
    
    MachineTypes(String nombre, int frames, int tstates, int borderLines,
                 boolean hasAY8912, boolean hasDisk, int model) {
        this.name = nombre;
        this.frames = frames;
        this.tstatesLine = tstates;
        this.borderLines = borderLines;
        this.scanLines = frames / tstates;
        this.hasAY8912 = hasAY8912;
        this.hasDisk = hasDisk;
        this.model = model;
        setWaitStatesTable(model);
    }
    
    private void setWaitStatesTable(int model) {
        switch( model ) {
            case 48:
                this.wStatesTable = wStatesTable48k;
                break;
            case 128:
                this.wStatesTable = wStatesTable128k;
                break;
            case 255:
                this.wStatesTable = wStatesTablePlus3;
                break;
            default:
                this.wStatesTable = wStatesTable48k;
        }
    }
    
    public String getName() {
        return name;
    }
    
    public int getFrames() {
        return frames;
    }
    
    public int getTstatesLine() {
        return tstatesLine;
    }
    
    public int getBorderLines() {
        return borderLines;
    }
    
    public int getScanLines() {
        return scanLines;
    }
    
    public boolean hasAY8912() {
        return hasAY8912;
    }
    
    public boolean hasDisk() {
        return hasDisk;
    }
    
    public byte[] getWstatesTable() {
        return wStatesTable;
    }
}