package sweetie.evaware.flora.core;

import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.Config;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.engine.DispatchEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class EventBus<T> {
    private static final Consumer<?>[] EMPTY = new Consumer<?>[0];
    private static final Registration<?>[] EMPTY_REGS = new Registration<?>[0];
    private static final Subscription NOOP_SUBSCRIPTION = () -> {};
    private static final Comparator<Registration<?>> REGISTRATION_ORDER =
            (a, b) -> Integer.compare(b.listener.priority(), a.listener.priority());

    private final Config config;
    private final DispatchEngine dispatchEngine;
    private final Runnable onChange;
    private int asynchronousLane = -1;

    private Registration<T>[] syncRegs = emptyRegs();
    private Registration<T>[] asyncRegs = emptyRegs();
    private Registration<T>[] parallelRegs = emptyRegs();

    private volatile Consumer<T>[] synchronous = emptyArray();
    private volatile Consumer<T>[] asynchronous = emptyArray();
    private volatile Consumer<T>[] parallel = emptyArray();
    private volatile boolean onlySynchronous = true;

    private volatile Consumer<Throwable> exceptionHandler;

    public EventBus() {
        this(Flora.config(), DispatchEngine.defaultEngine(), null);
    }

    public EventBus(Runnable onChange) {
        this(Flora.config(), DispatchEngine.defaultEngine(), onChange);
    }

    public EventBus(DispatchEngine dispatchEngine) {
        this(Flora.config(), dispatchEngine, null);
    }

    public EventBus(DispatchEngine dispatchEngine, Runnable onChange) {
        this(Flora.config(), dispatchEngine, onChange);
    }

    public EventBus(Config config, DispatchEngine dispatchEngine, Runnable onChange) {
        this.config = Objects.requireNonNull(config, "config");
        this.dispatchEngine = Objects.requireNonNull(dispatchEngine, "dispatchEngine");
        this.onChange = onChange;
    }

    public T post(T event) {
        Objects.requireNonNull(event, "event");
        final Consumer<T>[] sync = this.synchronous;
        final int len = sync.length;
        if (len == 0 && onlySynchronous) {
            return event;
        }

        final Predicate<Object> canceller = config.canceller(event.getClass());

        if (len > 0) {
            final Consumer<Throwable> handler = this.exceptionHandler != null
                    ? this.exceptionHandler
                    : config.exceptionHandler();
            SyncDispatcher.dispatch(event, sync, len, canceller, handler);
        }

        if (onlySynchronous || (canceller != null && canceller.test(event))) {
            return event;
        }

        final Consumer<T>[] async = this.asynchronous;
        if (async.length != 0) {
            dispatchEngine.dispatchAsync(asynchronousLane, event, async);
        }
        final Consumer<T>[] par = this.parallel;
        if (par.length != 0) {
            dispatchEngine.dispatchParallel(event, par);
        }
        return event;
    }

    public synchronized Subscription subscribe(Listener<T> listener) {
        Objects.requireNonNull(listener, "listener");
        Registration<T> reg = new Registration<>(this, listener);
        insertSingle(reg);
        notifyChange();
        return reg;
    }

    @SuppressWarnings("unchecked")
    public synchronized Subscription subscribeAll(List<? extends Listener<T>> list) {
        Objects.requireNonNull(list, "list");
        int size = list.size();
        if (size == 0) {
            return NOOP_SUBSCRIPTION;
        }
        if (size == 1) {
            return subscribe(list.get(0));
        }

        Registration<T>[] added = (Registration<T>[]) new Registration<?>[size];
        int syncCount = 0;
        int asyncCount = 0;
        int parallelCount = 0;

        for (int i = 0; i < size; i++) {
            Listener<T> l = Objects.requireNonNull(list.get(i), "listener");
            Registration<T> reg = new Registration<>(this, l);
            added[i] = reg;
            switch (l.mode()) {
                case SYNC -> syncCount++;
                case ASYNC -> asyncCount++;
                case ASYNC_PARALLEL -> parallelCount++;
            }
        }

        if (syncCount == size) {
            mergeMode(DispatchMode.SYNC, added);
        } else if (asyncCount == size) {
            mergeMode(DispatchMode.ASYNC, added);
        } else if (parallelCount == size) {
            mergeMode(DispatchMode.ASYNC_PARALLEL, added);
        } else {
            if (syncCount > 0) mergeMode(DispatchMode.SYNC, filterMode(added, DispatchMode.SYNC, syncCount));
            if (asyncCount > 0) mergeMode(DispatchMode.ASYNC, filterMode(added, DispatchMode.ASYNC, asyncCount));
            if (parallelCount > 0) mergeMode(DispatchMode.ASYNC_PARALLEL, filterMode(added, DispatchMode.ASYNC_PARALLEL, parallelCount));
        }

        notifyChange();
        return new BatchSubscription<>(this, added);
    }

    public Subscription subscribe(Consumer<T> callback) {
        return subscribe(new Listener<>(0, callback, DispatchMode.SYNC));
    }

    public Subscription subscribe(int priority, Consumer<T> callback) {
        return subscribe(new Listener<>(priority, callback, DispatchMode.SYNC));
    }

    public Subscription subscribe(int priority, DispatchMode mode, Consumer<T> callback) {
        return subscribe(new Listener<>(priority, callback, mode));
    }

    @SuppressWarnings("unchecked")
    void removeRegistration(Registration<T> reg) {
        DispatchMode mode = reg.listener.mode();
        Registration<T>[] oldRegs = regsFor(mode);
        int len = oldRegs.length;
        int idx = findRegistrationIndex(oldRegs, len, reg);
        if (idx < 0) return;

        if (len == 1) {
            publishMode(mode, emptyRegs(), emptyArray());
        } else {
            Registration<T>[] newRegs = (Registration<T>[]) new Registration<?>[len - 1];
            Consumer<T>[] oldConsumers = consumersFor(mode);
            Consumer<T>[] newConsumers = (Consumer<T>[]) new Consumer<?>[len - 1];
            if (idx > 0) {
                System.arraycopy(oldRegs, 0, newRegs, 0, idx);
                System.arraycopy(oldConsumers, 0, newConsumers, 0, idx);
            }
            if (idx < len - 1) {
                System.arraycopy(oldRegs, idx + 1, newRegs, idx, len - idx - 1);
                System.arraycopy(oldConsumers, idx + 1, newConsumers, idx, len - idx - 1);
            }
            publishMode(mode, newRegs, newConsumers);
        }
        notifyChange();
    }

    @SuppressWarnings("unchecked")
    private void insertSingle(Registration<T> reg) {
        Listener<T> listener = reg.listener;
        DispatchMode mode = listener.mode();
        Registration<T>[] oldRegs = regsFor(mode);
        int len = oldRegs.length;

        if (len == 0) {
            Registration<T>[] newRegs = (Registration<T>[]) new Registration<?>[]{reg};
            Consumer<T>[] newConsumers = (Consumer<T>[]) new Consumer<?>[]{listener.callback()};
            publishMode(mode, newRegs, newConsumers);
            return;
        }

        int idx = findInsertIndex(oldRegs, len, listener.priority());
        Registration<T>[] newRegs = (Registration<T>[]) new Registration<?>[len + 1];
        Consumer<T>[] oldConsumers = consumersFor(mode);
        Consumer<T>[] newConsumers = (Consumer<T>[]) new Consumer<?>[len + 1];

        if (idx > 0) {
            System.arraycopy(oldRegs, 0, newRegs, 0, idx);
            System.arraycopy(oldConsumers, 0, newConsumers, 0, idx);
        }
        newRegs[idx] = reg;
        newConsumers[idx] = listener.callback();
        if (idx < len) {
            System.arraycopy(oldRegs, idx, newRegs, idx + 1, len - idx);
            System.arraycopy(oldConsumers, idx, newConsumers, idx + 1, len - idx);
        }
        publishMode(mode, newRegs, newConsumers);
    }

    @SuppressWarnings("unchecked")
    private void mergeMode(DispatchMode mode, Registration<T>[] incoming) {
        if (incoming.length > 1 && !isDescending(incoming)) {
            Arrays.sort(incoming, REGISTRATION_ORDER);
        }
        Registration<T>[] oldRegs = regsFor(mode);
        int oldLen = oldRegs.length;
        int incLen = incoming.length;

        if (oldLen == 0) {
            Consumer<T>[] newConsumers = (Consumer<T>[]) new Consumer<?>[incLen];
            for (int i = 0; i < incLen; i++) {
                newConsumers[i] = incoming[i].listener.callback();
            }
            publishMode(mode, incoming, newConsumers);
            return;
        }

        int total = oldLen + incLen;
        Registration<T>[] newRegs = (Registration<T>[]) new Registration<?>[total];
        Consumer<T>[] oldConsumers = consumersFor(mode);
        Consumer<T>[] newConsumers = (Consumer<T>[]) new Consumer<?>[total];

        int i = 0, j = 0, k = 0;
        while (i < oldLen && j < incLen) {
            if (oldRegs[i].listener.priority() >= incoming[j].listener.priority()) {
                newRegs[k] = oldRegs[i];
                newConsumers[k] = oldConsumers[i];
                i++;
            } else {
                Registration<T> r = incoming[j++];
                newRegs[k] = r;
                newConsumers[k] = r.listener.callback();
            }
            k++;
        }
        if (i < oldLen) {
            int rem = oldLen - i;
            System.arraycopy(oldRegs, i, newRegs, k, rem);
            System.arraycopy(oldConsumers, i, newConsumers, k, rem);
        } else if (j < incLen) {
            while (j < incLen) {
                Registration<T> r = incoming[j++];
                newRegs[k] = r;
                newConsumers[k] = r.listener.callback();
                k++;
            }
        }

        publishMode(mode, newRegs, newConsumers);
    }

    @SuppressWarnings("unchecked")
    private void compactMode(DispatchMode mode, int removedCount) {
        Registration<T>[] oldRegs = regsFor(mode);
        int oldLen = oldRegs.length;
        int newLen = oldLen - removedCount;
        if (newLen <= 0) {
            publishMode(mode, emptyRegs(), emptyArray());
            return;
        }

        Registration<T>[] newRegs = (Registration<T>[]) new Registration<?>[newLen];
        Consumer<T>[] oldConsumers = consumersFor(mode);
        Consumer<T>[] newConsumers = (Consumer<T>[]) new Consumer<?>[newLen];
        int k = 0;
        for (int i = 0; i < oldLen; i++) {
            Registration<T> r = oldRegs[i];
            if (r.active) {
                newRegs[k] = r;
                newConsumers[k] = oldConsumers[i];
                k++;
            }
        }
        if (k != newLen) {
            newRegs = Arrays.copyOf(newRegs, k);
            newConsumers = Arrays.copyOf(newConsumers, k);
        }
        publishMode(mode, newRegs, newConsumers);
    }

    @SuppressWarnings("unchecked")
    private Registration<T>[] filterMode(Registration<T>[] all, DispatchMode mode, int count) {
        Registration<T>[] result = (Registration<T>[]) new Registration<?>[count];
        int k = 0;
        for (Registration<T> r : all) {
            if (r.listener.mode() == mode) {
                result[k++] = r;
            }
        }
        return result;
    }

    private static <T> boolean isDescending(Registration<T>[] regs) {
        int prev = regs[0].listener.priority();
        for (int i = 1; i < regs.length; i++) {
            int curr = regs[i].listener.priority();
            if (curr > prev) return false;
            prev = curr;
        }
        return true;
    }

    private static <T> int findInsertIndex(Registration<T>[] regs, int len, int priority) {
        if (priority <= regs[len - 1].listener.priority()) {
            return len;
        }
        if (priority > regs[0].listener.priority()) {
            return 0;
        }
        int low = 0;
        int high = len;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (regs[mid].listener.priority() >= priority) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static <T> int findRegistrationIndex(Registration<T>[] regs, int len, Registration<T> target) {
        if (len <= 16) {
            for (int i = 0; i < len; i++) {
                if (regs[i] == target) return i;
            }
            return -1;
        }
        int priority = target.listener.priority();
        int low = 0;
        int high = len;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (regs[mid].listener.priority() > priority) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        for (int i = low; i < len && regs[i].listener.priority() == priority; i++) {
            if (regs[i] == target) return i;
        }
        return -1;
    }

    private Registration<T>[] regsFor(DispatchMode mode) {
        return switch (mode) {
            case SYNC -> syncRegs;
            case ASYNC -> asyncRegs;
            case ASYNC_PARALLEL -> parallelRegs;
        };
    }

    private Consumer<T>[] consumersFor(DispatchMode mode) {
        return switch (mode) {
            case SYNC -> synchronous;
            case ASYNC -> asynchronous;
            case ASYNC_PARALLEL -> parallel;
        };
    }

    private void publishMode(DispatchMode mode, Registration<T>[] regs, Consumer<T>[] consumers) {
        switch (mode) {
            case SYNC -> {
                this.syncRegs = regs;
                this.synchronous = consumers;
            }
            case ASYNC -> {
                if (consumers.length > 0 && this.asynchronousLane < 0) {
                    this.asynchronousLane = dispatchEngine.acquireAsyncLane();
                }
                this.asyncRegs = regs;
                this.asynchronous = consumers;
                this.onlySynchronous = consumers.length == 0 && parallelRegs.length == 0;
            }
            case ASYNC_PARALLEL -> {
                this.parallelRegs = regs;
                this.parallel = consumers;
                this.onlySynchronous = asyncRegs.length == 0 && consumers.length == 0;
            }
        }
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    public synchronized int listenerCount() {
        return syncRegs.length + asyncRegs.length + parallelRegs.length;
    }

    @SuppressWarnings("unchecked")
    public synchronized void copyListenersTo(List<Listener<Object>> destination) {
        for (Registration<T> r : syncRegs) destination.add((Listener<Object>) r.listener);
        for (Registration<T> r : asyncRegs) destination.add((Listener<Object>) r.listener);
        for (Registration<T> r : parallelRegs) destination.add((Listener<Object>) r.listener);
    }

    public synchronized List<Listener<T>> getListeners() {
        int total = syncRegs.length + asyncRegs.length + parallelRegs.length;
        if (total == 0) return List.of();
        List<Listener<T>> list = new ArrayList<>(total);
        int activeModes = 0;
        if (syncRegs.length > 0) {
            activeModes++;
            for (Registration<T> r : syncRegs) list.add(r.listener);
        }
        if (asyncRegs.length > 0) {
            activeModes++;
            for (Registration<T> r : asyncRegs) list.add(r.listener);
        }
        if (parallelRegs.length > 0) {
            activeModes++;
            for (Registration<T> r : parallelRegs) list.add(r.listener);
        }
        if (activeModes > 1) {
            list.sort(Listener::compareTo);
        }
        return list;
    }

    public void setExceptionHandler(Consumer<Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        notifyChange();
    }

    public Consumer<Throwable> getExceptionHandler() {
        return exceptionHandler;
    }

    @SuppressWarnings("unchecked")
    private static <T> Consumer<T>[] emptyArray() {
        return (Consumer<T>[]) EMPTY;
    }

    @SuppressWarnings("unchecked")
    private static <T> Registration<T>[] emptyRegs() {
        return (Registration<T>[]) EMPTY_REGS;
    }

    private static final class BatchSubscription<T> implements Subscription {
        private final EventBus<T> bus;
        private final Registration<T>[] added;
        private boolean active = true;

        BatchSubscription(EventBus<T> bus, Registration<T>[] added) {
            this.bus = bus;
            this.added = added;
        }

        @Override
        public void unsubscribe() {
            synchronized (bus) {
                if (!active) return;
                active = false;
                int syncRemoved = 0;
                int asyncRemoved = 0;
                int parallelRemoved = 0;
                for (Registration<T> reg : added) {
                    if (reg.markInactive()) {
                        switch (reg.listener.mode()) {
                            case SYNC -> syncRemoved++;
                            case ASYNC -> asyncRemoved++;
                            case ASYNC_PARALLEL -> parallelRemoved++;
                        }
                    }
                }
                boolean changed = false;
                if (syncRemoved > 0) {
                    bus.compactMode(DispatchMode.SYNC, syncRemoved);
                    changed = true;
                }
                if (asyncRemoved > 0) {
                    bus.compactMode(DispatchMode.ASYNC, asyncRemoved);
                    changed = true;
                }
                if (parallelRemoved > 0) {
                    bus.compactMode(DispatchMode.ASYNC_PARALLEL, parallelRemoved);
                    changed = true;
                }
                if (changed) {
                    bus.notifyChange();
                }
            }
        }
    }
}
