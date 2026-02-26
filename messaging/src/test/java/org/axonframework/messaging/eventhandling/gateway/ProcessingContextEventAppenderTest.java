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

package org.axonframework.messaging.eventhandling.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;


import org.jspecify.annotations.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.mockito.*;

class ProcessingContextEventAppenderTest {

    private final EventSink mockEventSink = spy(new EventSink() {
        @Override
        public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                               @NonNull List<EventMessage> events) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void describeTo(@NonNull ComponentDescriptor descriptor) {
            // not needed for tests
        }
    });

    private final MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

    @Test
    void publishesPayloadsAsMessagesToEventSink() {
        ProcessingContext context = new StubProcessingContext();

        EventAppender testSubject = new ProcessingContextEventAppender(
                context,
                mockEventSink,
                messageTypeResolver
        );

        //noinspection unchecked
        ArgumentCaptor<List<EventMessage>> captor = ArgumentCaptor.forClass(List.class);

        String payload1 = "My Event 1";
        Integer payload2 = 500;
        testSubject.append(payload1, payload2);
        verify(mockEventSink).publish(
                eq(context),
                captor.capture()
        );

        List<EventMessage> publishedEvents = captor.getValue();
        assertEquals(2, publishedEvents.size());
        EventMessage publishedEvent1 = publishedEvents.get(0);
        EventMessage publishedEvent2 = publishedEvents.get(1);
        assertEquals(payload1, publishedEvent1.payload());
        assertEquals(payload2, publishedEvent2.payload());
        assertEquals(
                messageTypeResolver.resolveOrThrow(payload1).qualifiedName(),
                publishedEvent1.type().qualifiedName()
        );
        assertEquals(
                messageTypeResolver.resolveOrThrow(payload2).qualifiedName(),
                publishedEvent2.type().qualifiedName()
        );
    }
}