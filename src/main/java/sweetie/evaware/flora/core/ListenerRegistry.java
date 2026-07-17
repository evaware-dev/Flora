package sweetie.evaware.flora.core;

import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class ListenerRegistry<T> {
    private static final Subscription EMPTY_SUBSCRIPTION = () -> {
    };

    private final List<Registration<T>> registrations = new ArrayList<>();
    private volatile ListenerSnapshot<T> snapshot = ListenerSnapshot.empty();
    private int synchronousCount;
    private int asynchronousCount;
    private int parallelCount;

    ListenerSnapshot<T> snapshot() {
        return snapshot;
    }

    synchronized Subscription add(Listener<T> listener) {
        Objects.requireNonNull(listener, "listener");
        Registration<T> registration = new Registration<>(this, listener);
        registrations.add(insertionIndex(listener.priority()), registration);
        incrementCount(listener.mode());
        rebuildSnapshot();
        return registration;
    }

    synchronized Subscription addAll(List<? extends Listener<T>> listeners) {
        Objects.requireNonNull(listeners, "listeners");
        List<? extends Listener<T>> definitions = List.copyOf(listeners);
        if (definitions.isEmpty()) {
            return EMPTY_SUBSCRIPTION;
        }

        List<Registration<T>> added = new ArrayList<>(definitions.size());
        for (Listener<T> listener : definitions) {
            Registration<T> registration = new Registration<>(this, listener);
            registrations.add(insertionIndex(listener.priority()), registration);
            incrementCount(listener.mode());
            added.add(registration);
        }
        rebuildSnapshot();
        return new BatchRegistration<>(this, added);
    }

    private synchronized void remove(Registration<T> registration) {
        if (!registration.active) {
            return;
        }
        registration.active = false;
        if (registrations.remove(registration)) {
            decrementCount(registration.mode);
            rebuildSnapshot();
        }
    }

    private synchronized void removeAll(List<Registration<T>> removed) {
        boolean changed = false;
        for (Registration<T> registration : removed) {
            if (!registration.active) {
                continue;
            }
            registration.active = false;
            if (registrations.remove(registration)) {
                decrementCount(registration.mode);
                changed = true;
            }
        }
        if (changed) {
            rebuildSnapshot();
        }
    }

    private int insertionIndex(int priority) {
        int low = 0;
        int high = registrations.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (registrations.get(middle).priority >= priority) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private void rebuildSnapshot() {
        Consumer<T>[] synchronous = listenersArray(synchronousCount);
        Consumer<T>[] asynchronous = listenersArray(asynchronousCount);
        Consumer<T>[] parallel = listenersArray(parallelCount);
        int synchronousIndex = 0;
        int asynchronousIndex = 0;
        int parallelIndex = 0;

        for (Registration<T> registration : registrations) {
            switch (registration.mode) {
                case SYNC -> synchronous[synchronousIndex++] = registration.callback;
                case ASYNC -> asynchronous[asynchronousIndex++] = registration.callback;
                case ASYNC_PARALLEL -> parallel[parallelIndex++] = registration.callback;
            }
        }
        snapshot = new ListenerSnapshot<>(synchronous, asynchronous, parallel);
    }

    private void incrementCount(DispatchMode mode) {
        switch (mode) {
            case SYNC -> synchronousCount++;
            case ASYNC -> asynchronousCount++;
            case ASYNC_PARALLEL -> parallelCount++;
        }
    }

    private void decrementCount(DispatchMode mode) {
        switch (mode) {
            case SYNC -> synchronousCount--;
            case ASYNC -> asynchronousCount--;
            case ASYNC_PARALLEL -> parallelCount--;
        }
    }

    @SuppressWarnings("unchecked")
    private Consumer<T>[] listenersArray(int size) {
        return size == 0 ? ListenerSnapshot.emptyListeners() : (Consumer<T>[]) new Consumer<?>[size];
    }

    private static final class Registration<T> implements Subscription {
        private final ListenerRegistry<T> owner;
        private final int priority;
        private final Consumer<T> callback;
        private final DispatchMode mode;
        private boolean active = true;

        private Registration(ListenerRegistry<T> owner, Listener<T> definition) {
            this.owner = owner;
            this.priority = definition.priority();
            this.callback = definition.callback();
            this.mode = definition.mode();
        }

        @Override
        public void unsubscribe() {
            owner.remove(this);
        }
    }

    private static final class BatchRegistration<T> implements Subscription {
        private final ListenerRegistry<T> owner;
        private final List<Registration<T>> registrations;

        private BatchRegistration(ListenerRegistry<T> owner, List<Registration<T>> registrations) {
            this.owner = owner;
            this.registrations = registrations;
        }

        @Override
        public void unsubscribe() {
            owner.removeAll(registrations);
        }
    }
}
