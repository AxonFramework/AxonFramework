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

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.PriorityAnnotationComparator;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of {@link EventStateApplier} that applies state changes through a list of {@link EventStateApplier}
 * instances. Every instance is called during the application of an event, regardless of whether the event was already
 * handled by another. During construction, the list of {@link EventStateApplier} instances is sorted based on the
 * {@link org.axonframework.common.Priority} annotation on the classes of the instances, if present.
 *
 * @param <M> The model type to apply the event state to.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class MultiEventStateApplier<M> implements EventStateApplier<M>, DescribableComponent {

    private final List<EventStateApplier<M>> eventStateAppliers;

    /**
     * Constructs a multi-{@link EventStateApplier} that applies state changes through the given
     * {@code eventStateAppliers}. The list of {@link EventStateApplier} instances is sorted based on the
     * {@link org.axonframework.common.Priority} annotation on the classes of the instances, if present.
     *
     * @param eventStateAppliers The list of {@link EventStateApplier} instances to apply state changes through.
     */
    public MultiEventStateApplier(List<EventStateApplier<M>> eventStateAppliers) {
        this.eventStateAppliers = new ArrayList<>(eventStateAppliers);
        this.eventStateAppliers.sort(PriorityAnnotationComparator.getInstance());
    }

    /**
     * Constructs a multi-{@link EventStateApplier} that applies state changes through the given
     * {@code eventStateAppliers}. The list of {@link EventStateApplier} instances is sorted based on the
     * {@link org.axonframework.common.Priority} annotation on the classes of the instances, if present.
     *
     * @param eventStateAppliers The list of {@link EventStateApplier} instances to apply state changes through.
     */
    @SafeVarargs
    public MultiEventStateApplier(EventStateApplier<M>... eventStateAppliers) {
        this(List.of(eventStateAppliers));
    }

    @Override
    public M apply(@Nonnull M model, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext processingContext) {
        M result = model;
        for (EventStateApplier<M> eventStateApplier : eventStateAppliers) {
            result = eventStateApplier.apply(result, event, processingContext);
        }
        return result;
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("delegates", Collections.unmodifiableList(eventStateAppliers));
    }
}
