package com.litts.android.async.task;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * Handles errors that occur during the execution of a {@link Task}
 *
 * @param <E> The type of error this handler can process
 */
public interface ErrorHandler<E extends Throwable> {

    /**
     * Called on the main thread by {@link LifecycleTask} when a suitable error occurred during the execution of a {@link Task} and
     * the {@link androidx.lifecycle.Lifecycle Lifecycle} is or just became active ({@link androidx.lifecycle.Lifecycle.State#RESUMED RESUMED} or {@link androidx.lifecycle.Lifecycle.State#STARTED STARTED}).<br>
     * This method may receive subtypes of the supported error type, depending on the configuration of the {@link LifecycleTask}
     *
     * @param error The error that occurred. May not be null
     */
    @MainThread
    void onError(@NonNull E error);

}
