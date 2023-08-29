package com.litts.android.async.event;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * Callback that receives events from {@link LifecycleEventManager}
 *
 * @param <E> The type of event this callback can process.
 */
public interface Consumer<E> {

    /**
     * Called on the main Thread by {@link LifecycleEventManager} when a suitable event was fired and the related {@link androidx.lifecycle.Lifecycle Lifecycle}
     * is or just became active ({@link androidx.lifecycle.Lifecycle.State#RESUMED RESUMED} or {@link androidx.lifecycle.Lifecycle.State#STARTED STARTED}).<br>
     * This method may receive subtypes of the supported event type, depending on its registration. See {@link LifecycleEventManager}
     *
     * @param event The event that was fired. May not be null
     */
    @MainThread
    void onEvent(@NonNull E event);

}
