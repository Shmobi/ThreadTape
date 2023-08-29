package com.litts.android.async.task;

/**
 * A convenience wrapper for a {@link Task} of type {@link Void}
 *
 * @see Task
 */
public interface VoidTask {

    /**
     * A version of {@link Task#execute()} without result
     *
     * @throws Exception If any occur during the tasks execution
     * @see Task#execute()
     */
    void execute() throws Exception;


}
