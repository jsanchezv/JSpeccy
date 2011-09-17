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
    private final byte[] buf = new byte[2048];
    // Un frame completo lleno de ceros para enviarlo como aperitivo.
    private final int[] beeper = new int[962];
    private final byte[] nullbuf = new byte[1920];
    // Buffer de sonido para el AY
    private final int[] ayBufA = new int[962];
    private final int[] ayBufB = new int[962];
    private final int[] ayBufC = new int[962];
//    private final int[] ayNull = new int[961];
    private int bufp;
    private int level;
    private int audiotstates;
    private float timeRem, spf;
    private AY8912 ay;

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
    
    synchronized void open(int frameLen, AY8912 ay8912) {
        ay = ay8912;
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
        ay8912.setBufferChannels(ayBufA, ayBufB, ayBufC);
    }

    synchronized void updateAudio(int tstates, int value) {
        tstates = tstates - audiotstates;
        audiotstates += tstates;
        float time = tstates;

        synchronized (buf) {
            if (time + timeRem > spf) {
                level += ((spf - timeRem) / spf) * value;
                time -= spf - timeRem;
                beeper[bufp++] = level;
            } else {
                timeRem += time;
                level += (time / spf) * value;
                return;
            }

            while (time > spf) {
                beeper[bufp++] = value;
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

    private synchronized void flushBuffer(int len) {
        if (line != null) {
            synchronized (buf) {
                // Si al ir a escribir el frame la línea no está funcionando,
                // iniciar el envío y estrenar el sonido con un magnífico frame
                // lleno de ceros.
                if (!line.isRunning()) {
                    line.start();
                    line.write(nullbuf, 0, nullbuf.length); // y una magdalena
                }
                line.write(buf, 0, len);
            }
        }
    }

    public void flush() {
        bufp = level = 0;
        timeRem = 0.0f;
        if (line != null)
            line.flush();
    }

    public void endFrame() {
        int sample = 0;
        int ptr = 0;
//        int ayCnt = ay.getSampleCount();
        ay.endFrame();
//        System.out.println(String.format("Frame: %d bytes. AY Frame: %d bytes", bufp, ayCnt));
        if (bufp > 960)
            bufp = 960;
        for(int idx = 0; idx < bufp; idx++) {
            sample = beeper[idx] + ayBufA[idx] + ayBufB[idx] + ayBufC[idx];
//            sample = beeper[idx] + ayBufA[idx]; //[idx] + ayBufC[idx];
            buf[ptr++] = (byte) sample;
            buf[ptr++] = (byte)(sample >>> 8);
        }
        // Si el frame se ha quedado corto de una punta, rellenarlo
        // Copiamos el último sample del beeper y el último sample actualizado del AY
        if (ptr == 1918) {
            sample = beeper[958] + ayBufA[959] + ayBufB[959] + ayBufC[959];
//            sample = beeper[958] + ayBufA[959];// + ayBufC[959];
            buf[ptr++] = (byte) sample;
            buf[ptr++] = (byte)(sample >>> 8);
        }
        flushBuffer(ptr);
        bufp = 0;

        audiotstates -= Spectrum.FRAMES128k;
    }

    synchronized void close() {
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }

    public void reset() {
        audiotstates = 0;
        bufp = 0;
    }
}
