package os.companion.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import os.companion.ai.CompanionResponse;
import os.companion.ai.LocalModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class VoiceCoordinator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VoiceCoordinator.class);

    public enum State { PASSIVE, ACTIVE_LISTENING, TRANSCRIBING, THINKING, SPEAKING,
                        MIC_UNAVAILABLE, ERROR }

    private final MicrophoneCapture mic;
    private final VadEndpointer vad;
    private final SpeechRecognizer asr;
    private final TextToSpeech tts;
    private final LanguageRouter router;
    private final LocalModel model;

    private final Consumer<CompanionResponse> onResponse;
    private final Consumer<String> onUserText;
    private final Consumer<State> onState;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "voice-turn");
                t.setDaemon(true);
                return t;
            });
    private final AtomicBoolean turnRunning = new AtomicBoolean(false);
    private volatile boolean micOpen = false;
    private volatile State state = State.PASSIVE;

    public VoiceCoordinator(MicrophoneCapture mic, VadEndpointer vad, SpeechRecognizer asr,
                            TextToSpeech tts, LanguageRouter router, LocalModel model,
                            Consumer<CompanionResponse> onResponse, Consumer<String> onUserText,
                            Consumer<State> onState) {
        this.mic = mic;
        this.vad = vad;
        this.asr = asr;
        this.tts = tts;
        this.router = router;
        this.model = model;
        this.onResponse = onResponse;
        this.onUserText = onUserText;
        this.onState = onState;
    }

    public State state() {
        return state;
    }

    public double mouthLevel() {
        return tts.isSpeaking() ? Math.min(1, tts.rms() * 5.0) : 0;
    }

    public void start() {
        if (!mic.isAvailable()) {
            setState(State.MIC_UNAVAILABLE);
            log.warn("no microphone available");
            return;
        }
        try {
            mic.open();
            micOpen = true;
            setState(State.PASSIVE);
        } catch (Exception e) {
            setState(State.MIC_UNAVAILABLE);
            log.warn("microphone open failed: {}", e.toString());
        }
    }

    public void trigger() {
        if (!micOpen) {
            log.warn("voice trigger ignored: microphone unavailable");
            return;
        }
        if (state == State.SPEAKING) {
            tts.cancel();
        }
        if (!turnRunning.compareAndSet(false, true)) {
            return;
        }
        worker.submit(this::runTurn);
    }

    public void saySpontaneous(CompanionResponse r, Consumer<CompanionResponse> animate) {
        if (state != State.PASSIVE || !turnRunning.compareAndSet(false, true)) {
            return;
        }
        worker.submit(() -> {
            try {
                if (animate != null) {
                    animate.accept(r);
                }
                setState(State.SPEAKING);
                tts.speak(r.speech(), router.detect(r.speech()), pace(r));
                setState(State.PASSIVE);
            } catch (Exception e) {
                log.debug("spontaneous speech failed: {}", e.toString());
                setState(State.PASSIVE);
            } finally {
                turnRunning.set(false);
            }
        });
    }

    private void runTurn() {
        try {
            setState(State.ACTIVE_LISTENING);
            float[] utterance = vad.captureUtterance(mic, 6000, 15000, this::shuttingDown);
            if (utterance == null || utterance.length == 0) {
                setState(State.PASSIVE);
                return;
            }

            setState(State.TRANSCRIBING);
            SpeechRecognizer.Transcript t = asr.transcribe(utterance);
            if (t.text().isBlank()) {
                setState(State.PASSIVE);
                return;
            }
            if (onUserText != null) {
                onUserText.accept(t.text() + "  [" + t.lang() + "]");
            }

            setState(State.THINKING);

            CompanionResponse r = model.ensureReady()
                    ? model.ask(t.text())
                    : CompanionResponse.fallback("One sec — I'm still waking my brain up~");
            if (onResponse != null) {
                onResponse.accept(r);
            }

            setState(State.SPEAKING);
            tts.speak(r.speech(), router.detect(r.speech()), pace(r));
            setState(State.PASSIVE);
        } catch (Exception e) {
            log.warn("voice turn failed: {}", e.toString());
            setState(State.ERROR);
        } finally {
            turnRunning.set(false);
        }
    }

    private boolean shuttingDown() {
        return worker.isShutdown();
    }

    private static double pace(CompanionResponse r) {
        double base = 0.94 + r.energy() * 0.22;
        switch (r.emotion()) {
            case TIRED, SAD -> base -= 0.10;
            case EXCITED, HAPPY -> base += 0.06;
            default -> { }
        }
        return base;
    }

    private void setState(State s) {
        this.state = s;
        if (onState != null) {
            onState.accept(s);
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
        tts.cancel();
        try {
            mic.close();
        } catch (Exception ignored) {
        }
        vad.close();
        asr.close();
        tts.close();
    }
}
