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
import machine.Spectrum;

/**
 *
 * @author  jsanchez
 */
public class JSpeccyScreen extends javax.swing.JComponent {

    private BufferedImage tvImage, borderImage, screenImage;
    private AffineTransform escala;
    private AffineTransformOp escalaOp;
    private RenderingHints renderHints;
    private boolean doubleSize, frameReady, dirtyBorder;
    private Graphics2D gcTvImage, gcBorderImage, gcScreenImage;

    /** Creates new form JScreen */
    public JSpeccyScreen() {
        initComponents();

        tvImage = new BufferedImage(Spectrum.SCREEN_WIDTH, Spectrum.SCREEN_HEIGHT,
                                    BufferedImage.TYPE_INT_RGB);
        gcTvImage = tvImage.createGraphics();
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
//        bImg = spectrum.getScreenImage();
        setMaximumSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH * 2,
                Spectrum.SCREEN_HEIGHT * 2));
        setMinimumSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH,
                Spectrum.SCREEN_HEIGHT));
        setPreferredSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH,
                Spectrum.SCREEN_HEIGHT));
    }

    public void notifyNewFrame(boolean dirty) {
        frameReady = true;
        dirtyBorder = dirty;
    }

    public void setScreenImage(BufferedImage bImage, BufferedImage scrImage) {
        borderImage = bImage;
        screenImage = scrImage;
        gcBorderImage = borderImage.createGraphics();
        gcScreenImage = screenImage.createGraphics();
        frameReady = false;
        dirtyBorder = true;
    }

    public void toggleDoubleSize() {
        doubleSize = !doubleSize;
        if (doubleSize) {
            this.setPreferredSize(new Dimension(Spectrum.SCREEN_WIDTH * 2,
                    Spectrum.SCREEN_HEIGHT * 2));
        } else {
            this.setPreferredSize(new Dimension(Spectrum.SCREEN_WIDTH,
                    Spectrum.SCREEN_HEIGHT));
        }
    }

    @Override
    public void paintComponent(Graphics gc) {
        //super.paintComponent(gc);
        Graphics2D gc2 = (Graphics2D)gc;

        /* 20/03/2010
         * La pantalla se ha de dibujar en un solo paso. Si el borde no se
         * modificó, se vuelca sobre el doble búfer solo la pantalla. Si se
         * modificó el borde, primero se vuelca la pantalla sobre la imagen
         * del borde y luego se vuelca el borde. Si no, se pueden ver "artifacts"
         * en juegos como el TV-game.
         */

        // Esto sería un update solicitado por la clase Spectrum
        if (frameReady) {
            if(dirtyBorder) {
//                System.out.println("Update from Spectrum: frameReady + dirtyBorder");
                gcBorderImage.drawImage(screenImage, Spectrum.BORDER_WIDTH,
                                        Spectrum.BORDER_WIDTH, null);
                gcTvImage.drawImage(borderImage, 0, 0, null);
                if (doubleSize) {
                    gc2.drawImage(tvImage, escalaOp, 0, 0);
                } else {
                    gc2.drawImage(tvImage, 0, 0, null);
                }
                dirtyBorder = false;
            } else {
//                System.out.println("Update from Spectrum: frameReady");
                gcTvImage.drawImage(screenImage, Spectrum.BORDER_WIDTH,
                                    Spectrum.BORDER_WIDTH, null);
                if (doubleSize) {
                    gc2.drawImage(screenImage, escalaOp, Spectrum.BORDER_WIDTH,
                                  Spectrum.BORDER_WIDTH);
                } else {
                    gc2.drawImage(screenImage, Spectrum.BORDER_WIDTH,
                                  Spectrum.BORDER_WIDTH, null);
                }
            }
            frameReady = false;
        } else {
            // Esto sería un update solicitado por su cuenta por Swing
//            System.out.println("Update from Swing");
            if (doubleSize) {
                gc2.drawImage(tvImage, escalaOp, 0, 0);
            } else {
                gc2.drawImage(tvImage, 0, 0, this);
            }
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
