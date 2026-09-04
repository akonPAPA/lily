package os.companion.character.animation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpriteAnimatorTest {

    private static FrameAtlas atlas() {

        return FrameAtlas.of(32, 32, "sprites.png", Map.of(
                "walk", new FrameAtlas.Action("walk", 1, 4, 10, true),
                "wave", new FrameAtlas.Action("wave", 2, 3, 10, false)));
    }

    @Test
    void advancesFrameAfterFrameDuration() {

        SpriteAnimator a = new SpriteAnimator(atlas(), "walk");

        a.update(100);

        assertThat(a.currentFrame()).isEqualTo(1);
    }

    @Test
    void loopingActionWrapsBackToZero() {
        SpriteAnimator a = new SpriteAnimator(atlas(), "walk");
        a.update(400);
        assertThat(a.currentFrame()).isEqualTo(0);
        assertThat(a.isFinished()).isFalse();
    }

    @Test
    void nonLoopingActionStopsOnLastFrameAndFinishes() {
        SpriteAnimator a = new SpriteAnimator(atlas(), "wave");
        a.update(1000);
        assertThat(a.currentFrame()).isEqualTo(2);
        assertThat(a.isFinished()).isTrue();
    }

    @Test
    void playUnknownActionThrows() {
        SpriteAnimator a = new SpriteAnimator(atlas(), "walk");
        assertThatThrownBy(() -> a.play("nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
