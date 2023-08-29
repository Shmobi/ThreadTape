package com.litts.android.async.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Semaphore;


public class LifecycleLockTest {

    private Lifecycle mockedLifecycle;
    private LifecycleEventObserver currentObserver;

    @Before
    public void setup() {
        mockedLifecycle = mock(Lifecycle.class);
        doAnswer(invocation -> currentObserver = invocation.getArgument(0)).when(mockedLifecycle).addObserver(any(LifecycleEventObserver.class));
    }

    @Test
    public void testObtain_Resumed() throws InterruptedException {
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.RESUMED);
        LifecycleLock lock = new LifecycleLock(mockedLifecycle);
        assertTrue(lock.obtain());
    }

    @Test
    public void testObtain_Started() throws InterruptedException {
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.STARTED);
        LifecycleLock lock = new LifecycleLock(mockedLifecycle);
        assertTrue(lock.obtain());
    }

    @Test
    public void testObtain_Destroyed() throws InterruptedException {
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.DESTROYED);
        LifecycleLock lock = new LifecycleLock(mockedLifecycle);
        assertFalse(lock.obtain());
    }

    @Test
    public void testObtain_Wait_Resume() throws InterruptedException {
        testObtain_Wait(Lifecycle.Event.ON_RESUME, true);
    }

    @Test
    public void testObtain_Wait_Start() throws InterruptedException {
        testObtain_Wait(Lifecycle.Event.ON_START, true);

    }

    @Test
    public void testObtain_Wait_Destroy() throws InterruptedException {
        testObtain_Wait(Lifecycle.Event.ON_DESTROY, false);
    }

    @Test
    public void testObtain_Wait_Inactive() throws InterruptedException {
        LifecycleOwner mockedOwner = mock(LifecycleOwner.class);
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.CREATED);
        LifecycleLock lock = new LifecycleLock(mockedLifecycle);
        Semaphore threadStartSemaphore = new Semaphore(0);
        Thread t = new Thread(() -> {
            try {
                threadStartSemaphore.release();
                lock.obtain();
                fail();
            } catch (InterruptedException ie) {
                // interruption intended by test
            }
        });
        t.setDaemon(true);
        t.start();
        threadStartSemaphore.acquire(); // wait until thread is launched
        t.join(1000); // join for 1 sec to provide time for obtain
        assertEquals(Thread.State.WAITING, t.getState()); // verify the thread is waiting in obtain
        currentObserver.onStateChanged(mockedOwner, Lifecycle.Event.ON_STOP); // resume the lifecycle
        t.join(1000); // join the thread for 1 sec to provide time for obtain
        assertEquals(Thread.State.WAITING, t.getState()); // verify the thread is still waiting in obtain
    }

    private void testObtain_Wait(Lifecycle.Event event, boolean shouldObtain) throws InterruptedException {
        LifecycleOwner mockedOwner = mock(LifecycleOwner.class);
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.CREATED);
        LifecycleLock lock = new LifecycleLock(mockedLifecycle);
        Semaphore threadStartSemaphore = new Semaphore(0);
        Thread t = new Thread(() -> {
            try {
                threadStartSemaphore.release();
                assertEquals(shouldObtain, lock.obtain());
            } catch (InterruptedException ie) {
                fail();
            }
        });
        t.start();
        threadStartSemaphore.acquire(); // wait until thread is launched
        t.join(1000); // join for 1 sec to provide time for obtain
        assertEquals(Thread.State.WAITING, t.getState()); // verify the thread is waiting in obtain
        currentObserver.onStateChanged(mockedOwner, event); // resume the lifecycle
        t.join(); // join the thread until its death to confirm that obtain has returned
    }

}
