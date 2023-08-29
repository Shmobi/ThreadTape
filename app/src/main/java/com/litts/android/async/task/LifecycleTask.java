package com.litts.android.async.task;

import android.os.Handler;
import android.os.Looper;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes the work of a {@link Task} while providing callbacks about its progress on the main thread
 *
 * @param <R> The result type of the {@link Task} that will be executed
 * @see Builder for creation
 */
public class LifecycleTask<R> {

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final LifecycleLock lifecycleLock;
    private final Lifecycle lifecycle;
    private final Task<R> task;
    private final Runnable before;
    private final ResultHandler<R> resultHandler;
    private final Set<ErrorHandlerEntry<?>> errorHandlers;

    private LifecycleTask(Lifecycle lifecycle, Task<R> task, Runnable before, ResultHandler<R> resultHandler, Set<ErrorHandlerEntry<?>> errorHandlers) {
        this.lifecycle = lifecycle;
        this.lifecycleLock = new LifecycleLock(lifecycle);
        this.task = task;
        this.before = before;
        this.resultHandler = resultHandler;
        this.errorHandlers = new TreeSet<>(errorHandlers);
    }

    /**
     * Executes this {@link LifecycleTask}. Note that this method is synchronized and only one execution may run at a time.<br>
     *
     * <h1>Execution steps</h1>
     * The execution consists of the following steps
     * <h2>Before</h2>
     * This step is provided through {@link Builder#before(Runnable)}. It is executed first, before the main work of the {@link Task} is run.<br>
     * For details on execution, refer to the Callback execution section below
     * <h2>Task</h2>
     * This step is provided through {@link Builder#create(Task)} or {@link Builder#create(VoidTask)}. In this step the {@link Task}
     * will be executed on the calling Thread of this method. This is where the main work is performed.<br>
     * If the Task executes successfully, the {@link ResultHandler} will be called next if available (See Result handling section below).<br>
     * If the Task should fail to execute, any errors that occurred will be caught and handled by a suitable {@link ErrorHandler} if available (See Errors section below).<br>
     * If the bound {@link Lifecycle} should have reached {@link Lifecycle.State#DESTROYED DESTROYED} the task won't be started. If this state
     * is reached during execution, the Task won't be interrupted or cancelled in any way but after completion or failure, no more callbacks will be executed.
     * <h2>Result handling</h2>
     * This step is provided through {@link Builder#after(ResultHandler)} or {@link Builder#after(Runnable)}. It is executed after the
     * {@link Task} has successfully completed. If a {@link ResultHandler} was provided it will receive the result yielded by the task (which may be null).<br>
     * For details on execution, refer to the Callback execution section below
     * <h2>Errors</h2>
     * This step consists of one or more callbacks that are provided through
     * <ul>
     * <li>{@link Builder#onError(Class, ErrorHandler)}</li>
     * <li>{@link Builder#onError(Class, Runnable)}</li>
     * <li>{@link Builder#onUnspecifiedError(ErrorHandler)}</li>
     * <li>{@link Builder#onUnspecifiedError(Runnable)}</li>
     * </ul>
     * It is executed after the {@link Task} has failed and thrown a {@link Throwable}. Similar to a try-catch construct, the {@link LifecycleTask}
     * will use the {@link ErrorHandler} that was registered for the closest related type to handle the error.<br>
     * If there should be no suitable {@link ErrorHandler} the error will be ignored
     * For details on execution, refer to the Callback execution section below
     * <h1>Callback execution</h1>
     * All callbacks are executed on the main Thread. Their execution will only take place if the bound {@link Lifecycle} is active
     * ({@link Lifecycle.State#RESUMED RESUMED} or {@link Lifecycle.State#STARTED STARTED}) and will wait for the Lifecycle to become active if
     * it isn't. The Thread that called this method will wait for the Lifecycle and the execution of a callback before proceeding to the next step.
     * If the Lifecycle should reach {@link Lifecycle.State#DESTROYED DESTROYED} the execution of the callback will be skipped and the execution of
     * the {@link LifecycleTask} will end.<br>
     * If a callback is not set, its execution will be skipped and the {@link LifecycleTask} will move on to the next step (See Execution steps section above).
     * All callbacks are optional.
     */
    public synchronized void execute() {
        if (before == null || executeCallback(before)) {
            try {
                if (!Thread.currentThread().isInterrupted() && lifecycle.getCurrentState() != Lifecycle.State.DESTROYED) {
                    R result = task.execute();
                    if (resultHandler != null) {
                        executeCallback(() -> resultHandler.onResult(result));
                    }
                }
            } catch (Throwable t) {
                handleError(t);
            }
        }
    }

    /**
     * Creates a new {@link Thread} and runs {@link #execute()} on it.
     *
     * @return the created and started {@link Thread}. May not be null
     * @see #execute()
     */
    @NonNull
    public Thread executeAsync() {
        Thread t = new Thread(this::execute);
        t.start();
        return t;
    }

    private <E extends Throwable> void handleError(E error) {
        Class<?> errorType = error.getClass();
        Optional<ErrorHandlerEntry<?>> handler = errorHandlers.stream()
                .filter(entry -> entry.type.isAssignableFrom(errorType))
                .findFirst();
        handler.ifPresent(entry -> {
            ErrorHandler<E> errorHandler = (ErrorHandler<E>) entry.handler; // cast is safe since type must be assignable
            executeCallback(() -> errorHandler.onError(error));
        });
    }

    private boolean canExecute() {
        try {
            return !Thread.currentThread().isInterrupted() && lifecycleLock.obtain();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean executeCallback(Runnable runnable) {
        try {
            do {
                if (!canExecute()) {
                    return false;
                }
            } while (waitForCallback(runnable));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    private boolean waitForCallback(Runnable runnable) throws InterruptedException {
        AtomicBoolean isExecutionPending = new AtomicBoolean(true);
        Semaphore callbackSemaphore = new Semaphore(0);
        uiHandler.post(() -> {
            try {
                if (lifecycle.getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                    try {
                        runnable.run();
                    } finally {
                        isExecutionPending.set(false);
                    }
                }
            } finally {
                callbackSemaphore.release();
            }
        });
        callbackSemaphore.acquire();
        return isExecutionPending.get();
    }

    private static class ErrorHandlerEntry<E extends Throwable> implements Comparable<ErrorHandlerEntry<?>> {

        private final Class<? extends E> type;
        private final ErrorHandler<E> handler;
        private final int superTypeCount;

        private ErrorHandlerEntry(Class<? extends E> type, ErrorHandler<E> handler) {
            int superTypeCount = 1;
            Class<?> currentType = type;
            while (!Object.class.equals(currentType)) {
                superTypeCount++;
                currentType = currentType.getSuperclass();
            }
            this.type = type;
            this.handler = handler;
            this.superTypeCount = superTypeCount;
        }

        @Override
        public int compareTo(ErrorHandlerEntry<?> errorHandlerEntry) {
            return errorHandlerEntry.superTypeCount - this.superTypeCount;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj != null && obj.getClass() == ErrorHandlerEntry.class) {
                return this.type == ((ErrorHandlerEntry<?>) obj).type;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return type.hashCode();
        }
    }

    /**
     * Builder to configure and then create a {@link LifecycleTask}
     */
    public static class Builder<R> {

        private final Task<R> task;
        private final Set<ErrorHandlerEntry<?>> errorHandlers = new ArraySet<>();
        private Runnable before;
        private ResultHandler<R> resultHandler;

        private Builder(Task<R> task) {
            this.task = task;
        }

        /**
         * Creates a new {@link Builder} for a {@link VoidTask} without result
         *
         * @param task The task that should be executed by the resulting {@link LifecycleTask}. May not be null
         * @return the created Builder. May not be null
         */
        @NonNull
        public static Builder<Void> create(@NonNull VoidTask task) {
            return create(() -> {
                task.execute();
                return null;
            });
        }

        /**
         * Creates a new {@link Builder} for a {@link Task} with result
         *
         * @param task The task that should be executed by the resulting {@link LifecycleTask}. May not be null
         * @return the created Builder. May not be null
         */
        @NonNull
        public static <R> Builder<R> create(@NonNull Task<R> task) {
            return new Builder<>(task);
        }

        /**
         * Creates a {@link LifecycleTask} with the current configuration of this Builder
         *
         * @param lifecycle The {@link Lifecycle} that the LifecycleTask will be bound to. May not be null
         * @return the created {@link LifecycleTask}. May not be null
         */
        @NonNull
        public synchronized LifecycleTask<R> build(@NonNull Lifecycle lifecycle) {
            return new LifecycleTask<>(lifecycle, task, before, resultHandler, errorHandlers);
        }

        /**
         * Sets the before callback. Pass null to remove it
         *
         * @param before The before callback. May be null to remove it
         * @return this Builder for method chaining. May not be null
         * @see LifecycleTask#execute()
         */
        @NonNull
        public Builder<R> before(@Nullable Runnable before) {
            this.before = before;
            return this;
        }

        /**
         * Sets the {@link Runnable} as result callback. The Runnable won't receive the result that may be returned by the task.
         * Pass null to remove it
         *
         * @param runnable The result callback. May be null to remove it
         * @return this Builder for method chaining. May not be null
         * @see LifecycleTask#execute()
         */
        @NonNull
        public Builder<R> after(@Nullable Runnable runnable) {
            return after(runnable != null ? ignored -> runnable.run() : null);
        }

        /**
         * Sets the {@link ResultHandler} as result callback. The handler will receive the result that may be returned by the task.
         * Pass null to remove it
         *
         * @param resultHandler The result callback. May be null to remove it
         * @return this Builder for method chaining. May not be null
         * @see LifecycleTask#execute()
         */
        @NonNull
        public Builder<R> after(@Nullable ResultHandler<R> resultHandler) {
            this.resultHandler = resultHandler;
            return this;
        }

        /**
         * Registers the {@link Runnable} as the error callback for the provided type. The Runnable won't receive the occurred error.
         * Pass null to remove it. If another callback has already been registered for the provided type, it will be replaced by the new one.
         *
         * @param errorType The type of error that the callback is registered for. May not be null
         * @param runnable  The error callback. May be null to remove it
         * @return this Builder for method chaining. May not be null
         * @see LifecycleTask#execute()
         */
        @NonNull
        public synchronized <E extends Throwable> Builder<R> onError(@NonNull Class<E> errorType, @Nullable Runnable runnable) {
            return onError(errorType, runnable != null ? ignored -> runnable.run() : null);
        }

        /**
         * Registers the {@link ErrorHandler} as the error callback for the provided type. The handler will receive the occurred error.
         * Pass null to remove it. If another callback has already been registered for the provided type, it will be replaced by the new one.
         *
         * @param errorType    The type of error that the callback is registered for. May not be null
         * @param errorHandler The error callback. May be null to remove it
         * @return this Builder for method chaining. May not be null
         * @see LifecycleTask#execute()
         */
        @NonNull
        public synchronized <E extends Throwable> Builder<R> onError(@NonNull Class<? extends E> errorType, @Nullable ErrorHandler<E> errorHandler) {
            if (errorHandler != null) {
                errorHandlers.add(new ErrorHandlerEntry<>(errorType, errorHandler));
            } else {
                errorHandlers.removeIf(entry -> entry.type.equals(errorType));
            }
            return this;
        }

        /**
         * Designated method to remove a previously registered error callback. Same effect as passing
         * a null handler to {@link #onError(Class, Runnable)} or {@link #onError(Class, ErrorHandler)}.
         *
         * @param errorType The type of error for which the handler should be removed. May not be null
         * @return this Builder for method chaining. May not be null
         */
        @NonNull
        public synchronized <E extends Throwable> Builder<R> clearError(Class<E> errorType) {
            return onError(errorType, (Runnable) null);
        }

        /**
         * Convenience method for calling {@link #onError(Class, Runnable)} for error type {@link Throwable}
         *
         * @param runnable The error callback. May be null to remove it
         * @return this Builder for method chaining. May not be null
         * @see #onError(Class, Runnable)
         */
        @NonNull
        public synchronized Builder<R> onUnspecifiedError(@Nullable Runnable runnable) {
            return onUnspecifiedError(runnable != null ? ignored -> runnable.run() : null);
        }

        /**
         * Convenience method for calling {@link #onError(Class, ErrorHandler)} for error type {@link Throwable}
         *
         * @param errorHandler The error callback. May be null to remove it
         * @return this Builder for method chaining. May not be null
         */
        @NonNull
        public synchronized Builder<R> onUnspecifiedError(@Nullable ErrorHandler<Throwable> errorHandler) {
            return onError(Throwable.class, errorHandler);
        }

        /**
         * Designated method to remove the previously registered error callback for unspecified errors. Same effect as passing
         * a null handler to {@link #onUnspecifiedError(Runnable)} or {@link #onUnspecifiedError(ErrorHandler)}.
         *
         * @return this Builder for method chaining. May not be null
         */
        @NonNull
        public synchronized Builder<R> clearUnspecifiedError() {
            return onError(Throwable.class, (Runnable) null);
        }
    }


}
