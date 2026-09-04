package os.companion.character.behaviour;

import os.companion.character.rendering.CharacterOverlay;
import os.companion.nativeplatform.PlatformBridge;

import java.util.Random;

public final class MovementController {

    private enum Mode { PAUSE, WANDER, CHASE, GRAB }

    private static final double MIN_WANDER_MS = 2500, MAX_WANDER_MS = 6000;
    private static final double MIN_PAUSE_MS = 2000, MAX_PAUSE_MS = 5000;
    private static final double CHASE_TIMEOUT_MS = 6000;
    private static final double GRAB_MS = 900;
    private static final double CHASE_CHANCE = 0.4;
    private static final double GRAB_CHANCE = 0.6;
    private static final double SIT_CHANCE = 0.22;
    private static final double ARRIVE_PX = 64;
    private static final double FOLLOW_RADIUS = 240;
    private static final double GRAB_STEP_PX = 7;

    private static final double ROAM_SPEED = 120;
    private static final double CHASE_SPEED = 230;
    private static final double TIRED_SPEED_MUL = 0.55;

    private final Random rnd = new Random();
    private final PlatformBridge platform;

    private Mode mode = Mode.PAUSE;
    private double timerMs = 1500;
    private boolean enabled = true;

    private boolean grabEnabled = false;
    private boolean tired = false;

    private double minX, maxX, minY, maxY;
    private boolean boundsSet = false;

    public MovementController(PlatformBridge platform) {
        this.platform = platform;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setGrabEnabled(boolean grabEnabled) {
        this.grabEnabled = grabEnabled;
    }

    public void setTired(boolean tired) {
        this.tired = tired;
    }

    public void setBounds(double minX, double maxX, double minY, double maxY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.boundsSet = true;
    }

    public void tick(double deltaMs, CharacterOverlay overlay) {
        if (!enabled || overlay.isSpeaking()) {
            overlay.stopMoving();
            return;
        }

        if (overlay.consumeEdgeHit() && mode == Mode.WANDER && overlay.hasAction("push_window")) {
            overlay.play("push_window");
        }
        switch (mode) {
            case GRAB -> {
                doGrab(overlay);
                timerMs -= deltaMs;
                if (timerMs <= 0) {
                    enterPause(overlay);
                }
            }
            case CHASE -> {
                doChase(overlay);
                timerMs -= deltaMs;
                if (timerMs <= 0) {
                    enterPause(overlay);
                }
            }
            case WANDER -> {
                timerMs -= deltaMs;
                if (overlay.reachedTarget() || timerMs <= 0) {
                    enterPause(overlay);
                }
            }
            default -> {
                timerMs -= deltaMs;
                if (cursorIsNear(overlay) && grabEnabled && !tired) {
                    enterChase(overlay);
                } else if (timerMs <= 0) {
                    enterActive(overlay);
                }
            }
        }
    }

    private void enterActive(CharacterOverlay overlay) {
        boolean canChase = !tired && grabEnabled && platform != null && platform.cursorPosition() != null;
        if (canChase && rnd.nextDouble() < CHASE_CHANCE) {
            enterChase(overlay);
        } else {
            enterWander(overlay);
        }
    }

    private void enterWander(CharacterOverlay overlay) {
        mode = Mode.WANDER;
        double tx = boundsSet ? minX + rnd.nextDouble() * (maxX - minX - overlay.renderWidth())
                : overlay.worldX();
        double ty = boundsSet ? minY + rnd.nextDouble() * (maxY - minY - overlay.renderHeight())
                : overlay.worldY();
        overlay.setFacing(tx >= overlay.worldX());
        overlay.moveToward(tx, ty, speed(ROAM_SPEED));
        act(overlay, "walk");
        timerMs = random(MIN_WANDER_MS, MAX_WANDER_MS);
    }

    private void enterChase(CharacterOverlay overlay) {
        mode = Mode.CHASE;
        timerMs = CHASE_TIMEOUT_MS;
        act(overlay, "walk");
    }

    private void enterPause(CharacterOverlay overlay) {
        mode = Mode.PAUSE;
        overlay.stopMoving();
        if (tired && overlay.hasAction("sleep")) {
            act(overlay, rnd.nextBoolean() && overlay.hasAction("nap") ? "nap" : "sleep");
        } else {
            act(overlay, rnd.nextDouble() < SIT_CHANCE ? "sit" : "idle");
        }
        timerMs = random(MIN_PAUSE_MS, MAX_PAUSE_MS) * (tired ? 1.8 : 1.0);
    }

    private void act(CharacterOverlay overlay, String action) {
        if (overlay.hasAction(action)) {
            overlay.play(action);
        }
    }

    private boolean cursorIsNear(CharacterOverlay overlay) {
        int[] c = platform != null ? platform.cursorPosition() : null;
        if (c == null) {
            return false;
        }
        return Math.hypot(c[0] - overlay.centerX(), c[1] - overlay.centerY()) < FOLLOW_RADIUS;
    }

    private void doChase(CharacterOverlay overlay) {
        int[] c = platform.cursorPosition();
        if (c == null) {
            enterPause(overlay);
            return;
        }
        overlay.setFacing(c[0] >= overlay.centerX());
        boolean hasHand = overlay.hasHand();

        double dist = hasHand
                ? Math.hypot(c[0] - overlay.handScreenX(), c[1] - overlay.handScreenY())
                : Math.hypot(c[0] - overlay.centerX(), c[1] - overlay.centerY());
        if (dist < ARRIVE_PX) {
            overlay.stopMoving();
            if (grabEnabled && rnd.nextDouble() < GRAB_CHANCE) {
                mode = Mode.GRAB;
                timerMs = GRAB_MS;
                act(overlay, "grab_cursor");
                Sfx.chirp();
            } else {
                enterPause(overlay);
            }
        } else {

            double tx, ty;
            if (hasHand) {
                tx = c[0] - overlay.handOffsetX();
                ty = c[1] - overlay.handOffsetY();
            } else {
                tx = c[0] - overlay.renderWidth() / 2;
                ty = c[1] - overlay.renderHeight() * 0.30;
            }
            overlay.moveToward(tx, ty, speed(CHASE_SPEED));
        }
    }

    private void doGrab(CharacterOverlay overlay) {
        if (overlay.hasAction("held_drag")) {
            overlay.play("held_drag");
        }
        int[] c = platform.cursorPosition();
        if (c == null) {
            return;
        }
        double tx = overlay.hasHand() ? overlay.handScreenX() : overlay.centerX();
        double ty = overlay.hasHand()
                ? overlay.handScreenY()
                : overlay.worldY() + overlay.renderHeight() * 0.35;
        platform.moveCursor(stepToward(c[0], tx), stepToward(c[1], ty));
    }

    private int stepToward(double from, double to) {
        double d = to - from;
        if (Math.abs(d) <= GRAB_STEP_PX) {
            return (int) Math.round(to);
        }
        return (int) Math.round(from + Math.signum(d) * GRAB_STEP_PX);
    }

    private double speed(double base) {
        return tired ? base * TIRED_SPEED_MUL : base;
    }

    private double random(double lo, double hi) {
        return lo + rnd.nextDouble() * (hi - lo);
    }
}
