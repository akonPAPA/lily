package os.companion.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public final class MicrophoneCapture implements AutoCloseable {

    public static final int SAMPLE_RATE = 16000;

    private final AudioFormat format =
            new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private TargetDataLine line;

    public boolean isAvailable() {
        return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, format));
    }

    public void open() throws LineUnavailableException {
        line = AudioSystem.getTargetDataLine(format);
        line.open(format);
        line.start();
    }

    public float[] readSamples(int n) {
        byte[] buf = new byte[n * 2];
        int off = 0;
        while (off < buf.length) {
            int r = line.read(buf, off, buf.length - off);
            if (r <= 0) {
                break;
            }
            off += r;
        }
        int got = off / 2;
        float[] out = new float[got];
        for (int i = 0; i < got; i++) {
            int lo = buf[i * 2] & 0xff;
            int hi = buf[i * 2 + 1];
            out[i] = (short) ((hi << 8) | lo) / 32768f;
        }
        return out;
    }

    public void flush() {
        if (line != null) {
            line.flush();
        }
    }

    @Override
    public void close() {
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
    }
}
