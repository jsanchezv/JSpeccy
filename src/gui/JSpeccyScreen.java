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
public class JSpeccyScreen extends javax.swing.JComponent {

    private BufferedImage tvImage;
    private BufferedImage tvImageFiltered;
    private Graphics2D tvImageFilteredGc;
    private AffineTransform escala;
    private AffineTransformOp escalaOp;
    private RenderingHints renderHints;
    private int zoom;
    private int[] imageBuffer;
    private int[] percentColor= new int[256];

    /** Creates new form JScreen */
    public JSpeccyScreen() {
        initComponents();

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

        setMaximumSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH * 2,
            Spectrum.SCREEN_HEIGHT * 2));
        setMinimumSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH,
            Spectrum.SCREEN_HEIGHT));
        setPreferredSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH,
            Spectrum.SCREEN_HEIGHT));
        
        tvImageFiltered = new BufferedImage(Spectrum.SCREEN_WIDTH * 2, Spectrum.SCREEN_HEIGHT * 2,
            BufferedImage.TYPE_INT_RGB);
        imageBuffer =
            ((DataBufferInt) tvImageFiltered.getRaster().getDataBuffer()).getBankData()[0];
        tvImageFilteredGc = tvImageFiltered.createGraphics();
        
        percentColor[0] = 0;
        for (int color = 1; color < percentColor.length; color++) {
            percentColor[color] = (int)(color * 0.70f);
        }
    }

    public void setTvImage(BufferedImage bImage) {
        tvImage = bImage;
    }

    public void setZoom(int zoom) {
        if (this.zoom == zoom)
            return;
        
        if (zoom < 2)
            zoom = 1;
        
        if (zoom > 4)
            zoom = 4;
        
        this.zoom = zoom;
        
        if (zoom > 1) {
            escala = AffineTransform.getScaleInstance(zoom, zoom);
            escalaOp = new AffineTransformOp(escala, renderHints);
            this.setPreferredSize(
                new Dimension(Spectrum.SCREEN_WIDTH * zoom, Spectrum.SCREEN_HEIGHT * zoom));
        } else {
            this.setPreferredSize(
                new Dimension(Spectrum.SCREEN_WIDTH, Spectrum.SCREEN_HEIGHT));
        }
    }

    public int getZoom() {
        return zoom;
    }
    
    public boolean isZoomed() {
        return zoom > 1;
    }

    @Override
    public void paintComponent(Graphics gc) {
        //super.paintComponent(gc);
        Graphics2D gc2 = (Graphics2D) gc;

        if (zoom > 1) {
            tvImageFilteredGc.drawImage(tvImage, escalaOp, 0, 0);
            filterRGB2x();
//            drawScanlines();
            gc2.drawImage(tvImageFiltered, 0, 0, null);
//            gc2.drawImage(tvImageFiltered, escalaOp, 0, 0);
        } else {
            gc2.drawImage(tvImage, 0, 0, null);
        }
    }

    public void drawScanlines() {
        int pos = 0;
        
        int width = Spectrum.SCREEN_WIDTH * zoom;
        
        while (pos < imageBuffer.length) {
            if (pos % width == 0)
                pos += width;
            
            int color = imageBuffer[pos];
            if (color == 0) {
                pos++;
                continue;
            }
            
            int red = percentColor[color >>> 16];
            int green = percentColor[(color >>> 8) & 0xff];
            int blue = percentColor[color & 0xff];
            imageBuffer[pos] = (red << 16) | (green << 8)  | blue;
            pos++;
        }
    }
    
    public void filterRGB2x() {
        int pixel = 0;

        while (pixel < imageBuffer.length) {
            for (int col = 0; col < Spectrum.SCREEN_WIDTH; col++) {
//                 imageBuffer[pixel++] &= 0xff0000;
                pixel++;
                int color = imageBuffer[pixel];
                if (color == 0) {
                    pixel++;
                    continue;
                }

                int red = percentColor[color >>> 16];
                int green = percentColor[(color >>> 8) & 0xff];
                int blue = percentColor[color & 0xff];
                imageBuffer[pixel++] = (red << 16) | (green << 8)  | blue;
//                imageBuffer[pixel++] &= 0x00ff00;

            }

            for (int col = 0; col < Spectrum.SCREEN_WIDTH; col++) {
//                imageBuffer[pixel] &= 0x0000ff;
                int color = imageBuffer[pixel];
                if (color == 0) {
                    pixel += 2;
                    continue;
                }

                int red = percentColor[color >>> 16];
                int green = percentColor[(color >>> 8) & 0xff];
                int blue = percentColor[color & 0xff];
                imageBuffer[pixel] = (red << 16) | (green << 8)  | blue;
                pixel += 2;

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
