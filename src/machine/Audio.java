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
    public int bufp;
    private int oldFrame, oldStates;
    static final int FREQ = 22050;
    SourceDataLine line;
    int cntSamples;

    Audio() {
        try {
//            mul = FREQ;
            AudioFormat fmt = new AudioFormat(FREQ, 16, 1, true, false);
            System.out.println(fmt);
            SourceDataLine lineOut = (SourceDataLine) AudioSystem.getLine(
                    new DataLine.Info(SourceDataLine.class, fmt));
            lineOut.open(fmt, 4096);
            lineOut.start();
            line = lineOut;
            bufp = 0;
            oldFrame = oldStates = cntSamples = 0;
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    synchronized void generateSample(int frame, int tstates) {
        byte POS_SIGNAL_MSB = (byte)0x7f;
        byte POS_SIGNAL_LSB = (byte)0xf8;
        byte NEG_SIGNAL_MSB = (byte)0x80;
        byte NEG_SIGNAL_LSB = (byte)0x08;

        if( frame - oldFrame > 1 ) {
            oldFrame = frame;
            oldStates = tstates;
            return;
        }

        int longSample = (tstates < oldStates ? (tstates + (69888 - oldStates)) :
                                                (tstates - oldStates));
        int nSamples = longSample / 157;
        int rem = longSample % 157;
        if( rem > 78 )
            nSamples++;

        for( int count = 0; count < nSamples; count++ ) {
            if( count < nSamples / 2 ) {
                buf[bufp++] = POS_SIGNAL_LSB;
                buf[bufp++] = POS_SIGNAL_MSB;
            } else {
                buf[bufp++] = NEG_SIGNAL_LSB;
                buf[bufp++] = NEG_SIGNAL_MSB;
            }
            cntSamples++;
            if( bufp == buf.length )
                bufp = flush(bufp);
        }
        System.out.println(String.format("time: %d\tlongSample: %d\tnSamples: %d\tTotal: %d",
                System.currentTimeMillis(), longSample, nSamples, cntSamples));
        oldStates = tstates;
        oldFrame = frame;
    }
    synchronized int flush(int p) {
        SourceDataLine lineOut = line;
        if (lineOut != null) {
            lineOut.write(buf, 0, p);
        }
        return 0;
    }

    synchronized void close() {
        SourceDataLine lineOut = line;
        if (lineOut != null) {
            line = null;
            lineOut.stop();
            lineOut.close();
        }
    }
}
