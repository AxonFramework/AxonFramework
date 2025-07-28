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
import org.axonframework.eventhandling.async.SequencingPolicy;
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

import static java.util.Objects.requireNonNull;

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
public class SequencingEventHandlingComponent2 extends DelegatingEventHandlingComponent {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final SequencingPolicy sequencingPolicy;
    private final Context.ResourceKey<Map<Object, CompletableFuture<?>>> INVOCATIONS_KEY =
            Context.ResourceKey.withLabel("SequencingEventHandlingComponent2.invocations");

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate         The instance to delegate calls to.
     * @param sequencingPolicy The policy to determine the sequence identifier for events.
     */
    public SequencingEventHandlingComponent2(
            @Nonnull SequencingPolicy sequencingPolicy,
            @Nonnull EventHandlingComponent delegate
    ) {
        super(delegate);
        this.sequencingPolicy = requireNonNull(sequencingPolicy, "SequencingPolicy may not be null");
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        Map<Object, CompletableFuture<?>> invocations =
                context.computeResourceIfAbsent(INVOCATIONS_KEY, ConcurrentHashMap::new);
        Object sequenceIdentifier = sequenceIdentifierFor(event, context);

        CompletableFuture<Message<Void>> resultFuture = new CompletableFuture<>();
        MessageStream.Empty<Message<Void>> resultStream = MessageStream.fromFuture(resultFuture).ignoreEntries();

        invocations.compute(sequenceIdentifier, (sq, previousInvocation) -> {
            CompletableFuture<?> nextInvocation;
            if (previousInvocation == null) {
                logger.debug("No previous invocation for sequence identifier [{}]. Handling immediately.",
                             sequenceIdentifier);
                nextInvocation = delegate.handle(event, context).asCompletableFuture();
            } else {
                logger.debug(
                        "Previous invocation found for sequence identifier [{}]. Chaining the current event handling.",
                        sequenceIdentifier);
                nextInvocation = previousInvocation.thenCompose(
                        (r) -> delegate.handle(event, context).asCompletableFuture()
                );
            }
            nextInvocation.whenComplete((r, e) -> {
                if (e != null) {
                    resultFuture.completeExceptionally(e);
                } else {
                    resultFuture.complete(null);
                }
            });
            return nextInvocation;
        });
        return resultStream;
    }

    @Nonnull
    @Override
    public Object sequenceIdentifierFor(@Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        requireNonNull(event, "Event Message may not be null");
        return sequencingPolicy.getSequenceIdentifierFor(event)
                               .orElseGet(() -> delegate.sequenceIdentifierFor(event, context));
    }
}
