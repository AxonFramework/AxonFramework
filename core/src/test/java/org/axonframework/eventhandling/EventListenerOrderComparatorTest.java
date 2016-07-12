package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.junit.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventListenerOrderComparatorTest {

    private OrderResolver orderResolver;
    private EventListenerOrderComparator testSubject;

    @Before
    public void setUp() throws Exception {
        orderResolver = mock(OrderResolver.class);
        testSubject = new EventListenerOrderComparator(orderResolver);

    }

    @Test
    public void testCompareSameHandlers() throws Exception {
        EventListener listener1 = mock(EventListener.class);
        assertEquals(0, testSubject.compare(listener1, listener1));

        verifyZeroInteractions(orderResolver);
    }

    @Test
    public void testCompareEqualHandlers() throws Exception {
        EventListener listener1 = new StubEventListener(true, 0);
        EventListener listener2 = new StubEventListener(true, 1);
        assertEquals(0, testSubject.compare(listener1, listener2));

        verifyZeroInteractions(orderResolver);
    }

    @Test
    public void testCompareHandlersWithStaticHashCode() throws Exception {
        EventListener listener1 = new StubEventListener(false, 1);
        EventListener listener2 = new StubEventListener(false, 1);

        assertFalse(0 == testSubject.compare(listener1, listener2));
    }

    private static class StubEventListener implements EventListener {

        private final boolean alwaysEqual;
        private final int hashCode;

        private StubEventListener(boolean alwaysEqual, int hashCode) {
            this.alwaysEqual = alwaysEqual;
            this.hashCode = hashCode;
        }

        @Override
        public void handle(EventMessage event) {
        }

        @Override
        public boolean equals(Object o) {
            return alwaysEqual || this == o;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    @Test
    public void testCompareHighestPrecendenceToLowestPrecedence() throws Exception {
        EventListener highestPrecedence = new NullEventListener();
        EventListener lowestPrecedence = new NullEventListener();

        when(orderResolver.orderOf(highestPrecedence)).thenReturn(Ordered.HIGHEST_PRECEDENCE);
        when(orderResolver.orderOf(lowestPrecedence)).thenReturn(Ordered.LOWEST_PRECEDENCE);

        int result = testSubject.compare(highestPrecedence, lowestPrecedence);

        Assert.assertEquals(result, -1);
    }

    private class NullEventListener implements EventListener {
        @Override
        public void handle(EventMessage event) {
        }
    }
}
