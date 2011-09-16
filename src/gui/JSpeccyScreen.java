/*
 * JScreen.java
 *
 * Created on 15 de enero de 2008, 12:50
 */
package gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import java.util.Arrays;
import machine.Spectrum;

/**
 *
 * @author  jsanchez
 */
public class JSpeccyScreen extends javax.swing.JPanel {

    //Vector con los valores correspondientes a lo colores anteriores
    public static final int[] Paleta = {
        0x000000, /* negro */
        0x0000c0, /* azul */
        0xc00000, /* rojo */
        0xc000c0, /* magenta */
        0x00c000, /* verde */
        0x00c0c0, /* cyan */
        0xc0c000, /* amarillo */
        0xc0c0c0, /* blanco */
        0x000000, /* negro brillante */
        0x0000ff, /* azul brillante */
        0xff0000, /* rojo brillante	*/
        0xff00ff, /* magenta brillante */
        0x00ff00, /* verde brillante */
        0x00ffff, /* cyan brillante */
        0xffff00, /* amarillo brillante */
        0xffffff /* blanco brillante */};
    // Tablas de valores de Paper/Ink. Para cada valor general de atributo,
    // corresponde una entrada en la tabla que hace referencia al color
    // en la paleta. Para los valores superiores a 127, los valores de Paper/Ink
    // ya están cambiados, lo que facilita el tratamiento del FLASH.
    private static final int Paper[] = new int[256];
    private static final int Ink[] = new int[256];
    // Tabla de correspondencia entre la dirección de pantalla y su atributo
    public final int scr2attr[] = new int[0x1800];
    // Tabla de correspondencia entre cada atributo y el primer byte del carácter
    // en la pantalla del Spectrum (la contraria de la anterior)
    private final int attr2scr[] = new int[768];
    // Tabla de correspondencia entre la dirección de pantalla del Spectrum
    // y la dirección que le corresponde en el BufferedImage.
    private final int bufAddr[] = new int[0x1800];
    // Tabla que contiene la dirección de pantalla del primer byte de cada
    // carácter en la columna cero.
    public final int scrAddr[] = new int[192];
    // Tabla que indica si un byte de la pantalla ha sido modificado y habrá que
    // redibujarlo.
    private final boolean dirtyByte[] = new boolean[0x1800];
    // Tabla de traslación entre t-states y la dirección de la pantalla del
    // Spectrum que se vuelca en ese t-state o -1 si no le corresponde ninguna.
    private final int states2scr[] = new int[70000];

    private static final int BORDER_WIDTH = 40;
    private static final int SCREEN_WIDTH = BORDER_WIDTH + 256 + BORDER_WIDTH;
    private static final int SCREEN_HEIGHT = BORDER_WIDTH + 192 + BORDER_WIDTH;

    static {
        // Inicialización de las tablas de Paper/Ink
        /* Para cada valor de atributo, hay dos tablas, donde cada una
         * ya tiene el color que le corresponde, para no tener que extraerlo
         */
        for (int idx = 0; idx < 256; idx++) {
            int ink = (idx & 0x07) | ((idx & 0x40) != 0 ? 0x08 : 0x00);
            int paper = ((idx >>> 3) & 0x07) | ((idx & 0x40) != 0 ? 0x08 : 0x00);
            if (idx < 128) {
                Ink[idx] = Paleta[ink];
                Paper[idx] = Paleta[paper];
            } else {
                Ink[idx] = Paleta[paper];
                Paper[idx] = Paleta[ink];
            }
        }
    }
    private int flash = 0x7f; // 0x7f == ciclo off, 0xff == ciclo on
    private boolean doubleSize = false;
    private int pScrn[];
    private BufferedImage bImg;
    private int imgData[];
    private BufferedImage bImgScr;
    private int imgDataScr[];
    private AffineTransform escala;
    private AffineTransformOp escalaOp;
    private RenderingHints renderHints;
    private Spectrum speccy;
    // t-states del último cambio de border
    private int lastChgBorder;
    // veces que ha cambiado el borde en el último frame
    private int nBorderChanges;
    public boolean screenUpdated;
    // t-states del ciclo contended por I=0x40-0x7F o -1
    public int m1contended;
    // valor del registro R cuando se produjo el ciclo m1
    public int m1regR;

    /** Creates new form JScreen */
    public JSpeccyScreen(Spectrum spectrum) {
        initComponents();

        bImg = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        imgData = ((DataBufferInt) bImg.getRaster().getDataBuffer()).getBankData()[0];
        bImgScr = new BufferedImage(256, 192, BufferedImage.TYPE_INT_RGB);
        imgDataScr = ((DataBufferInt) bImgScr.getRaster().getDataBuffer()).getBankData()[0];
        buildScreenTables();
        escala = AffineTransform.getScaleInstance(2.0f, 2.0f);
        renderHints = new RenderingHints(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        renderHints.put(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_SPEED);
        renderHints.put(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_OFF);
        renderHints.put(RenderingHints.KEY_COLOR_RENDERING,
            RenderingHints.VALUE_COLOR_RENDER_SPEED);
        escalaOp = new AffineTransformOp(escala, renderHints);
        //speccy = new Spectrum();
        this.speccy = spectrum;
        pScrn = speccy.getSpectrumMem();
        lastChgBorder = 0;
        m1contended = -1;
        Arrays.fill(dirtyByte, true);
        screenUpdated = false;
        setMaximumSize(new java.awt.Dimension(SCREEN_WIDTH * 2, SCREEN_HEIGHT * 2));
        setMinimumSize(new java.awt.Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setPreferredSize(new java.awt.Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
    }

    public synchronized void toggleFlash() {
        flash = (flash == 0x7f ? 0xff : 0x7f);
        for(int addrAttr = 0x5800; addrAttr < 0x5b00; addrAttr++)
            if( pScrn[addrAttr] > 0x7f ) {
                int address = attr2scr[addrAttr & 0x3ff] & 0x1fff;
                for (int scan = 0; scan < 8; scan++) {
                    dirtyByte[address] = true;
                    address += 256;
                }
            }
//        if(nBorderChanges == 0 )
//            nBorderChanges = 1;
    }

    public void toggleDoubleSize() {
        doubleSize = !doubleSize;
        if (doubleSize) {
            this.setPreferredSize(new Dimension(SCREEN_WIDTH * 2, SCREEN_HEIGHT * 2));
        } else {
            this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        }
    }

    @Override
    public void paintComponent(Graphics gc) {
        //super.paintComponent(gc);
        paintScreen((Graphics2D) gc);
    }

    private void paintScreen(Graphics2D gc2) {


        //long start = System.currentTimeMillis();

        //System.out.println("Borrado: " + (System.currentTimeMillis() - start));

        // Rejilla horizontal de test
//        for( int idx = 0; idx < 36; idx ++ )
//            Arrays.fill(imgData, idx*2816, idx*2816+352, 0x404040);

        //System.out.println("Decode: " + (System.currentTimeMillis() - start));

        // si nBorderChanges == 0 y no se actualizó la pantalla es muy probable
        // que haya sido Swing el que ha llamado a paintComponent porque algo
        // ha oscurecido parte de la ventana (un menú por ejemplo). En ese caso
        // no queda otra que dibujarlo todo.
        if (nBorderChanges > 0 || (nBorderChanges == 0 && !screenUpdated)) {
            if (nBorderChanges == 1) {
                intArrayFill(imgData, Paleta[speccy.portFE & 0x07]);
                //updateBorder(lastChgBorder - 1);
                nBorderChanges = 0;
            } else {
                nBorderChanges = 1;
            }

            if (doubleSize) {
                gc2.drawImage(bImg, escalaOp, 0, 0);
            } else {
                gc2.drawImage(bImg, 0, 0, this);
            }
            //System.out.println("Draw border");
        }

        if (doubleSize) {
            gc2.drawImage(bImgScr, escalaOp, BORDER_WIDTH * 2, BORDER_WIDTH * 2);
        } else {
            gc2.drawImage(bImgScr, BORDER_WIDTH, BORDER_WIDTH, this);
        }
        screenUpdated = false;
        //System.out.println("ms: " + (System.currentTimeMillis() - start));
        //System.out.println("");
    }

    /*
     * Cada línea completa de imagen dura 224 T-Estados, divididos en:
     * 128 T-Estados en los que se dibujan los 256 pixeles de pantalla
     * 24 T-Estados en los que se dibujan los 48 pixeles del borde derecho
     * 48 T-Estados iniciales de H-Sync y blanking
     * 24 T-Estados en los que se dibujan 48 pixeles del borde izquierdo
     *
     * Cada pantalla consta de 312 líneas divididas en:
     * 16 líneas en las cuales el haz vuelve a la parte superior de la pantalla
     * 48 líneas de borde superior
     * 192 líneas de pantalla
     * 56 líneas de borde inferior de las cuales se ven solo 48
     */
    private int tStatesToScrPix(int tstates) {

        // Si los tstates son < 3584 (16 * 224), no estamos en la zona visible
        if (tstates < (3584 + ((48 - BORDER_WIDTH) * 224))) {
            return 0;
        }

        // Se evita la zona no visible inferior
        if (tstates > (256 + BORDER_WIDTH) * 224) {
            return imgData.length - 1;
        }

        tstates -= 3584 + ((48 - BORDER_WIDTH) * 224);

        int row = tstates / 224;
        int col = tstates % 224;

        int mod = col % 8;
        col -= mod;
        if (mod > 3) {
            col += 4;
        }

//        System.out.println(String.format("t-states: %d\trow: %d\tcol: %d\tmod: %d",
//                tstates+3584, row, col, mod));

        int pix = row * SCREEN_WIDTH;

        if (col < (128 + BORDER_WIDTH / 2)) {
            return pix += col * 2 + BORDER_WIDTH;
        }
        if (col > (199 + (48 - BORDER_WIDTH) / 2)) {
            return pix += (col - (200 + (48 - BORDER_WIDTH) / 2)) * 2 + SCREEN_WIDTH;
        } else {
            return pix + SCREEN_WIDTH;
        }
    }

    public void updateBorder(int tstates) {
        int startPix, endPix, color;

        if (tstates < lastChgBorder) {
            startPix = tStatesToScrPix(lastChgBorder);
            if (startPix < imgData.length - 1) {
                color = Paleta[speccy.portFE & 0x07];
                for (int count = startPix; count < imgData.length - 1; count++) {
                    imgData[count] = color;
                }
            }
            lastChgBorder = 0;
        }

        startPix = tStatesToScrPix(lastChgBorder);
        if (startPix > imgData.length - 1) {
            lastChgBorder = tstates;
            nBorderChanges++;
            return;
        }

        endPix = tStatesToScrPix(tstates);
        if (endPix > imgData.length - 1) {
            endPix = imgData.length - 1;
        }

        if( startPix < endPix ) {
            color = Paleta[speccy.portFE & 0x07];
            for (int count = startPix; count < endPix; count++) {
                imgData[count] = color;
            }
        }
        lastChgBorder = tstates;
        nBorderChanges++;
        screenUpdated = true;
    }

    public void updateInterval(int fromTstates, int toTstates) {

        //System.out.println(String.format("from: %d\tto: %d", fromTstates, toTstates));
        while (fromTstates <= toTstates) {
            int fromAddr = states2scr[fromTstates];
            if (fromAddr == -1 || !dirtyByte[fromAddr & 0x1fff]) {
                fromTstates++;
                continue;
            }

            int scrByte = 0, attr = 0;
            // si m1contended es != -1 es que hay que emular el efecto snow.
            if (m1contended == -1) {
                scrByte = pScrn[fromAddr];
                fromAddr &= 0x1fff;
                attr = pScrn[scr2attr[fromAddr]];
            } else {
                int addr;
                int mod = m1contended % 8;
                if (mod == 0 || mod == 1) {
                    addr = (fromAddr & 0xff00) | m1regR;
                    scrByte = pScrn[addr];
                    attr = pScrn[scr2attr[fromAddr & 0x1fff]];
                    //System.out.println("Snow even");
                }
                if (mod == 2 || mod == 3) {
                    addr = (scr2attr[fromAddr & 0x1fff] & 0xff00) | m1regR;
                    scrByte = pScrn[fromAddr];
                    attr = pScrn[addr & 0x1fff];
                    //System.out.println("Snow odd");
                }
                fromAddr &= 0x1fff;
                m1contended = -1;
            }

            int addrBuf = bufAddr[fromAddr];
            if (attr > 0x7f) {
                attr &= flash;
            }
            int ink = Ink[attr];
            int paper = Paper[attr];
            for (int mask = 0x80; mask != 0; mask >>= 1) {
                if ((scrByte & mask) != 0) {
                    imgDataScr[addrBuf++] = ink;
                } else {
                    imgDataScr[addrBuf++] = paper;
                }
            }
            dirtyByte[fromAddr] = false;
            screenUpdated = true;
            fromTstates++;
        }
    }

    public void screenUpdated(int address) {
        if (address < 0x5800) {
            dirtyByte[address & 0x1fff] = true;
        } else {
            int addr = attr2scr[address & 0x3ff] & 0x1fff;
            for (int scan = 0; scan < 8; scan++) {
                dirtyByte[addr] = true;
                addr += 256;
            }
        }
    }

    public void invalidateScreen() {
        nBorderChanges = 1;
        screenUpdated = true;
        Arrays.fill(dirtyByte, true);
    }

    public void intArrayFill(int[] array, int value) {
        int len = array.length;
        if (len > 0) {
            array[0] = value;
        }

        for (int idx = 1; idx < len; idx += idx) {
            System.arraycopy(array, 0, array, idx, ((len - idx) < idx) ? (len - idx) : idx);
        }
    }

    private void buildScreenTables() {
        int row, col, scan;

        //Inicialización de la tabla de direcciones de pantalla
        /* Hay una entrada en la tabla con la dirección del primer byte
         * de cada fila de la pantalla.
         */
        for (int linea = 0; linea < 24; linea++) {
            int idx, lsb, msb, addr;
            lsb = ((linea & 0x07) << 5);
            msb = linea & 0x18;
            addr = (msb << 8) + lsb;
            idx = linea << 3;
            for (scan = 0; scan < 8; scan++, addr += 256) {
                scrAddr[scan + idx] = 0x4000 + addr;
            }
        }

        for (int address = 0x4000; address < 0x5800; address++) {
            row = ((address & 0xe0) >>> 5) | ((address & 0x1800) >>> 8);
            col = address & 0x1f;
            scan = (address & 0x700) >>> 8;
            //System.out.println(String.format("Fila :%d\t Col: %d\t scan: %d", row, col, scan));

            bufAddr[address & 0x1fff] = row * 2048 + scan * 256 + col * 8;
            scr2attr[address & 0x1fff] = 0x5800 + row * 32 + col;
        }

        for (int address = 0x5800; address < 0x5B00; address++) {
            attr2scr[address & 0x3ff] = 0x4000 | ((address & 0x300) << 3) | (address & 0xff);
        }

        Arrays.fill(states2scr, -1);
        for (int tstates = 14336; tstates < 57344; tstates += 4) {
            int fromScan = tstates / 224 - 64;
            int fromCol = (tstates % 224) / 4;
            if (fromCol > 31) {
                continue;
            }
            states2scr[tstates - 8] = scrAddr[fromScan] + fromCol;
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setBorder(javax.swing.BorderFactory.createEtchedBorder());
        setDoubleBuffered(false);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
