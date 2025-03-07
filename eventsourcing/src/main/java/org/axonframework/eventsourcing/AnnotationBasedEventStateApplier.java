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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;


/**
 * Implementation of {@link EventStateApplier} that applies state changes through {@link EventSourcingHandler} annotated
 * methods using an {@link AnnotatedHandlerInspector}.
 *
 * @param <M> The type of model to apply state changes to
 * @author Mateusz Nowak
 * @see EventSourcingHandler
 * @see AnnotatedHandlerInspector
 * @since 5.0.0
 */
public class AnnotationBasedEventStateApplier<M> implements EventStateApplier<M> {

    private final AnnotatedHandlerInspector<M> inspector;

    /**
     * Initialize a new annotation-based {@link EventStateApplier}.
     *
     * @param modelType The type of model this instance will handle state changes for.
     */
    public AnnotationBasedEventStateApplier(@Nonnull Class<M> modelType) {
        this(
                modelType,
                AnnotatedHandlerInspector.inspectType(modelType,
                                                      ClasspathParameterResolverFactory.forClass(modelType),
                                                      ClasspathHandlerDefinition.forClass(modelType))
        );
    }

    /**
     * Initialize a new annotation-based {@link EventStateApplier}.
     *
     * @param modelType The type of model this instance will handle state changes for.
     * @param inspector The inspector to use to find the annotated handlers on the model.
     */
    public AnnotationBasedEventStateApplier(@Nonnull Class<M> modelType,
                                            @Nonnull AnnotatedHandlerInspector<M> inspector
    ) {
        requireNonNull(modelType, "Model Type may not be null");
        this.inspector = requireNonNull(inspector,
                                        "Annotated Handler Inspector may not be null");
    }

    @Override
    public M apply(@Nonnull M state, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext processingContext) {
        requireNonNull(state, "Model may not be null");
        requireNonNull(event, "Event Message may not be null");

        try {
            var eventHandlerResult = handle(state, event, processingContext).join();
            return modelFromStreamResultOrUpdatedExisting(eventHandlerResult, state);
        } catch (Exception e) {
            throw new StateEvolvingException(
                    "Failed to apply event [" + event.type() + "] in order to evolve [" + state.getClass() + "] state",
                    e);
        }
    }

    private CompletableFuture<? extends MessageStream.Entry<?>> handle(
            M model,
            EventMessage<?> event,
            ProcessingContext processingContext
    ) {
        var listenerType = model.getClass();
        var handler = inspector.getHandlers(listenerType)
                               .filter(h -> h.canHandle(event, processingContext))
                               .findFirst();
        if (handler.isPresent()) {
            var interceptor = inspector.chainedInterceptor(listenerType);
            var stream = interceptor.handle(event, processingContext, model, handler.get());
            return stream.first().asCompletableFuture();
        }
        return CompletableFuture.completedFuture(null);
    }

    private M modelFromStreamResultOrUpdatedExisting(MessageStream.Entry<?> potentialModelFromStream, M existing) {
        if (potentialModelFromStream != null) {
            var resultPayload = potentialModelFromStream.message().getPayload();
            if (resultPayload != null && existing.getClass().isAssignableFrom(resultPayload.getClass())) {
                //noinspection unchecked
                return (M) existing.getClass().cast(resultPayload);
            }
        }
        return existing;
    }
}