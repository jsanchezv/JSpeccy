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
    private boolean anyFilter = true;
    private boolean palFilter = true;
    private boolean scanlinesFilter = false;
    private boolean rgbFilter = false;
    private int[] imageBuffer, imagePalBuffer;
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
       0.25f, 0.5f, 0.25f
    };
    
    private ConvolveOp palOp;
    
    private int tableY[] = new int[32];
    private int tableU[] = new int[32];
    private int tableV[] = new int[32];

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

        screenWidth = Spectrum.SCREEN_WIDTH;
        screenHeight = Spectrum.SCREEN_HEIGHT;
        setMaximumSize(new java.awt.Dimension(screenWidth, screenHeight));
        setMinimumSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH,
            Spectrum.SCREEN_HEIGHT));
        setPreferredSize(new java.awt.Dimension(Spectrum.SCREEN_WIDTH,
            Spectrum.SCREEN_HEIGHT));
        
        tvPalImage = new BufferedImage(Spectrum.SCREEN_WIDTH, Spectrum.SCREEN_HEIGHT,
            BufferedImage.TYPE_INT_RGB);
        tvPalImageGc = tvPalImage.createGraphics();
        imagePalBuffer =
                ((DataBufferInt) tvPalImage.getRaster().getDataBuffer()).getBankData()[0];
        
        interpolationMethod = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
        
        scanline1 [0] = scanline2 [0] = 0; // scanline3 [0] = 0;
        for (int color = 1; color < scanline1 .length; color++) {
            scanline1 [color] = (int)(color * 0.80f);
            scanline2 [color] = (int)(color * 0.70f);
//            scanline3 [color] = (int)(color * 0.15625f);
        }
        
        palOp = new ConvolveOp(new Kernel(PAL_KERNEL.length, 1, PAL_KERNEL), ConvolveOp.EDGE_NO_OP, null);
        
        int yuv[] = new int[3];
        
        for (int color = 0; color < Spectrum.Paleta.length; color++) {
            int rgb = Spectrum.Paleta[color];
            rgb2yuv(rgb, yuv);
            rgb %= 31;
            tableY[rgb] = yuv[0];
            tableU[rgb] = yuv[1];
            tableV[rgb] = yuv[2];
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
            screenWidth = Spectrum.SCREEN_WIDTH;
            screenHeight = Spectrum.SCREEN_HEIGHT;
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
                        tvPalImageGc.drawImage(tvImage, 0, 0, null);
                        palFilterYUV();
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
//                    gc2.drawImage(tvImage, palOp, 0, 0);
                    tvPalImageGc.drawImage(tvImage, 0, 0, null);
                    palFilterYUV();
                    gc2.drawImage(tvPalImage, 0, 0, null);
                } else {
                    gc2.drawImage(tvImage, 0, 0, null);
                }
        }
    }
    
    private void drawScanlines2x() {

        int color = 0, res = 0;
        
        // Jump the first line
        int pixel = screenWidth;
        
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
            
            // Jump the next line
            pixel += screenWidth;
        }        
    }
    
    private void drawScanlines3x() {
        int color = 0, res1 = 0, res2 = 0;
        
        int jump = screenWidth * 2;
        
        // Jump the first line
        int pixel = screenWidth;
        
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
                
                imageBuffer[pixel + screenWidth] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + screenWidth] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + screenWidth] = res2;
                imageBuffer[pixel++] = res1;
            }
            
            pixel += jump;
        }
    }
    
    private void drawScanlines4x() {
        int color = 0, res1 = 0, res2 = 0;
        
        int jump = screenWidth * 3;
        
        int pixel = screenWidth * 2;
        
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
                
                imageBuffer[pixel + screenWidth] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + screenWidth] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + screenWidth] = res2;
                imageBuffer[pixel++] = res1;
                
                imageBuffer[pixel + screenWidth] = res2;
                imageBuffer[pixel++] = res1;
            }
            
            pixel += jump;
        }
    }
    
    private void filterRGB2x() {

        int pixel = 0;
        
        while (pixel < imageBuffer.length) {
            for (int col = 0; col < Spectrum.SCREEN_WIDTH; col++) {
                 imageBuffer[pixel + screenWidth] &= blueMask;
                 imageBuffer[pixel++] &= redMask;
                 imageBuffer[pixel++] &= greenMask;
            }
            pixel += screenWidth;
        }
    }
    
    private void filterRGB3x() {

        int jump = screenWidth * 2;
        
        int pixel = 0;
        
        while (pixel < imageBuffer.length) {
            for (int col = 0; col < Spectrum.SCREEN_WIDTH; col++) {
                imageBuffer[pixel + screenWidth] &= greenMask;
                pixel++;
                
                imageBuffer[pixel + screenWidth] &= redMask;
                imageBuffer[pixel + jump] &= blueMask;
                imageBuffer[pixel++] &= greenMask;
                
                imageBuffer[pixel + jump] &= redMask;
                imageBuffer[pixel++] &= blueMask;
            }
            pixel += jump;
        }
    }

    /*
     * Y = R *  .299000 + G *  .587000 + B *  .114000
     * U = R * -.168736 + G * -.331264 + B *  .500000 + 128 or U = (B - Y) * 0.565 + 128
     * V = R *  .500000 + G * -.418688 + B * -.081312 + 128 or V = (R - Y) * 0.713 + 128
     * http://www.fourcc.org/fccyvrgb.php
     * http://softpixel.com/~cwright/programming/colorspace/
     */
    private void rgb2yuv(int rgb, int[] yuv) {
        int r = rgb >>> 16;
//        int g = ((rgb >>> 8) & 0xff);
        int b = rgb & 0xff;

        int y = (int) (0.299 * r + 0.587 * ((rgb >>> 8) & 0xff) + 0.114 * b);
//        int u = (int)(r * -.168736 + g * -.331264 + b *  0.5);
//        int v = (int)(r * 0.5 + g * -0.418688 + b * -0.081312);
        int u = (int) ((b - y) * 0.565);
        int v = (int) ((r - y) * 0.713);

        yuv[0] = y;
        yuv[1] = u + 128;
        yuv[2] = v + 128;
    }
    
    /*
     * R = Y + 1.402 (V - 128)
     * G = Y - 0.34414 (U - 128) - 0.71414 (V - 128)
     * B = Y + 1.772 (U - 128)
     */
    private int yuv2rgb(int y, int u, int v) {
        u -= 128;
        v -= 128;
//        int r = (int)(y + 1.4075 * v);
        int r = (int) (y + 1.402 * v);
        if (r < 0) {
            r = 0;
        }
        if (r > 255) {
            r = 255;
        }

//        int g = (int)(y - 0.3455 * u - 0.7169 * v);
        int g = (int) (y - 0.34414 * u - 0.71414 * v);
        if (g < 0) {
            g = 0;
        }
        if (g > 255) {
            g = 255;
        }

//        int b = (int)(y + 1.7790 * u);
        int b = (int) (y + 1.772 * u);
        if (b < 0) {
            b = 0;
        }
        if (b > 255) {
            b = 255;
        }

        return (r << 16) | (g << 8) | b;
    }
  
    private void palFilterYUV() {
        
        long ini = System.currentTimeMillis();

        int u1, v1;
        int y2, u2, v2;
        int y3, u3, v3;
        
        int start = Spectrum.BORDER_HEIGHT * 320 + Spectrum.BORDER_WIDTH - 2;
        int limit = (Spectrum.BORDER_HEIGHT + 192) * 320 - Spectrum.BORDER_WIDTH + 1;
        int pixel = 0;
        
        while (pixel < limit) {
            pixel = start;
            int idx = imagePalBuffer[pixel++] % 31;
            // Y1 is never used
            u1 = tableU[idx];
            v1 = tableV[idx];

            idx = imagePalBuffer[pixel] % 31;
            y2 = tableY[idx];
            u2 = tableU[idx];
            v2 = tableV[idx];
            for (int col = 0; col < 258; col++) {
                idx = imagePalBuffer[pixel + 1] % 31;
                y3 = tableY[idx];
                u3 = tableU[idx];
                v3 = tableV[idx];
                imagePalBuffer[pixel++] = yuv2rgb(y2,
                        (u1 + u1 + u2 + u3) >>> 2,
                        (v1 + v1 + v2 + v3) >>> 2);
                u1 = u2;
                v1 = v2;
                y2 = y3;
                u2 = u3;
                v2 = v3;
            }
            start += 320;
        }
        
        System.out.println(String.format("PalFilterYUV in %d ms.", System.currentTimeMillis() - ini));
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

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
