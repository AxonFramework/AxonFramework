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
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link EventHandlingComponent} wrapper that ensures events with the same sequence identifier are handled
 * sequentially.
 * <p>
 * This component uses the {@link ProcessingContext} to track the last invocation for each sequence identifier. When a
 * new event arrives, it checks if there's an ongoing process for its sequence identifier. If so, it chains the new
 * event's handling to occur after the previous one completes. If not, it handles the event immediately.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class SequencingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Context.ResourceKey<Map<Object, CompletableFuture<?>>> sequencedInvocationsKey =
            Context.ResourceKey.withLabel("sequencedInvocations");

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate         The instance to delegate calls to.
     * @param sequencingPolicy The policy to determine the sequence identifier for events.
     */
    public SequencingEventHandlingComponent(
            @Nonnull EventHandlingComponent delegate
    ) {
        super(delegate);
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        Map<Object, CompletableFuture<?>> invocations =
                context.computeResourceIfAbsent(sequencedInvocationsKey, ConcurrentHashMap::new);

        CompletableFuture<Message<Void>> resultFuture = new CompletableFuture<>();

        invocations.compute(
                sequenceIdentifierFor(event, context),
                (sequenceIdentifier, previousInvocation) -> chainedSequenceInvocations(
                        sequenceIdentifier,
                        previousInvocation,
                        event,
                        context
                ).whenComplete((r, e) -> {
                    if (e != null) {
                        resultFuture.completeExceptionally(e);
                    } else {
                        resultFuture.complete(null);
                    }
                }));

        return MessageStream.fromFuture(resultFuture).ignoreEntries();
    }

    private CompletableFuture<?> chainedSequenceInvocations(
            Object sequenceIdentifier,
            CompletableFuture<?> previousInvocation,
            EventMessage<?> event,
            ProcessingContext context
    ) {
        if (previousInvocation == null) {
            logger.debug("Event [{}] | No previous invocation for sequence identifier [{}]. Handling immediately.",
                         event, sequenceIdentifier);
            return delegate.handle(event, context).asCompletableFuture();
        } else {
            logger.debug(
                    "Event [{}] | Previous invocation found for sequence identifier [{}]. Chaining the current event handling.",
                    event,
                    sequenceIdentifier);
            return previousInvocation.thenCompose(
                    (r) -> delegate.handle(event, context).asCompletableFuture()
            );
        }
    }
}
