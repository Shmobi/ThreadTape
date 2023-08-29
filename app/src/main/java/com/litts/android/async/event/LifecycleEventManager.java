package com.litts.android.async.event;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches fired events to suitable registered {@link Consumer}s with an active {@link Lifecycle}
 */
public class LifecycleEventManager {

    private final Handler eventHandler;
    private final Map<Class<?>, List<Observer<?>>> observerRegistry = new HashMap<>();

    /**
     * Creates a new instance without any enqueued events or registered {@link Consumer}s
     */
    public LifecycleEventManager() {
        this(new Handler(Looper.getMainLooper()));
    }

    /**
     * Required for testing, allows for a mocked {@link Handler} to control callback synchronisation
     *
     * @param handler the desired handler to use for calling {@link Consumer}s. May not be null
     */
    LifecycleEventManager(@NonNull Handler handler) {
        this.eventHandler = handler;
    }

    /**
     * Convenience method for registering a {@link Consumer} that will also receive events that are of the subtype of the desired event type.
     *
     * @param lifecycleOwner The {@link LifecycleOwner} that the Consumer will be bound to. May not be null
     * @param eventType      The type of event the the Consumer will receive, including any subtypes. May not be null
     * @param consumer       The {@link Consumer} that will receive the events. May not be null
     * @param <E>            The event type that is supported by the {@link Consumer}
     * @return this {@link LifecycleEventManager} for method chaining
     * @see #register(LifecycleOwner, Class, Consumer, boolean)
     */
    @AnyThread
    public synchronized <E> LifecycleEventManager register(@NonNull LifecycleOwner lifecycleOwner, @NonNull Class<? extends E> eventType, @NonNull Consumer<E> consumer) {
        return register(lifecycleOwner, eventType, consumer, true);
    }

    /**
     * Registers the provided {@link Consumer} for the desired event type and binds it to the provided {@link LifecycleOwner}. Registering a Consumer for the same type and owner
     * multiple times will replace the previous Consumer with the new one.<br>
     * If desired, the Consumer can be restricted to only receive events that are direct instances of the provided event type, or to also receive events of any subtype.<br>
     * If the {@link Lifecycle} has reached {@link Lifecycle.State#DESTROYED} the Consumer will not be registered.<br>
     * Once the {@link Lifecycle} of the Consumer reaches {@link Lifecycle.State#DESTROYED} it will automatically be unregistered.
     *
     * @param lifecycleOwner  The {@link LifecycleOwner} that the Consumer will be bound to. May not be null
     * @param eventType       The type of event the the Consumer will receive, including any subtypes. May not be null
     * @param consumer        The {@link Consumer} that will receive the events. May not be null
     * @param receiveSubtypes Whether or not the Consumer should also receive events that are subtypes of the registered event type
     * @param <E>             The event type that is supported by the {@link Consumer}
     * @return this {@link LifecycleEventManager} for method chaining
     */
    @AnyThread
    public synchronized <E> LifecycleEventManager register(@NonNull LifecycleOwner lifecycleOwner, @NonNull Class<? extends E> eventType, @NonNull Consumer<E> consumer, boolean receiveSubtypes) {
        Observer<E> observer = new Observer<>(lifecycleOwner, consumer, receiveSubtypes);
        synchronized (observer) {
            if (observer.currentState != Lifecycle.State.DESTROYED) {
                List<Observer<?>> observers = observerRegistry.computeIfAbsent(eventType, entry -> new ArrayList<>());
                observers.replaceAll(registeredObserver -> registeredObserver.lifecycleOwner == observer.lifecycleOwner ? observer : registeredObserver);
            }
        }
        return this;
    }

    /**
     * Unregisters the provided {@link Consumer} for a specific event type it has previously been registered for
     *
     * @param eventType The even type that the Consumer has been registered for. May not be null
     * @param consumer  The {@link Consumer} that should be unregistered. May not be null
     * @param <E>       The event type that is supported by the {@link Consumer}
     * @return this {@link LifecycleEventManager} for method chaining
     */
    @AnyThread
    public synchronized <E> LifecycleEventManager unregister(@NonNull Class<? extends E> eventType, @NonNull Consumer<?> consumer) {
        List<Observer<?>> observers = observerRegistry.get(eventType);
        if (observers != null) {
            observers.removeIf(observer -> observer.consumer == consumer);
            if (observers.isEmpty()) {
                observerRegistry.remove(eventType);
            }
        }
        return this;
    }

    /**
     * Unregisters the provided {@link Consumer} for all event types it has previously been registered for
     *
     * @param consumer The {@link Consumer} that should be unregistered. May not be null
     * @return this {@link LifecycleEventManager} for method chaining
     */
    @AnyThread
    public synchronized LifecycleEventManager unregister(@NonNull Consumer<?> consumer) {
        observerRegistry.values()
                .removeIf(entry -> {
                    entry.removeIf(observer -> {
                        if (observer.consumer == consumer) {
                            observer.dismiss();
                            return true;
                        }
                        return false;
                    });
                    return entry.isEmpty();
                });
        return this;
    }

    /**
     * Unregisters all {@link Consumer}s that have previously been registered for the provided event type
     *
     * @param eventType The even type for wich all Consumers should be unregistered. Passing null will have no effect
     * @return this {@link LifecycleEventManager} for method chaining
     */
    @AnyThread
    public synchronized LifecycleEventManager unregisterAll(@Nullable Class<?> eventType) {
        List<Observer<?>> observers = observerRegistry.remove(eventType);
        if (observers != null) {
            observers.forEach(Observer::dismiss);
        }
        return this;
    }

    /**
     * Unregisters all currently registered {@link Consumer}s
     *
     * @return this {@link LifecycleEventManager} for method chaining
     */
    @AnyThread
    public synchronized LifecycleEventManager unregisterAll() {
        List<List<Observer<?>>> allObservers = new ArrayList<>(observerRegistry.values());
        observerRegistry.clear();
        allObservers.forEach(observers -> observers.forEach(Observer::dismiss));
        return this;
    }

    /**
     * Dispatches the provided event to all suitable {@link Consumer}s that are currently registered for it.<br>
     * If a consumer's {@link Lifecycle} is currently not active ({@link Lifecycle.State#RESUMED RESUMED} or {@link Lifecycle.State#STARTED STARTED}) the
     * event will be queued and later dispatched to it, once an active state is reached again. If it should reach {@link Lifecycle.State#DESTROYED DESTROYED}
     * any queued events will not be dispatched to it. The Queue will hold a strong reference to events until there are no more potential Consumers.<br>
     * Events are be dispatched to {@link Consumer#onEvent(Object)} on the main thread.
     *
     * @param event The event that should be fired. May not be null
     * @param <E>   The events type
     * @return true if the event has been dispatched or queued for at least one Consumer
     */
    @AnyThread
    public synchronized <E> boolean fire(@NonNull E event) {
        Class<?> eventType = event.getClass();
        AtomicBoolean wasEventDispatched = new AtomicBoolean(false);
        observerRegistry.entrySet().stream()
                .filter(entry -> entry.getKey().isAssignableFrom(eventType))
                .forEach(entry -> entry.getValue().stream()
                        .filter(observer -> observer.receiveSubtypes || eventType.equals(entry.getKey()))
                        .forEach(observer -> {
                            ((Observer<E>) observer).dispatch(event); // cast is safe, verified through event class
                            wasEventDispatched.set(true);
                        }));
        return wasEventDispatched.get();
    }

    private synchronized LifecycleEventManager unregisterObserver(Observer<?> observer) {
        observerRegistry.values().removeIf(observers -> {
            observers.remove(observer);
            return observers.isEmpty();
        });
        observer.dismiss();
        return this;
    }

    private class Observer<E> implements LifecycleEventObserver {

        private final LifecycleOwner lifecycleOwner;
        private final Lifecycle lifecycle;
        private final Consumer<E> consumer;
        private final boolean receiveSubtypes;
        private final Queue<E> eventQueue = new ConcurrentLinkedQueue<>();
        private Lifecycle.State currentState;

        private Observer(LifecycleOwner lifecycleOwner, Consumer<E> consumer, boolean receiveSubtypes) {
            this.lifecycleOwner = lifecycleOwner;
            lifecycle = lifecycleOwner.getLifecycle();
            this.consumer = consumer;
            currentState = lifecycle.getCurrentState();
            this.receiveSubtypes = receiveSubtypes;
            lifecycle.addObserver(this);
        }

        private void dismiss() {
            lifecycle.removeObserver(this);
            eventQueue.clear();
        }

        private synchronized void dispatch(E event) {
            if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
                eventHandler.post(() -> {
                    if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        consumer.onEvent(event);
                    } else if (currentState != Lifecycle.State.DESTROYED) {
                        eventQueue.offer(event);
                    }
                });
            } else {
                eventQueue.offer(event);
            }
        }

        @Override
        public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
            synchronized (this) {
                currentState = event.getTargetState();
                if (currentState == Lifecycle.State.RESUMED || currentState == Lifecycle.State.STARTED) {
                    while (!eventQueue.isEmpty()) {
                        dispatch(eventQueue.poll());
                    }
                }
            }
            if (currentState == Lifecycle.State.DESTROYED) {
                unregisterObserver(this);
            }
        }
    }

}
