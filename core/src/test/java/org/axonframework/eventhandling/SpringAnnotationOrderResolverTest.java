package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.junit.*;
import org.springframework.core.annotation.Order;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class SpringAnnotationOrderResolverTest {

    private SpringAnnotationOrderResolver testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new SpringAnnotationOrderResolver();
    }

    @Test
    public void testOrderFetchedFromAnnotation() throws Exception {
        assertEquals(4, testSubject.orderOf(new OrderedEventListener()));
    }

    @Test
    public void testOrderFetchedFromAnnotationOnTarget() throws Exception {
        assertEquals(4, testSubject.orderOf(new EventListenerProxy() {
            @Override
            public Class<?> getTargetType() {
                return OrderedEventListener.class;
            }

            @Override
            public void handle(EventMessage event) {
                throw new UnsupportedOperationException("Not implemented yet");
            }
        }));
    }

    @Test
    public void testDefaultOrderWhenNotAnnotated() throws Exception {
        assertEquals(0, testSubject.orderOf(event -> {
            throw new UnsupportedOperationException("Not implemented yet");
        }));

    }

    @Order(4)
    private static class OrderedEventListener implements EventListener {

        @Override
        public void handle(EventMessage event) {
            throw new UnsupportedOperationException("Not implemented yet");
        }
    }
}
