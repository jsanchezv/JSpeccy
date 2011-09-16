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
import java.util.Timer;
import machine.Clock;
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
    private int borderColor[] = new int[256];
    private BufferedImage bImg;
    private AffineTransform escala;
    private AffineTransformOp escalaOp;
    private RenderingHints renderHints;
    private Timer timerFrame;
    private Clock taskFrame;
    private Spectrum speccy;
    private int nFrame;
        
    /** Creates new form JScreen */
    public JSpeccyScreen() {
        initComponents();
        
        bImg = new BufferedImage(320, 256, BufferedImage.TYPE_INT_RGB);
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
        speccy = new Spectrum();
        pScrn = speccy.getSpectrumMem();
        timerFrame = new Timer();
        taskFrame = new Clock(this);
        //Arrays.fill(borderColor, 0x07);
        startEmulation();
    }

    public void startEmulation() {
        timerFrame.scheduleAtFixedRate(taskFrame, 20, 20);
    }

    public void stopEmulation() {
        timerFrame.cancel();
    }

    public void generateFrame() {
        speccy.execFrame();
        if( ++nFrame % 16 == 0 ) {
            toggleFlash();
            speccy.scrMod = true;
        }
        if( speccy.scrMod )
            repaint();
    }

    public synchronized void toggleFlash() {
        flash = (flash == 0x7f ? 0xff : 0x7f);
        //System.out.println("Flash: " + flash);
    }
    
    public void toggleDoubleSize() {
        doubleSize = !doubleSize;
        if( doubleSize ) {
            this.setPreferredSize(new Dimension(640,512));
        }
        else {
            this.setPreferredSize(new Dimension(320,256));
        }
    }
    
    public void changeBorderColor(int linea, int color) {
        borderColor[linea] = color;
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

        border = Paleta[speccy.portMap[0xfe] & 0x07];
        posIni = 0;
        for(int idx = 0; idx < 32; idx++) {
            //border = Paleta[borderColor[idx]];
            for( int pix = 0; pix < 320; pix++ )
                imgData[posIni++] = border;
        }
        
        //System.out.println("Borrado: " + (System.currentTimeMillis() - start));
        //pScrn = SpectrumRam;
        //int posIni = 10272;  // 10272 = 32 * 352 + 32
        for( int cordy = 0; cordy < 192; cordy++ ) {
            // Poner el borde izquierdo
            //border = Paleta[borderColor[cordy+32]];
            for( int leftBorder = 0; leftBorder < 32; leftBorder++ )
                imgData[posIni++] = border;
            
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
            
            // Pone el borde derecho
            for( int rightBorder = 0; rightBorder < 32; rightBorder++ )
                imgData[posIni++] = border;
        }
        
        for (int idx = 0; idx < 32; idx++) {
            //border = Paleta[borderColor[idx]];
            for (int pix = 0; pix < 320; pix++) {
                imgData[posIni++] = border;
            }
        }
        //System.out.println("Decode: " + (System.currentTimeMillis() - start));
        if( doubleSize ) {
            gc2.drawImage(bImg, escalaOp, 0, 0);
        } else {
            gc2.drawImage(bImg, 0, 0, null);
        }       
        //System.out.println("ms: " + (System.currentTimeMillis() - start));
        //System.out.println("");
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setBorder(javax.swing.BorderFactory.createEtchedBorder());
        setMaximumSize(new java.awt.Dimension(640, 512));
        setMinimumSize(new java.awt.Dimension(320, 256));
        setPreferredSize(new java.awt.Dimension(320, 256));
    }// </editor-fold>//GEN-END:initComponents
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    
}
