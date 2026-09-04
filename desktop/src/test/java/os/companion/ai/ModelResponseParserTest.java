package os.companion.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelResponseParserTest {

    private final ModelResponseParser parser = new ModelResponseParser();

    @Test
    void parsesCleanJson() {
        CompanionResponse r = parser.parse(
                "{\"speech\":\"Oi, you're finally awake.\",\"emotion\":\"AMUSED\",\"gesture\":\"WAVE\",\"energy\":0.72}");
        assertThat(r.speech()).isEqualTo("Oi, you're finally awake.");
        assertThat(r.emotion()).isEqualTo(Emotion.AMUSED);
        assertThat(r.gesture()).isEqualTo(Gesture.WAVE);
        assertThat(r.energy()).isEqualTo(0.72);
    }

    @Test
    void extractsJsonFromCodeFenceAndProse() {
        String raw = "Sure! Here you go:\n```json\n{\"speech\":\"Hey\",\"emotion\":\"HAPPY\",\"gesture\":\"NOD\",\"energy\":0.5}\n```";
        CompanionResponse r = parser.parse(raw);
        assertThat(r.speech()).isEqualTo("Hey");
        assertThat(r.emotion()).isEqualTo(Emotion.HAPPY);
        assertThat(r.gesture()).isEqualTo(Gesture.NOD);
    }

    @Test
    void unknownEnumsFallBackToSafeDefaults() {
        CompanionResponse r = parser.parse(
                "{\"speech\":\"hm\",\"emotion\":\"MELANCHOLY\",\"gesture\":\"BACKFLIP\",\"energy\":5}");
        assertThat(r.emotion()).isEqualTo(Emotion.NEUTRAL);
        assertThat(r.gesture()).isEqualTo(Gesture.NONE);
        assertThat(r.energy()).isEqualTo(1.0);
    }

    @Test
    void bracesInsideStringsDoNotBreakExtraction() {
        CompanionResponse r = parser.parse(
                "{\"speech\":\"use {curly} braces\",\"emotion\":\"NEUTRAL\",\"gesture\":\"NONE\",\"energy\":0.4}");
        assertThat(r.speech()).isEqualTo("use {curly} braces");
    }

    @Test
    void nonJsonFallsBackToSpeakingRawText() {
        CompanionResponse r = parser.parse("Just a plain sentence.");
        assertThat(r.speech()).isEqualTo("Just a plain sentence.");
        assertThat(r.emotion()).isEqualTo(Emotion.NEUTRAL);
        assertThat(r.gesture()).isEqualTo(Gesture.NONE);
    }

    @Test
    void blankInputYieldsEmptySafeResponse() {
        CompanionResponse r = parser.parse("   ");
        assertThat(r.speech()).isEmpty();
        assertThat(r.emotion()).isEqualTo(Emotion.NEUTRAL);
    }
}
