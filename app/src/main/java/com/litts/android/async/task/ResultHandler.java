package com.litts.android.async.task;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

/**
 * Handles the result of a {@link Task}
 *
 * @param <R> The type of result this handler can process
 */
public interface ResultHandler<R> {

    /**
     * Called on the main thread by {@link LifecycleTask} when a {@link Task} was successfully executed if the
     * {@link androidx.lifecycle.Lifecycle Lifecycle} is or just became active ({@link androidx.lifecycle.Lifecycle.State#RESUMED RESUMED} or {@link androidx.lifecycle.Lifecycle.State#STARTED STARTED}).<br>
     *
     * @param result The result of the Task. May be null, depending on the Task
     */
    @MainThread
    void onResult(@Nullable R result);

}
