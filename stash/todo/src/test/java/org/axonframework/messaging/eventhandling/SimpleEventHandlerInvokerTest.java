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

package org.axonframework.messaging.eventhandling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collections;
import java.util.List;

import static org.axonframework.messaging.eventhandling.DomainEventTestUtils.createDomainEvent;
import static org.axonframework.messaging.eventhandling.DomainEventTestUtils.createDomainEvents;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SimpleEventHandlerInvoker}.
 *
 * @author Rene de Waele
 */
class SimpleEventHandlerInvokerTest {

    private static final Object NO_RESET_PAYLOAD = null;

    private org.axonframework.messaging.eventhandling.EventMessageHandler mockHandler1;
    private org.axonframework.messaging.eventhandling.EventMessageHandler mockHandler2;

    private SimpleEventHandlerInvoker testSubject;

    @BeforeEach
    void setUp() {
        mockHandler1 = mock(org.axonframework.messaging.eventhandling.EventMessageHandler.class);
        mockHandler2 = mock(EventMessageHandler.class);
        testSubject = SimpleEventHandlerInvoker.builder()
                                               .eventHandlers("test", mockHandler1, mockHandler2)
                                               .build();
    }

    @Test
    void singleEventPublication() throws Exception {
        EventMessage event = createDomainEvent();

        ProcessingContext context = StubProcessingContext.forMessage(event);
        testSubject.handle(event, context, Segment.ROOT_SEGMENT);

        InOrder inOrder = inOrder(mockHandler1, mockHandler2);
        inOrder.verify(mockHandler1).handleSync(event, context);
        inOrder.verify(mockHandler2).handleSync(event, context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void repeatedEventPublication() throws Exception {
        List<? extends EventMessage> events = createDomainEvents(2);

        for (EventMessage event : events) {
            testSubject.handle(event, StubProcessingContext.forMessage(event), Segment.ROOT_SEGMENT);
        }

        InOrder inOrder = inOrder(mockHandler1, mockHandler2);
        inOrder.verify(mockHandler1).handleSync(eq(events.get(0)), any());
        inOrder.verify(mockHandler2).handleSync(eq(events.get(0)), any());
        inOrder.verify(mockHandler1).handleSync(eq(events.get(1)), any());
        inOrder.verify(mockHandler2).handleSync(eq(events.get(1)), any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void performReset() {
        ProcessingContext context = new StubProcessingContext();
        testSubject.performReset(context);

        verify(mockHandler1).prepareReset(NO_RESET_PAYLOAD, context);
        verify(mockHandler2).prepareReset(NO_RESET_PAYLOAD, context);
    }

    @Test
    void performResetWithResetContext() {
        String resetContext = "reset-context";

        ProcessingContext context = new StubProcessingContext();
        testSubject.performReset(resetContext, context);

        verify(mockHandler1).prepareReset(eq(resetContext), eq(context));
        verify(mockHandler2).prepareReset(eq(resetContext), eq(context));
    }

    @Test
    void buildWithNullEventHandlersListThrowsAxonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.eventHandlers((List<?>) null));
    }

    @Test
    void buildWithEmptyEventHandlersListThrowsAxonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        List<Object> testEventHandlers = Collections.emptyList();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.eventHandlers(testEventHandlers));
    }

    @Test
    void buildWithoutEventHandlersListThrowsAxonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();

        assertThrows(AxonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithNullParameterResolverFactoryThrowsAxonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.parameterResolverFactory(null));
    }

    @Test
    void buildWithNullHandlerDefinitionThrowsAxonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.handlerDefinition(null));
    }

    @Test
    void buildWithNullListenerInvocationErrorHandlerThrowsAxonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.listenerInvocationErrorHandler(null));
    }

    @Test
    void buildWithNullSequencingPolicyThrowsAxonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.sequencingPolicy(null));
    }

    @Test
    void shouldSupportResetWhenAtLeastOneEventMessageHandlerSupportsReset() {
        doReturn(true).when(mockHandler1).supportsReset();
        doReturn(false).when(mockHandler2).supportsReset();

        assertTrue(testSubject.supportsReset());
    }

    @Test
    void shouldNotSupportResetWhenNoEventMessageHandlerSupportsReset() {
        doReturn(false).when(mockHandler1).supportsReset();
        doReturn(false).when(mockHandler2).supportsReset();

        assertFalse(testSubject.supportsReset());
    }
}
