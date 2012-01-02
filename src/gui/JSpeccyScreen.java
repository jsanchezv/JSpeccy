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
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.DataBufferInt;
import java.awt.image.Kernel;
import machine.Spectrum;

/**
 *
 * @author  jsanchez
 */
public class JSpeccyScreen extends javax.swing.JComponent {

    private BufferedImage tvImage;
    private BufferedImage tvImageFiltered;
    private BufferedImage tvPalImage;
    private Graphics2D tvImageFilteredGc;
    private Graphics2D tvPalImageGc;
//    private AffineTransform escala;
//    private AffineTransformOp escalaOp;
//    private RenderingHints renderHints;
    private int zoom, screenWidth, screenHeight;
    private Object interpolationMethod;
    private boolean anyFilter = false;
    private boolean palFilter = true;
    private boolean scanlinesFilter = true;
    private boolean rgbFilter = false;
    private int[] imageBuffer;
    private int[] scanline1 = new int[256];
    private int[] scanline2 = new int[256];
//    private int[] scanline3 = new int[256];
    private static final int redMask = 0xff0000;
    private static final int greenMask = 0x00ff00;
    private static final int blueMask = 0x0000ff;
    private static final int redGreenMask = 0xffff00;
    private static final int greenBlueMask = 0x00ffff;
    private static final int redBlueMask = 0xff00ff;
    
    public static final float[] PAL_KERNEL = {
         // low-pass filter kernel
       0.1f, 0.70f, 0.13f, 0.075f
    };
    
    private ConvolveOp palOp;

    /** Creates new form JScreen */
    public JSpeccyScreen() {
        initComponents();

//        escala = AffineTransform.getScaleInstance(2.0f, 2.0f);
//        renderHints = new RenderingHints(RenderingHints.KEY_INTERPOLATION,
//            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
//        renderHints.put(RenderingHints.KEY_RENDERING,
//            RenderingHints.VALUE_RENDER_SPEED);
//        renderHints.put(RenderingHints.KEY_ANTIALIASING,
//            RenderingHints.VALUE_ANTIALIAS_OFF);
//        renderHints.put(RenderingHints.KEY_COLOR_RENDERING,
//            RenderingHints.VALUE_COLOR_RENDER_SPEED);
//        escalaOp = new AffineTransformOp(escala, renderHints);

        screenWidth = Spectrum.SCREEN_WIDTH * 2;
        screenHeight = Spectrum.SCREEN_HEIGHT * 2;
        setMaximumSize(new java.awt.Dimension(screenWidth, screenHeight));
        setMinimumSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH,
            Spectrum.SCREEN_HEIGHT));
        setPreferredSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH,
            Spectrum.SCREEN_HEIGHT));
        
        tvPalImage = new BufferedImage(Spectrum.SCREEN_WIDTH, Spectrum.SCREEN_HEIGHT,
            BufferedImage.TYPE_INT_RGB);
        tvPalImageGc = tvPalImage.createGraphics();
        
        interpolationMethod = RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        
        scanline1 [0] = scanline2 [0] = 0; // scanline3 [0] = 0;
        for (int color = 1; color < scanline1 .length; color++) {
            scanline1 [color] = (int)(color * 0.80f);
            scanline2 [color] = (int)(color * 0.70f);
//            scanline3 [color] = (int)(color * 0.15625f);
        }
        
        palOp = new ConvolveOp(new Kernel(PAL_KERNEL.length, 1, PAL_KERNEL), ConvolveOp.EDGE_NO_OP, null);
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
            screenWidth = Spectrum.SCREEN_WIDTH * zoom;
            screenHeight = Spectrum.SCREEN_HEIGHT * zoom;
            tvImageFiltered = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
            imageBuffer =
                ((DataBufferInt) tvImageFiltered.getRaster().getDataBuffer()).getBankData()[0];
            tvImageFilteredGc = tvImageFiltered.createGraphics();
            tvImageFilteredGc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationMethod);
//            escala = AffineTransform.getScaleInstance(zoom, zoom);
//            escalaOp = new AffineTransformOp(escala, renderHints);
            this.setPreferredSize(new Dimension(screenWidth, screenHeight));
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
        
        switch (zoom) {
            case 2:    
                if (anyFilter) {
                    if (palFilter) {
                        tvPalImageGc.drawImage(tvImage, palOp, 0, 0);
                        tvImageFilteredGc.drawImage(tvPalImage, 0, 0, screenWidth, screenHeight, null);
                    } else {
                        tvImageFilteredGc.drawImage(tvImage, 0, 0, screenWidth, screenHeight, null);
                    }

                    if (scanlinesFilter)
                        drawScanlines2x();
                    
                    if (rgbFilter)
                        filterRGB2x();
                    
                    gc2.drawImage(tvImageFiltered, 0, 0, null);
                } else {
                    gc2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationMethod);
                    gc2.drawImage(tvImage, 0, 0, screenWidth, screenHeight, null);
                }
                break;
            case 3:
                if (anyFilter) {
                    if (palFilter) {
                        tvPalImageGc.drawImage(tvImage, palOp, 0, 0);
                        tvImageFilteredGc.drawImage(tvPalImage, 0, 0, screenWidth, screenHeight, null);
                    } else {
                        tvImageFilteredGc.drawImage(tvImage, 0, 0, screenWidth, screenHeight, null);
                    }

                    if (scanlinesFilter)
                        drawScanlines3x();
                    
                    if (rgbFilter)
                        filterRGB3x();
                    
                    gc2.drawImage(tvImageFiltered, 0, 0, null);
                } else {
                    gc2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationMethod);
                    gc2.drawImage(tvImage, 0, 0, screenWidth, screenHeight, null);
                }
                break;
            case 4:
                if (anyFilter) {
                    if (palFilter) {
                        tvPalImageGc.drawImage(tvImage, palOp, 0, 0);
                        tvImageFilteredGc.drawImage(tvPalImage, 0, 0, screenWidth, screenHeight, null);
                    } else {
                        tvImageFilteredGc.drawImage(tvImage, 0, 0, screenWidth, screenHeight, null);
                    }

                    if (scanlinesFilter)
                        drawScanlines4x();
                    
                    if (rgbFilter)
                        filterRGB2x();
                    
                    gc2.drawImage(tvImageFiltered, 0, 0, null);
                } else {
                    gc2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationMethod);
                    gc2.drawImage(tvImage, 0, 0, screenWidth, screenHeight, null);
                }
                break;
            default:
                if (palFilter) {
                    gc2.drawImage(tvImage, palOp, 0, 0);
                } else {
                    gc2.drawImage(tvImage, 0, 0, null);
                }
//                gc2.drawImage(tvImage, cop, 0, 0);
        }
    }
    
    private void drawScanlines2x() {
        int color = 0, res = 0;
        
        int width = Spectrum.SCREEN_WIDTH * 2;
        
        int pixel = width;
        
        while (pixel < imageBuffer.length) {
            for (int col = 0; col < Spectrum.SCREEN_WIDTH; col++) {
                
                if (imageBuffer[pixel] == 0) {
                    pixel += 2;
                    continue;
                }
                
                if (color != imageBuffer[pixel]) {
                    color = imageBuffer[pixel];
                    int red = scanline1 [color >>> 16];
                    int green = scanline1 [(color >>> 8) & 0xff];
                    int blue = scanline1 [color & 0xff];
                    res = (red << 16) | (green << 8)  | blue;
                }
                
                imageBuffer[pixel++] = res;
                imageBuffer[pixel++] = res;
            }
            
            pixel += width;
        }
    }
    
    private void drawScanlines3x() {
        int color = 0, res1 = 0, res2 = 0;
        
        int width = Spectrum.SCREEN_WIDTH * 3;
        
        int pixel = width;
        
        while (pixel < imageBuffer.length) {
            for (int col = 0; col < Spectrum.SCREEN_WIDTH; col++) {
                
                if (imageBuffer[pixel] == 0) {
                    pixel += 3;
                    continue;
                }
                
                if (color != imageBuffer[pixel]) {
                    color = imageBuffer[pixel];
                    int red = color >>> 16;
                    int green = (color >>> 8) & 0xff;
                    int blue = color & 0xff;
                    res1 = (scanline1[red] << 16) | (scanline1[green] << 8)  | scanline1[blue];
                    res2 = (scanline2[red] << 16) | (scanline2[green] << 8)  | scanline2[blue];
                }
                
                imageBuffer[pixel + width] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + width] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + width] = res2;
                imageBuffer[pixel++] = res1;
            }
            
            pixel += width * 2;
        }
    }
    
    private void drawScanlines4x() {
        int color = 0, res1 = 0, res2 = 0;
        
        int width = Spectrum.SCREEN_WIDTH * 4;
        
        int pixel = width * 2;
        
        while (pixel < imageBuffer.length) {
            for (int col = 0; col < Spectrum.SCREEN_WIDTH; col++) {
                
                if (imageBuffer[pixel] == 0) {
                    pixel += 4;
                    continue;
                }
                
                if (color != imageBuffer[pixel]) {
                    color = imageBuffer[pixel];
                    int red = color >>> 16;
                    int green = (color >>> 8) & 0xff;
                    int blue = color & 0xff;
                    res1 = (scanline1[red] << 16) | (scanline1[green] << 8)  | scanline1[blue];
                    res2 = (scanline2[red] << 16) | (scanline2[green] << 8)  | scanline2[blue];
                }
                
                imageBuffer[pixel + width] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + width] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + width] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + width] = res2;
                imageBuffer[pixel++] = res1;
            }
            
            pixel += width * 3;
        }
    }
    
    private void filterRGB2x() {

        int width = Spectrum.SCREEN_WIDTH * zoom;

        int pixel = 0;
        
        while (pixel < imageBuffer.length) {
            for (int col = 0; col < width / 2; col++) {
                 imageBuffer[pixel + width] &= blueMask;
                 imageBuffer[pixel++] &= redMask;
                 imageBuffer[pixel++] &= greenMask;
            }
            pixel += width;
        }
    }
    
    private void filterRGB3x() {

        int width = Spectrum.SCREEN_WIDTH * zoom;

        int pixel = 0;
        
        while (pixel < imageBuffer.length) {
            for (int col = 0; col < width / 3; col++) {
                imageBuffer[pixel + width] &= greenMask;
                pixel++;
                
                imageBuffer[pixel + width] &= redMask;
                imageBuffer[pixel + width * 2] &= blueMask;
                imageBuffer[pixel++] &= greenMask;
                
                imageBuffer[pixel + width * 2] &= redMask;
                imageBuffer[pixel++] &= blueMask;
            }
            pixel += width * 2;
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

    /**
     * @return the palFilter
     */
    public boolean isPalFilter() {
        return palFilter;
    }

    /**
     * @param palFilter the palFilter to set
     */
    public void setPalFilter(boolean palFilter) {
        this.palFilter = palFilter;
        
        if (palFilter) {
            rgbFilter = false;
            anyFilter = true;
        }
        
    }

    /**
     * @return the scanlinesFilter
     */
    public boolean isScanlinesFilter() {
        return scanlinesFilter;
    }

    /**
     * @param scanlinesFilter the scanlinesFilter to set
     */
    public void setScanlinesFilter(boolean scanlinesFilter) {
        this.scanlinesFilter = scanlinesFilter;
        
        if (scanlinesFilter) {
            rgbFilter = false;
            anyFilter = true;
        }
    }

    /**
     * @return the rgbFilter
     */
    public boolean isRgbFilter() {
        return rgbFilter;
    }

    /**
     * @param rgbFilter the rgbFilter to set
     */
    public void setRgbFilter(boolean rgbFilter) {
        this.rgbFilter = rgbFilter;
        
        if (rgbFilter) {
            palFilter = scanlinesFilter = false;
            anyFilter = true;
        }
    }

    /**
     * @param interpolationMethod the interpolationMethod to set
     */
    public void setInterpolationMethod(Object interpolationMethod) {
        this.interpolationMethod = interpolationMethod;
    }
}
