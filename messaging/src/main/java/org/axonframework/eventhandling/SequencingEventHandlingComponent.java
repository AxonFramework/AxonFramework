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

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SequencingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private static final Context.ResourceKey<Map<Object, MessageStream.Empty<Message<Void>>>> SEQUENCE_TRACKING_KEY =
            Context.ResourceKey.withLabel("SequenceTrackingMap");

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate The instance to delegate calls to.
     */
    public SequencingEventHandlingComponent(@Nonnull EventHandlingComponent delegate) {
        super(delegate);
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        Object sequenceIdentifier = sequenceIdentifierFor(event, context);

        // Get or create the sequence tracking map
        Map<Object, MessageStream.Empty<Message<Void>>> sequenceMap = context.computeResourceIfAbsent(
                SEQUENCE_TRACKING_KEY,
                ConcurrentHashMap::new
        );

        // Check if there's an ongoing invocation for this sequence
        MessageStream.Empty<Message<Void>> previousInvocation = sequenceMap.get(sequenceIdentifier);

        if (previousInvocation == null) {
            // No previous invocation - execute immediately
            MessageStream.Empty<Message<Void>> currentInvocation = delegate.handle(event, context);
            sequenceMap.put(sequenceIdentifier, currentInvocation);
            return currentInvocation;
        } else {
            // Previous invocation exists - chain after it completes
            CompletableFuture<Message<Void>> chainedFuture =
                    previousInvocation.asCompletableFuture()
                            .thenCompose(ignored -> {
                                // Execute the current event after the previous one completes
                                MessageStream.Empty<Message<Void>> currentInvocation = delegate.handle(event, context);
                                return currentInvocation.asCompletableFuture();
                            })
                            .thenApply(ignored -> null);

            // Create a new stream from the chained future
            MessageStream.Empty<Message<Void>> chainedInvocation = MessageStream.fromFuture(chainedFuture).ignoreEntries();

            // Update the sequence map with the new invocation
            sequenceMap.put(sequenceIdentifier, chainedInvocation);

            return chainedInvocation;
        }
    }
}
