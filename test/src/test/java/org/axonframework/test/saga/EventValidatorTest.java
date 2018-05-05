package org.axonframework.test.saga;

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.aggregate.MyOtherEvent;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.matchers.Matchers;
import org.junit.Before;
import org.junit.Test;

public class EventValidatorTest {

    private EventValidator testSubject;

    @Before
    public void setUp(){
        testSubject = new EventValidator(null, AllFieldsFilter.instance());
    }

    @Test
    public void testAssertPublishedEventsWithNoEventsMatcherIfNoEventWasPublished() {
        testSubject.assertPublishedEventsMatching(Matchers.noEvents());
    }

    @Test(expected = AxonAssertionError.class)
    public void testAssertPublishedEventsWithNoEventsMatcherThrowsAssertionErrorIfEventWasPublished() {
        testSubject.handle(GenericEventMessage.asEventMessage(new MyOtherEvent()));

        testSubject.assertPublishedEventsMatching(Matchers.noEvents());
    }

    @Test
    public void testAssertPublishedEventsIfNoEventWasPublished() {
        testSubject.assertPublishedEvents();
    }

    @Test(expected = AxonAssertionError.class)
    public void testAssertPublishedEventsThrowsAssertionErrorIfEventWasPublished() {
        testSubject.handle(GenericEventMessage.asEventMessage(new MyOtherEvent()));

        testSubject.assertPublishedEvents();
    }



}
