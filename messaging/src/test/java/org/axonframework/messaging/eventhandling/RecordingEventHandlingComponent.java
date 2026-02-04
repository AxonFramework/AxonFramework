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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An {@link EventHandlingComponent} implementation that records all events it handles.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class RecordingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final List<EventMessage> recorded = new CopyOnWriteArrayList<>();

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate The instance to delegate calls to.
     */
    public RecordingEventHandlingComponent(@Nonnull EventHandlingComponent delegate) {
        super(delegate);
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        CompletableFuture<Message> resultFuture = new CompletableFuture<>();
        delegate.handle(event, context).asCompletableFuture()
                .whenComplete((r, e) -> {
                    recorded.add(event);
                    if (e != null) {
                        resultFuture.completeExceptionally(e);
                    } else {
                        resultFuture.complete(null);
                    }
                });
        return MessageStream.fromFuture(resultFuture).ignoreEntries();
    }

    public List<EventMessage> recorded() {
        return recorded;
    }

    public boolean handled(EventMessage event) {
        return recorded.contains(event);
    }
}
