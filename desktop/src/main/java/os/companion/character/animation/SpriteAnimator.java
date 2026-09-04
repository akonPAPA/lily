package os.companion.character.animation;

public final class SpriteAnimator {

    private final FrameAtlas atlas;

    private FrameAtlas.Action current;
    private double accumulatorMs;
    private int frameIndex;
    private boolean finished;

    public SpriteAnimator(FrameAtlas atlas, String initialAction) {
        this.atlas = atlas;
        play(initialAction);
    }

    public void play(String actionName) {
        FrameAtlas.Action next = atlas.action(actionName);
        if (next == null) {
            throw new IllegalArgumentException("unknown action: " + actionName);
        }
        if (current != null && current.name().equals(actionName) && current.loop() && !finished) {
            return;
        }
        this.current = next;
        this.accumulatorMs = 0;
        this.frameIndex = 0;
        this.finished = false;
    }

    public void update(double deltaMs) {
        if (finished) {
            return;
        }
        accumulatorMs += deltaMs;
        double frameMs = current.frameDurationMs();
        while (accumulatorMs >= frameMs) {
            accumulatorMs -= frameMs;
            frameIndex++;
            if (frameIndex >= current.frames()) {
                if (current.loop()) {
                    frameIndex = 0;
                } else {
                    frameIndex = current.frames() - 1;
                    finished = true;
                    break;
                }
            }
        }
    }

    public FrameAtlas.Action currentAction() {
        return current;
    }

    public int currentFrame() {
        return frameIndex;
    }

    public boolean isFinished() {
        return finished;
    }
}
