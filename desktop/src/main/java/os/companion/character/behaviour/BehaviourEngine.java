package os.companion.character.behaviour;

import javafx.animation.PauseTransition;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import os.companion.ai.CompanionResponse;
import os.companion.ai.Emotion;
import os.companion.ai.Gesture;
import os.companion.character.rendering.CharacterOverlay;

public final class BehaviourEngine {

    private static final double MS_PER_CHAR = 55;
    private static final double MIN_TALK_MS = 1200;
    private static final double MAX_TALK_MS = 5000;
    private static final double GESTURE_MS = 700;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BehaviourEngine.class);

    public void react(CharacterOverlay overlay, CompanionResponse response) {
        log.debug("react: emotion={} gesture={} energy={}",
                response.emotion(), response.gesture(), response.energy());
        applyEmotion(overlay, response.emotion());
        CharacterOverlay.FxType fx = fxFor(response.emotion());
        if (fx != null) {
            overlay.playFx(fx);
        }
        overlay.setFacing(true);
        overlay.setWalking(false);

        double talkMs = Math.max(MIN_TALK_MS,
                Math.min(MAX_TALK_MS, response.speech().length() * MS_PER_CHAR));

        overlay.showBubble(response.speech(), talkMs + 1800);

        switch (response.gesture()) {
            case WAVE -> overlay.playWave();
            case BOUNCE -> overlay.playHop();
            case NOD -> overlay.playNod();
            case SHAKE_HEAD -> overlay.playShake();
            default -> {
                switch (response.emotion()) {
                    case EXCITED, HAPPY -> overlay.playHop();
                    case ANGRY -> overlay.playShake();
                    default -> { }
                }
            }
        }

        String gestureAction = mapGesture(response.gesture());
        String reactionAction = gestureAction != null ? gestureAction : mapEmotion(response.emotion());
        if (reactionAction != null && overlay.hasAction(reactionAction)) {
            overlay.play(reactionAction);
            PauseTransition afterGesture = new PauseTransition(Duration.millis(GESTURE_MS));
            afterGesture.setOnFinished(e -> talkThenIdle(overlay, talkMs));
            afterGesture.play();
        } else {
            talkThenIdle(overlay, talkMs);
        }
    }

    private void talkThenIdle(CharacterOverlay overlay, double talkMs) {

        if (overlay.hasAction("talk")) {
            overlay.play("talk");
        }
        overlay.setSpeaking(true);
        PauseTransition talk = new PauseTransition(Duration.millis(talkMs));
        talk.setOnFinished(e -> {
            overlay.setSpeaking(false);
            overlay.play(overlay.hasAction("idle") ? "idle" : "walk");
        });
        talk.play();
    }

    public void showThought(CharacterOverlay overlay, CompanionResponse response) {
        applyEmotion(overlay, response.emotion());
        double ms = Math.max(2500, Math.min(6500, response.speech().length() * MS_PER_CHAR));
        overlay.showBubble(response.speech(), ms);
    }

    private String mapGesture(Gesture gesture) {
        return switch (gesture) {
            case WAVE -> "wave";
            case NOD -> "nod";
            case SIT -> "sit";
            case BOUNCE -> "happy";
            default -> null;
        };
    }

    private String mapEmotion(Emotion emotion) {
        return switch (emotion) {
            case HAPPY -> "happy";
            case SAD -> "sad";
            case ANGRY -> "angry";
            case EXCITED -> "surprised";
            case TIRED -> "sleep";
            default -> null;
        };
    }

    private void applyEmotion(CharacterOverlay overlay, Emotion emotion) {
        applyEmotion(overlay, emotion, 1.0);
    }

    private void applyEmotion(CharacterOverlay overlay, Emotion emotion, double scale) {
        switch (emotion) {
            case HAPPY    -> overlay.setTint(Color.rgb(255, 215, 0), 0.22 * scale);
            case AMUSED   -> overlay.setTint(Color.rgb(255, 170, 60), 0.20 * scale);
            case EXCITED  -> overlay.setTint(Color.rgb(255, 120, 80), 0.26 * scale);
            case SAD      -> overlay.setTint(Color.rgb(80, 120, 220), 0.28 * scale);
            case TIRED    -> overlay.setTint(Color.rgb(110, 110, 160), 0.24 * scale);
            case ANGRY    -> overlay.setTint(Color.rgb(220, 50, 40), 0.30 * scale);
            case CONFUSED -> overlay.setTint(Color.rgb(170, 90, 210), 0.24 * scale);
            case NEUTRAL  -> overlay.setTint(null, 0);
        }
    }

    public void setAmbientMood(CharacterOverlay overlay, Emotion emotion) {
        applyEmotion(overlay, emotion, 0.55);
    }

    public void ambientMicroEmotion(CharacterOverlay overlay, java.util.Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.5) {
            setAmbientMood(overlay, Emotion.HAPPY);
            overlay.playHop();
            overlay.playFx(CharacterOverlay.FxType.HEARTS);
        } else if (r < 0.8) {
            setAmbientMood(overlay, Emotion.AMUSED);
            overlay.playFx(CharacterOverlay.FxType.NOTE);
        } else {
            setAmbientMood(overlay, Emotion.NEUTRAL);
        }
    }

    private CharacterOverlay.FxType fxFor(Emotion emotion) {
        return switch (emotion) {
            case HAPPY -> CharacterOverlay.FxType.HEARTS;
            case EXCITED -> CharacterOverlay.FxType.SPARKLES;
            case AMUSED -> CharacterOverlay.FxType.NOTE;
            case SAD -> CharacterOverlay.FxType.TEAR;
            case ANGRY -> CharacterOverlay.FxType.ANGER;
            case CONFUSED -> CharacterOverlay.FxType.QUESTION;
            case TIRED -> CharacterOverlay.FxType.ZZZ;
            case NEUTRAL -> null;
        };
    }
}
