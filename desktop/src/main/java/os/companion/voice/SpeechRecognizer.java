package os.companion.voice;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

import java.nio.file.Path;

public final class SpeechRecognizer implements AutoCloseable {

    public record Transcript(String text, String lang) { }

    public static final int SAMPLE_RATE = 16000;

    private final OfflineRecognizer recognizer;

    private SpeechRecognizer(OfflineRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    public static SpeechRecognizer loadWhisper(Path dir, String prefix) {
        OfflineWhisperModelConfig whisper = OfflineWhisperModelConfig.builder()
                .setEncoder(dir.resolve(prefix + "-encoder.int8.onnx").toString())
                .setDecoder(dir.resolve(prefix + "-decoder.int8.onnx").toString())
                .setLanguage("")
                .setTask("transcribe")
                .build();
        OfflineModelConfig model = OfflineModelConfig.builder()
                .setWhisper(whisper)
                .setTokens(dir.resolve(prefix + "-tokens.txt").toString())
                .setNumThreads(4)
                .setProvider("cpu")
                .setDebug(false)
                .build();
        FeatureConfig feat = FeatureConfig.builder()
                .setSampleRate(SAMPLE_RATE).setFeatureDim(80).build();
        OfflineRecognizerConfig cfg = OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(model)
                .setFeatureConfig(feat)
                .setDecodingMethod("greedy_search")
                .build();
        return new SpeechRecognizer(new OfflineRecognizer(cfg));
    }

    public Transcript transcribe(float[] samples) {
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE);
            recognizer.decode(stream);
            OfflineRecognizerResult r = recognizer.getResult(stream);
            String lang = r.getLang() == null ? "" : r.getLang().replace("<|", "").replace("|>", "");
            return new Transcript(r.getText() == null ? "" : r.getText().trim(), lang);
        } finally {
            stream.release();
        }
    }

    @Override
    public void close() {
        recognizer.release();
    }
}
