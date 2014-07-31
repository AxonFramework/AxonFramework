package org.axonframework.test.saga;

import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.MyOtherEvent;
import org.axonframework.test.matchers.Matchers;
import org.junit.Before;
import org.junit.Test;

public class EventValidatorTest {

    private EventValidator testSubject;

    @Before
    public void setUp(){
        EventBus eventBusShouldNotBeUsed = null;
        testSubject = new EventValidator(eventBusShouldNotBeUsed);
    }

    @Test
    public void testAssertPublishedEventsWithNoEventsMatcherIfNoEventWasPublished() throws Exception {
        testSubject.assertPublishedEventsMatching(Matchers.noEvents());
    }

    @Test(expected = AxonAssertionError.class)
    public void testAssertPublishedEventsWithNoEventsMatcherThrowsAssertionErrorIfEventWasPublished() throws Exception {
        testSubject.handle(GenericEventMessage.asEventMessage(new MyOtherEvent()));

        testSubject.assertPublishedEventsMatching(Matchers.noEvents());
    }

    @Test
    public void testAssertPublishedEventsIfNoEventWasPublished() throws Exception {
        testSubject.assertPublishedEvents();
    }

    @Test(expected = AxonAssertionError.class)
    public void testAssertPublishedEventsThrowsAssertionErrorIfEventWasPublished() throws Exception {
        testSubject.handle(GenericEventMessage.asEventMessage(new MyOtherEvent()));

        testSubject.assertPublishedEvents();
    }



}