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

import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.internal.stubbing.answers.ReturnsArgumentAt;

import java.util.ArrayList;
import java.util.List;

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
        when(decorator1.decorateForRead(any(), any(DomainEventStream.class)))
                .thenAnswer(new ReturnsArgumentAt(1));
        when(decorator2.decorateForRead(any(), any(DomainEventStream.class)))
                .thenAnswer(new ReturnsArgumentAt(1));
        when(decorator1.decorateForAppend(any(), anyList()))
                .thenAnswer(new ReturnsArgumentAt(1));
        when(decorator2.decorateForAppend(any(), anyList()))
                .thenAnswer(new ReturnsArgumentAt(1));
        testSubject  = new CompositeEventStreamDecorator(asList(decorator1, decorator2));
    }

    @Test
    public void testDecorateForRead() throws Exception {
        DomainEventStream eventStream = mock(DomainEventStream.class);
        testSubject.decorateForRead("id", eventStream);

        InOrder inOrder = inOrder(decorator1, decorator2);
        inOrder.verify(decorator1).decorateForRead("id", eventStream);
        inOrder.verify(decorator2).decorateForRead("id", eventStream);
        verifyNoMoreInteractions(decorator1, decorator2);
    }

    @Test
    public void testDecorateForAppend() throws Exception {
        Aggregate aggregate = mock(Aggregate.class);
        List<DomainEventMessage<?>> eventStream = new ArrayList<>();
        testSubject.decorateForAppend(aggregate, eventStream);

        InOrder inOrder = inOrder(decorator1, decorator2);
        inOrder.verify(decorator2).decorateForAppend(aggregate, eventStream);
        inOrder.verify(decorator1).decorateForAppend(aggregate, eventStream);
        verifyNoMoreInteractions(decorator1, decorator2);
    }
}
