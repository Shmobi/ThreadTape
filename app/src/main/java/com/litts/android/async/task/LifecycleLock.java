package com.litts.android.async.task;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * Synchronisation utility that allows to wait for a {@link Lifecycle} to reach a desired state
 */
public class LifecycleLock {

    private Lifecycle.State currentState;

    /**
     * Creates a new instance that is bound to the provided {@link Lifecycle}
     *
     * @param lifecycle The {@link Lifecycle} this instance will be bound to. May not be null
     */
    public LifecycleLock(@NonNull Lifecycle lifecycle) {
        lifecycle.addObserver(new Observer());
        currentState = lifecycle.getCurrentState();
    }

    /**
     * Potentially {@link Thread#wait() waits} for the {@link Lifecycle} to reach {@link Lifecycle.State#RESUMED RESUMED}, {@link Lifecycle.State#STARTED STARTED} or {@link Lifecycle.State#DESTROYED DESTROYED}.
     * Then returns whether the Lifecycle is active or not.
     *
     * @return true if the {@link Lifecycle} is {@link Lifecycle.State#RESUMED RESUMED} or {@link Lifecycle.State#STARTED STARTED}. false if the {@link Lifecycle} is {@link Lifecycle.State#DESTROYED DESTROYED}
     * @throws InterruptedException If the Thread was interrupted while waiting for the Lifecycle to change to one of the desired states
     */
    public synchronized boolean obtain() throws InterruptedException {
        while (currentState != Lifecycle.State.RESUMED && currentState != Lifecycle.State.STARTED && currentState != Lifecycle.State.DESTROYED) {
            wait();
        }
        return currentState.isAtLeast(Lifecycle.State.STARTED);
    }

    private class Observer implements LifecycleEventObserver {
        @Override
        public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
            synchronized (LifecycleLock.this) {
                currentState = event.getTargetState();
                switch (currentState) {
                    case STARTED:
                    case RESUMED:
                    case DESTROYED:
                        LifecycleLock.this.notifyAll();
                        break;
                    default:
                        // other states are irrelevant
                        break;
                }
            }
        }
    }


}
