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

import java.util.Set;

/**
 * Interface describing a group of {@link EntityEvolver EntityEvolvers} belonging to a single entity of type {@code E},
 * forming an event sourced component.
 * <p>
 * The {@link #supportedEvents()} describes the events supported by this entity evolver.
 *
 * @param <E> The entity type to evolve.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface EventSourcedComponent<E> extends EntityEvolver<E> {

    /**
     * All supported {@link EventMessage events}, referenced through a {@link QualifiedName}.
     *
     * @return All supported {@link EventMessage events}, referenced through a {@link QualifiedName}.
     */
    Set<QualifiedName> supportedEvents();
}
