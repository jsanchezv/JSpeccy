/*
 *	Audio.java
 *
 *  2009-2010 José Luis Sánchez
 *
 *  with parts from:
 *	Copyright 2007-2008 Jan Bobrowski <jb@wizard.ae.krakow.pl>
 *
 *	This program is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	version 2 as published by the Free Software Foundation.
 */
package machine;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

class Audio {
    static final int FREQ = 48000;
    private SourceDataLine line;
    private DataLine.Info infoDataLine;
    private AudioFormat fmt;
    private SourceDataLine sdl;
    // Buffer de sonido para el frame actual, hay más espacio del necesario.
    private final byte buf[] = new byte[2048];
    // Un frame completo lleno de ceros para enviarlo como aperitivo.
    private final byte nullbuf[] = new byte[1920];
    private int bufp;
    private int level;
    public int audiotstates;
    private float timeRem, spf;

    Audio() {
        try {
            fmt = new AudioFormat(FREQ, 16, 1, true, false);
            System.out.println(fmt);
            infoDataLine = new DataLine.Info(SourceDataLine.class, fmt);
            sdl = (SourceDataLine) AudioSystem.getLine(infoDataLine);
            line = null;
            java.util.Arrays.fill(nullbuf, (byte)0);
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
    synchronized void open(int frameLen) {
        timeRem = (float) 0.0;
        spf = (float) frameLen / (FREQ / 50);
        audiotstates = bufp = level = 0;
        if (line == null)
        {
            try {
                sdl.open(fmt, nullbuf.length * 2); // Espacio para dos frames
                // No se llama al método start hasta tener el primer buffer
//                sdl.start();
                line = sdl;
            } catch (LineUnavailableException ex) {
                Logger.getLogger(Audio.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    synchronized void updateAudio(int tstates, int value) {
        tstates = tstates - audiotstates;
        audiotstates += tstates;
        float time = tstates;

        synchronized (buf) {
            if (time + timeRem > spf) {
                level += ((spf - timeRem) / spf) * value;
                time -= spf - timeRem;
                buf[bufp++] = (byte) level;
                buf[bufp++] = (byte) (level >> 8);
                if (bufp == buf.length) {
                    bufp = flushBuffer(bufp);
                }
            } else {
                timeRem += time;
                level += (time / spf) * value;
                return;
            }

            byte lsb = (byte) value;
            byte msb = (byte) (value >> 8);
            while (time > spf) {
                buf[bufp++] = lsb;
                buf[bufp++] = msb;
                if (bufp == buf.length) {
                    bufp = flushBuffer(bufp);
                }
                time -= spf;
            }
        }

        // calculamos el nivel de sonido de la parte residual de la muestra
        // para el próximo cambio de estado
        level = (int) (value * (time / spf));
        timeRem = time;
//        System.out.println(String.format("tstates: %d speaker: %04x timeRem: %f level: %d",
//            tstates, (short)value, timeRem, (short)level));
    }

    private synchronized int flushBuffer(int ptr) {
        if (line != null) {
            synchronized (buf) {
                // Si al ir a escribir el frame la línea no está funcionando,
                // iniciar el envío y estrenar el sonido con un magnífico frame
                // lleno de ceros.
                if (!line.isRunning()) {
                    line.start();
                    line.write(nullbuf, 0, nullbuf.length); // y una magdalena
                }
                line.write(buf, 0, ptr);
            }
        }
        return 0;
    }

    public void flush() {
        bufp = level = 0;
        timeRem = 0.0f;
        if (line != null)
            line.flush();
    }

    public void endFrame() {
//        System.out.println("Frame: " + bufp + " bytes");
        // Si el frame se ha quedado corto de una punta, rellenarlo
        if (bufp == 1918) {
            buf[bufp++] = (byte) level;
            buf[bufp++] = (byte) (level >> 8);
        }
        bufp = flushBuffer(bufp);
    }

    synchronized void close() {
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }
}
