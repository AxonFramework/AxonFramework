package org.axonframework.eventhandling;

import org.axonframework.domain.EventMessage;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.*;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class EventTemplateTest {

    private UnitOfWork mockUnitOfWork;
    private EventBus mockEventBus;
    private EventTemplate testSubject;
    private Map<String, Object> additionalMetaData;
    private Object payload;

    @Before
    public void setUp() throws Exception {
        mockUnitOfWork = mock(UnitOfWork.class);
        mockEventBus = mock(EventBus.class);
        additionalMetaData = new HashMap<String, Object>();
        additionalMetaData.put("key1", "value1");
        testSubject = new EventTemplate(mockEventBus, additionalMetaData);
        payload = new Object();
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.clear(CurrentUnitOfWork.get());
        }
    }

    @Test
    public void testUnitOfWorkUsedToSendEvent() throws Exception {
        CurrentUnitOfWork.set(mockUnitOfWork);

        testSubject.publishEvent(payload);

        verifyZeroInteractions(mockEventBus);
        verify(mockUnitOfWork).publishEvent(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return "value1".equals(eventMessage.getMetaData().get("key1"))
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an event message with a 'key1' meta data property");
            }
        }), eq(mockEventBus));
    }

    @Test
    public void testUnitOfWorkUsedToSendEvent_OverlappingMetaData() throws Exception {
        CurrentUnitOfWork.set(mockUnitOfWork);

        Map<String, Object> moreMetaData = new HashMap<String, Object>();
        moreMetaData.put("key1", "value2");
        moreMetaData.put("key2", "value1");
        testSubject.publishEvent(payload, moreMetaData);

        verifyZeroInteractions(mockEventBus);
        verify(mockUnitOfWork).publishEvent(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return "value2".equals(eventMessage.getMetaData().get("key1"))
                        && eventMessage.getMetaData().containsKey("key2")
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an event message with a 'key1' and 'key2' meta data property");
            }
        }), eq(mockEventBus));
    }

    @Test
    public void testEventSentImmediatelyWhenNoActiveUnitOfWorkExists_OverlappingMetaData() throws Exception {

        Map<String, Object> moreMetaData = new HashMap<String, Object>();
        moreMetaData.put("key1", "value2");
        moreMetaData.put("key2", "value1");
        testSubject.publishEvent(payload, moreMetaData);

        verifyZeroInteractions(mockUnitOfWork);
        verify(mockEventBus).publish(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return "value2".equals(eventMessage.getMetaData().get("key1"))
                        && eventMessage.getMetaData().containsKey("key2")
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an event message with a 'key1' and 'key2' meta data property");
            }
        }));
    }

    @Test
    public void testEventSentImmediatelyWhenNoActiveUnitOfWorkExists() throws Exception {
        testSubject.publishEvent(payload);

        verifyZeroInteractions(mockUnitOfWork);
        verify(mockEventBus).publish(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return "value1".equals(eventMessage.getMetaData().get("key1"))
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an event message with a 'key1' meta data property");
            }
        }));
    }

    @Test
    public void testEventSentImmediatelyWhenNoActiveUnitOfWorkExists_NoAdditionalMetaData() throws Exception {
        testSubject = new EventTemplate(mockEventBus);
        testSubject.publishEvent(payload);

        verifyZeroInteractions(mockUnitOfWork);
        verify(mockEventBus).publish(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return eventMessage.getMetaData().isEmpty()
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an event message with a 'key1' meta data property");
            }
        }));
    }

    @Test
    public void testActiveUnitOfWorkUsedToDispatchEvent_NoAdditionalMetaData() throws Exception {
        CurrentUnitOfWork.set(mockUnitOfWork);

        testSubject = new EventTemplate(mockEventBus);
        testSubject.publishEvent(payload);

        verifyZeroInteractions(mockEventBus);
        verify(mockUnitOfWork).publishEvent(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return eventMessage.getMetaData().isEmpty()
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an event message with a 'key1' meta data property");
            }
        }), eq(mockEventBus));
    }
}
