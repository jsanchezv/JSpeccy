/*
 *	Audio.java
 *
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
    SourceDataLine line;
    DataLine.Info infoDataLine;
    AudioFormat fmt;
    SourceDataLine sdl;
    private final byte buf[] = new byte[4096];
    int bufp;
    int level;
    public int audiotstates;
    private float timeRem, spf;

    Audio() {
        try {
            fmt = new AudioFormat(FREQ, 16, 1, true, false);
            System.out.println(fmt);
            infoDataLine = new DataLine.Info(SourceDataLine.class, fmt);
            sdl = (SourceDataLine) AudioSystem.getLine(infoDataLine);
//            sdl.open(fmt, buf.length * 2);
//            sdl.start();
//            line = sdl;
            line = null;
//            System.out.println(String.format("maxBufferSize: %d minBufferSize: %d",
//                infoDataLine.getMaxBufferSize(), infoDataLine.getMinBufferSize()));
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
    synchronized void open(int hz) {
        timeRem = (float) 0.0;
        spf = (float) 69888 / ((float) FREQ / (float) 50);
        level = 0;
        audiotstates = 0;
        if (line == null)
        {
            try {
                sdl.open(fmt, buf.length * 2);
                sdl.start();
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
            if (time + timeRem >= spf) {
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
                line.write(buf, 0, ptr);
            }
        }
        return 0;
    }

    public void flush() {
        bufp = flushBuffer(bufp);
    }

    synchronized void close() {
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
    }
}
