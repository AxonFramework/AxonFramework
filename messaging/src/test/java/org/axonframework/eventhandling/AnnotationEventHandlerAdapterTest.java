package org.axonframework.eventhandling;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AnnotationEventHandlerAdapterTest {

    private AnnotationEventHandlerAdapter testSubject;
    private StubEventhandler handler;

    @Before
    public void setUp() throws Exception {
        handler = new StubEventhandler();
        testSubject = new AnnotationEventHandlerAdapter(handler);
    }

    @Test
    public void invokeAllHandlers() throws Exception {
        testSubject.handle(GenericEventMessage.asEventMessage(new StubEvent()));

        assertEquals(2, handler.invocationCount);
    }

    class StubEventhandler {
        private int invocationCount;

        @EventHandler
        public void on(StubConcept event) {
            invocationCount++;
        }

        @EventHandler
        public void on(StubEvent event) {
            invocationCount++;
        }
    }

    interface StubConcept {
    }

    private class StubEvent implements StubConcept {
    }

}