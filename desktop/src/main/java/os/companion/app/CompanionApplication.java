package os.companion.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import os.companion.ai.CompanionResponse;
import os.companion.ai.LocalModel;
import os.companion.ai.PersonaLoader;
import os.companion.character.animation.SpriteSheet;
import os.companion.character.behaviour.BehaviourEngine;
import os.companion.character.rendering.CharacterOverlay;
import os.companion.character.rendering.StickyNote;
import os.companion.config.AppConfig;
import os.companion.nativeplatform.PlatformBridge;
import os.companion.nativeplatform.windows.WindowsBridge;
import os.companion.voice.LanguageRouter;
import os.companion.voice.MicrophoneCapture;
import os.companion.voice.SpeechRecognizer;
import os.companion.voice.TextToSpeech;
import os.companion.voice.VadEndpointer;
import os.companion.voice.VoiceCoordinator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CompanionApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(CompanionApplication.class);

    private static final String CHARACTER = "lily";
    private static final String WINDOW_TITLE = "CompanionOS::Lily";
    private static final String INITIAL_ACTION = "idle";
    private static final double DRAG_THRESHOLD_PX = 6;

    private final AppConfig config = AppConfig.discover();
    private final PlatformBridge platform =
            WindowsBridge.isWindows() ? new WindowsBridge() : PlatformBridge.noop();

    private CharacterOverlay overlay;
    private long lastNanos;
    private boolean dragging;
    private boolean dragMoved;
    private double pressScreenX, pressScreenY;
    private double dragOffsetX;
    private double dragOffsetY;

    private final java.time.Instant startedAt = java.time.Instant.now();
    private double tiredAfterHours = 6.0;

    private final BehaviourEngine behaviour = new BehaviourEngine();
    private os.companion.character.behaviour.MovementController movement;
    private os.companion.character.behaviour.ContextReactor context;
    private os.companion.character.behaviour.ActivityController activity;
    private os.companion.character.behaviour.ScreenPerception perception;
    private os.companion.character.behaviour.OutfitSet outfits;
    private String currentOutfit = os.companion.character.behaviour.OutfitSet.BASE;
    private final ExecutorService inference =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "slm-worker");
                t.setDaemon(true);
                return t;
            });
    private final AtomicBoolean modelReady = new AtomicBoolean(false);
    private final AtomicBoolean asking = new AtomicBoolean(false);
    private LocalModel model;
    private os.companion.ai.OllamaManager ollama;

    private final java.util.concurrent.ScheduledExecutorService chatter =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "chatter");
                t.setDaemon(true);
                return t;
            });
    private final java.util.Random rnd = new java.util.Random();
    private final StickyNote stickyNote = new StickyNote();
    private final os.companion.nativeplatform.ClipboardReader clipboard =
            new os.companion.nativeplatform.ClipboardReader();
    private static final String CHATTER_CUE =
            "[You are idly hanging out on the user's desktop. Say ONE short, playful, "
            + "in-character remark to them, under 12 words. Do not repeat earlier lines.]";
    private static final String NOTE_CUE =
            "[Write a very short sticky-note message to leave for the user, under 8 words, "
            + "playful and in character.]";

    private volatile VoiceCoordinator voice;

    @Override
    public void start(Stage stage) {
        Path characterDir = config.characterDir(CHARACTER);
        try {
            os.companion.character.animation.RigModel rig =
                    os.companion.character.animation.RigModel.loadIfPresent(characterDir);
            if (rig != null) {
                overlay = new CharacterOverlay(rig);
                log.info("loaded articulated rig for '{}'", CHARACTER);
            } else {
                outfits = os.companion.character.behaviour.OutfitSet.load(characterDir);
                overlay = new CharacterOverlay(
                        outfits.get(os.companion.character.behaviour.OutfitSet.BASE), INITIAL_ACTION);
            }
        } catch (Exception e) {
            log.error("Could not load character '{}' from {}", CHARACTER, characterDir, e);
            Platform.exit();
            return;
        }

        try {
            os.companion.character.animation.RigModel idleRig =
                    os.companion.character.animation.RigModel.loadIfPresent(characterDir.resolve("rig"));
            if (idleRig != null) {
                overlay.setIdleRig(idleRig);
                log.info("hybrid idle-rig loaded");
            }
        } catch (Exception e) {
            log.debug("idle-rig unavailable: {}", e.toString());
        }

        movement = new os.companion.character.behaviour.MovementController(platform);
        context = new os.companion.character.behaviour.ContextReactor(platform, movement);
        activity = new os.companion.character.behaviour.ActivityController(
                platform, movement, context, behaviour);

        os.companion.nativeplatform.ScreenGrabber grabber =
                new os.companion.nativeplatform.ScreenGrabber(platform);
        os.companion.ai.VisionModel vision =
                new os.companion.ai.VisionModel(AppConfig.OLLAMA_BASE_URL, AppConfig.OLLAMA_VISION_MODEL);
        perception = new os.companion.character.behaviour.ScreenPerception(grabber, vision);
        perception.setEnabled(getParameters().getRaw().contains("--see"));
        perception.setOnDescribed((desc, changed) -> {
            if (model != null) {
                model.setScreenContext(desc);
            }
            if (changed) {
                Platform.runLater(() -> onScreenChanged(desc));
            }
        });

        Pane root = new Pane(overlay.canvas());
        root.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(root, overlay.renderWidth(), overlay.renderHeight(), Color.TRANSPARENT);

        installInput(stage, scene);

        stage.setTitle(WINDOW_TITLE);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);
        stage.setScene(scene);

        Rectangle2D work = Screen.getPrimary().getVisualBounds();
        double floorY = work.getMinY() + work.getHeight() - overlay.renderHeight();
        overlay.setWorldPosition(work.getMinX(), floorY);
        stage.setX(overlay.worldX());
        stage.setY(overlay.worldY());
        movement.setBounds(work.getMinX(), work.getMinX() + work.getWidth(),
                work.getMinY(), work.getMinY() + work.getHeight());

        activity.setHome(overlay.worldX(), floorY);

        stage.show();

        reinforceAlwaysOnTop();

        startRenderLoop(stage, work);
        greetOnLaunch();
        startFatigueClock();
        startGlobalHotkey();
        initModelAsync();
        initVoiceAsync();
        log.info("CompanionOS overlay started (platform bridge supported = {})", platform.isSupported());
        log.info("Press SPACE to talk to Lily by voice; T for the text debug prompt.");
    }

    private void initVoiceAsync() {
        java.nio.file.Path speech = config.speechDir();
        Thread t = new Thread(() -> {
            try {
                MicrophoneCapture mic = new MicrophoneCapture();
                VadEndpointer vad = VadEndpointer.load(speech.resolve("vad").resolve("silero_vad.onnx"));
                SpeechRecognizer asr = SpeechRecognizer.loadWhisper(
                        speech.resolve("asr").resolve("sherpa-onnx-whisper-base"), "base");
                java.nio.file.Path tts = speech.resolve("tts");

                java.nio.file.Path jenny = tts.resolve("vits-piper-en_GB-jenny_dioco-medium");
                java.nio.file.Path enDir;
                String enModel;
                if (java.nio.file.Files.isDirectory(jenny)) {
                    enDir = jenny;
                    enModel = "en_GB-jenny_dioco-medium.onnx";
                } else {
                    enDir = tts.resolve("vits-piper-en_US-amy-medium-int8");
                    enModel = "en_US-amy-medium.onnx";
                }
                log.info("TTS EN voice: {}", enDir.getFileName());
                TextToSpeech voiceOut = TextToSpeech.load(
                        enDir, enModel,
                        tts.resolve("vits-piper-ru_RU-irina-medium-int8"), "ru_RU-irina-medium.onnx");

                VoiceCoordinator vc = new VoiceCoordinator(
                        mic, vad, asr, voiceOut, new LanguageRouter(), model,
                        r -> Platform.runLater(() -> behaviour.react(overlay, r)),
                        userText -> log.info("heard: {}", userText),
                        state -> log.debug("voice state: {}", state));
                vc.start();
                this.voice = vc;
                log.info("voice pipeline ready — press SPACE and speak (EN or RU).");
            } catch (Throwable e) {
                log.warn("voice pipeline unavailable: {}", e.toString());
            }
        }, "voice-init");
        t.setDaemon(true);
        t.start();
    }

    private void initModelAsync() {
        String persona = new PersonaLoader().loadOrDefault(
                config.characterDir(CHARACTER).resolve("persona.md"), "Lily");
        model = LocalModel.forExternalServer(
                AppConfig.OLLAMA_BASE_URL, AppConfig.OLLAMA_MODEL, persona,  6);
        ollama = new os.companion.ai.OllamaManager(AppConfig.OLLAMA_BASE_URL, AppConfig.OLLAMA_MODEL,
                config.modelsDir().resolve("Phi4Mini.Modelfile"));

        activity.setOnActivate(why -> inference.submit(this::warmModel));
        inference.submit(() -> {
            boolean ok = warmModel();
            if (ok && getParameters().getRaw().contains("--demo")) {
                ask("Hi Lily, introduce yourself in one short line.");
            }
            if (!ok) {
                log.info("local SLM not reachable yet — will wake it on the first trigger "
                        + "(start Ollama; `ollama create companion-phi4-mini -f models/Phi4Mini.Modelfile`)");
            }
            scheduleNextChatter();
        });
    }

    private boolean warmModel() {
        if (ollama != null) {
            ollama.ensureUp(java.time.Duration.ofSeconds(60));
        }
        boolean ok = model != null && model.ensureReady();
        modelReady.set(ok);
        if (ok) {
            log.info("local SLM ready ({} @ {})", AppConfig.OLLAMA_MODEL, AppConfig.OLLAMA_BASE_URL);
        }
        return ok;
    }

    private boolean firstChatter = true;

    private void scheduleNextChatter() {
        boolean goose = activity != null
                && activity.mode() == os.companion.character.behaviour.ActivityController.Mode.GOOSE;
        long delay;
        if (firstChatter) {
            delay = 10 + rnd.nextInt(12);
        } else if (goose) {
            delay = 18 + rnd.nextInt(30);
        } else {
            delay = 45 + rnd.nextInt(65);
        }
        firstChatter = false;
        chatter.schedule(this::maybeChatter, delay, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void maybeChatter() {
        boolean idle = !asking.get()
                && (voice == null || voice.state() == VoiceCoordinator.State.PASSIVE)
                && overlay != null && !overlay.isSpeaking();
        if (idle && asking.compareAndSet(false, true)) {
            try {
                if (!modelReady.get() && !warmModel()) {
                    sayCanned();
                } else if (rnd.nextInt(100) < 25) {

                    String clip = clipboard.readText();
                    if (clip != null && rnd.nextBoolean()) {
                        Platform.runLater(() -> dropNote(clip));
                    } else {
                        String note = model.spontaneous(NOTE_CUE).speech();
                        Platform.runLater(() -> dropNote(note));
                    }
                } else {

                    Platform.runLater(() ->
                            wake(os.companion.character.behaviour.ActivityController.Trigger.SPONTANEOUS));
                    CompanionResponse r = model.spontaneous(chatterCue());
                    VoiceCoordinator vc = voice;
                    if (vc != null) {
                        vc.saySpontaneous(r, rr -> Platform.runLater(() -> behaviour.react(overlay, rr)));
                    } else {
                        Platform.runLater(() -> behaviour.showThought(overlay, r));
                    }
                }
            } catch (Exception e) {
                log.debug("chatter skipped: {}", e.getMessage());
            } finally {
                asking.set(false);
            }
        }
        scheduleNextChatter();
    }

    private void dropSmartNote() {
        String clip = clipboard.readText();
        wake(os.companion.character.behaviour.ActivityController.Trigger.SPONTANEOUS);
        if (clip != null && rnd.nextBoolean()) {
            Platform.runLater(() -> dropNote(clip));
            return;
        }
        String text = clip != null ? clip : CANNED_LINES[rnd.nextInt(CANNED_LINES.length)];
        if (modelReady.get() || warmModel()) {
            try {
                text = model.spontaneous(NOTE_CUE).speech();
            } catch (Exception e) {
                log.debug("note SLM skipped: {}", e.getMessage());
            }
        }
        final String note = text;
        Platform.runLater(() -> dropNote(note));
    }

    private void dropNote(String text) {
        if (overlay == null || overlay.canvas().getScene() == null) {
            return;
        }
        double noteX = overlay.isFacingRight()
                ? overlay.worldX() + overlay.renderWidth() * 0.55
                : overlay.worldX() - 170;
        double noteY = overlay.worldY() + overlay.renderHeight() * 0.30;
        stickyNote.drop(overlay.canvas().getScene().getWindow(),
                Math.max(0, noteX), Math.max(0, noteY), text);
    }

    private String chatterCue() {
        String title = platform.foregroundWindowTitle();
        if (title != null && !title.isBlank()
                && !title.contains("CompanionOS") && rnd.nextBoolean()) {
            String app = title.length() > 60 ? title.substring(0, 60) : title;
            return "[The user is currently using an app/window titled \"" + app
                    + "\". Make ONE short, playful, in-character remark about what they're "
                    + "doing, under 12 words.]";
        }
        return CHATTER_CUE;
    }

    private void petReaction() {
        if (overlay == null) {
            return;
        }
        boolean surprised = rnd.nextBoolean() && overlay.hasAction("surprised");
        String action = surprised ? "surprised" : "happy";
        if (overlay.hasAction(action)) {
            overlay.play(action);
        }
        overlay.playHop();
        os.companion.character.behaviour.Sfx.chirp();
    }

    private void greetOnLaunch() {
        javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(900));
        p.setOnFinished(e -> {
            if (overlay != null && overlay.hasAction("wave")) {
                overlay.setFacing(true);
                overlay.play("wave");
            }
        });
        p.play();
    }

    private void startFatigueClock() {
        var named = getParameters().getNamed();
        if (named.containsKey("tired-after")) {
            try {
                tiredAfterHours = Math.max(0.0, Double.parseDouble(named.get("tired-after")));
            } catch (NumberFormatException ignored) {

            }
        }
        long periodSec = (long) Math.max(5, Math.min(3600, tiredAfterHours * 3600 / 6));
        chatter.scheduleAtFixedRate(() -> {
            double hrs = java.time.Duration.between(startedAt, java.time.Instant.now()).toMillis()
                    / 3_600_000.0;
            if (hrs >= tiredAfterHours && activity != null) {
                activity.setTired(true);
                log.debug("fatigue: tired after {}h uptime", String.format("%.2f", hrs));
            }
        }, periodSec, periodSec, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static final String[] CANNED_LINES = {
        "Hi! I'm here even when my brain's offline~",
        "Pat pat? I like being clicked! ♥",
        "Ehehe~ what are you up to?",
        "I'm keeping your desktop cozy!",
        "Let's take a little break together?",
        "You're doing great — keep going!",
        "Boop! Caught your cursor~",
        "Wanna hang out? I'm bored~",
        "Psst… you've been busy. Sip some water!",
        "I reorganised your windows. You're welcome~",
        "Honk~ did I scare you?",
        "Look at me, look at me! ✨",
        "I'll just sit right here on your window~",
        "Tap me if you need a friend!",
        "Nyaa~ I'm your desktop buddy!",
    };

    private void sayCanned() {
        if (overlay == null) {
            return;
        }
        String line = CANNED_LINES[rnd.nextInt(CANNED_LINES.length)];
        VoiceCoordinator vc = voice;
        if (vc != null) {

            CompanionResponse r = CompanionResponse.fallback(line);
            vc.saySpontaneous(r, rr -> Platform.runLater(() -> behaviour.react(overlay, rr)));
            return;
        }
        Platform.runLater(() -> {
            double ms = Math.max(1600, line.length() * 55.0);
            animateSpeech(line);
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(
                    javafx.util.Duration.millis(ms));
            p.setOnFinished(e -> {
                overlay.setSpeaking(false);
                if (overlay.hasAction("idle")) {
                    overlay.play("idle");
                }
            });
            p.play();
        });
    }

    private void animateSpeech(String line) {
        if (overlay == null) {
            return;
        }
        if (overlay.hasAction("talk")) {
            overlay.play("talk");
        }
        overlay.setSpeaking(true);
        overlay.showBubble(line, Math.max(1600, line.length() * 55.0) + 1500);
    }

    private void promptAndAsk() {
        if (asking.get()) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Talk to Lily");
        dialog.setHeaderText(null);
        dialog.setContentText("Say something:");
        dialog.initOwner(overlay.canvas().getScene().getWindow());
        dialog.showAndWait().ifPresent(this::ask);
    }

    private void ask(String userText) {
        if (userText == null || userText.isBlank() || !asking.compareAndSet(false, true)) {
            return;
        }
        wake(os.companion.character.behaviour.ActivityController.Trigger.VOICE);
        overlay.play(overlay.hasAction("idle") ? "idle" : "walk");
        inference.submit(() -> {
            try {
                if (!modelReady.get() && !warmModel()) {
                    sayCanned();
                    return;
                }
                long t0 = System.nanoTime();
                CompanionResponse r = model.ask(userText);
                long ms = Math.round((System.nanoTime() - t0) / 1_000_000.0);
                log.info("Lily [{}/{}, {} ms]: {}",
                        r.emotion(), r.gesture(), ms, r.speech());
                Platform.runLater(() -> behaviour.react(overlay, r));
            } catch (Exception e) {
                log.warn("inference failed: {}", e.getMessage());
            } finally {
                asking.set(false);
            }
        });
    }

    private void installInput(Stage stage, Scene scene) {
        scene.setOnMousePressed(e -> {
            dragging = true;
            dragMoved = false;
            pressScreenX = e.getScreenX();
            pressScreenY = e.getScreenY();
            movement.setEnabled(false);
            if (activity != null) {
                activity.setHandled(true);
            }
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });
        scene.setOnMouseDragged(e -> {
            if (!dragging) {
                return;
            }

            if (!dragMoved && Math.hypot(e.getScreenX() - pressScreenX,
                    e.getScreenY() - pressScreenY) > DRAG_THRESHOLD_PX) {
                dragMoved = true;
                if (overlay.hasAction("held_drag")) {
                    overlay.play("held_drag");
                }
            }
            if (dragMoved) {
                double nx = e.getScreenX() - dragOffsetX;
                double ny = e.getScreenY() - dragOffsetY;
                stage.setX(nx);
                stage.setY(ny);
                overlay.setWorldPosition(nx, ny);
            }
        });
        scene.setOnMouseReleased(e -> {
            dragging = false;
            movement.setEnabled(true);
            if (activity != null) {
                activity.setHandled(false);
                activity.activate(os.companion.character.behaviour.ActivityController.Trigger.PET);
            }
            if (dragMoved) {
                if (overlay.hasAction("idle")) {
                    overlay.play("idle");
                }
            } else {
                petReaction();
            }
        });

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                Platform.exit();
            } else if (e.getCode() == KeyCode.SPACE) {
                VoiceCoordinator vc = voice;
                if (vc != null) {
                    wake(os.companion.character.behaviour.ActivityController.Trigger.VOICE);
                    vc.trigger();
                } else {
                    log.warn("voice not ready yet");
                }
            } else if (e.getCode() == KeyCode.T) {
                promptAndAsk();
            } else if (e.getCode() == KeyCode.W) {
                overlay.setWalking(true);
            } else if (e.getCode() == KeyCode.S) {
                overlay.setWalking(false);
            } else if (e.getCode() == KeyCode.O) {
                cycleOutfit();
            } else if (e.getCode() == KeyCode.V) {
                toggleScreenVision();
            } else if (e.getCode() == KeyCode.G) {
                toggleGooseMode();
            } else if (e.getCode() == KeyCode.N) {
                inference.submit(this::dropSmartNote);
            } else if (e.getCode().isDigitKey()) {
                playByIndex(e.getCode());
            }
        });
    }

    private void cycleOutfit() {
        if (outfits == null || overlay == null) {
            return;
        }
        String next = outfits.next(currentOutfit);
        if (!next.equals(currentOutfit) && overlay.swapSpriteSheet(outfits.get(next))) {
            currentOutfit = next;
            log.info("outfit -> {}", next);
        }
    }

    private void reinforceAlwaysOnTop() {
        if (!platform.isSupported()) {
            return;
        }
        final int[] attempts = {0};
        javafx.animation.Timeline t = new javafx.animation.Timeline();
        t.getKeyFrames().add(new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(250), e -> {
            boolean ok = platform.setAlwaysOnTop(WINDOW_TITLE);
            attempts[0]++;
            if (ok || attempts[0] >= 8) {
                t.stop();
                if (ok) {
                    log.debug("native always-on-top enforced after {} attempt(s)", attempts[0]);
                }
            }
        }));
        t.setCycleCount(javafx.animation.Animation.INDEFINITE);
        t.play();
    }

    private static final int VK_CONTROL = 0x11, VK_SHIFT = 0x10, VK_SPACE = 0x20;

    private void startGlobalHotkey() {
        if (!platform.isSupported()) {
            return;
        }
        Thread t = new Thread(() -> {
            boolean was = false;
            while (true) {
                boolean down = platform.isKeyDown(VK_CONTROL)
                        && platform.isKeyDown(VK_SHIFT) && platform.isKeyDown(VK_SPACE);
                if (down && !was) {
                    VoiceCoordinator vc = voice;
                    if (vc != null) {
                        Platform.runLater(() -> {
                            wake(os.companion.character.behaviour.ActivityController.Trigger.VOICE);
                            vc.trigger();
                        });
                    }
                }
                was = down;
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "global-hotkey");
        t.setDaemon(true);
        t.start();
        log.info("global push-to-talk: Ctrl+Shift+Space (works from any window)");
    }

    private void wake(os.companion.character.behaviour.ActivityController.Trigger why) {
        if (activity != null) {
            activity.activate(why);
        }
    }

    private void toggleGooseMode() {
        if (activity == null || overlay == null) {
            return;
        }
        var mode = activity.toggleMode(overlay);
        boolean goose = mode == os.companion.character.behaviour.ActivityController.Mode.GOOSE;
        overlay.showBubble(goose ? "GOOSE MODE! honk~ 🪿" : "Okay, calming down~", 2600);
        log.info("behaviour mode -> {}", mode);
    }

    private void toggleScreenVision() {
        if (perception == null || overlay == null) {
            return;
        }
        if (!perception.isAvailable()) {
            overlay.showBubble("I can't peek at the screen here~", 2500);
            return;
        }
        boolean on = perception.toggle();
        if (!on) {
            model.setScreenContext(null);
        }
        overlay.showBubble(on ? "Okay, I'll peek at your screen~ 👀" : "Not looking anymore~", 2600);
        log.info("screen vision {}", on ? "ON" : "OFF");
    }

    private void onScreenChanged(String desc) {
        wake(os.companion.character.behaviour.ActivityController.Trigger.SCREEN);
        if (rnd.nextInt(100) < 50) {
            screenRemark(desc);
        }
    }

    private void screenRemark(String desc) {
        if (overlay == null || overlay.isSpeaking() || asking.get()) {
            return;
        }
        if (voice != null && voice.state() != VoiceCoordinator.State.PASSIVE) {
            return;
        }
        if (!modelReady.get() && !warmModel()) {
            return;
        }
        if (!asking.compareAndSet(false, true)) {
            return;
        }
        inference.submit(() -> {
            try {
                String cue = "[You just glanced at the user's screen and saw: \"" + desc
                        + "\". Make ONE short, playful, in-character remark about it, under 14 words.]";
                CompanionResponse r = model.spontaneous(cue);
                VoiceCoordinator vc = voice;
                if (vc != null) {
                    vc.saySpontaneous(r, rr -> Platform.runLater(() -> behaviour.react(overlay, rr)));
                } else {
                    Platform.runLater(() -> behaviour.showThought(overlay, r));
                }
            } catch (Exception e) {
                log.debug("screen remark skipped: {}", e.getMessage());
            } finally {
                asking.set(false);
            }
        });
    }

    private void playByIndex(KeyCode digit) {
        int idx = digit.getCode() - KeyCode.DIGIT1.getCode();
        java.util.List<String> actions = overlay.actionNames();
        if (idx >= 0 && idx < actions.size()) {
            overlay.play(actions.get(idx));
        }
    }

    private void startRenderLoop(Stage stage, Rectangle2D work) {
        lastNanos = 0;
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) {
                    lastNanos = now;
                    return;
                }
                double deltaMs = (now - lastNanos) / 1_000_000.0;
                lastNanos = now;

                activity.tick(deltaMs, overlay);
                if (perception != null) {
                    perception.tick(deltaMs, activity.isActive());
                }
                VoiceCoordinator vc = voice;
                overlay.setMouthLevel(vc != null ? vc.mouthLevel() : 0);
                overlay.update(deltaMs,
                        work.getMinX(), work.getMinX() + work.getWidth(),
                        work.getMinY(), work.getMinY() + work.getHeight());
                overlay.render();

                if (!dragging) {
                    stage.setX(overlay.worldX());
                    stage.setY(overlay.worldY());
                }
            }
        }.start();
    }

    @Override
    public void stop() {
        chatter.shutdownNow();
        stickyNote.clearAll();
        inference.shutdownNow();
        if (perception != null) {
            perception.close();
        }
        VoiceCoordinator vc = voice;
        if (vc != null) {
            vc.close();
        }
        if (model != null) {
            model.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static final class Launcher {
        public static void main(String[] args) {
            CompanionApplication.main(args);
        }
    }
}
