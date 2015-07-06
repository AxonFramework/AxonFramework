package org.axonframework.unitofwork;

import org.junit.Test;

import static org.mockito.Mockito.*;

public class UnitOfWorkListenerCollectionTest {

    @Test
    public void shouldClearListenersCollectionOnCleanupToAllowGarbageCollectionOfListenersWhenDisruptorIsBeingUsed() {
        UnitOfWorkListener listener = mock(UnitOfWorkListener.class);
        UnitOfWorkListenerCollection listenerCollection = new UnitOfWorkListenerCollection();
        listenerCollection.add(listener);

        listenerCollection.onCleanup(null);
        verify(listener, times(1)).onCleanup(null);

        reset(listener);
        listenerCollection.onCleanup(null);
        verify(listener, never()).onCleanup(null);
    }
}