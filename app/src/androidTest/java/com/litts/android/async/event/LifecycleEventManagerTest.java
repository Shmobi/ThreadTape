package com.litts.android.async.event;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Message;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import org.junit.Before;
import org.junit.Test;

public class LifecycleEventManagerTest {

    private Lifecycle mockedLifecycle;
    private LifecycleEventObserver currentLifecycleObserver;
    private LifecycleOwner mockedLifecycleOwner;
    private Handler mockedHandler;

    @Before
    public void setup() {
        mockedLifecycle = mock(Lifecycle.class);
        mockedLifecycleOwner = mock(LifecycleOwner.class);
        mockedHandler = mock(Handler.class);
        when(mockedLifecycleOwner.getLifecycle()).thenReturn(mockedLifecycle);
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.RESUMED);
        doAnswer(invocation -> currentLifecycleObserver = invocation.getArgument(0)).when(mockedLifecycle).addObserver(any(LifecycleEventObserver.class));
        when(mockedHandler.sendMessageAtTime(any(), anyLong())).thenAnswer(invocation -> {
            ((Message) invocation.getArgument(0)).getCallback().run();
            return true;
        });
    }

    @Test
    public void testRegister() {
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);

        Integer event = 3;
        manager.fire(event);
        verify(mockedConsumer).onEvent(event);
    }

    @Test
    public void testRegister_withSubtypes() {
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer, true);

        Integer event = 3;
        manager.fire(event);
        verify(mockedConsumer).onEvent(event);
    }

    @Test
    public void testRegister_withoutSubtypes() {
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer, false);

        manager.fire(3);
        verify(mockedConsumer, never()).onEvent(any());
        Object event = new Object();
        manager.fire(event);
        verify(mockedConsumer).onEvent(event);
    }

    @Test
    public void testRegister_multipleConsumerForSameOwner() {
        Consumer<Object>[] mockedConsumers = new Consumer[10];
        for (int i = 0; i < mockedConsumers.length; i++) {
            mockedConsumers[i] = mock(Consumer.class);
        }
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        for (Consumer<Object> mockedConsumer : mockedConsumers) {
            manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        }

        Object event = new Object();
        manager.fire(event);
        for (int i = 0; i < mockedConsumers.length - 1; i++) {
            verify(mockedConsumers[i], never()).onEvent(any());
        }
        verify(mockedConsumers[mockedConsumers.length - 1]).onEvent(event);
    }

    @Test
    public void testUnregister_withEventType() {
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        manager.register(mockedLifecycleOwner, Integer.class, mockedConsumer);
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        manager.unregister(Integer.class, mockedConsumer);

        Integer event = 3;
        manager.fire(event);
        verify(mockedConsumer, times(1)).onEvent(event);
    }

    @Test
    public void testUnregister_withoutEventType() {
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        manager.register(mockedLifecycleOwner, Integer.class, mockedConsumer);
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        manager.unregister(mockedConsumer);

        Integer event = 3;
        manager.fire(event);
        verify(mockedConsumer, never()).onEvent(event);
    }

    @Test
    public void testUnregisterAll_withEventType() {
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        manager.register(mockedLifecycleOwner, Integer.class, mockedConsumer);
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        manager.unregisterAll(Integer.class);

        Integer event = 3;
        manager.fire(event);
        verify(mockedConsumer, times(1)).onEvent(event);
    }

    @Test
    public void testUnregisterAll_withoutEventType() {
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        manager.register(mockedLifecycleOwner, Integer.class, mockedConsumer);
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        manager.unregisterAll();

        Integer event = 3;
        manager.fire(event);
        verify(mockedConsumer, never()).onEvent(event);
    }

    @Test
    public void testFire_noConsumers() {
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        assertFalse(manager.fire(new Object()));
    }

    @Test
    public void testFire_singleConsumer() {
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);

        Object event = new Object();
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        assertTrue(manager.fire(event));
        verify(mockedConsumer).onEvent(event);
    }

    @Test
    public void testFire_multipleConsumer() {
        Consumer<Object>[] mockedConsumers = new Consumer[10];
        for (int i = 0; i < mockedConsumers.length; i++) {
            mockedConsumers[i] = mock(Consumer.class);
        }
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);
        for (Consumer<Object> mockedConsumer : mockedConsumers) {
            LifecycleOwner mockedLifecycleOwner = mock(LifecycleOwner.class);
            when(mockedLifecycleOwner.getLifecycle()).thenReturn(mockedLifecycle);
            manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        }

        Object event = new Object();
        assertTrue(manager.fire(event));
        for (Consumer<Object> mockedConsumer : mockedConsumers) {
            verify(mockedConsumer).onEvent(event);
        }
    }

    @Test
    public void testFire_waitForActiveState() {
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.CREATED);
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);

        Object event = new Object();
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        assertTrue(manager.fire(event));
        verify(mockedConsumer, never()).onEvent(event);
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.RESUMED);
        currentLifecycleObserver.onStateChanged(mockedLifecycleOwner, Lifecycle.Event.ON_RESUME);
        verify(mockedConsumer).onEvent(event);
    }

    @Test
    public void testFire_lifecycleDestroyed() {
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.CREATED);
        Consumer<Object> mockedConsumer = mock(Consumer.class);
        LifecycleEventManager manager = new LifecycleEventManager(mockedHandler);

        Object event = new Object();
        manager.register(mockedLifecycleOwner, Object.class, mockedConsumer);
        assertTrue(manager.fire(event));
        verify(mockedConsumer, never()).onEvent(event);
        when(mockedLifecycle.getCurrentState()).thenReturn(Lifecycle.State.DESTROYED);
        currentLifecycleObserver.onStateChanged(mockedLifecycleOwner, Lifecycle.Event.ON_DESTROY);
        verify(mockedConsumer, never()).onEvent(event);
        assertFalse(manager.fire(event));
    }

}
