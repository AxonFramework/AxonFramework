/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.utils.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link InterceptingEventSink}.
 *
 * @author Steven van Beelen
 */
class InterceptingEventSinkTest {

    private static final MessageType TEST_EVENT_TYPE = new MessageType("event");

    private EventSink eventSink;
    private AtomicInteger interceptorCounterOne;
    private MessageDispatchInterceptor<Message> interceptorOne;
    private AtomicInteger interceptorCounterTwo;
    private MessageDispatchInterceptor<Message> interceptorTwo;

    private InterceptingEventSink testSubject;

    @BeforeEach
    void setUp() {
        eventSink = mock(EventSink.class);
        //noinspection unchecked
        when(eventSink.publish(any(), any(List.class)))
                .thenReturn(FutureUtils.emptyCompletedFuture());
        when(eventSink.publish(any(), any(EventMessage.class)))
                .thenReturn(FutureUtils.emptyCompletedFuture());

        interceptorCounterOne = new AtomicInteger(0);
        interceptorOne = (message, context, chain) -> {
            interceptorCounterOne.incrementAndGet();
            return chain.proceed(message, context);
        };
        interceptorCounterTwo = new AtomicInteger(0);
        interceptorTwo = (message, context, chain) -> {
            interceptorCounterTwo.incrementAndGet();
            return chain.proceed(message, context);
        };

        testSubject = new InterceptingEventSink(eventSink, List.of(interceptorOne, interceptorTwo));
    }

    @Test
    void dispatchInterceptorsInvokedOnPublish() {
        EventMessage testEvent = new GenericEventMessage(TEST_EVENT_TYPE, "test");

        CompletableFuture<Void> result =
                testSubject.publish(StubProcessingContext.forMessage(testEvent), testEvent);

        ArgumentCaptor<EventMessage> publishedEvent = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventSink).publish(any(), publishedEvent.capture());

        assertThat(publishedEvent.getValue()).isEqualTo(testEvent);
        assertThat(interceptorCounterOne).hasValue(1);
        assertThat(interceptorCounterTwo).hasValue(1);
        assertThat(result).isDone();
    }

    @Test
    void dispatchInterceptorsAreInvokedForEveryEvent() throws Exception {
        EventMessage firstEvent = new GenericEventMessage(TEST_EVENT_TYPE, "first");
        EventMessage secondEvent = new GenericEventMessage(TEST_EVENT_TYPE, "second");
        EventMessage thirdEvent = new GenericEventMessage(TEST_EVENT_TYPE, "third");
        EventMessage fourthEvent = new GenericEventMessage(TEST_EVENT_TYPE, "fourth");

        testSubject.publish(null, firstEvent, secondEvent).get();
        testSubject.publish(null, thirdEvent, fourthEvent).get();

        assertThat(interceptorCounterOne.get()).isEqualTo(4);
        assertThat(interceptorCounterTwo.get()).isEqualTo(4);
    }

    @Test
    void exceptionsInDispatchInterceptorReturnFailedStream() {
        EventMessage testEvent = new GenericEventMessage(TEST_EVENT_TYPE, "test");

        MessageDispatchInterceptor<Message> faultyInterceptor = (message, context, chain) -> {
            throw new MockException();
        };
        InterceptingEventSink faultyInterceptingEventSink =
                new InterceptingEventSink(eventSink, List.of(faultyInterceptor));

        CompletableFuture<Void> result = faultyInterceptingEventSink.publish(null, testEvent);

        assertThat(result).isDone();
        assertThat(result).isCompletedExceptionally();
        assertThat(result.exceptionNow()).isInstanceOf(MockException.class);
    }

    @Test
    void describeIncludesAllRelevantProperties() {
        MockComponentDescriptor descriptor = new MockComponentDescriptor();

        testSubject.describeTo(descriptor);

        Map<String, Object> describedProperties = descriptor.getDescribedProperties();
        assertThat(describedProperties).size().isEqualTo(2);
        assertThat(describedProperties).containsKey("delegate");
        assertThat(describedProperties.get("delegate")).isEqualTo(eventSink);
        assertThat(describedProperties).containsKey("dispatchInterceptors");
        //noinspection unchecked
        List<MessageDispatchInterceptor<? super Message>> dispatchInterceptors =
                (List<MessageDispatchInterceptor<? super Message>>) describedProperties.get("dispatchInterceptors");
        assertThat(dispatchInterceptors).containsExactly(interceptorOne, interceptorTwo);
    }
}