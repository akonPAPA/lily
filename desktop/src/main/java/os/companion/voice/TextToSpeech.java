package os.companion.voice;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TextToSpeech implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeech.class);

    private static final double DEFAULT_PITCH = 1.15;
    private final double pitch;

    private final OfflineTts en;
    private final OfflineTts ru;

    private final AtomicBoolean speaking = new AtomicBoolean(false);
    private volatile boolean cancelled = false;
    private volatile double rms = 0;

    private TextToSpeech(OfflineTts en, OfflineTts ru) {
        this.en = en;
        this.ru = ru;
        double p = DEFAULT_PITCH;
        try {
            String override = System.getProperty("CUTE_PITCH", System.getenv("CUTE_PITCH"));
            if (override != null) {
                p = Double.parseDouble(override);
            }
        } catch (Exception ignored) {

        }
        this.pitch = Math.max(1.0, Math.min(1.4, p));
    }

    public static TextToSpeech load(Path enDir, String enModel, Path ruDir, String ruModel) {
        return new TextToSpeech(buildVits(enDir, enModel), buildVits(ruDir, ruModel));
    }

    private static OfflineTts buildVits(Path dir, String modelFile) {

        OfflineTtsVitsModelConfig vits = OfflineTtsVitsModelConfig.builder()
                .setModel(dir.resolve(modelFile).toString())
                .setTokens(dir.resolve("tokens.txt").toString())
                .setDataDir(dir.resolve("espeak-ng-data").toString())
                .setNoiseScale(0.667f)
                .setNoiseScaleW(0.9f)
                .setLengthScale(1.0f)
                .build();
        OfflineTtsModelConfig model = OfflineTtsModelConfig.builder()
                .setVits(vits).setNumThreads(2).setProvider("cpu").setDebug(false).build();
        OfflineTtsConfig cfg = OfflineTtsConfig.builder().setModel(model).build();
        return new OfflineTts(cfg);
    }

    public boolean isSpeaking() {
        return speaking.get();
    }

    public double rms() {
        return rms;
    }

    public void cancel() {
        cancelled = true;
    }

    public void speak(String text, LanguageRouter.Lang lang) {
        speak(text, lang, 1.0);
    }

    public void speak(String text, LanguageRouter.Lang lang, double speed) {
        if (text == null || text.isBlank()) {
            return;
        }
        OfflineTts tts = lang == LanguageRouter.Lang.RU ? ru : en;
        float sp = (float) Math.max(0.6, Math.min(1.6, speed));

        float genSpeed = (float) Math.max(0.4, Math.min(1.8, sp / pitch));
        cancelled = false;
        speaking.set(true);
        try {
            for (String sentence : splitSentences(text)) {
                if (cancelled) {
                    break;
                }
                GeneratedAudio audio = tts.generate(sentence, 0, genSpeed);
                play(audio.getSamples(), (int) Math.round(audio.getSampleRate() * pitch));
            }
        } catch (Exception e) {
            log.warn("TTS failed: {}", e.toString());
        } finally {
            speaking.set(false);
            rms = 0;
        }
    }

    private static java.util.List<String> splitSentences(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String part : text.strip().split("(?<=[.!?…。！？])\\s+")) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > 160) {
                for (String q : p.split("(?<=[,;:，])\\s+")) {
                    String s = q.strip();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            } else {
                out.add(p);
            }
        }
        if (out.isEmpty()) {
            out.add(text.strip());
        }
        return out;
    }

    private void play(float[] samples, int sampleRate) throws Exception {
        AudioFormat fmt = new AudioFormat(sampleRate, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
            line.open(fmt);
            line.start();
            int chunk = 1600;
            byte[] buf = new byte[chunk * 2];
            for (int i = 0; i < samples.length && !cancelled; i += chunk) {
                int n = Math.min(chunk, samples.length - i);
                double sum = 0;
                for (int j = 0; j < n; j++) {
                    float s = Math.max(-1f, Math.min(1f, samples[i + j]));
                    short v = (short) (s * 32767);
                    buf[j * 2] = (byte) (v & 0xff);
                    buf[j * 2 + 1] = (byte) ((v >> 8) & 0xff);
                    sum += s * s;
                }
                rms = Math.sqrt(sum / Math.max(1, n));
                line.write(buf, 0, n * 2);
            }
            if (cancelled) {
                line.flush();
            } else {
                line.drain();
            }
            line.stop();
        }
    }

    @Override
    public void close() {
        en.release();
        ru.release();
    }
}
