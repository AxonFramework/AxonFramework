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
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;


/**
 * Implementation of {@link EventStateApplier} that applies state changes through {@link EventSourcingHandler} annotated
 * methods using an {@link AnnotatedHandlerInspector}.
 *
 * @param <M> The type of model to apply state changes to
 * @see EventSourcingHandler
 * @see AnnotatedHandlerInspector
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class AnnotationBasedEventStateApplier<M> implements EventStateApplier<M> {

    private final AnnotatedHandlerInspector<Object> inspector;

    /**
     * Initialize a new {@link AnnotationBasedEventStateApplier}.
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
     * Initialize a new {@link AnnotationBasedEventStateApplier}.
     *
     * @param modelType           The type of model this instance will handle state changes for.
     * @param inspector           The inspector to use to find the annotated handlers on the model.
     */
    public AnnotationBasedEventStateApplier(@Nonnull Class<M> modelType,
                                            @Nonnull AnnotatedHandlerInspector<Object> inspector
    ) {
        requireNonNull(modelType, "Model Type may not be null");
        this.inspector = requireNonNull(inspector,
                                        "Annotated Handler Inspector may not be null");
    }

    @Override
    public M apply(@Nonnull M model, @Nonnull EventMessage<?> event) {
        requireNonNull(model, "Model may not be null");
        requireNonNull(event, "Event Message may not be null");

        try {
            var result = handle(model, event);
            if (result != null && model.getClass().isAssignableFrom(result.getClass())) {
                //noinspection unchecked
                return (M) model.getClass().cast(result);
            }
        } catch (Exception e) {
            throw new StateEvolvingException(
                    "Failed to apply event [" + event.type() + "] in order to evolve [" + model.getClass() + "] state",
                    e);
        }
        return model;
    }

    private Object handle(M model, EventMessage<?> event) throws Exception {
        var listenerType = model.getClass();
        var handler =
                inspector.getHandlers(listenerType)
                         .filter(h -> h.canHandle(event, null))
                         .findFirst();
        if (handler.isPresent()) {
            var interceptor = inspector.chainedInterceptor(listenerType);
            return interceptor.handleSync(event, model, handler.get());
        }
        return null;
    }
}