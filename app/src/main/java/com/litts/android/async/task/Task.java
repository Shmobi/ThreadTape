package com.litts.android.async.task;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

/**
 * A Task that can be executed by a {@link LifecycleTask}. May yield a result
 *
 * @param <R> The result type
 */
public interface Task<R> {

    /**
     * Called by {@link LifecycleTask#execute()} on its Thread.<br>
     * Executes this Task's work. Any errors during its execution may be thrown and later on handled by {@link ErrorHandler}s that are registered
     * with the {@link LifecycleTask}. May yield a result that will later on be processed by the {@link ResultHandler} that is registered with the {@link LifecycleTask}.
     *
     * @return the result of this task. May be null
     * @throws Exception If any occur during the tasks execution
     */
    @AnyThread
    @Nullable
    R execute() throws Exception;

}
