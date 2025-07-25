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
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link DelegatingEventHandlingComponent} that ensures events with the same sequence identifier
 * are processed sequentially. Events with different sequence identifiers can be processed concurrently.
 * <p>
 * This component uses the {@link ProcessingContext} to manage the sequencing state, storing a map of
 * sequence identifiers to message streams that are currently being processed.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SequencingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private static final Logger logger = LoggerFactory.getLogger(SequencingEventHandlingComponent.class);

    /**
     * ResourceKey for storing the map of sequence identifiers to message streams in the ProcessingContext.
     */
    private final ResourceKey<ConcurrentMap<Object, MessageStream<?>>> sequencingMapKey =
            ResourceKey.withLabel("sequencingMap");

    /**
     * Constructs a new SequencingEventHandlingComponent with the given delegate.
     *
     * @param delegate The EventHandlingComponent to delegate calls to.
     */
    public SequencingEventHandlingComponent(@Nonnull EventHandlingComponent delegate) {
        super(delegate);
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                    @Nonnull ProcessingContext context) {
        Object sequenceIdentifier = sequenceIdentifierFor(event, context);
        logger.debug("Handling event [{}] with sequence identifier [{}]", event.getIdentifier(), sequenceIdentifier);

        // Get or create the map of sequence identifiers to message streams
        ConcurrentMap<Object, MessageStream<?>> sequencingMap =
                context.computeResourceIfAbsent(sequencingMapKey, ConcurrentHashMap::new);

        // Check if there's already a message stream for this sequence identifier
        MessageStream<?> currentStream = sequencingMap.get(sequenceIdentifier);
        
        if (currentStream == null) {
            // No current stream, create a new one and process the event immediately
            logger.debug("No current stream for sequence identifier [{}], processing immediately", sequenceIdentifier);
            MessageStream.Empty<Message<Void>> result = delegate.handle(event, context);
            sequencingMap.put(sequenceIdentifier, result);
            
            // When the stream completes, remove it from the map
            result.whenComplete(() -> {
                logger.debug("Processing completed for sequence identifier [{}], removing from map", sequenceIdentifier);
                sequencingMap.remove(sequenceIdentifier, result);
            });
            
            return result;
        } else {
            // There's already a stream for this sequence identifier, wait for it to complete
            logger.debug("Found existing stream for sequence identifier [{}], waiting for it to complete", sequenceIdentifier);
            
            // Create a reference to hold the actual stream that will be used once the current stream completes
            AtomicReference<MessageStream.Empty<Message<Void>>> resultStreamRef = new AtomicReference<>(MessageStream.empty());
            
            // Create a custom MessageStream.Empty implementation that delegates to the actual stream
            MessageStream.Empty<Message<Void>> delegatingStream = new MessageStream.Empty<Message<Void>>() {
                @Override
                public Optional<MessageStream.Entry<Message<Void>>> next() {
                    return resultStreamRef.get().next();
                }

                @Override
                public Optional<MessageStream.Entry<Message<Void>>> peek() {
                    return resultStreamRef.get().peek();
                }

                @Override
                public void onAvailable(@Nonnull Runnable callback) {
                    resultStreamRef.get().onAvailable(callback);
                }

                @Override
                public Optional<Throwable> error() {
                    return resultStreamRef.get().error();
                }

                @Override
                public boolean isCompleted() {
                    return resultStreamRef.get().isCompleted();
                }

                @Override
                public boolean hasNextAvailable() {
                    return resultStreamRef.get().hasNextAvailable();
                }

                @Override
                public void close() {
                    resultStreamRef.get().close();
                }
            };
            
            // When the current stream completes, process this event
            currentStream.whenComplete(() -> {
                logger.debug("Previous stream completed for sequence identifier [{}], processing next event", sequenceIdentifier);
                MessageStream.Empty<Message<Void>> result = delegate.handle(event, context);
                sequencingMap.put(sequenceIdentifier, result);
                
                // When this stream completes, remove it from the map
                result.whenComplete(() -> {
                    logger.debug("Processing completed for sequence identifier [{}], removing from map", sequenceIdentifier);
                    sequencingMap.remove(sequenceIdentifier, result);
                });
                
                // Update the reference to the actual stream
                resultStreamRef.set(result);
            });
            
            return delegatingStream;
        }
    }
}