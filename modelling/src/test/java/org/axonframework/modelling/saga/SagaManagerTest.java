/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.modelling.saga;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.utils.MockException;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.Mockito.*;

class SagaManagerTest {

    private AbstractSagaManager<Object> testSubject;
    private SagaRepository<Object> mockSagaRepository;
    private ListenerInvocationErrorHandler mockErrorHandler;
    private Saga<Object> mockSaga1;
    private Saga<Object> mockSaga2;
    private Saga<Object> mockSaga3;
    private AssociationValue associationValue;
    private TestSpanFactory spanFactory;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        mockSagaRepository = mock(SagaRepository.class);
        mockSaga1 = mock(Saga.class);
        mockSaga2 = mock(Saga.class);
        mockSaga3 = mock(Saga.class);
        mockErrorHandler = mock(ListenerInvocationErrorHandler.class);
        when(mockSaga1.isActive()).thenReturn(true);
        when(mockSaga2.isActive()).thenReturn(true);
        when(mockSaga3.isActive()).thenReturn(false);
        when(mockSaga1.getSagaIdentifier()).thenReturn("saga1");
        when(mockSaga2.getSagaIdentifier()).thenReturn("saga2");
        when(mockSaga3.getSagaIdentifier()).thenReturn("saga3");
        when(mockSagaRepository.load("saga1")).thenReturn(mockSaga1);
        when(mockSagaRepository.load("saga2")).thenReturn(mockSaga2);
        when(mockSagaRepository.load("saga3")).thenReturn(mockSaga3);
        when(mockSagaRepository.load("noSaga")).thenReturn(null);
        associationValue = new AssociationValue("association", "value");
        final AssociationValuesImpl associationValues = new AssociationValuesImpl(singleton(associationValue));
        when(mockSaga1.getAssociationValues()).thenReturn(associationValues);
        when(mockSaga2.getAssociationValues()).thenReturn(associationValues);
        when(mockSaga3.getAssociationValues()).thenReturn(associationValues);

        when(mockSaga1.canHandle(any(EventMessage.class))).thenReturn(true);
        when(mockSaga2.canHandle(any(EventMessage.class))).thenReturn(true);

        when(mockSagaRepository.find(eq(associationValue)))
                .thenReturn(setOf("saga1", "saga2", "saga3", "noSaga"));

        testSubject = TestableAbstractSagaManager.builder()
                                                 .sagaRepository(mockSagaRepository)
                                                 .listenerInvocationErrorHandler(mockErrorHandler)
                                                 .associationValue(associationValue)
                                                 .spanFactory(spanFactory)
                                                 .build();
    }

    @Test
    void sagasLoaded() throws Exception {
        EventMessage<?> event = new GenericEventMessage<>(new Object());
        UnitOfWork<? extends EventMessage<?>> unitOfWork = new DefaultUnitOfWork<>(event);
        unitOfWork.executeWithResult(() -> {
            testSubject.handle(event, Segment.ROOT_SEGMENT);
            return null;
        });
        verify(mockSagaRepository).find(associationValue);
        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSaga3, never()).handle(event);
    }

    @Test
    void sagaIsTraced() throws Exception {
        EventMessage<?> event = new GenericEventMessage<>(new Object());
        UnitOfWork<? extends EventMessage<?>> unitOfWork = new DefaultUnitOfWork<>(event);
        unitOfWork.executeWithResult(() -> {
            testSubject.handle(event, Segment.ROOT_SEGMENT);
            return null;
        });
        spanFactory.verifySpanCompleted("SagaManager[Object].invokeSaga saga1");
        spanFactory.verifySpanCompleted("SagaManager[Object].invokeSaga saga2");
    }

    @Test
    void sagaIsTracedForCreation() throws Exception {
        testSubject = TestableAbstractSagaManager.builder()
                                                 .sagaRepository(mockSagaRepository)
                                                 .listenerInvocationErrorHandler(mockErrorHandler)
                                                 .sagaCreationPolicy(SagaCreationPolicy.IF_NONE_FOUND)
                                                 .associationValue(new AssociationValue("someKey", "someValue"))
                .spanFactory(spanFactory)
                                                 .build();

        EventMessage<?> event = new GenericEventMessage<>(new Object());
        when(mockSagaRepository.createInstance(any(), any())).thenReturn(mockSaga1);
        when(mockSagaRepository.find(any())).thenReturn(Collections.emptySet());

        testSubject.handle(event, Segment.ROOT_SEGMENT);
        spanFactory.verifySpanCompleted("SagaManager[Object].startNewSaga");
        spanFactory.verifySpanCompleted("SagaManager[Object].invokeSaga saga1");
    }

    @Test
    void exceptionPropagated() throws Exception {
        EventMessage<?> event = new GenericEventMessage<>(new Object());
        MockException toBeThrown = new MockException();
        doThrow(toBeThrown).when(mockSaga1).handle(event);
        doThrow(toBeThrown).when(mockErrorHandler).onError(toBeThrown, event, mockSaga1);
        UnitOfWork<? extends EventMessage<?>> unitOfWork = new DefaultUnitOfWork<>(event);
        ResultMessage<Object> resultMessage = unitOfWork.executeWithResult(() -> {
            testSubject.handle(event, Segment.ROOT_SEGMENT);
            return null;
        });
        if (resultMessage.isExceptional()) {
            Throwable e = resultMessage.exceptionResult();
            e.printStackTrace();
            assertEquals("Mock", e.getMessage());
        } else {
            fail("Expected exception to be propagated");
        }
        verify(mockSaga1, times(1)).handle(event);
        verify(mockErrorHandler).onError(toBeThrown, event, mockSaga1);
    }

    @Test
    void sagaIsCreatedInRootSegment() throws Exception {
        testSubject = TestableAbstractSagaManager.builder()
                                                 .sagaRepository(mockSagaRepository)
                                                 .listenerInvocationErrorHandler(mockErrorHandler)
                                                 .sagaCreationPolicy(SagaCreationPolicy.IF_NONE_FOUND)
                                                 .associationValue(new AssociationValue("someKey", "someValue"))
                                                 .build();

        EventMessage<?> event = new GenericEventMessage<>(new Object());
        when(mockSagaRepository.createInstance(any(), any())).thenReturn(mockSaga1);
        when(mockSagaRepository.find(any())).thenReturn(Collections.emptySet());

        testSubject.handle(event, Segment.ROOT_SEGMENT);
        verify(mockSagaRepository).createInstance(any(), any());
    }

    @Test
    void sagaIsOnlyCreatedInSegmentMatchingAssociationValue() throws Exception {
        testSubject = TestableAbstractSagaManager.builder()
                                                 .sagaRepository(mockSagaRepository)
                                                 .listenerInvocationErrorHandler(mockErrorHandler)
                                                 .sagaCreationPolicy(SagaCreationPolicy.IF_NONE_FOUND)
                                                 .associationValue(new AssociationValue("someKey", "someValue"))
                                                 .build();

        Segment[] segments = Segment.ROOT_SEGMENT.split();
        Segment matchingSegment = segments[0].matches("someValue") ? segments[0] : segments[1];
        Segment otherSegment = segments[0].matches("someValue") ? segments[1] : segments[0];

        EventMessage<?> event = new GenericEventMessage<>(new Object());
        ArgumentCaptor<String> createdSaga = ArgumentCaptor.forClass(String.class);
        when(mockSagaRepository.createInstance(createdSaga.capture(), any())).thenReturn(mockSaga1);
        when(mockSagaRepository.find(any())).thenReturn(Collections.emptySet());

        testSubject.handle(event, otherSegment);
        verify(mockSagaRepository, never()).createInstance(any(), any());

        testSubject.handle(event, matchingSegment);
        verify(mockSagaRepository).createInstance(any(), any());

        createdSaga.getAllValues()
                   .forEach(sagaId -> assertTrue(matchingSegment.matches(sagaId),
                           "Saga ID doesn't match segment that should have created it: " + sagaId) );
        createdSaga.getAllValues()
                   .forEach(sagaId -> assertFalse(otherSegment.matches(sagaId),
                                                  "Saga ID matched against the wrong segment: " + sagaId));
    }

    @Test
    void sagaIsNotCreatedIfAssociationValueAndSagaIdMatchDifferentSegments() throws Exception {
        AssociationValue associationValue = new AssociationValue("someKey", "someValue");
        testSubject = TestableAbstractSagaManager.builder()
                                                 .sagaRepository(mockSagaRepository)
                                                 .listenerInvocationErrorHandler(mockErrorHandler)
                                                 .sagaCreationPolicy(SagaCreationPolicy.IF_NONE_FOUND)
                                                 .associationValue(associationValue)
                                                 .build();

        // Test won't work if the saga ID and association value map to the same minimum-sized segment.
        assumeTrue((associationValue.hashCode() & Integer.MAX_VALUE) !=
                           (mockSaga1.getSagaIdentifier().hashCode() & Integer.MAX_VALUE));

        EventMessage<?> event = new GenericEventMessage<>(new Object());

        String sagaId = mockSaga1.getSagaIdentifier();
        when(mockSagaRepository.find(any())).thenReturn(singleton(sagaId));
        when(mockSagaRepository.createInstance(any(), any())).thenReturn(mockSaga2);

        Segment matchesIdSegment = Segment.ROOT_SEGMENT;
        Segment matchesValueSegment;
        do {
            Segment[] segments = matchesIdSegment.split();
            matchesIdSegment = segments[0].matches(sagaId) ? segments[0] : segments[1];
            matchesValueSegment = segments[0].matches(associationValue) ? segments[0] : segments[1];
        } while (matchesIdSegment.equals(matchesValueSegment));

        testSubject.handle(event, matchesIdSegment);
        testSubject.handle(event, matchesValueSegment);
        verify(mockSagaRepository, never()).createInstance(any(), any());
        verify(mockSaga1).handle(event);
    }

    @Test
    void exceptionSuppressed() throws Exception {
        EventMessage<?> event = new GenericEventMessage<>(new Object());
        MockException toBeThrown = new MockException();
        doThrow(toBeThrown).when(mockSaga1).handle(event);
        testSubject.handle(event, Segment.ROOT_SEGMENT);
        verify(mockSaga1).handle(event);
        verify(mockSaga2).handle(event);
        verify(mockSaga3, never()).handle(event);
        verify(mockErrorHandler).onError(toBeThrown, event, mockSaga1);
    }

    @SuppressWarnings({"unchecked"})
    private <T> Set<T> setOf(T... items) {
        return new CopyOnWriteArraySet<>(Arrays.asList(items));
    }

    private static class TestableAbstractSagaManager extends AbstractSagaManager<Object> {

        private final SagaCreationPolicy sagaCreationPolicy;
        private final AssociationValue associationValue;

        private TestableAbstractSagaManager(Builder builder) {
            super(builder);
            this.sagaCreationPolicy = builder.sagaCreationPolicy;
            this.associationValue = builder.associationValue;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public boolean canHandle(@Nonnull EventMessage<?> eventMessage, @Nonnull Segment segment) {
            return true;
        }

        @Override
        protected SagaInitializationPolicy getSagaCreationPolicy(EventMessage<?> event) {
            return new SagaInitializationPolicy(sagaCreationPolicy, associationValue);
        }

        @Override
        protected Set<AssociationValue> extractAssociationValues(EventMessage<?> event) {
            return singleton(associationValue);
        }

        public static class Builder extends AbstractSagaManager.Builder<Object> {

            private SagaCreationPolicy sagaCreationPolicy = SagaCreationPolicy.NONE;
            private AssociationValue associationValue;

            private Builder() {
                super.sagaType(Object.class);
                super.sagaFactory(Object::new);
            }

            @Override
            public Builder sagaRepository(SagaRepository<Object> sagaRepository) {
                super.sagaRepository(sagaRepository);
                return this;
            }

            @Override
            public Builder sagaType(Class<Object> sagaType) {
                super.sagaType(sagaType);
                return this;
            }

            @Override
            public Builder sagaFactory(Supplier<Object> sagaFactory) {
                super.sagaFactory(sagaFactory);
                return this;
            }

            @Override
            public Builder listenerInvocationErrorHandler(
                    ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
                super.listenerInvocationErrorHandler(listenerInvocationErrorHandler);
                return this;
            }

            private Builder sagaCreationPolicy(SagaCreationPolicy sagaCreationPolicy) {
                this.sagaCreationPolicy = sagaCreationPolicy;
                return this;
            }

            private Builder associationValue(AssociationValue associationValue) {
                this.associationValue = associationValue;
                return this;
            }

            public Builder spanFactory(SpanFactory spanFactory) {
                super.spanFactory(spanFactory);
                return this;
            }

            public TestableAbstractSagaManager build() {
                return new TestableAbstractSagaManager(this);
            }
        }
    }
}
