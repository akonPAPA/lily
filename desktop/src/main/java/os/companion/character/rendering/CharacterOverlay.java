package os.companion.character.rendering;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.geometry.VPos;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import os.companion.character.animation.FrameAtlas;
import os.companion.character.animation.RigModel;
import os.companion.character.animation.SpriteAnimator;
import os.companion.character.animation.SpriteSheet;

import java.util.ArrayList;
import java.util.List;

public final class CharacterOverlay {

    private static final double HEADROOM_PX = 12;
    private static final double BREATH_HZ = 0.2;
    private static final double BREATH_AMP = 3;
    private static final double TALK_HZ = 3.2;
    private static final double TALK_AMP = 2;
    private static final double MOUTH_HZ = 6.5;
    private static final Color MOUTH_FILL = Color.rgb(214, 110, 130);
    private static final Color MOUTH_DARK = Color.rgb(170, 78, 100);

    private static final double BUBBLE_PAD_PX = 130;
    private static final double BUBBLE_MARGIN = 10;
    private static final double BUBBLE_INNER = 13;
    private static final double BUBBLE_RADIUS = 24;
    private static final Font BUBBLE_FONT = Font.font("Comic Sans MS", 15);
    private static final Font BUBBLE_HEART_FONT = Font.font("Segoe UI Emoji", 12);
    private static final double BUBBLE_LINE_H = 21;
    private static final Color BUBBLE_BG = Color.web("#FFF4FA");
    private static final Color BUBBLE_BORDER = Color.web("#FFAAD2");
    private static final Color BUBBLE_TEXT = Color.web("#9E5C79");
    private static final Color BUBBLE_SHADOW = Color.rgb(255, 150, 190, 0.30);
    private static final Color BUBBLE_HEART = Color.web("#FF8FBB");

    private static final double WAVE_DUR_MS = 1300;
    private static final double NOD_DUR_MS = 700;

    private static final double BREATH_SCALE = 0.022;
    private static final double SWAY_HZ = 0.13;
    private static final double SWAY_DEG = 1.1;

    private static final double WALK_BOB_HZ = 2.6;
    private static final double WALK_BOB_AMP = 4.5;
    private static final double WALK_LEAN_DEG = 3.5;

    private static final double HOP_DUR_MS = 520, HOP_HEIGHT = 16;
    private static final double SHAKE_DUR_MS = 430, SHAKE_AMP = 6, SHAKE_FREQ = 7;
    private static final double FLAT_NOD_DUR_MS = 430, NOD_DIP = 9;
    private static final Color LASH = Color.rgb(70, 45, 60);

    private SpriteSheet sheet;
    private SpriteAnimator animator;
    private final RigModel rig;

    private RigModel idleRig;
    private final Canvas canvas;

    private final double frameW;
    private final double frameH;
    private final int scale;
    private final FrameAtlas.MouthRegion mouth;

    private double waveMs = 0;
    private double nodMs = 0;

    private double hopMs = 0;
    private double shakeMs = 0;
    private double flatNodMs = 0;
    private double mouthLevel = 0;
    private double mouthLevelSmoothed = 0;
    private java.util.List<FrameAtlas.MouthRegion> eyes = java.util.List.of();
    private Color eyelidColor = null;
    private double nextBlinkAtMs = 2500;
    private double blinkMs = 0;

    public static final double WALK_SPEED = 42;

    private double worldX;
    private double worldY;
    private double velocityX = 0;
    private double velocityY = 0;
    private boolean facingRight = true;

    private boolean seeking = false;
    private double targetX, targetY;
    private double moveSpeed = WALK_SPEED;
    private boolean edgeHit = false;
    private static final double ARRIVE_EPS = 6;

    private double elapsedMs = 0;
    private boolean speaking = false;
    private double speakMs = 0;

    private Color tint = null;
    private double tintAlpha = 0;

    private static final double FADE_DUR_MS = 90;
    private double fadeMs = 0;
    private double prevSx = 0, prevSy = 0;
    private boolean prevValid = false;

    public enum FxType { HEARTS, SPARKLES, NOTE, TEAR, ANGER, QUESTION, EXCLAIM, ZZZ }
    private static final class Fx {
        final FxType type;
        final double durMs;
        final long seed;
        double ageMs;
        Fx(FxType type, double durMs, long seed) { this.type = type; this.durMs = durMs; this.seed = seed; }
    }
    private final List<Fx> fxs = new ArrayList<>();
    private static final int MAX_FX = 6;
    private static final Font FX_EMOJI_FONT = Font.font("Segoe UI Emoji", 20);
    private static final java.util.Random FX_RND = new java.util.Random();

    private List<String> bubbleLines = null;
    private double bubbleHideAtMs = 0;
    private double bubbleShownAtMs = 0;
    private int bubbleTotalChars = 0;
    private static final double BUBBLE_POP_MS = 200;
    private static final double BUBBLE_HIDE_MS = 220;
    private static final double BUBBLE_TYPE_CPS = 45;

    private final double topPad;

    private CharacterOverlay(SpriteSheet sheet, SpriteAnimator animator, RigModel rig,
                             double frameW, double frameH, int scale, FrameAtlas.MouthRegion mouth) {
        this.sheet = sheet;
        this.animator = animator;
        this.rig = rig;
        this.frameW = frameW;
        this.frameH = frameH;
        this.scale = scale;
        this.mouth = mouth;
        this.topPad = BUBBLE_PAD_PX + HEADROOM_PX;
        this.canvas = new Canvas(frameW * scale, frameH * scale + topPad + HEADROOM_PX);

        boolean smooth = sheet != null && sheet.atlas().isSmooth();
        this.canvas.getGraphicsContext2D().setImageSmoothing(smooth);
        if (sheet != null) {
            this.eyes = sheet.atlas().eyes();
            this.eyelidColor = sampleSkin(sheet.image());
        }
    }

    private static Color sampleSkin(Image img) {
        javafx.scene.image.PixelReader pr = img.getPixelReader();
        Color fallback = Color.rgb(245, 214, 185);
        if (pr == null) {
            return fallback;
        }
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        double[][] pts = {{0.5, 0.42}, {0.44, 0.40}, {0.56, 0.40}, {0.5, 0.45}, {0.5, 0.40}};
        Color firstOpaque = null;
        for (double[] p : pts) {
            int x = (int) (p[0] * w);
            int y = (int) (p[1] * h);
            if (x < 0 || x >= w || y < 0 || y >= h) {
                continue;
            }
            Color c = pr.getColor(x, y);
            if (c.getOpacity() < 0.9) {
                continue;
            }
            if (firstOpaque == null) {
                firstOpaque = c;
            }

            if (c.getRed() > 0.6 && c.getRed() >= c.getGreen()
                    && c.getRed() - c.getBlue() > 0.06) {
                return c;
            }
        }
        return firstOpaque != null ? firstOpaque : fallback;
    }

    public CharacterOverlay(SpriteSheet sheet, String initialAction) {
        this(sheet, new SpriteAnimator(sheet.atlas(), initialAction), null,
                sheet.atlas().frameWidth(), sheet.atlas().frameHeight(),
                sheet.atlas().renderScale() > 0 ? sheet.atlas().renderScale()
                        : (sheet.atlas().frameHeight() <= 128 ? 2 : 1),
                sheet.atlas().mouth());
    }

    public CharacterOverlay(RigModel rig) {
        this(null, null, rig, rig.frameWidth(), rig.frameHeight(),
                rig.renderScale() > 0 ? rig.renderScale() : 1, rig.mouth());
    }

    public Canvas canvas() {
        return canvas;
    }

    public void setIdleRig(RigModel rig) {
        if (rig != null && (rig.frameWidth() != (int) frameW || rig.frameHeight() != (int) frameH)) {
            return;
        }
        this.idleRig = rig;
    }

    private boolean useIdleRig() {
        return idleRig != null && animator != null && animator.currentAction() != null
                && "idle".equals(animator.currentAction().name());
    }

    public double renderWidth() {
        return canvas.getWidth();
    }

    public double renderHeight() {
        return canvas.getHeight();
    }

    public void play(String action) {
        if (animator != null) {

            FrameAtlas.Action cur = animator.currentAction();
            if (cur != null && !cur.name().equals(action) && sheet != null) {
                prevSx = sheet.sourceX(animator.currentFrame());
                prevSy = sheet.sourceY(cur);
                prevValid = true;
                fadeMs = FADE_DUR_MS;
            }
            animator.play(action);
        }
    }

    public boolean swapSpriteSheet(SpriteSheet newSheet) {
        if (newSheet == null || rig != null) {
            return false;
        }
        if (newSheet.atlas().frameWidth() != (int) frameW
                || newSheet.atlas().frameHeight() != (int) frameH) {
            return false;
        }
        String action = animator != null && animator.currentAction() != null
                ? animator.currentAction().name() : "idle";
        if (!newSheet.atlas().hasAction(action)) {
            action = newSheet.atlas().actions().keySet().iterator().next();
        }
        this.sheet = newSheet;
        this.animator = new SpriteAnimator(newSheet.atlas(), action);
        this.eyes = newSheet.atlas().eyes();
        this.eyelidColor = sampleSkin(newSheet.image());
        return true;
    }

    public java.util.List<String> actionNames() {
        return sheet == null ? java.util.List.of()
                : new java.util.ArrayList<>(sheet.atlas().actions().keySet());
    }

    public boolean hasAction(String name) {
        return sheet != null && sheet.atlas().hasAction(name);
    }

    public boolean hasHand() {
        return sheet != null && sheet.atlas().hand() != null;
    }

    public double handOffsetX() {
        FrameAtlas.MouthRegion h = sheet != null ? sheet.atlas().hand() : null;
        double hx = (h != null ? h.x() : 0.5) * frameW * scale;
        return facingRight ? hx : renderWidth() - hx;
    }

    public double handOffsetY() {
        FrameAtlas.MouthRegion h = sheet != null ? sheet.atlas().hand() : null;
        double hy = (h != null ? h.y() : 0.45) * frameH * scale;
        return topPad + hy;
    }

    public double handScreenX() {
        return worldX + handOffsetX();
    }

    public double handScreenY() {
        return worldY + handOffsetY();
    }

    public boolean isRigged() {
        return rig != null;
    }

    public void playWave() {
        if (rig != null) {
            waveMs = WAVE_DUR_MS;
        } else {
            hopMs = HOP_DUR_MS;
        }
    }

    public void playNod() {
        nodMs = NOD_DUR_MS;
        flatNodMs = FLAT_NOD_DUR_MS;
    }

    public void playHop() {
        hopMs = HOP_DUR_MS;
    }

    public void playFx(FxType type) {
        if (type == null) {
            return;
        }
        double dur = switch (type) {
            case ZZZ -> 2600;
            case TEAR -> 1500;
            case ANGER, EXCLAIM -> 800;
            case QUESTION -> 1200;
            default -> 1500;
        };
        fxs.add(new Fx(type, dur, FX_RND.nextLong()));
        while (fxs.size() > MAX_FX) {
            fxs.remove(0);
        }
    }

    public void playShake() {
        shakeMs = SHAKE_DUR_MS;
    }

    public void setMouthLevel(double level) {
        this.mouthLevel = Math.max(0, Math.min(1, level));
    }

    public void debugForceBlink() {
        this.blinkMs = 60;
    }

    public void setSpeaking(boolean speaking) {
        if (speaking && !this.speaking) {
            speakMs = 0;
        }
        this.speaking = speaking;
    }

    public void setTint(Color color, double alpha) {
        this.tint = color;
        this.tintAlpha = Math.max(0, Math.min(1, alpha));
    }

    public void showBubble(String text, double durationMs) {
        if (text == null || text.isBlank()) {
            clearBubble();
            return;
        }
        this.bubbleLines = wrap(text.strip(), canvas.getWidth() - 2 * BUBBLE_MARGIN - 2 * BUBBLE_INNER);
        this.bubbleHideAtMs = elapsedMs + durationMs;
        this.bubbleShownAtMs = elapsedMs;
        this.bubbleTotalChars = this.bubbleLines.stream().mapToInt(String::length).sum();
    }

    public void clearBubble() {
        this.bubbleLines = null;
    }

    private static List<String> wrap(String text, double maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (textWidth(candidate) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }

        if (lines.size() > 4) {
            lines = new ArrayList<>(lines.subList(0, 4));
            lines.set(3, lines.get(3) + "…");
        }
        return lines;
    }

    private static double textWidth(String s) {
        Text t = new Text(s);
        t.setFont(BUBBLE_FONT);
        return t.getLayoutBounds().getWidth();
    }

    public void setWorldPosition(double x, double y) {
        this.worldX = x;
        this.worldY = y;
    }

    public double worldX() {
        return worldX;
    }

    public double worldY() {
        return worldY;
    }

    public void setWalking(boolean walking) {
        if (walking) {
            seeking = false;
            if (velocityX == 0) {
                velocityX = facingRight ? WALK_SPEED : -WALK_SPEED;
            }
        } else {
            seeking = false;
            velocityX = 0;
            velocityY = 0;
        }
    }

    public void moveToward(double tx, double ty, double speed) {
        this.targetX = tx;
        this.targetY = ty;
        this.moveSpeed = speed;
        this.seeking = true;
    }

    public void stopMoving() {
        seeking = false;
        velocityX = 0;
        velocityY = 0;
    }

    public boolean reachedTarget() {
        return !seeking;
    }

    public boolean consumeEdgeHit() {
        boolean e = edgeHit;
        edgeHit = false;
        return e;
    }

    public double centerX() {
        return worldX + renderWidth() / 2;
    }

    public double centerY() {
        return worldY + topPad + frameH * scale / 2.0;
    }

    public boolean isWalking() {
        return velocityX != 0 || velocityY != 0;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public boolean isFacingRight() {
        return facingRight;
    }

    public void setFacing(boolean right) {
        this.facingRight = right;
        if (velocityX != 0) {
            velocityX = right ? WALK_SPEED : -WALK_SPEED;
        }
    }

    public void update(double deltaMs, double minX, double maxX, double minY, double maxY) {
        if (animator != null) {
            animator.update(deltaMs);
        }
        elapsedMs += deltaMs;
        if (speaking) {
            speakMs += deltaMs;
        }
        fadeMs = Math.max(0, fadeMs - deltaMs);
        waveMs = Math.max(0, waveMs - deltaMs);
        nodMs = Math.max(0, nodMs - deltaMs);
        if (!fxs.isEmpty()) {
            for (Fx fx : fxs) {
                fx.ageMs += deltaMs;
            }
            fxs.removeIf(fx -> fx.ageMs >= fx.durMs);
        }
        hopMs = Math.max(0, hopMs - deltaMs);
        shakeMs = Math.max(0, shakeMs - deltaMs);
        flatNodMs = Math.max(0, flatNodMs - deltaMs);

        mouthLevelSmoothed += (mouthLevel - mouthLevelSmoothed) * Math.min(1, deltaMs / 60.0);

        if (!eyes.isEmpty()) {
            if (blinkMs > 0) {
                blinkMs = Math.max(0, blinkMs - deltaMs);
            } else if (elapsedMs >= nextBlinkAtMs) {
                blinkMs = 120;
                nextBlinkAtMs = elapsedMs + 3000 + Math.random() * 3500;
            }
        }

        if (bubbleLines != null && elapsedMs >= bubbleHideAtMs) {
            bubbleLines = null;
        }

        double dt = deltaMs / 1000.0;
        double rightLimit = maxX - renderWidth();
        double bottomLimit = maxY - renderHeight();

        if (seeking) {
            double dx = targetX - worldX;
            double dy = targetY - worldY;
            double dist = Math.hypot(dx, dy);
            if (dist <= ARRIVE_EPS) {
                seeking = false;
                velocityX = 0;
                velocityY = 0;
            } else {
                velocityX = dx / dist * moveSpeed;
                velocityY = dy / dist * moveSpeed;
            }
        }

        worldX += velocityX * dt;
        worldY += velocityY * dt;

        boolean hitX = false;
        if (worldX <= minX) {
            worldX = minX;
            hitX = true;
        } else if (worldX >= rightLimit) {
            worldX = rightLimit;
            hitX = true;
        }
        if (worldY <= minY) {
            worldY = minY;
        } else if (worldY >= bottomLimit) {
            worldY = bottomLimit;
        }
        if (hitX) {
            edgeHit = true;
            if (!seeking) {
                velocityX = -velocityX;
            }
        }
        if (velocityX != 0) {
            facingRight = velocityX > 0;
        }
    }

    public void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (rig != null) {
            renderRig(gc);
            return;
        }
        if (useIdleRig()) {
            renderRigIdle(gc);
            if (!fxs.isEmpty()) {
                drawFxLayer(gc);
            }
            if (bubbleLines != null) {
                drawBubble(gc);
            }
            return;
        }

        FrameAtlas.Action action = animator.currentAction();
        double sx0 = sheet.sourceX(animator.currentFrame());
        double sy0 = sheet.sourceY(action);
        Image img = sheet.image();

        double drawW = frameW * scale;
        double drawH = frameH * scale;
        double baseY = topPad;
        double feetX = canvas.getWidth() / 2;
        double feetY = baseY + drawH;

        double breathPhase = Math.sin(elapsedMs / 1000.0 * 2 * Math.PI * BREATH_HZ);
        double scaleY = 1 + breathPhase * BREATH_SCALE;
        double scaleX = 1 - breathPhase * BREATH_SCALE * 0.5;
        double sway = Math.sin(elapsedMs / 1000.0 * 2 * Math.PI * SWAY_HZ) * SWAY_DEG;

        boolean moving = isWalking();
        double walkBob = moving
                ? Math.abs(Math.sin(elapsedMs / 1000.0 * 2 * Math.PI * WALK_BOB_HZ)) * WALK_BOB_AMP : 0;
        if (moving) {
            sway += facingRight ? WALK_LEAN_DEG : -WALK_LEAN_DEG;
        }

        double talkBob = speaking
                ? Math.abs(Math.sin(speakMs / 1000.0 * 2 * Math.PI * TALK_HZ)) * TALK_AMP : 0;
        double hopY = hopMs > 0 ? -Math.sin((1 - hopMs / HOP_DUR_MS) * Math.PI) * HOP_HEIGHT : 0;
        double nodY = flatNodMs > 0 ? Math.sin((1 - flatNodMs / FLAT_NOD_DUR_MS) * Math.PI) * NOD_DIP : 0;
        double shakeX = shakeMs > 0
                ? Math.sin((1 - shakeMs / SHAKE_DUR_MS) * 2 * Math.PI * SHAKE_FREQ)
                        * SHAKE_AMP * (shakeMs / SHAKE_DUR_MS)
                : 0;

        double open = mouthOpenAmount();

        gc.save();
        gc.translate(shakeX, hopY + nodY - talkBob - walkBob);
        if (!facingRight) {
            gc.translate(canvas.getWidth(), 0);
            gc.scale(-1, 1);
        }
        gc.translate(feetX, feetY);
        gc.rotate(sway);
        gc.scale(scaleX, scaleY);
        gc.translate(-feetX, -feetY);

        gc.drawImage(img, sx0, sy0, frameW, frameH, 0, baseY, drawW, drawH);

        if (fadeMs > 0 && prevValid) {
            gc.setGlobalAlpha(Math.max(0, Math.min(1, fadeMs / FADE_DUR_MS)));
            gc.drawImage(img, prevSx, prevSy, frameW, frameH, 0, baseY, drawW, drawH);
            gc.setGlobalAlpha(1);
        }

        if (tint != null && tintAlpha > 0) {
            gc.setGlobalBlendMode(BlendMode.SRC_ATOP);
            gc.setGlobalAlpha(tintAlpha);
            gc.setFill(tint);
            gc.fillRect(0, baseY, drawW, drawH);
            gc.setGlobalAlpha(1);
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        }

        if (mouth != null && open > 0.02) {
            drawMouth(gc, baseY, open);
        }
        if (!eyes.isEmpty() && blinkMs > 0 && eyelidColor != null) {
            drawBlink(gc, baseY);
        }
        gc.restore();

        if (!fxs.isEmpty()) {
            drawFxLayer(gc);
        }
        if (bubbleLines != null) {
            drawBubble(gc);
        }
    }

    private double mouthOpenAmount() {
        if (mouthLevelSmoothed > 0.05) {
            return Math.min(1, mouthLevelSmoothed);
        }
        if (speaking) {
            return Math.abs(Math.sin(speakMs / 1000.0 * 2 * Math.PI * MOUTH_HZ));
        }
        return 0;
    }

    private void drawMouth(GraphicsContext gc, double drawY, double open) {
        drawMouth(gc, drawY, open, mouth);
    }

    private void drawMouth(GraphicsContext gc, double drawY, double open, FrameAtlas.MouthRegion m) {
        if (m == null) {
            return;
        }
        double cx = m.x() * frameW * scale;
        double cy = drawY + m.y() * frameH * scale;
        double w = m.w() * frameW * scale;

        double h = m.h() * frameH * scale * (0.25 + 0.9 * open);
        gc.setFill(MOUTH_DARK);
        gc.fillRoundRect(cx - w / 2, cy - h / 2, w, h, w * 0.7, h * 0.9);
        gc.setFill(MOUTH_FILL);
        gc.fillRoundRect(cx - w / 2 + 0.8, cy - h / 2 + 0.8,
                Math.max(1, w - 1.6), Math.max(1, h - 1.6), w * 0.6, h * 0.8);

        gc.setFill(Color.rgb(255, 180, 200, 0.6));
        gc.fillOval(cx - w * 0.18, cy + h * 0.05, w * 0.36, Math.max(1, h * 0.35));
    }

    private void drawBlink(GraphicsContext gc, double drawY) {
        double close = Math.sin((1 - blinkMs / 120.0) * Math.PI);
        gc.setGlobalAlpha(Math.min(1, close * 1.4));
        for (FrameAtlas.MouthRegion eye : eyes) {
            double cx = eye.x() * frameW * scale;
            double cy = drawY + eye.y() * frameH * scale;
            double ew = eye.w() * frameW * scale;
            double eh = eye.h() * frameH * scale;
            gc.setFill(eyelidColor);
            gc.fillOval(cx - ew / 2, cy - eh / 2, ew, eh);
            gc.setStroke(LASH);
            gc.setLineWidth(1.5);
            gc.strokeLine(cx - ew * 0.4, cy, cx + ew * 0.4, cy);
        }
        gc.setGlobalAlpha(1);
    }

    private void renderRigIdle(GraphicsContext gc) {
        RigModel r = idleRig;
        double t = elapsedMs / 1000.0;
        double breath = Math.sin(t * 2 * Math.PI * BREATH_HZ) * BREATH_AMP;
        double talk = speaking
                ? Math.abs(Math.sin(speakMs / 1000.0 * 2 * Math.PI * TALK_HZ)) * TALK_AMP : 0;
        double baseY = topPad + breath - talk;
        double drawW = frameW * scale;
        double drawH = frameH * scale;
        double hopY = hopMs > 0 ? -Math.sin((1 - hopMs / HOP_DUR_MS) * Math.PI) * HOP_HEIGHT : 0;

        double headTilt = Math.sin(t * 2 * Math.PI * 0.16) * 3.5
                + (speaking ? Math.sin(speakMs / 1000.0 * 2 * Math.PI * 1.6) * 2.0 : 0)
                + (flatNodMs > 0 ? Math.sin((1 - flatNodMs / FLAT_NOD_DUR_MS) * Math.PI) * 6 : 0);
        double hairL = Math.sin(t * 2 * Math.PI * 0.13) * 2.6;
        double hairR = Math.sin(t * 2 * Math.PI * 0.13 + Math.PI) * 2.6;

        gc.save();
        gc.translate(0, hopY);
        if (!facingRight) {
            gc.translate(canvas.getWidth(), 0);
            gc.scale(-1, 1);
        }
        double open = mouthOpenAmount();
        for (String part : r.zorder()) {
            Image img = r.part(part);
            if (img == null) {
                continue;
            }
            double angle = switch (part) {
                case "head" -> headTilt;
                case "armL" -> hairL;
                case "armR" -> hairR;
                default -> 0;
            };
            if (angle != 0) {
                RigModel.Pivot p = r.pivotFor(part);
                double px = p.x() * drawW;
                double py = baseY + p.y() * drawH;
                gc.save();
                gc.translate(px, py);
                gc.rotate(angle);
                gc.translate(-px, -py);
                gc.drawImage(img, 0, baseY, drawW, drawH);
                if (part.equals("head") && open > 0.02) {
                    drawMouth(gc, baseY, open, r.mouth());
                }
                gc.restore();
            } else {
                gc.drawImage(img, 0, baseY, drawW, drawH);
            }
        }
        if (tint != null && tintAlpha > 0) {
            gc.setGlobalBlendMode(BlendMode.SRC_ATOP);
            gc.setGlobalAlpha(tintAlpha);
            gc.setFill(tint);
            gc.fillRect(0, baseY, drawW, drawH);
            gc.setGlobalAlpha(1);
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        }
        gc.restore();
    }

    private void renderRig(GraphicsContext gc) {
        double breath = Math.sin(elapsedMs / 1000.0 * 2 * Math.PI * BREATH_HZ) * BREATH_AMP;
        double talk = speaking
                ? Math.abs(Math.sin(speakMs / 1000.0 * 2 * Math.PI * TALK_HZ)) * TALK_AMP
                : 0;
        double baseY = topPad + breath - talk;
        double drawW = frameW * scale;
        double drawH = frameH * scale;

        gc.save();
        if (!facingRight) {
            gc.translate(canvas.getWidth(), 0);
            gc.scale(-1, 1);
        }

        for (String part : rig.zorder()) {
            javafx.scene.image.Image img = rig.part(part);
            if (img == null) {
                continue;
            }
            double angle = rigAngle(part);
            boolean isHead = part.equals("head");
            if (angle != 0 || isHead) {
                RigModel.Pivot p = rig.pivotFor(part);
                double px = p.x() * drawW;
                double py = baseY + p.y() * drawH;
                gc.save();
                gc.translate(px, py);
                gc.rotate(angle);
                gc.translate(-px, -py);
                gc.drawImage(img, 0, baseY, drawW, drawH);
                if (isHead && mouth != null) {
                    double open = mouthOpenAmount();
                    if (open > 0.02) {
                        drawMouth(gc, baseY, open);
                    }
                }
                gc.restore();
            } else {
                gc.drawImage(img, 0, baseY, drawW, drawH);
            }
        }

        if (tint != null && tintAlpha > 0) {
            gc.setGlobalBlendMode(BlendMode.SRC_ATOP);
            gc.setGlobalAlpha(tintAlpha);
            gc.setFill(tint);
            gc.fillRect(0, baseY, drawW, drawH);
            gc.setGlobalAlpha(1);
            gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        }
        gc.restore();

        if (bubbleLines != null) {
            drawBubble(gc);
        }
    }

    private double rigAngle(String part) {
        double t = elapsedMs / 1000.0;
        switch (part) {
            case "head" -> {
                double a = Math.sin(t * 2 * Math.PI * 0.15) * 1.8;
                if (speaking) {
                    a += Math.sin(speakMs / 1000.0 * 2 * Math.PI * 1.6) * 1.8;
                }
                if (nodMs > 0) {
                    double k = 1 - nodMs / NOD_DUR_MS;
                    a += Math.sin(k * Math.PI) * 6.0;
                }
                return a;
            }
            case "armR" -> {
                double a = Math.sin(t * 2 * Math.PI * 0.13) * 1.5;
                if (waveMs > 0) {
                    double k = waveMs / WAVE_DUR_MS;
                    a += -12 + Math.sin(k * 2 * Math.PI * 3) * 9;
                }
                return a;
            }
            case "armL" -> {
                return Math.sin(t * 2 * Math.PI * 0.13 + Math.PI) * 1.3;
            }
            default -> {
                return 0;
            }
        }
    }

    private void drawFxLayer(GraphicsContext gc) {
        double cx = canvas.getWidth() / 2;
        double headTop = topPad + 0.06 * frameH * scale;
        double faceY = topPad + (mouth != null ? mouth.y() : 0.17) * frameH * scale;
        gc.save();
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(FX_EMOJI_FONT);
        for (Fx fx : new ArrayList<>(fxs)) {
            double t = clamp01(fx.ageMs / fx.durMs);
            double fade = Math.min(1, Math.min(t * 4, (1 - t) * 4));
            java.util.Random r = new java.util.Random(fx.seed);
            switch (fx.type) {
                case HEARTS -> {
                    gc.setFill(BUBBLE_HEART);
                    for (int i = 0; i < 3; i++) {
                        double ph = r.nextDouble();
                        double x = cx + (i - 1) * 15 + Math.sin((t + ph) * 6) * 6;
                        double y = headTop - 6 - t * 46 - i * 6;
                        gc.setGlobalAlpha(fade);
                        gc.fillText("♥", x, y);
                    }
                }
                case SPARKLES -> {
                    gc.setFill(Color.web("#FFE27A"));
                    for (int i = 0; i < 4; i++) {
                        double a = i * (Math.PI / 2) + fx.ageMs / 200.0;
                        double x = cx + Math.cos(a) * (18 + i);
                        double y = headTop + 6 + Math.sin(a) * 12;
                        double tw = 0.5 + 0.5 * Math.sin(fx.ageMs / 90.0 + i);
                        gc.setGlobalAlpha(fade * tw);
                        gc.fillText("✦", x, y);
                    }
                }
                case NOTE -> {
                    gc.setFill(Color.web("#C98BDA"));
                    gc.setGlobalAlpha(fade);
                    gc.fillText("♪", cx + 16 + Math.sin(t * 6) * 5, headTop - t * 40);
                }
                case TEAR -> {
                    gc.setGlobalAlpha(fade);
                    gc.setFill(Color.web("#7FC5FF"));
                    double x = cx + 9;
                    double y = faceY + t * 26;
                    gc.fillOval(x - 2.5, y - 3.5, 5, 7);
                }
                case ANGER -> {
                    double pop = Math.sin(Math.min(1, t * 2) * Math.PI / 2);
                    gc.setGlobalAlpha(fade);
                    gc.setFont(Font.font("Segoe UI Emoji", 16 + pop * 8));
                    gc.setFill(Color.web("#FF5A6E"));
                    gc.fillText("💢", cx + 22, headTop + 4);
                    gc.setFont(FX_EMOJI_FONT);
                }
                case QUESTION -> {
                    gc.setGlobalAlpha(fade);
                    gc.setFill(Color.web("#9E5C79"));
                    gc.fillText("?", cx + Math.sin(fx.ageMs / 160.0) * 5, headTop - 14);
                }
                case EXCLAIM -> {
                    double pop = Math.sin(Math.min(1, t * 3) * Math.PI / 2);
                    gc.setGlobalAlpha(fade);
                    gc.setFill(Color.web("#FF7BA6"));
                    gc.fillText("!", cx, headTop - 10 - pop * 8);
                }
                case ZZZ -> {
                    gc.setFill(Color.web("#B9A7D6"));
                    for (int i = 0; i < 3; i++) {
                        double seg = clamp01(t * 3 - i * 0.5);
                        if (seg <= 0) {
                            continue;
                        }
                        gc.setGlobalAlpha(Math.min(1, (1 - seg) * 2) * fade);
                        gc.setFont(Font.font("Segoe UI Emoji", 12 + i * 4));
                        gc.fillText("z", cx + 16 + i * 10 + seg * 6, headTop - 4 - seg * 22 - i * 6);
                    }
                    gc.setFont(FX_EMOJI_FONT);
                }
            }
        }
        gc.setGlobalAlpha(1);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);
        gc.restore();
    }

    private void drawBubble(GraphicsContext gc) {
        double t = elapsedMs - bubbleShownAtMs;
        double appear = clamp01(t / BUBBLE_POP_MS);
        double disappear = clamp01((bubbleHideAtMs - elapsedMs) / BUBBLE_HIDE_MS);
        double pop = easeOutBack(appear);
        double scale = (0.78 + 0.22 * pop) * (0.9 + 0.1 * disappear);
        double alpha = Math.min(appear, disappear);
        double wobble = Math.sin(elapsedMs / 1000.0 * 2 * Math.PI * 0.5) * 0.8;

        int reveal = (int) Math.floor(t / 1000.0 * BUBBLE_TYPE_CPS);
        List<String> shown = reveal >= bubbleTotalChars ? bubbleLines : revealLines(bubbleLines, reveal);
        if (shown.isEmpty()) {
            return;
        }

        double pivotX = canvas.getWidth() / 2;
        double pivotY = BUBBLE_PAD_PX * 0.5;
        gc.save();
        gc.setGlobalAlpha(Math.max(0, alpha));
        gc.translate(pivotX, pivotY);
        gc.rotate(wobble);
        gc.scale(scale, scale);
        gc.translate(-pivotX, -pivotY);
        paintBubble(gc, canvas.getWidth(), shown);
        gc.restore();
        gc.setGlobalAlpha(1);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    private static double easeOutBack(double x) {
        double c1 = 1.70158, c3 = c1 + 1;
        double p = x - 1;
        return 1 + c3 * p * p * p + c1 * p * p;
    }

    private static List<String> revealLines(List<String> lines, int n) {
        List<String> out = new ArrayList<>();
        int left = n;
        for (String line : lines) {
            if (left <= 0) {
                break;
            }
            if (line.length() <= left) {
                out.add(line);
                left -= line.length();
            } else {
                out.add(line.substring(0, left));
                left = 0;
            }
        }
        return out;
    }

    public static void paintBubble(GraphicsContext gc, double canvasWidth, List<String> bubbleLines) {
        int n = bubbleLines.size();
        double maxLineW = 0;
        for (String line : bubbleLines) {
            maxLineW = Math.max(maxLineW, textWidth(line));
        }
        double boxW = Math.min(canvasWidth - 2 * BUBBLE_MARGIN, maxLineW + 2 * BUBBLE_INNER);
        double boxH = n * BUBBLE_LINE_H + 2 * BUBBLE_INNER;
        double x = (canvasWidth - boxW) / 2;
        double y = Math.max(6, BUBBLE_PAD_PX - boxH - 16);
        double cx = canvasWidth / 2;

        double[][] dots = {{cx, y + boxH + 8, 8}, {cx - 5, y + boxH + 21, 5.5}, {cx - 9, y + boxH + 31, 3.5}};
        for (double[] d : dots) {
            gc.setFill(BUBBLE_SHADOW);
            gc.fillOval(d[0] - d[2] + 1, d[1] - d[2] + 2, d[2] * 2, d[2] * 2);
            gc.setFill(BUBBLE_BG);
            gc.fillOval(d[0] - d[2], d[1] - d[2], d[2] * 2, d[2] * 2);
            gc.setStroke(BUBBLE_BORDER);
            gc.setLineWidth(2);
            gc.strokeOval(d[0] - d[2], d[1] - d[2], d[2] * 2, d[2] * 2);
        }

        gc.setFill(BUBBLE_SHADOW);
        gc.fillRoundRect(x + 3, y + 5, boxW, boxH, BUBBLE_RADIUS, BUBBLE_RADIUS);
        gc.setFill(BUBBLE_BG);
        gc.fillRoundRect(x, y, boxW, boxH, BUBBLE_RADIUS, BUBBLE_RADIUS);
        gc.setStroke(BUBBLE_BORDER);
        gc.setLineWidth(2.5);
        gc.strokeRoundRect(x, y, boxW, boxH, BUBBLE_RADIUS, BUBBLE_RADIUS);

        gc.setFont(BUBBLE_HEART_FONT);
        gc.setFill(BUBBLE_HEART);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("♥", x + 15, y + 4);
        gc.fillText("♥", x + boxW - 15, y + 4);

        gc.setFill(BUBBLE_TEXT);
        gc.setFont(BUBBLE_FONT);
        gc.setTextBaseline(VPos.TOP);
        for (int i = 0; i < n; i++) {
            gc.fillText(bubbleLines.get(i), cx, y + BUBBLE_INNER + i * BUBBLE_LINE_H);
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BASELINE);
    }
}
