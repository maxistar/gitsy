package me.maxistar.gitsync;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private static EventBus instance;
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<EventListener>> subscribers = new ConcurrentHashMap<>();

    public static synchronized EventBus getInstance() {
        if (instance == null) {
            instance = new EventBus();
        }
        return instance;
    }

    public <T> void subscribe(Class<T> eventType, EventListener<T> listener) {
        this.subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public <T> void unsubscribe(Class<T> eventType, EventListener<T> listener) {
        if (subscribers.containsKey(eventType)) {
            subscribers.get(eventType).remove(listener);
        }
    }

    public <T> void post(T event) {
        Class<?> eventType = event.getClass();
        if (subscribers.containsKey(eventType)) {
            for (EventListener listener : subscribers.get(eventType)) {
                listener.onEvent(event);
            }
        }
    }

    public interface EventListener<T> {
        void onEvent(T event);
    }
}
