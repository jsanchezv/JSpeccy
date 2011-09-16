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
        0xffffff  /* blanco brillante */

    };
    
    private static int Paper[] = new int[256];
    private static int Ink[] = new int[256];
    public static int scrAddr[] = new int[192];
    public static int scr2attr[] = new int[192];
    //private static int scrData[] = new int[352 * 288 * 3];
    
    static {
        // Inicialización de las tablas de Paper/Ink
        /* Para cada valor de atributo, hay dos tablas, donde cada una
         * ya tiene el color que le corresponde, para no tener que extraerlo
         */
        for( int idx = 0; idx < 256; idx++ ) {
            int ink = (idx & 0x07) | ((idx & 0x40) != 0 ? 0x08 : 0x00);
            int paper = ((idx >>> 3) & 0x07) | ((idx & 0x40) != 0 ? 0x08 : 0x00);
            if( idx < 128 ) {
                Ink[idx]   = Paleta[ink];
                Paper[idx] = Paleta[paper];
            } else {
                Ink[idx]   = Paleta[paper];
                Paper[idx] = Paleta[ink];
            }
        }
        
        //Inicialización de la tabla de direcciones de pantalla
        /* Hay una entrada en la tabla con la dirección del primer byte
         * de cada fila de la pantalla.
         */
        for( int linea = 0; linea < 24; linea++ )
        {
            int idx, lsb, msb, addr, attr;
            lsb = ((linea & 0x07) << 5);
            msb = linea & 0x18;
            addr = (msb << 8) + lsb;
            idx = linea << 3;
            attr = linea * 32;
            for( int scan = 0; scan < 8; scan++, addr += 256 ) {
                scrAddr[scan + idx] = 0x4000 + addr;
                scr2attr[scan + idx] = 0x4000 + attr + 6144;
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
    //private Timer timerFrame;
    //private Clock taskFrame;
    private Spectrum speccy;
    //private int nFrame;
        
    /** Creates new form JScreen */
    public JSpeccyScreen(Spectrum spectrum) {
        initComponents();
        
        bImg = new BufferedImage(352, 288, BufferedImage.TYPE_INT_RGB);
        imgData = ((DataBufferInt)bImg.getRaster().getDataBuffer()).getBankData()[0];
        bImgScr = new BufferedImage(256, 192, BufferedImage.TYPE_INT_RGB);
        imgDataScr = ((DataBufferInt)bImgScr.getRaster().getDataBuffer()).getBankData()[0];
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
        //System.out.println("imgData.length = " + imgData.length);
        //timerFrame = new Timer();
        //taskFrame = new Clock(this);
        //startEmulation();
        //addKeyListener(this);
        //requestFocus();
    }

    public synchronized void toggleFlash() {
        flash = (flash == 0x7f ? 0xff : 0x7f);
        for( int addr = 0x5800; addr < 0x5b00; addr++ )
            if( pScrn[addr] > 0x7f ) {
                updateAttrChar(addr, pScrn[addr]);
                speccy.scrMod = true;
            }
    }
    
    public void toggleDoubleSize() {
        doubleSize = !doubleSize;
        if( doubleSize ) {
            this.setPreferredSize(new Dimension(704, 576));
        }
        else {
            this.setPreferredSize(new Dimension(352, 288));
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
        //pScrn = SpectrumRam;
        if( speccy.nTimesBorderChg != 0 ) {
            //long start = System.currentTimeMillis();
            updateBorder();
            speccy.fullRedraw = true;
            //System.out.println("updateBorder: " + (System.currentTimeMillis() - start));
        }
        
        if( speccy.fullRedraw ) {
            updateScreen();
            //System.out.println("updateScreen()");
//        } else {
            //System.out.println("NOT updateScreen()");
        }
//        else {
//            Graphics2D gcScr = bImg.createGraphics();
//            gcScr.drawImage(bImgScr, 48, 48, this);
//            gcScr.dispose();
//            //System.out.println("update chars");
//        }
    

        // Rejilla horizontal de test
//        for( int idx = 0; idx < 36; idx ++ )
//            Arrays.fill(imgData, idx*2816, idx*2816+352, 0x404040);
        
        //System.out.println("Decode: " + (System.currentTimeMillis() - start));
        if ( speccy.fullRedraw ) {
            if (doubleSize) {
                gc2.drawImage(bImg, escalaOp, 0, 0);
            } else {
                gc2.drawImage(bImg, 0, 0, this);
            }
        }

        //if( speccy.scrMod || speccy.attrMod ) {
            if (doubleSize) {
                gc2.drawImage(bImgScr, escalaOp, 96, 96);
            } else {
                gc2.drawImage(bImgScr, 48, 48, this);
            }
        //}
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
        if( tstates < 3584 )
            return 0;

        // Si son mayores que 68095 (304 * 224), es la zona no visible inferior
        if( tstates > 68095 )
            return imgData.length - 1;

        tstates -= 3584;
        
        int row = tstates / 224;
        int col = tstates % 224;

        int mod = col % 8;
        col -= mod;
        if( mod > 3 )
            col += 4;

//        System.out.println(String.format("t-states: %d\trow: %d\tcol: %d\tmod: %d",
//                tstates+3584, row, col, mod));
        
        int pix = row * 352;

        if (col < 153) {
            return pix += col * 2 + 48;
        }
        if (col > 199) {
            return pix += (col - 200) * 2 + 352;
        } else {
            return pix + 352;
        }
    }

    private void updateBorder() {
        int nBorderChg = speccy.nTimesBorderChg;
        int startPix, endPix;

//        System.out.println("Cambios de border: " + nBorderChg);
//        for( int idx = 0; idx < nBorderChg; idx++ )
//            System.out.println(String.format("statesBorderChg: %d\tvalueBorderChg %d",
//                    speccy.statesBorderChg[idx], speccy.valueBorderChg[idx]));

        if (nBorderChg == 1) {
            speccy.nTimesBorderChg = 0;
            intArrayFill(imgData, Paleta[speccy.portFE & 0x07]);
        } else {
            int color;
            for (int idx = 0; idx < (nBorderChg - 1); idx++) {
                if( speccy.statesBorderChg[idx + 1] < 3584 )
                    continue;

                startPix = tStatesToScrPix(speccy.statesBorderChg[idx]);
                if( startPix > imgData.length - 1)
                    continue;

                endPix = tStatesToScrPix(speccy.statesBorderChg[idx + 1]);
                if( endPix > imgData.length - 1)
                    endPix = imgData.length - 1;

                color = Paleta[speccy.valueBorderChg[idx]];
                for (int count = startPix; count < endPix; count++)
                    imgData[count] = color;
            }

            // Pinta desde el último cambio hasta el final
            startPix = tStatesToScrPix(speccy.statesBorderChg[nBorderChg - 1]);
            if( startPix < imgData.length - 1) {
                color = Paleta[speccy.valueBorderChg[nBorderChg - 1]];
                for( int count = startPix; count < imgData.length - 1; count++ )
                    imgData[count] = color;
            }

            // Y encola para el siguiente frame el primer cambio
            speccy.statesBorderChg[0] = 0;
            speccy.valueBorderChg[0] = speccy.portFE & 0x07;
            speccy.nTimesBorderChg = 1;
        }
    }

    public void updateScreen() {
        int paper, ink;
        int addr, nAttr;
        int pixel, attr;
        int posIni;

        for( int cordy = 0; cordy < 192; cordy++ ) {
            posIni = 256 * cordy;
            // Ahora dibujamos la línea de pantalla
            addr = scrAddr[cordy];
            nAttr = scr2attr[cordy];
            for( int cordx = 0; cordx < 32; cordx++ ) {
                attr = pScrn[nAttr++];
                if( attr > 0x7f )
                    attr &= flash;
                ink = Ink[attr];
                paper = Paper[attr];
                pixel = pScrn[addr++];
                for( int mask = 0x80; mask != 0; mask >>= 1 ) {
                    if( (pixel & mask) != 0 )
                        imgDataScr[posIni++] = ink;
                    else
                        imgDataScr[posIni++] = paper;
                }
            }
        }
    }

    public void updateScreenByte(int address, int value) {
        int row = ((address & 0xe0) >>> 5) | ((address & 0x1800) >>> 8);
        int col = address & 0x1f;
        int scan = (address & 0x700) >>> 8;
        //System.out.println(String.format("Fila :%d\t Col: %d\t scan: %d", row, col, scan));

        int addrPtr = row * 2048 + scan * 256 + col * 8;
        int attr = pScrn[scr2attr[row << 3] + col];
        if( attr > 0x7f )
            attr &= flash;
        int ink = Ink[attr];
        int paper = Paper[attr];
        for (int mask = 0x80; mask != 0; mask >>= 1) {
            if ((value & mask) != 0) {
                imgDataScr[addrPtr++] = ink;
            } else {
                imgDataScr[addrPtr++] = paper;
            }
        }
    }

    public void updateAttrChar(int address, int attr) {
        int row = (address >>> 5)  & 0x1f;
        int col = address & 0x1f;
        if( attr > 0x7f )
            attr &= flash;
        int ink = Ink[attr];
        int paper = Paper[attr];

        for (int scan = 0; scan < 8; scan++) {
            int addrPtr = row * 2048 + scan * 256 + col * 8;
            int scrByte = pScrn[scrAddr[row << 3] + col + scan * 256];
//            System.out.println(String.format("Fila :%d\t Col: %d\t scan: %d\taddress %04X",
//                                row, col, scan, scrAddr[row << 3] + col));
            for (int mask = 0x80; mask != 0; mask >>= 1) {
                if ((scrByte & mask) != 0) {
                    imgDataScr[addrPtr++] = ink;
                } else {
                    imgDataScr[addrPtr++] = paper;
                }
            }
        }
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

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setBorder(javax.swing.BorderFactory.createEtchedBorder());
        setMaximumSize(new java.awt.Dimension(704, 576));
        setMinimumSize(new java.awt.Dimension(352, 288));
        setPreferredSize(new java.awt.Dimension(352, 288));
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    
}
