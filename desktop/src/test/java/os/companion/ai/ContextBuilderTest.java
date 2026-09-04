package os.companion.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderTest {

    private final ContextBuilder builder = new ContextBuilder();

    @Test
    void firstMessageIsSystemWithPersonaAndContract() {
        List<ChatMessage> msgs = builder.build("Name: Lily\nPlayful.", List.of(), "hi");
        assertThat(msgs.get(0).role()).isEqualTo("system");
        assertThat(msgs.get(0).content())
                .contains("Name: Lily")
                .contains("EMOTION is one of")
                .contains("GESTURE is one of");
    }

    @Test
    void userUtteranceIsLastMessage() {
        List<ChatMessage> msgs = builder.build("persona", List.of(), "should I go outside?");
        ChatMessage last = msgs.get(msgs.size() - 1);
        assertThat(last.role()).isEqualTo("user");
        assertThat(last.content()).isEqualTo("should I go outside?");
    }

    @Test
    void historyIsInterleavedBetweenSystemAndCurrentTurn() {
        ConversationBuffer buf = new ConversationBuffer(4);
        buf.addUser("morning");
        buf.addAssistant("M-morning...");
        List<ChatMessage> msgs = builder.build("persona", buf.recent(), "how are you?");

        assertThat(msgs).hasSize(4);
        assertThat(msgs.get(1).role()).isEqualTo("user");
        assertThat(msgs.get(1).content()).isEqualTo("morning");
        assertThat(msgs.get(2).role()).isEqualTo("assistant");
        assertThat(msgs.get(3).content()).isEqualTo("how are you?");
    }

    @Test
    void bufferKeepsOnlyRecentTurns() {
        ConversationBuffer buf = new ConversationBuffer(1);
        buf.addUser("u1");
        buf.addAssistant("a1");
        buf.addUser("u2");
        buf.addAssistant("a2");
        assertThat(buf.recent()).extracting(ConversationBuffer.Turn::text)
                .containsExactly("u2", "a2");
    }
}
