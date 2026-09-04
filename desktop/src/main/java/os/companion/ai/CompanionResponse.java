package os.companion.ai;

public record CompanionResponse(String speech, Emotion emotion, Gesture gesture, double energy) {

    public CompanionResponse {
        speech = speech == null ? "" : speech;
        emotion = emotion == null ? Emotion.NEUTRAL : emotion;
        gesture = gesture == null ? Gesture.NONE : gesture;
        energy = Math.max(0.0, Math.min(1.0, energy));
    }

    public static CompanionResponse fallback(String speech) {
        return new CompanionResponse(speech, Emotion.NEUTRAL, Gesture.NONE, 0.5);
    }
}
