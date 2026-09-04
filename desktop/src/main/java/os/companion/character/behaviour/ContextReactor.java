package os.companion.character.behaviour;

import os.companion.character.rendering.CharacterOverlay;
import os.companion.nativeplatform.PlatformBridge;

public final class ContextReactor {

    private enum Reaction {
        JEALOUS("jealous", "angry"),
        SHY("shy", "surprised"),
        LOOKING("looking", "sit_on_edge"),
        GAMING("happy", "happy"),
        CODING("looking", "nod");

        final String preferred;
        final String placeholder;
        Reaction(String preferred, String placeholder) {
            this.preferred = preferred;
            this.placeholder = placeholder;
        }
    }

    private static final Object[][] KEYWORDS = {
        {Reaction.JEALOUS, new String[]{"discord", "whatsapp", "telegram", "messenger",
                "tinder", "bumble", "dating", "match.com"}},
        {Reaction.SHY,     new String[]{"youtube", "netflix", "twitch", "tiktok", "vlc",
                "movie", " - video"}},
        {Reaction.CODING,  new String[]{"visual studio", "vs code", "intellij", "pycharm",
                "terminal", "powershell", "cmd.exe", ".java", ".py", ".ts", "git"}},
        {Reaction.GAMING,  new String[]{"steam", "minecraft", "league of legends", "valorant",
                "game", "epic games"}},
        {Reaction.LOOKING, new String[]{"chrome", "firefox", "edge", "wikipedia", "docs",
                ".pdf", "word", "notion", "reddit"}},
    };

    private static final long REACT_COOLDOWN_MS = 25_000;
    private static final double POLL_MS = 4000;
    private static final double PERCH_CHANCE = 0.15;

    private final PlatformBridge platform;
    private final MovementController movement;
    private boolean enabled = true;

    private double accumMs = 0;
    private Reaction lastReaction = null;
    private long lastReactAt = 0;
    private final java.util.Random rnd = new java.util.Random();

    public ContextReactor(PlatformBridge platform, MovementController movement) {
        this.platform = platform;
        this.movement = movement;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean maybePoll(double deltaMs, CharacterOverlay overlay) {
        if (!enabled || platform == null || !platform.isSupported()) {
            return false;
        }
        accumMs += deltaMs;
        if (accumMs < POLL_MS || overlay.isSpeaking()) {
            return false;
        }
        accumMs = 0;
        Reaction r = classify(platform.foregroundWindowTitle());
        if (r == null || r == lastReaction) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastReactAt < REACT_COOLDOWN_MS) {
            return false;
        }
        lastReaction = r;
        lastReactAt = now;
        react(overlay, r);
        return true;
    }

    private Reaction classify(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String t = title.toLowerCase();
        if (t.contains("companionos")) {
            return null;
        }
        for (Object[] rule : KEYWORDS) {
            for (String kw : (String[]) rule[1]) {
                if (t.contains(kw)) {
                    return (Reaction) rule[0];
                }
            }
        }
        return null;
    }

    private void react(CharacterOverlay overlay, Reaction r) {
        String action = overlay.hasAction(r.preferred) ? r.preferred
                : overlay.hasAction(r.placeholder) ? r.placeholder : null;
        if (action != null) {
            overlay.play(action);
        }
        if ((r == Reaction.LOOKING || r == Reaction.CODING) && rnd.nextDouble() < PERCH_CHANCE) {
            perchOnForeground(overlay);
        }
    }

    public void perchOnForeground(CharacterOverlay overlay) {
        int[] rect = platform.foregroundWindowRect();
        if (rect == null || movement == null) {
            return;
        }
        double tx = rect[0] + 30;
        double ty = Math.max(0, rect[1] - overlay.renderHeight() * 0.55);
        movement.setEnabled(false);
        overlay.moveToward(tx, ty, 220);

        javafx.animation.PauseTransition arrive = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(1200));
        arrive.setOnFinished(e -> {
            if (overlay.hasAction("looking")) {
                overlay.play("looking");
            } else if (overlay.hasAction("sit_on_edge")) {
                overlay.play("sit_on_edge");
            }
        });
        arrive.play();

        javafx.animation.PauseTransition release = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(6000));
        release.setOnFinished(e -> movement.setEnabled(true));
        release.play();
    }
}
