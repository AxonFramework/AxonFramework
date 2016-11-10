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
