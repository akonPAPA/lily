package os.companion.voice;

import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

import java.nio.file.Path;

public final class VadEndpointer implements AutoCloseable {

    private static final int WINDOW = 512;

    private final Vad vad;

    private VadEndpointer(Vad vad) {
        this.vad = vad;
    }

    public static VadEndpointer load(Path vadModel) {
        SileroVadModelConfig silero = SileroVadModelConfig.builder()
                .setModel(vadModel.toString())
                .setThreshold(0.5f)
                .setMinSilenceDuration(0.6f)
                .setMinSpeechDuration(0.25f)
                .setMaxSpeechDuration(15f)
                .setWindowSize(WINDOW)
                .build();
        VadModelConfig cfg = VadModelConfig.builder()
                .setSileroVadModelConfig(silero)
                .setSampleRate(MicrophoneCapture.SAMPLE_RATE)
                .setNumThreads(1)
                .setProvider("cpu")
                .setDebug(false)
                .build();
        return new VadEndpointer(new Vad(cfg));
    }

    public float[] captureUtterance(MicrophoneCapture mic, long maxWaitForSpeechMs,
                                    long maxUtteranceMs, java.util.function.BooleanSupplier stop) {
        vad.reset();
        long start = System.currentTimeMillis();
        boolean speechStarted = false;

        while (!stop.getAsBoolean()) {
            float[] chunk = mic.readSamples(WINDOW);
            vad.acceptWaveform(chunk);

            if (vad.isSpeechDetected()) {
                speechStarted = true;
            }
            if (!vad.empty()) {
                SpeechSegment seg = vad.front();
                float[] samples = seg.getSamples();
                vad.pop();
                return samples;
            }

            long elapsed = System.currentTimeMillis() - start;
            if (!speechStarted && elapsed > maxWaitForSpeechMs) {
                return null;
            }
            if (elapsed > maxUtteranceMs) {
                vad.flush();
                if (!vad.empty()) {
                    float[] samples = vad.front().getSamples();
                    vad.pop();
                    return samples;
                }
                return null;
            }
        }
        return null;
    }

    @Override
    public void close() {
        vad.release();
    }
}
