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
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.function.BiFunction;
import javax.annotation.Nonnull;

/**
 * EventStateApplier implementation that applies a single type of event to a model of type {@code M} based on the given
 * {@code eventStateApplier}. Both the event type and the payload type are checked before applying the event to the
 * model.
 *
 * @param <P> The payload type of the event to apply.
 * @param <M> The model type to apply the event state to.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class SingleEventEventStateApplier<P, M> implements EventStateApplier<M> {

    private final QualifiedName qualifiedName;
    private final Class<P> payloadType;
    private final BiFunction<M, P, M> eventStateApplier;

    /**
     * Constructs a single-{@link EventStateApplier} that applies state changes through the given
     * {@code eventStateApplier}. The event type and payload type are checked before applying the event to the model.
     *
     * @param qualifiedName     The event type to check against.
     * @param payloadType       The payload type to check against.
     * @param eventStateApplier The function to apply the event to the model.
     */
    public SingleEventEventStateApplier(QualifiedName qualifiedName, Class<P> payloadType, BiFunction<M, P, M> eventStateApplier) {
        this.qualifiedName = qualifiedName;
        this.payloadType = payloadType;
        this.eventStateApplier = eventStateApplier;
    }

    @Override
    public M apply(@Nonnull M model, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext processingContext) {
        if (qualifiedName.equals(event.type().qualifiedName()) && payloadType.isInstance(event.getPayload())) {
            P payload = payloadType.cast(event.getPayload());
            return eventStateApplier.apply(model, payload);
        }
        return model;
    }
}
