/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.modelling.utils.MockException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.*;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

import static java.util.Collections.singleton;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SagaManagerTest {

    private AbstractSagaManager<Object> testSubject;
    private SagaRepository<Object> mockSagaRepository;
    private ListenerInvocationErrorHandler mockErrorHandler;
    private Saga<Object> mockSaga1;
    private Saga<Object> mockSaga2;
    private Saga<Object> mockSaga3;
    private AssociationValue associationValue;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
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
                                                 .build();
    }

    @Test
    public void testSagasLoaded() throws Exception {
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
    public void testExceptionPropagated() throws Exception {
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
    public void testSagaIsCreatedInRootSegment() throws Exception {
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
    public void testSagaIsOnlyCreatedInSegmentMatchingAssociationValue() throws Exception {
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
                   .forEach(sagaId -> assertTrue("Saga ID doesn't match segment that should have created it: " + sagaId,
                                                 matchingSegment.matches(sagaId)));
        createdSaga.getAllValues()
                   .forEach(sagaId -> assertFalse("Saga ID matched against the wrong segment: " + sagaId,
                                                  otherSegment.matches(sagaId)));
    }

    @Test
    public void testExceptionSuppressed() throws Exception {
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
        public boolean canHandle(EventMessage<?> eventMessage, Segment segment) {
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

            public TestableAbstractSagaManager build() {
                return new TestableAbstractSagaManager(this);
            }
        }
    }
}
