/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.InterceptingEventBus;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.StreamingCondition;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.common.util.MockException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link InterceptingEventStore}.
 *
 * @author Steven van Beelen
 */
class InterceptingEventStoreTest {

    private static final MessageType TEST_EVENT_TYPE = new MessageType("event");

    private EventStoreTransaction eventStoreTransaction;
    private EventStore eventStore;
    private ProcessingContext processingContext;
    private AtomicInteger interceptorCounterOne;
    private MessageDispatchInterceptor<Message> interceptorOne;
    private AtomicInteger interceptorCounterTwo;
    private MessageDispatchInterceptor<Message> interceptorTwo;

    private InterceptingEventStore testSubject;

    @BeforeEach
    void setUp() {
        eventStoreTransaction = mock(EventStoreTransaction.class);
        eventStore = mock(EventStore.class);
        processingContext = mock(ProcessingContext.class);
        when(eventStore.transaction(any(ProcessingContext.class))).thenReturn(eventStoreTransaction);
        when(eventStore.transaction(any(), any(ProcessingContext.class))).thenReturn(eventStoreTransaction);
        //noinspection unchecked
        when(eventStore.publish(any(), any(List.class)))
                .thenReturn(FutureUtils.emptyCompletedFuture());
        when(eventStore.publish(any(), any(EventMessage.class)))
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

        testSubject = new InterceptingEventStore(eventStore, List.of(interceptorOne, interceptorTwo));
    }

    @Test
    void dispatchInterceptorsInvokedOnTransactionAppend() {
        EventMessage testEvent = new GenericEventMessage(TEST_EVENT_TYPE, "test");
        ProcessingContext testContext = StubProcessingContext.forMessage(testEvent);

        EventStoreTransaction transaction = testSubject.transaction(testContext);

        transaction.appendEvent(testEvent);

        ArgumentCaptor<EventMessage> appendedEvent = ArgumentCaptor.forClass(EventMessage.class);
        // InterceptingEventStore.transaction(context) delegates to transaction(null, context)
        verify(eventStore).transaction(isNull(), eq(testContext));
        verify(eventStoreTransaction).appendEvent(appendedEvent.capture());

        assertThat(appendedEvent.getValue()).isEqualTo(testEvent);
        assertThat(interceptorCounterOne).hasValue(1);
        assertThat(interceptorCounterTwo).hasValue(1);
    }

    @Test
    void dispatchInterceptorsAreInvokedForEveryEventOnTransactionAppend() {
        EventMessage firstEvent = new GenericEventMessage(TEST_EVENT_TYPE, "first");
        EventMessage secondEvent = new GenericEventMessage(TEST_EVENT_TYPE, "second");
        EventMessage thirdEvent = new GenericEventMessage(TEST_EVENT_TYPE, "third");
        EventMessage fourthEvent = new GenericEventMessage(TEST_EVENT_TYPE, "fourth");

        EventStoreTransaction firstTransaction = testSubject.transaction(new StubProcessingContext());
        firstTransaction.appendEvent(firstEvent);
        firstTransaction.appendEvent(secondEvent);
        EventStoreTransaction secondTransaction = testSubject.transaction(new StubProcessingContext());
        secondTransaction.appendEvent(thirdEvent);
        secondTransaction.appendEvent(fourthEvent);

        assertThat(interceptorCounterOne.get()).isEqualTo(4);
        assertThat(interceptorCounterTwo.get()).isEqualTo(4);
    }

    @Test
    void delegateTransactionSourceDirectly() {
        SourcingCondition testCondition = SourcingCondition.conditionFor(EventCriteria.havingAnyTag());

        testSubject.transaction(new StubProcessingContext())
                   .source(testCondition);

        verify(eventStoreTransaction).source(testCondition);
    }

    @Test
    void delegateTransactionOnAppendDirectly() {
        Consumer<EventMessage> testOnAppend = (event) -> {
        };

        testSubject.transaction(new StubProcessingContext())
                   .onAppend(testOnAppend);

        verify(eventStoreTransaction).onAppend(testOnAppend);
    }

    @Test
    void delegateTransactionAppendPositionDirectly() {
        testSubject.transaction(new StubProcessingContext())
                   .appendPosition();

        verify(eventStoreTransaction).appendPosition();
    }

    @Test
    void dispatchInterceptorsInvokedOnPublish() {
        EventMessage testEvent = new GenericEventMessage(TEST_EVENT_TYPE, "test");

        CompletableFuture<Void> result =
                testSubject.publish(StubProcessingContext.forMessage(testEvent), testEvent);

        ArgumentCaptor<EventMessage> publishedEvent = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventStore).publish(any(), publishedEvent.capture());

        assertThat(publishedEvent.getValue()).isEqualTo(testEvent);
        assertThat(interceptorCounterOne).hasValue(1);
        assertThat(interceptorCounterTwo).hasValue(1);
        assertThat(result).isDone();
    }

    @Test
    void dispatchInterceptorsAreInvokedForEveryEventOnPublish() throws Exception {
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
    void exceptionsInDispatchInterceptorReturnFailedStreamOnPublish() {
        EventMessage testEvent = new GenericEventMessage(TEST_EVENT_TYPE, "test");

        MessageDispatchInterceptor<Message> faultyInterceptor = (message, context, chain) -> {
            throw new MockException();
        };
        InterceptingEventStore faultyInterceptingEventStore =
                new InterceptingEventStore(eventStore, List.of(faultyInterceptor));

        CompletableFuture<Void> result = faultyInterceptingEventStore.publish(null, testEvent);

        assertThat(result).isDone();
        assertThat(result).isCompletedExceptionally();
        assertThat(result.exceptionNow()).isInstanceOf(MockException.class);
    }

    @Test
    void delegateOpenStreamDirectly() {
        StreamingCondition testCondition = StreamingCondition.startingFrom(TrackingToken.FIRST);

        testSubject.open(testCondition, processingContext);

        verify(eventStore).open(testCondition, processingContext);
    }

    @Test
    void delegateFirstTokenDirectly() {
        testSubject.firstToken(processingContext);

        verify(eventStore).firstToken(processingContext);
    }

    @Test
    void delegateLatestTokenDirectly() {
        testSubject.latestToken(processingContext);

        verify(eventStore).latestToken(processingContext);
    }

    @Test
    void delegateTokenAtDirectly() {
        Instant testInstant = Instant.now();

        testSubject.tokenAt(testInstant, processingContext);

        verify(eventStore).tokenAt(testInstant, processingContext);
    }

    @Test
    void describeIncludesAllRelevantProperties() {
        MockComponentDescriptor descriptor = new MockComponentDescriptor();

        testSubject.describeTo(descriptor);

        Map<String, Object> describedProperties = descriptor.getDescribedProperties();
        assertThat(describedProperties).size().isEqualTo(3);
        assertThat(describedProperties).containsKey("delegate");
        assertThat(describedProperties.get("delegate")).isEqualTo(eventStore);
        assertThat(describedProperties).containsKey("dispatchInterceptors");
        //noinspection unchecked
        List<MessageDispatchInterceptor<? super Message>> dispatchInterceptors =
                (List<MessageDispatchInterceptor<? super Message>>) describedProperties.get("dispatchInterceptors");
        assertThat(dispatchInterceptors).containsExactly(interceptorOne, interceptorTwo);
        assertThat(describedProperties).containsKey("delegateBus");
        assertThat(describedProperties.get("delegateBus")).isInstanceOf(InterceptingEventBus.class);
    }
}