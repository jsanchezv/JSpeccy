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
import machine.Spectrum;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

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
    private int zoom, screenWidth, screenHeight;
    private Object interpolationMethod;
    private boolean anyFilter = false;
    private boolean palFilter = false;
    private boolean scanlinesFilter = false;
    private boolean rgbFilter = false;
    private int[] imageBuffer, imagePalBuffer;
    private int[] scanline1 = new int[256];
    private int[] scanline2 = new int[256];
    private static final int redMask = 0xff0000;
    private static final int greenMask = 0x00ff00;
    private static final int blueMask = 0x0000ff;

    private int tableYUV[][] = new int[3][32];
    private static final int Yuv = 0;
    private static final int yUv = 1;
    private static final int yuV = 2;

    // Estos miembros solo cambian cuando cambia el tamaño del borde
    private int LEFT_BORDER = 32;
    private int RIGHT_BORDER = 32;
    private int SCREEN_WIDTH = LEFT_BORDER + 256 + RIGHT_BORDER;
    private int TOP_BORDER = 24;
    private int BOTTOM_BORDER = 24;
    private int SCREEN_HEIGHT = TOP_BORDER + 192 + BOTTOM_BORDER;

    private int borderMode;

    /** Creates new form JScreen */
    public JSpeccyScreen() {
        initComponents();

        screenWidth = SCREEN_WIDTH;
        screenHeight = SCREEN_HEIGHT;
        zoom = borderMode = 1;

        Dimension screenSize = new Dimension(screenWidth, screenHeight);
        setMaximumSize(screenSize);
        setMinimumSize(screenSize);
        setPreferredSize(screenSize);

        tvPalImage = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        tvPalImageGc = tvPalImage.createGraphics();
        imagePalBuffer =
                ((DataBufferInt) tvPalImage.getRaster().getDataBuffer()).getBankData()[0];

        interpolationMethod = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;

        scanline1 [0] = scanline2 [0] = 0; // scanline3 [0] = 0;
        for (int color = 1; color < scanline1 .length; color++) {
            scanline1 [color] = (int)(color * 0.80f);
            scanline2 [color] = (int)(color * 0.70f);
        }

        int yuv[] = new int[3];
        for (int color = 0; color < Spectrum.Paleta.length; color++) {
            int rgb = Spectrum.Paleta[color];
            rgb2yuv(rgb, yuv);
            rgb %= 31;
            tableYUV[Yuv][rgb] = yuv[0];
            tableYUV[yUv][rgb] = yuv[1];
            tableYUV[yuV][rgb] = yuv[2];
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

        screenWidth = SCREEN_WIDTH * zoom;
        screenHeight = SCREEN_HEIGHT * zoom;

        if (zoom > 1) {
            tvImageFiltered = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
            imageBuffer =
                ((DataBufferInt) tvImageFiltered.getRaster().getDataBuffer()).getBankData()[0];
            if (tvImageFilteredGc != null)
                tvImageFilteredGc.dispose();
            tvImageFilteredGc = tvImageFiltered.createGraphics();
            tvImageFilteredGc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationMethod);
        }

        Dimension screenSize = new Dimension(screenWidth, screenHeight);
        setMaximumSize(screenSize);
        setMinimumSize(screenSize);
        setPreferredSize(screenSize);
    }

    public int getZoom() {
        return zoom;
    }

    public boolean isZoomed() {
        return zoom > 1;
    }

    public void setBorderMode(int mode) {
        if (borderMode == mode)
            return;

        borderMode = mode;

        switch(mode) {
            case 0: // no border
                LEFT_BORDER = RIGHT_BORDER = TOP_BORDER = BOTTOM_BORDER = 0;
                break;
            case 2: // Full standard border
                LEFT_BORDER = 48;
                RIGHT_BORDER = 48;
                TOP_BORDER = 48;
                BOTTOM_BORDER = 56;
                break;
            case 3: // Huge border
                LEFT_BORDER = 64;
                RIGHT_BORDER = 64;
                TOP_BORDER = 56;
                BOTTOM_BORDER = 56;
                break;
            default: // Standard border
                LEFT_BORDER = 32;
                RIGHT_BORDER = 32;
                TOP_BORDER = 24;
                BOTTOM_BORDER = 24;
        }

        SCREEN_WIDTH = LEFT_BORDER + 256 + RIGHT_BORDER;
        SCREEN_HEIGHT = TOP_BORDER + 192 + BOTTOM_BORDER;

        tvPalImage = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_RGB);
        if (tvPalImageGc != null)
                tvPalImageGc.dispose();
        tvPalImageGc = tvPalImage.createGraphics();
        imagePalBuffer =
                ((DataBufferInt) tvPalImage.getRaster().getDataBuffer()).getBankData()[0];

        screenWidth = SCREEN_WIDTH * zoom;
        screenHeight = SCREEN_HEIGHT * zoom;

        tvImageFiltered = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
        imageBuffer =
            ((DataBufferInt) tvImageFiltered.getRaster().getDataBuffer()).getBankData()[0];
        if (tvImageFilteredGc != null)
                tvImageFilteredGc.dispose();
        tvImageFilteredGc = tvImageFiltered.createGraphics();
        tvImageFilteredGc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationMethod);

        Dimension screenSize = new Dimension(screenWidth, screenHeight);
        setMaximumSize(screenSize);
        setMinimumSize(screenSize);
        setPreferredSize(screenSize);
    }

    public int getBorderMode() {
        return borderMode;
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
                        tvPalImageGc.drawImage(tvImage, 0, 0, null);
                        palFilterYUV();
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
                        tvPalImageGc.drawImage(tvImage, 0, 0, null);
                        palFilterYUV();
                        tvImageFilteredGc.drawImage(tvPalImage, 0, 0, screenWidth, screenHeight, null);
                    } else {
                        tvImageFilteredGc.drawImage(tvImage, 0, 0, screenWidth, screenHeight, null);
                    }

                    if (scanlinesFilter)
                        drawScanlines4x();

                    if (rgbFilter)
                        filterRGB4x();

                    gc2.drawImage(tvImageFiltered, 0, 0, null);
                } else {
                    gc2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationMethod);
                    gc2.drawImage(tvImage, 0, 0, screenWidth, screenHeight, null);
                }
                break;
            default:
                if (palFilter) {
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
            for (int col = 0; col < SCREEN_WIDTH; col++) {

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
            for (int col = 0; col < SCREEN_WIDTH; col++) {

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
            for (int col = 0; col < SCREEN_WIDTH; col++) {

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
            for (int col = 0; col < SCREEN_WIDTH; col++) {
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
            for (int col = 0; col < SCREEN_WIDTH; col++) {
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

    private void filterRGB4x() {

        int pixel = 0;

        int jump = screenWidth * 3;

        while (pixel < imageBuffer.length) {
            for (int col = 0; col < SCREEN_WIDTH; col++) {
                imageBuffer[pixel] &= redMask;
                imageBuffer[pixel + 1] &= redMask;
                imageBuffer[pixel + screenWidth] &= redMask;
                imageBuffer[pixel + screenWidth + 1] &= redMask;

                imageBuffer[pixel + screenWidth * 2] &= blueMask;
                imageBuffer[pixel + screenWidth * 2 + 1] &= blueMask;
                imageBuffer[pixel + screenWidth * 3] &= blueMask;
                imageBuffer[pixel + screenWidth * 3 + 1] &= blueMask;

                pixel += 2;

                imageBuffer[pixel] &= greenMask;
                imageBuffer[pixel + 1] &= greenMask;
                imageBuffer[pixel + screenWidth] &= greenMask;
                imageBuffer[pixel + screenWidth + 1] &= greenMask;

                pixel += 2;
            }
            pixel += jump;
        }
    }

    /*
     * Equations from ITU.BT-601 Y'CbCr
     * Y = R *  .299000 + G *  .587000 + B *  .114000
     * U = R * -.168736 + G * -.331264 + B *  .500000 + 128
     * V = R *  .500000 + G * -.418688 + B * -.081312 + 128
     * or
     * U = (B - Y) * 0.565 + 128
     * V = (R - Y) * 0.713 + 128
     * 
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

    /* Inverse equations of rgb2yuv
     * R = Y + 1.4075 (V - 128)
     * G = Y - 0.3455 (U - 128) - 0.7169 (V - 128)
     * B = Y + 1.7790 (U - 128)
     * 
     * Inverse equation from JPEG/JFIF
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

//        long ini = System.currentTimeMillis();

        int idx1, idx2, idx3;

        int start = TOP_BORDER * SCREEN_WIDTH + LEFT_BORDER;
        int limit = (TOP_BORDER + 192) * SCREEN_WIDTH - RIGHT_BORDER - 1;
        int pixel = 0;

        while (pixel < limit) {
            pixel = start;
            idx1 = imagePalBuffer[pixel++] % 31;
            idx2 = imagePalBuffer[pixel] % 31;
            for (int col = 0; col < 254; col++) {
                idx3 = imagePalBuffer[pixel + 1] % 31;
                imagePalBuffer[pixel++] = yuv2rgb(tableYUV[Yuv][idx2],
                        (tableYUV[yUv][idx1] + 2 * tableYUV[yUv][idx2] + tableYUV[yUv][idx3]) >>> 2,
                        (tableYUV[yuV][idx1] + 2 * tableYUV[yuV][idx2] + tableYUV[yuV][idx3]) >>> 2);
                idx1 = idx2;
                idx2 = idx3;
            }
            start += SCREEN_WIDTH;
        }

//        System.out.println(String.format("PalFilterYUV in %d ms.", System.currentTimeMillis() - ini));
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
        if (tvImageFilteredGc != null) {
            tvImageFilteredGc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationMethod);
        }
    }

    /**
     * @return the anyFilter
     */
    public boolean isAnyFilter() {
        return anyFilter;
    }

    /**
     * @param anyFilter the anyFilter to set
     */
    public void setAnyFilter(boolean anyFilter) {
        this.anyFilter = anyFilter;
        if (!anyFilter) {
            palFilter = scanlinesFilter = rgbFilter = false;
        }
    }
}
