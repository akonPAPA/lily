package os.companion.ai;

public enum Gesture {
    NONE,
    WAVE,
    NOD,
    SHAKE_HEAD,
    SHRUG,
    BOUNCE,
    LOOK_AWAY,
    SIT;

    public static Gesture fromOrNone(String raw) {
        if (raw == null) {
            return NONE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
