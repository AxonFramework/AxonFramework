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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEventStream;
import org.axonframework.domain.SimpleDomainEventStream;
import org.junit.*;
import org.mockito.*;
import org.mockito.internal.stubbing.answers.*;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

public class CompositeEventStreamDecoratorTest {

    private CompositeEventStreamDecorator testSubject;
    private EventStreamDecorator decorator1;
    private EventStreamDecorator decorator2;

    @Before
    public void setUp() throws Exception {
        decorator1 = mock(EventStreamDecorator.class);
        decorator2 = mock(EventStreamDecorator.class);
        when(decorator1.decorateForRead(anyString(), any(), any(DomainEventStream.class)))
                .thenAnswer(new ReturnsArgumentAt(2));
        when(decorator2.decorateForRead(anyString(), any(), any(DomainEventStream.class)))
                .thenAnswer(new ReturnsArgumentAt(2));
        when(decorator1.decorateForAppend(anyString(), any(EventSourcedAggregateRoot.class), any(DomainEventStream.class)))
                .thenAnswer(new ReturnsArgumentAt(2));
        when(decorator2.decorateForAppend(anyString(), any(EventSourcedAggregateRoot.class), any(DomainEventStream.class)))
                .thenAnswer(new ReturnsArgumentAt(2));
        testSubject  = new CompositeEventStreamDecorator(asList(decorator1, decorator2));
    }

    @Test
    public void testDecorateForRead() throws Exception {
        final SimpleDomainEventStream eventStream = new SimpleDomainEventStream();
        testSubject.decorateForRead("test", "id", eventStream);

        InOrder inOrder = inOrder(decorator1, decorator2);
        inOrder.verify(decorator1).decorateForRead("test", "id", eventStream);
        inOrder.verify(decorator2).decorateForRead("test", "id", eventStream);
        verifyNoMoreInteractions(decorator1, decorator2);
    }

    @Test
    public void testDecorateForAppend() throws Exception {
        final SimpleDomainEventStream eventStream = new SimpleDomainEventStream();
        final EventSourcedAggregateRoot aggregate = mock(EventSourcedAggregateRoot.class);
        testSubject.decorateForAppend("test", aggregate, eventStream);

        InOrder inOrder = inOrder(decorator1, decorator2);
        inOrder.verify(decorator2).decorateForAppend("test", aggregate, eventStream);
        inOrder.verify(decorator1).decorateForAppend("test", aggregate, eventStream);
        verifyNoMoreInteractions(decorator1, decorator2);
    }
}