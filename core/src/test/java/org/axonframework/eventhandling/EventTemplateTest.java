/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.correlation.CorrelationDataHolder;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.*;

import java.util.Collections;
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
    private Object payload;

    @Before
    public void setUp() throws Exception {
        CorrelationDataHolder.clear();
        mockUnitOfWork = mock(UnitOfWork.class);
        mockEventBus = mock(EventBus.class);
        testSubject = new EventTemplate(mockEventBus, Collections.singletonMap("key1", "value1"));
        payload = new Object();
    }

    @After
    public void tearDown() throws Exception {
        CorrelationDataHolder.clear();
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
    public void testMessagesHaveCorrelationDataAttached() {
        CorrelationDataHolder.setCorrelationData(Collections.singletonMap("correlationId", "testing"));
        testSubject.publishEvent(payload, Collections.singletonMap("scope", "test"));

        verify(mockEventBus).publish(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return "testing".equals(eventMessage.getMetaData().get("correlationId"))
                        && eventMessage.getMetaData().containsKey("scope")
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an event message with a 'correlationId' and 'scope' meta data property");
            }
        }));
        verifyNoMoreInteractions(mockUnitOfWork, mockEventBus);
    }

    @Test
    public void testMessagesUseExplicitlyProvidedHeadersWhenConflictingWithCorrelationHeaders() {
        CorrelationDataHolder.setCorrelationData(Collections.singletonMap("correlationId", "testing"));
        testSubject.publishEvent(payload, Collections.singletonMap("correlationId", "overridden"));

        verify(mockEventBus).publish(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return "overridden".equals(eventMessage.getMetaData().get("correlationId"))
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(
                        "an event message with a meta data property 'correlationId' of value 'overridden'");
            }
        }));
        verifyNoMoreInteractions(mockUnitOfWork, mockEventBus);
    }

    @Test
    public void testMessagesUseExplicitlyProvidedHeadersInMessageWhenConflictingWithCorrelationHeaders() {
        CorrelationDataHolder.setCorrelationData(Collections.singletonMap("correlationId", "testing"));
        testSubject.publishEvent(GenericEventMessage.asEventMessage(payload)
                                                    .withMetaData(Collections.singletonMap("correlationId",
                                                                                           "overridden")));

        verify(mockEventBus).publish(argThat(new TypeSafeMatcher<EventMessage<?>>() {
            @Override
            protected boolean matchesSafely(EventMessage<?> eventMessage) {
                return "overridden".equals(eventMessage.getMetaData().get("correlationId"))
                        && eventMessage.getPayload().equals(payload);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(
                        "an event message with a meta data property 'correlationId' of value 'overridden'");
            }
        }));
        verifyNoMoreInteractions(mockUnitOfWork, mockEventBus);
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
