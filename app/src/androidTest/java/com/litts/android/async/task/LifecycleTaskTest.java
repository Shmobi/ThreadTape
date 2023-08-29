package com.litts.android.async.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import org.junit.Before;
import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;

public class LifecycleTaskTest {

    private Lifecycle mockedLifecycle;
    private LifecycleEventObserver currentObserver;

    @Before
    public void setup() {
        mockedLifecycle = mock(Lifecycle.class);
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.RESUMED);
        doAnswer(invocation -> currentObserver = invocation.getArgument(0)).when(mockedLifecycle).addObserver(any(LifecycleEventObserver.class));
    }

    /* LifecycleTask */
    @Test
    public void testBefore() {
        Runnable mockedBefore = mock(Runnable.class);
        LifecycleTask.Builder.create(() -> null)
                .before(mockedBefore)
                .build(mockedLifecycle)
                .execute();
        verify(mockedBefore).run();
    }

    @Test
    public void testExecute() throws Exception {
        Task<Object> mockedTask = mock(Task.class);
        LifecycleTask.Builder.create(mockedTask)
                .build(mockedLifecycle)
                .execute();
        verify(mockedTask).execute();
    }

    @Test
    public void testExecuteAsync() throws Exception {
        Task<Object> mockedTask = mock(Task.class);
        Thread t = LifecycleTask.Builder.create(mockedTask)
                .build(mockedLifecycle)
                .executeAsync();
        t.join();
        verify(mockedTask).execute();
    }

    @Test
    public void testAfter_Runnable() {
        Runnable mockedAfter = mock(Runnable.class);
        LifecycleTask.Builder.create(() -> null)
                .after(mockedAfter)
                .build(mockedLifecycle)
                .execute();
        verify(mockedAfter).run();
    }

    @Test
    public void testAfter_ResultHandler() {
        ResultHandler<Object> mockedAfter = mock(ResultHandler.class);
        Object result = new Object();
        LifecycleTask.Builder.create(() -> result)
                .after(mockedAfter)
                .build(mockedLifecycle)
                .execute();
        verify(mockedAfter).onResult(result);
    }

    @Test
    public void testOnError_Runnable() {
        Runnable mockedHandler = mock(Runnable.class);
        Task<Object> task = () -> {
            throw new IOException();
        };
        LifecycleTask.Builder.create(task)
                .onError(IOException.class, mockedHandler)
                .build(mockedLifecycle)
                .execute();
        verify(mockedHandler).run();
    }

    @Test
    public void testOnError_ErrorHandler() {
        ErrorHandler<IOException> mockedHandler = mock(ErrorHandler.class);
        IOException exception = new IOException();
        Task<Object> task = () -> {
            throw exception;
        };
        LifecycleTask.Builder.create(task)
                .onError(IOException.class, mockedHandler)
                .build(mockedLifecycle)
                .execute();
        verify(mockedHandler).onError(exception);
    }

    @Test
    public void testOnUnspecifiedError_Runnable() {
        Runnable mockedHandler = mock(Runnable.class);
        Task<Object> task = () -> {
            throw new IOException();
        };
        LifecycleTask.Builder.create(task)
                .onError(IOException.class, mockedHandler)
                .build(mockedLifecycle)
                .execute();
        verify(mockedHandler).run();
    }

    @Test
    public void testOnUnspecifiedError_ErrorHandler() {
        ErrorHandler<Throwable> mockedHandler = mock(ErrorHandler.class);
        IOException exception = new IOException();
        Task<Object> task = () -> {
            throw exception;
        };
        LifecycleTask.Builder.create(task)
                .onUnspecifiedError(mockedHandler)
                .build(mockedLifecycle)
                .execute();
        verify(mockedHandler).onError(exception);
    }

    @Test
    public void testOnError_Inheritance() {
        ErrorHandler<IOException> mockedIOHandler = mock(ErrorHandler.class);
        ErrorHandler<Exception> mockedExceptionHandler = mock(ErrorHandler.class);
        ErrorHandler<Throwable> mockedUnspecifiedHandler = mock(ErrorHandler.class);
        EOFException exception = new EOFException();
        Task<Object> task = () -> {
            throw exception;
        };
        LifecycleTask.Builder.create(task)
                .onUnspecifiedError(mockedUnspecifiedHandler)
                .onError(Exception.class, mockedExceptionHandler)
                .onError(IOException.class, mockedIOHandler)
                .build(mockedLifecycle)
                .execute();
        verify(mockedUnspecifiedHandler, never()).onError(any());
        verify(mockedExceptionHandler, never()).onError(any());
        verify(mockedIOHandler).onError(exception);
    }

    @Test
    public void testLifecycleBinding_waitForResume() throws InterruptedException {
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.CREATED);
        Runnable mockedBefore = mock(Runnable.class);
        LifecycleTask.Builder<?> builder = LifecycleTask.Builder.create(() -> null)
                .before(mockedBefore);
        verifyCallbackOnLifecycleChange(builder, mockedBefore, Lifecycle.Event.ON_RESUME, true);

        Runnable mockedAfter = mock(Runnable.class);
        builder = LifecycleTask.Builder.create(() -> null)
                .after(mockedAfter);
        verifyCallbackOnLifecycleChange(builder, mockedAfter, Lifecycle.Event.ON_RESUME, true);

        Runnable mockedErrorRunnable = mock(Runnable.class);
        Task<Object> task = () -> {
            throw new IOException();
        };
        builder = LifecycleTask.Builder.create(task)
                .onUnspecifiedError(mockedErrorRunnable);
        verifyCallbackOnLifecycleChange(builder, mockedErrorRunnable, Lifecycle.Event.ON_RESUME, true);
    }

    @Test
    public void testLifecycleBinding_waitForDestroyed() throws InterruptedException {
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.CREATED);
        Runnable mockedBefore = mock(Runnable.class);
        LifecycleTask.Builder<?> builder = LifecycleTask.Builder.create(() -> null)
                .before(mockedBefore);
        verifyCallbackOnLifecycleChange(builder, mockedBefore, Lifecycle.Event.ON_DESTROY, false);

        Runnable mockedAfter = mock(Runnable.class);
        builder = LifecycleTask.Builder.create(() -> null)
                .after(mockedAfter);
        verifyCallbackOnLifecycleChange(builder, mockedAfter, Lifecycle.Event.ON_DESTROY, false);

        Runnable mockedErrorRunnable = mock(Runnable.class);
        Task<Object> task = () -> {
            throw new IOException();
        };
        builder = LifecycleTask.Builder.create(task)
                .onUnspecifiedError(mockedErrorRunnable);
        verifyCallbackOnLifecycleChange(builder, mockedErrorRunnable, Lifecycle.Event.ON_DESTROY, false);
    }

    /* Builder */
    @Test
    public void testBuilderCreate_void() {
        LifecycleTask.Builder<Void> voidBuilder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"));
        assertNotNull(voidBuilder);
    }

    @Test
    public void testBuilderCreate_withType() {
        LifecycleTask.Builder<Integer> intBuilder = LifecycleTask.Builder.create(() -> {
            fail("Shouldn't be called by builder");
            return 0;
        });
        assertNotNull(intBuilder);
    }

    @Test
    public void testBuilderBefore() {
        LifecycleTask.Builder<Void> builder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .before(() -> fail("Shouldn't be called by builder"));
        assertNotNull(builder);
    }

    @Test
    public void testBuilderAfter_Runnable() {
        LifecycleTask.Builder<Void> builder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .after(() -> fail("Shouldn't be called by builder"));
        assertNotNull(builder);
    }

    @Test
    public void testBuilderAfter_ResultHandler() {
        LifecycleTask.Builder<Integer> builder = LifecycleTask.Builder.create(() -> {
            fail("Shouldn't be called by builder");
            return 0;
        }).after((Integer result) -> fail("Shouldn't be called by builder"));
        assertNotNull(builder);
    }

    @Test
    public void testBuilderOnError_Runnable() {
        LifecycleTask.Builder<Void> builder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .onError(IOException.class, () -> fail("Shouldn't be called by builder"));
        assertNotNull(builder);
    }

    @Test
    public void testBuilderOnError_ErrorHandler() {
        LifecycleTask.Builder<Void> builder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .onError(IOException.class, (IOException ex) -> fail("Shouldn't be called by builder"));
        assertNotNull(builder);
    }

    @Test
    public void testBuilderClearError() {
        LifecycleTask.Builder<Void> builder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .clearError(IOException.class);
        assertNotNull(builder);
    }

    @Test
    public void testBuilderOnUnspecifiedError_Runnable() {
        LifecycleTask.Builder<Void> builder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .onUnspecifiedError(() -> fail("Shouldn't be called by builder"));
        assertNotNull(builder);
    }

    @Test
    public void testBuilderOnUnspecifiedError_ErrorHandler() {
        LifecycleTask.Builder<Void> builder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .onUnspecifiedError((Throwable t) -> fail("Shouldn't be called by builder"));
        assertNotNull(builder);
    }

    @Test
    public void testBuilderClearUnspecifiedError() {
        LifecycleTask.Builder<Void> builder = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .clearUnspecifiedError();
        assertNotNull(builder);
    }

    @Test
    public void testBuilderBuild() {
        Lifecycle mockedLifecycle = mock(Lifecycle.class);
        LifecycleTask<Void> voidTask = LifecycleTask.Builder.create(() -> fail("Shouldn't be called by builder"))
                .build(mockedLifecycle);
        LifecycleTask<Integer> intTask = LifecycleTask.Builder.create(() -> {
            fail("Shouldn't be called by builder");
            return 0;
        }).build(mockedLifecycle);
        assertNotNull(voidTask);
        assertNotNull(intTask);
    }

    private void verifyCallbackOnLifecycleChange(LifecycleTask.Builder<?> builder, Runnable mockedCallback, Lifecycle.Event desiredEvent, boolean executionExcepted) throws InterruptedException {
        LifecycleOwner mockedOwner = mock(LifecycleOwner.class);
        Thread t = builder.build(mockedLifecycle)
                .executeAsync();
        t.join(1000); // wait 1sec to give the async task time to run into lifecycle lock
        assertEquals(Thread.State.WAITING, t.getState());  // verify that the thread is waiting now
        verify(mockedCallback, never()).run(); // verify that the callback hasn't been run yet
        when(mockedLifecycle.getCurrentState()).thenReturn(desiredEvent.getTargetState());
        currentObserver.onStateChanged(mockedOwner, desiredEvent); // resume the lifecycle
        t.join(); // wait for completion of the thread
        verify(mockedCallback, times(executionExcepted ? 1 : 0)).run(); // verify that the callback now has been run
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.CREATED); // reset lifecycle for next test
    }

}
