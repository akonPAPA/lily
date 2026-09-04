package os.companion.app;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Consumer<Object>>> subscribers =
            new ConcurrentHashMap<>();

    public interface Event { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Event> void subscribe(Class<T> type, Consumer<T> handler) {
        subscribers
                .computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                .add((Consumer<Object>) (Consumer) handler);
    }

    public void publish(Event event) {
        List<Consumer<Object>> handlers = subscribers.get(event.getClass());
        if (handlers == null) {
            return;
        }
        for (Consumer<Object> handler : handlers) {
            handler.accept(event);
        }
    }
}
