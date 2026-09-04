package os.companion.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ConversationBuffer {

    public record Turn(Role role, String text) { }

    public enum Role { USER, ASSISTANT }

    private final int maxMessages;
    private final Deque<Turn> turns = new ArrayDeque<>();

    public ConversationBuffer(int maxTurns) {
        this.maxMessages = Math.max(1, maxTurns) * 2;
    }

    public void addUser(String text) {
        add(new Turn(Role.USER, text));
    }

    public void addAssistant(String text) {
        add(new Turn(Role.ASSISTANT, text));
    }

    private void add(Turn turn) {
        turns.addLast(turn);
        while (turns.size() > maxMessages) {
            turns.removeFirst();
        }
    }

    public List<Turn> recent() {
        return new ArrayList<>(turns);
    }

    public void clear() {
        turns.clear();
    }
}
