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
        0xffffff  /* blanco brillante */

    };
    
    private static int Paper[] = new int[256];
    private static int Ink[] = new int[256];
    private static int scrAddr[] = new int[192];
    private static int scr2attr[] = new int[192];
    //private static int scrData[] = new int[352 * 288 * 3];
    private static int imgData[];
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
        
        bImg = new BufferedImage(352, 296, BufferedImage.TYPE_INT_RGB);
        imgData = ((DataBufferInt)bImg.getRaster().getDataBuffer()).getBankData()[0];
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
        System.out.println("imgData.length = " + imgData.length);
        //timerFrame = new Timer();
        //taskFrame = new Clock(this);
        //startEmulation();
        //addKeyListener(this);
        //requestFocus();
    }



//    public void startEmulation() {
//        timerFrame.scheduleAtFixedRate(taskFrame, 0, 20);
//    }
//
//    public void stopEmulation() {
//        timerFrame.cancel();
//    }

//    public void generateFrame() {
//        //long start = System.currentTimeMillis();
//        speccy.execFrame();
//        if( ++nFrame % 16 == 0 ) {
//            toggleFlash();
//            speccy.scrMod = true;
//        }
//        if( speccy.scrMod )
//            repaint();
//        //System.out.println("generateFrame en: " + (System.currentTimeMillis() - start));
//    }

    public synchronized void toggleFlash() {
        flash = (flash == 0x7f ? 0xff : 0x7f);
    }
    
    public void toggleDoubleSize() {
        doubleSize = !doubleSize;
        if( doubleSize ) {
            this.setPreferredSize(new Dimension(704, 592));
        }
        else {
            this.setPreferredSize(new Dimension(352, 296));
        }
    }
    
    @Override
    public void paintComponent(Graphics gc) {
        //super.paintComponent(gc);
        paintScreen((Graphics2D) gc);
    }
    
    private void paintScreen(Graphics2D gc2) {
        int paper, ink;
        int addr, nAttr;
        int pixel, attr;
        int border, posIni;
        
        //long start = System.currentTimeMillis();
        
        //System.out.println("Borrado: " + (System.currentTimeMillis() - start));
        //pScrn = SpectrumRam;
        if( speccy.nTimesBorderChg != 0 )
            updateBorder();

        for( int cordy = 0; cordy < 192; cordy++ ) {
            posIni = 352 * cordy + 16944; // 16944 = 48 * 352 + 48
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
                        imgData[posIni++] = ink;
                    else
                        imgData[posIni++] = paper;
                }
            }
        }

        for( int idx = 0; idx < 37; idx ++ )
            Arrays.fill(imgData, idx*2816, idx*2816+352, 0x404040);
        
        //System.out.println("Decode: " + (System.currentTimeMillis() - start));
        if (doubleSize) {
            gc2.drawImage(bImg, escalaOp, 0, 0);
        } else {
            gc2.drawImage(bImg, 0, 0, null);
        }
        //System.out.println("ms: " + (System.currentTimeMillis() - start));
        //System.out.println("");
    }
    
    /*
     * Cada línea completa de imagen dura 224 T-Estados, divididos en:
     * 48 T-Estados iniciales de H-Sync y blanking
     * 24 T-Estados en los que se dibujan 48 pixeles del borde izquierdo
     * 128 T-Estados en los que se dibujan los 256 pixeles de pantalla
     * 24 T-Estados en los que se dibujan los 48 pixeles del borde derecho
     *
     * Cada pantalla consta de 312 líneas divididas en:
     * 16 líneas en las cuales el haz vuelve a la parte superior de la pantalla
     * 48 líneas de borde superior
     * 192 líneas de pantalla
     * 56 líneas de borde inferior
     */
    private int tStatesToScrPix(int tstates) {

        // Si los tstates son < 3632 (16 * 224 + 48), no estamos en la zona visible
        if( tstates < 3584 )
            return 0;

        int linea = tstates / 224;
        int pix = (tstates % 224);
//        System.out.println(String.format("T-States: %d\tlinea: %d\tpix: %d\taddr: %d",
//                tstates,linea, pix, (linea*352+pix)));
        return linea * 352 + pix;
    }

    private void updateBorder() {
        int nBorderChg = speccy.nTimesBorderChg;
        int startPix, endPix;

//        System.out.println("Cambios de border: " + nBorderChg);
//        for( int idx = 0; idx < nBorderChg; idx++ )
//            System.out.println(String.format("statesBorderChg: %d\tvalueBorderChg %d",
//                    speccy.statesBorderChg[idx], speccy.valueBorderChg[idx]));

        for( int idx = 0; idx < (nBorderChg - 1); idx++ ) {
            startPix = tStatesToScrPix(speccy.statesBorderChg[idx]);
            endPix = tStatesToScrPix(speccy.statesBorderChg[idx + 1]);
            if( startPix >= imgData.length )
                startPix = imgData.length - 1;
            if( endPix >= imgData.length )
                endPix = imgData.length - 1;
            //System.out.println(String.format("startPix: %d\tendPix %d", startPix, endPix));
            if( endPix > startPix )
                Arrays.fill(imgData, startPix, endPix -1,
                            Paleta[speccy.valueBorderChg[idx]]);
        }

        startPix = tStatesToScrPix(speccy.statesBorderChg[nBorderChg - 1]);
        if( startPix >= imgData.length )
            startPix = imgData.length - 1;
        //System.out.println("startPix final: " + startPix);

        Arrays.fill(imgData, startPix, imgData.length - 1,
                    Paleta[speccy.valueBorderChg[nBorderChg - 1]]);

        if( speccy.statesBorderChg[0] > 0 ) {
            speccy.statesBorderChg[0] = 0;
            speccy.valueBorderChg[0] = speccy.valueBorderChg[nBorderChg - 1];
            speccy.nTimesBorderChg = 1;
        }
        else
            speccy.nTimesBorderChg = 0;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setBorder(javax.swing.BorderFactory.createEtchedBorder());
        setMaximumSize(new java.awt.Dimension(704, 592));
        setMinimumSize(new java.awt.Dimension(352, 296));
        setPreferredSize(new java.awt.Dimension(352, 296));
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    
}
