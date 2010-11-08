/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.annotation;

import org.axonframework.domain.DomainEvent;
import org.axonframework.eventhandling.annotation.AnnotationEventHandlerInvoker;
import org.axonframework.eventsourcing.AbstractEventSourcedEntity;

/**
 * Convenience super type for entities (other than aggregate roots) that have their event handler methods annotated with
 * the {@link org.axonframework.eventhandling.annotation.EventHandler} annotation.
 * <p/>
 * Note that each entity receive <strong>all</strong> events applied in the entire aggregate. Entities are responsible
 * for filtering out the actual events to take action on.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractAnnotatedEntity extends AbstractEventSourcedEntity {

    private final AnnotationEventHandlerInvoker eventHandlerInvoker = new AnnotationEventHandlerInvoker(this);

    /**
     * Calls the appropriate {@link org.axonframework.eventhandling.annotation.EventHandler} annotated handler with the
     * provided event.
     *
     * @param event The event to handle
     * @see org.axonframework.eventhandling.annotation.EventHandler
     */
    @Override
    protected void handle(DomainEvent event) {
        eventHandlerInvoker.invokeEventHandlerMethod(event);
    }

}
