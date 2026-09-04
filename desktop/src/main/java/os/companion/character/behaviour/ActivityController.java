package os.companion.character.behaviour;

import os.companion.ai.Emotion;
import os.companion.character.rendering.CharacterOverlay;
import os.companion.nativeplatform.PlatformBridge;

import java.util.Random;
import java.util.function.Consumer;

public final class ActivityController {

    public enum State { AMBIENT, ACTIVE }
    public enum Trigger { VOICE, CONTEXT, CURSOR, SPONTANEOUS, SCREEN, PET }

    public enum Mode { CALM, GOOSE }

    private static final double ACTIVE_WINDOW_MS = 45_000;
    private static final double AMBIENT_HOLD_MIN = 8_000, AMBIENT_HOLD_MAX = 22_000;
    private static final double CURSOR_NEAR_PX = 90;
    private static final double CURSOR_COOLDOWN_MS = 20_000;
    private static final double HOME_EPS = 10;
    private static final double GO_HOME_SPEED = 110;
    private static final double MICRO_EMOTION_CHANCE = 0.30;

    private final PlatformBridge platform;
    private final MovementController movement;
    private final ContextReactor context;
    private final BehaviourEngine behaviour;
    private final Random rnd = new Random();

    private Mode mode = Mode.CALM;
    private State state = State.AMBIENT;
    private double activeMs = 0;
    private double ambientHoldMs = 0;
    private double clock = 0;
    private double shoveAccumMs = 0;
    private double perchCooldownMs = 14000;
    private double perchingMs = 0;
    private double lastCursorWakeMs = -1e9;
    private static final double SHOVE_INTERVAL_MS = 4200;
    private static final double PERCH_HOLD_MS = 6500;
    private static final double PERCH_INTERVAL_CALM_MS = 30000;
    private static final double PERCH_INTERVAL_GOOSE_MS = 12000;
    private boolean tired = false;
    private boolean handled = false;
    private boolean goingHome = false;
    private boolean perkPending = false;

    private double homeX, homeY;
    private boolean homeSet = false;

    private Consumer<Trigger> onActivate;

    private static final String[] AMBIENT_ACTIONS = { "idle", "idle", "sit" };

    public ActivityController(PlatformBridge platform, MovementController movement,
                              ContextReactor context, BehaviourEngine behaviour) {
        this.platform = platform;
        this.movement = movement;
        this.context = context;
        this.behaviour = behaviour;
        movement.setEnabled(false);
    }

    public void setHome(double x, double y) {
        this.homeX = x;
        this.homeY = y;
        this.homeSet = true;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    public void setTired(boolean tired) {
        this.tired = tired;
        movement.setTired(tired);
    }

    public void setOnActivate(Consumer<Trigger> onActivate) {
        this.onActivate = onActivate;
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public Mode mode() {
        return mode;
    }

    public Mode toggleMode(CharacterOverlay overlay) {
        mode = (mode == Mode.CALM) ? Mode.GOOSE : Mode.CALM;
        if (mode == Mode.GOOSE) {
            state = State.ACTIVE;
            activeMs = ACTIVE_WINDOW_MS;
            movement.setEnabled(true);
            movement.setGrabEnabled(true);
            perkPending = true;
        } else {
            enterAmbient(overlay);
        }
        return mode;
    }

    public void activate(Trigger why) {
        boolean wasActive = state == State.ACTIVE;
        state = State.ACTIVE;
        activeMs = ACTIVE_WINDOW_MS;
        goingHome = false;
        movement.setEnabled(true);

        movement.setGrabEnabled(why == Trigger.CURSOR);
        if (!wasActive) {
            perkPending = true;
            if (onActivate != null) {
                onActivate.accept(why);
            }
        }
    }

    public void tick(double deltaMs, CharacterOverlay overlay) {
        clock += deltaMs;
        if (!homeSet) {
            homeX = overlay.worldX();
            homeY = overlay.worldY();
            homeSet = true;
        }

        if (handled || overlay.isSpeaking()) {
            if (state == State.ACTIVE) {
                activeMs = Math.max(activeMs, 4000);
            }
            return;
        }

        perchCooldownMs -= deltaMs;
        if (perchingMs > 0) {
            perchingMs -= deltaMs;
            return;
        }
        if (perchCooldownMs <= 0) {
            perchCooldownMs = (mode == Mode.GOOSE) ? PERCH_INTERVAL_GOOSE_MS : PERCH_INTERVAL_CALM_MS;
            if (platform != null && platform.foregroundWindowRect() != null && rnd.nextDouble() < 0.75) {
                context.perchOnForeground(overlay);
                perchingMs = PERCH_HOLD_MS;
                return;
            }
        }

        if (mode == Mode.GOOSE) {
            state = State.ACTIVE;
            activeMs = ACTIVE_WINDOW_MS;
            movement.setEnabled(true);
            movement.setGrabEnabled(true);
            shoveAccumMs += deltaMs;
            if (shoveAccumMs >= SHOVE_INTERVAL_MS && platform != null && platform.isSupported()) {
                shoveAccumMs = 0;
                int dir = rnd.nextBoolean() ? 1 : -1;
                platform.moveForegroundWindow(dir * (40 + rnd.nextInt(70)), (rnd.nextInt(3) - 1) * 22);
                overlay.playHop();
                if (rnd.nextDouble() < 0.45) {
                    overlay.showBubble("HONK! 🪿", 1300);
                }
            }
            context.maybePoll(deltaMs, overlay);
            movement.tick(deltaMs, overlay);
            return;
        }

        if (state == State.AMBIENT && cursorNear(overlay)
                && clock - lastCursorWakeMs > CURSOR_COOLDOWN_MS) {
            lastCursorWakeMs = clock;
            activate(Trigger.CURSOR);
        }

        boolean reacted = context.maybePoll(deltaMs, overlay);
        if (reacted && state == State.AMBIENT) {
            activate(Trigger.CONTEXT);
        }

        if (state == State.ACTIVE) {
            if (perkPending) {
                perkPending = false;
                faceCursor(overlay);
                behaviour.setAmbientMood(overlay, Emotion.HAPPY);
                overlay.playHop();
            }
            movement.tick(deltaMs, overlay);
            activeMs -= deltaMs;
            if (activeMs <= 0) {
                enterAmbient(overlay);
            }
        } else {
            tickAmbient(deltaMs, overlay);
        }
    }

    private void enterAmbient(CharacterOverlay overlay) {
        state = State.AMBIENT;
        movement.setEnabled(false);
        movement.setGrabEnabled(false);
        overlay.stopMoving();
        goingHome = true;
        ambientHoldMs = 0;
        behaviour.setAmbientMood(overlay, Emotion.NEUTRAL);
    }

    private void tickAmbient(double deltaMs, CharacterOverlay overlay) {
        double dx = homeX - overlay.worldX();
        double dy = homeY - overlay.worldY();
        boolean atHome = Math.hypot(dx, dy) < HOME_EPS;
        if (!atHome && goingHome) {
            overlay.setFacing(dx >= 0);
            overlay.moveToward(homeX, homeY, tired ? GO_HOME_SPEED * 0.6 : GO_HOME_SPEED);
            act(overlay, "walk");
            return;
        }
        goingHome = false;
        overlay.stopMoving();
        ambientHoldMs -= deltaMs;
        if (ambientHoldMs <= 0) {
            pickAmbientActivity(overlay);
            ambientHoldMs = random(AMBIENT_HOLD_MIN, AMBIENT_HOLD_MAX) * (tired ? 1.6 : 1.0);
        }
    }

    private void pickAmbientActivity(CharacterOverlay overlay) {
        if (tired) {
            String rest = overlay.hasAction("nap") ? "nap"
                    : overlay.hasAction("sleep") ? "sleep" : "idle";
            act(overlay, rest);
            behaviour.setAmbientMood(overlay, Emotion.TIRED);
            return;
        }
        act(overlay, AMBIENT_ACTIONS[rnd.nextInt(AMBIENT_ACTIONS.length)]);
        if (rnd.nextDouble() < MICRO_EMOTION_CHANCE) {
            behaviour.ambientMicroEmotion(overlay, rnd);
        } else {
            behaviour.setAmbientMood(overlay, Emotion.NEUTRAL);
        }
    }

    private void faceCursor(CharacterOverlay overlay) {
        int[] c = platform != null ? platform.cursorPosition() : null;
        if (c != null) {
            overlay.setFacing(c[0] >= overlay.centerX());
        }
    }

    private boolean cursorNear(CharacterOverlay overlay) {
        int[] c = platform != null ? platform.cursorPosition() : null;
        if (c == null) {
            return false;
        }
        return Math.hypot(c[0] - overlay.centerX(), c[1] - overlay.centerY()) < CURSOR_NEAR_PX;
    }

    private void act(CharacterOverlay overlay, String action) {
        if (overlay.hasAction(action)) {
            overlay.play(action);
        }
    }

    private double random(double lo, double hi) {
        return lo + rnd.nextDouble() * (hi - lo);
    }
}
