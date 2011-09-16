/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package machine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 *
 * @author jsanchez
 */
public class Audio {

    private byte buf[] = new byte[4096];
    static final int FREQ = 22050;
    SourceDataLine line;

    Audio() {
        try {
//            mul = FREQ;
            AudioFormat fmt = new AudioFormat(FREQ, 16, 1, true, false);
            System.out.println(fmt);
            SourceDataLine l = (SourceDataLine) AudioSystem.getLine(
                    new DataLine.Info(SourceDataLine.class, fmt));
            l.open(fmt, 4096);
            l.start();
            line = l;
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    synchronized int flush(int p) {
        SourceDataLine l = line;
        if (l != null) {
            l.write(buf, 0, p);
        }
        return 0;
    }

    synchronized void close() {
        SourceDataLine l = line;
        if (l != null) {
            line = null;
            l.stop();
            l.close();
        }
    }
}
