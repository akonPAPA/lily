package os.companion.ai;

import java.util.ArrayList;
import java.util.List;

public final class ContextBuilder {

    private static final String RUNTIME_RULES = """
            You are the intelligence of an animated desktop companion character.
            You speak out loud through text-to-speech, so keep replies short, natural,
            and conversational — usually one or two sentences. Never describe actions
            you cannot actually perform. You may only reference what the user is doing
            on screen when a "What the user appears to be looking at" note is provided
            below; otherwise do not claim to see their screen.""";

    private static final String OUTPUT_CONTRACT = """
            Respond with ONE JSON object and nothing else, in exactly this shape:
            {"speech": string, "emotion": EMOTION, "gesture": GESTURE, "energy": number 0..1}
            EMOTION is one of: NEUTRAL, HAPPY, SAD, ANGRY, AMUSED, CONFUSED, EXCITED, TIRED.
            GESTURE is one of: NONE, WAVE, NOD, SHAKE_HEAD, SHRUG, BOUNCE, LOOK_AWAY, SIT.
            "speech" is what you say out loud. "energy" is how energetic you sound.""";

    public List<ChatMessage> build(String persona, List<ConversationBuffer.Turn> history, String userText) {
        return build(persona, history, userText, null);
    }

    public List<ChatMessage> build(String persona, List<ConversationBuffer.Turn> history,
                                   String userText, String screenNote) {
        List<ChatMessage> messages = new ArrayList<>();

        String screen = (screenNote == null || screenNote.isBlank()) ? ""
                : "\n\n# What the user appears to be looking at right now "
                  + "(from a quick local glance at the screen)\n" + screenNote.strip();

        String system = RUNTIME_RULES
                + "\n\n# Character persona (authoritative for style and personality)\n"
                + persona
                + screen
                + "\n\n# Output format\n"
                + OUTPUT_CONTRACT;
        messages.add(ChatMessage.system(system));

        if (history != null) {
            for (ConversationBuffer.Turn t : history) {
                if (t.role() == ConversationBuffer.Role.USER) {
                    messages.add(ChatMessage.user(t.text()));
                } else {
                    messages.add(ChatMessage.assistant(t.text()));
                }
            }
        }

        messages.add(ChatMessage.user(userText == null ? "" : userText));
        return messages;
    }
}
