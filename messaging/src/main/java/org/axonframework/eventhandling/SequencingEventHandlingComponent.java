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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of an EventHandlingComponent that ensures events with the same sequence identifier
 * are handled sequentially. This component delegates the actual event handling to another EventHandlingComponent,
 * but ensures that events with the same sequence identifier (as determined by the delegate's
 * {@link #sequenceIdentifierFor(EventMessage, ProcessingContext)} method) are processed one by one.
 * <p>
 * This implementation manages a queue of tasks for each sequence identifier to ensure sequential processing.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SequencingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private static final Logger logger = LoggerFactory.getLogger(SequencingEventHandlingComponent.class);

    private final ResourceKey<ConcurrentMap<Object, CompletableFuture<Void>>> sequencingKey =
            ResourceKey.withLabel("sequencingEventHandlers");

    private final ConcurrentMap<Object, CompletableFuture<Void>> inProgress;

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate The instance to delegate calls to.
     */
    public SequencingEventHandlingComponent(@Nonnull EventHandlingComponent delegate) {
        super(delegate);
        this.inProgress = new ConcurrentHashMap<>();
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        Object sequenceIdentifier = sequenceIdentifierFor(event, context);
        logger.debug("Handling event [{}] with sequence identifier [{}] in {}",
                event.getIdentifier(), sequenceIdentifier, context);

        ConcurrentMap<Object, CompletableFuture<Void>> sequencingMap =
                context.computeResourceIfAbsent(sequencingKey, ConcurrentHashMap::new);

        CompletableFuture<Void> currentTask = sequencingMap.computeIfAbsent(sequenceIdentifier, id -> {
            // No task for this sequence identifier in the current context
            CompletableFuture<Void> previousTask = inProgress.get(id);
            if (previousTask == null) {
                // No previous task, we can proceed immediately
                logger.debug("No previous task found for sequence [{}]. Proceeding immediately.", id);
                return CompletableFuture.completedFuture(null);
            } else {
                // Wait for the previous task to complete
                logger.debug("Previous task detected. Will wait for it to complete before handling [{}]", id);
                return previousTask.exceptionally(e -> {
                    logger.debug("Previous task for sequence [{}] completed with exception", id, e);
                    return null;
                });
            }
        });

        // Create a new future that will be completed when this event is processed
        CompletableFuture<Void> taskCompletion = new CompletableFuture<>();

        // Chain the current task with the actual event handling
        currentTask.thenRun(() -> {
            try {
                logger.debug("Processing event [{}] with sequence identifier [{}]", 
                        event.getIdentifier(), sequenceIdentifier);
                
                // Handle the event
                MessageStream.Empty<Message<Void>> result = delegate.handle(event, context);
                
                // Register completion handlers
                context.whenComplete(pc -> {
                    logger.debug("Processing of event [{}] with sequence identifier [{}] completed successfully",
                            event.getIdentifier(), sequenceIdentifier);
                    
                    // Update the inProgress map with this task
                    inProgress.put(sequenceIdentifier, taskCompletion);
                    
                    // Complete the task
                    taskCompletion.complete(null);
                    
                    // Remove from the current context's map
                    sequencingMap.remove(sequenceIdentifier);
                });
                
                context.onError((pc, phase, error) -> {
                    logger.debug("Processing of event [{}] with sequence identifier [{}] completed with error",
                            event.getIdentifier(), sequenceIdentifier, error);
                    
                    // Update the inProgress map with this task
                    inProgress.put(sequenceIdentifier, taskCompletion);
                    
                    // Complete the task
                    taskCompletion.complete(null);
                    
                    // Remove from the current context's map
                    sequencingMap.remove(sequenceIdentifier);
                });
            } catch (Exception e) {
                logger.error("Error handling event [{}] with sequence identifier [{}]",
                        event.getIdentifier(), sequenceIdentifier, e);
                
                // Update the inProgress map with this task
                inProgress.put(sequenceIdentifier, taskCompletion);
                
                // Complete the task
                taskCompletion.complete(null);
                
                // Remove from the current context's map
                sequencingMap.remove(sequenceIdentifier);
                
                // Rethrow the exception
                throw e;
            }
        });

        // Return an empty message stream
        return MessageStream.empty().cast();
    }
}
