package os.companion.character.behaviour;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public final class Sfx {

    private static final int RATE = 44100;

    private Sfx() {
    }

    public static void chirp() {
        Thread t = new Thread(() -> {
            try {
                byte[] a = tone(660, 90, 0.25);
                byte[] b = tone(990, 110, 0.25);
                play(concat(a, b));
            } catch (Exception ignored) {

            }
        }, "sfx-chirp");
        t.setDaemon(true);
        t.start();
    }

    private static byte[] tone(double freq, int ms, double gain) {
        int n = RATE * ms / 1000;
        byte[] buf = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double env = Math.min(1, Math.min(i, n - i) / (RATE * 0.01));
            double s = Math.sin(2 * Math.PI * freq * i / RATE) * gain * env;
            short v = (short) (s * 32767);
            buf[i * 2] = (byte) (v & 0xff);
            buf[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
        }
        return buf;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void play(byte[] pcm) throws Exception {
        AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
            line.open(fmt);
            line.start();
            line.write(pcm, 0, pcm.length);
            line.drain();
            line.stop();
        }
    }
}
