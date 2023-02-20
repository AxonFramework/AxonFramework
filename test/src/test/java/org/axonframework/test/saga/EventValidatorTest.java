package org.axonframework.test.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.aggregate.MyOtherEvent;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.matchers.Matchers;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EventValidatorTest {

    private EventValidator testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new EventValidator(null, AllFieldsFilter.instance());
    }

    @Test
    void assertPublishedEventsWithNoEventsMatcherIfNoEventWasPublished() {
        testSubject.assertPublishedEventsMatching(Matchers.noEvents());
    }

    @Test
    void assertPublishedEventsWithNoEventsMatcherThrowsAssertionErrorIfEventWasPublished() {
        testSubject.handle(GenericEventMessage.asEventMessage(new MyOtherEvent()));

        assertThrows(AxonAssertionError.class, () -> testSubject.assertPublishedEventsMatching(Matchers.noEvents()));
    }

    @Test
    void assertPublishedEventsIfNoEventWasPublished() {
        testSubject.assertPublishedEvents();
    }

    @Test
    void assertPublishedEventsThrowsAssertionErrorIfEventWasPublished() {
        testSubject.handle(GenericEventMessage.asEventMessage(new MyOtherEvent()));

        assertThrows(AxonAssertionError.class, testSubject::assertPublishedEvents);
    }

    @Test
    void assertPublishedEventsForEventMessages() {
        EventMessage<MyOtherEvent> testEventMessage = GenericEventMessage.asEventMessage(new MyOtherEvent());
        testSubject.handle(testEventMessage);

        testSubject.assertPublishedEvents(testEventMessage);
    }
}
