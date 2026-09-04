package os.companion.ai;

public enum Emotion {
    NEUTRAL,
    HAPPY,
    SAD,
    ANGRY,
    AMUSED,
    CONFUSED,
    EXCITED,
    TIRED;

    public static Emotion fromOrNeutral(String raw) {
        if (raw == null) {
            return NEUTRAL;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEUTRAL;
        }
    }
}
