package com.linkBuddy.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.linkBuddy.entity.RequestStatus;
import com.vaadin.flow.shared.Registration;

/**
 * Simple pub/sub used to push real-time notifications to a user's open UI (MyRequestsView)
 * when AdminRequestService approves or rejects one of their bookmark requests.
 *
 * Views register a listener on attach and deregister on detach; broadcast() runs each
 * listener asynchronously - the listener itself must call ui.access(...) to touch the UI.
 */
@Component
public class RequestBroadcaster {

    private final Executor executor = Executors.newCachedThreadPool();
    private final List<Consumer<BookmarkRequestEvent>> listeners = new CopyOnWriteArrayList<>();

    public Registration register(Consumer<BookmarkRequestEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void broadcast(BookmarkRequestEvent event) {
        for (Consumer<BookmarkRequestEvent> listener : listeners) {
            executor.execute(() -> listener.accept(event));
        }
    }

    public record BookmarkRequestEvent(String username, Long requestId, String title, RequestStatus status) {
    }
}