/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.domain.DomainEventMessage;
import org.axonframework.eventhandling.annotation.AnnotationEventHandlerInvoker;
import org.axonframework.eventsourcing.AbstractEventSourcedAggregateRoot;
import org.axonframework.eventsourcing.IncompatibleAggregateException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import javax.persistence.MappedSuperclass;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;
import static org.axonframework.common.ReflectionUtils.fieldsOf;

/**
 * Convenience super type for aggregate roots that have their event handler methods annotated with the {@link
 * org.axonframework.eventhandling.annotation.EventHandler} annotation.
 * <p/>
 * Implementations can call the {@link #apply(Object)} method to have an event applied. *
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.annotation.EventHandler
 * @since 0.1
 */
@MappedSuperclass
public abstract class AbstractAnnotatedAggregateRoot extends AbstractEventSourcedAggregateRoot {

    private static final long serialVersionUID = -1206026570158467937L;
    private transient AnnotationEventHandlerInvoker eventHandlerInvoker;
    private transient Field identifierField;

    /**
     * Initialize the aggregate root with a random identifier.
     */
    protected AbstractAnnotatedAggregateRoot() {
        super();
        eventHandlerInvoker = new AnnotationEventHandlerInvoker(this);
    }

    /**
     * Calls the appropriate {@link org.axonframework.eventhandling.annotation.EventHandler} annotated handler with the
     * provided event.
     *
     * @param event The event to handle
     * @see org.axonframework.eventhandling.annotation.EventHandler
     */
    @Override
    protected void handle(DomainEventMessage event) {
        if (eventHandlerInvoker == null) {
            eventHandlerInvoker = new AnnotationEventHandlerInvoker(this);
        }
        eventHandlerInvoker.invokeEventHandlerMethod(event);
    }

    @Override
    public Object getIdentifier() {
        if (identifierField == null) {
            identifierField = locateIdentifierField(this);
        }
        try {
            return identifierField.get(this);
        } catch (IllegalAccessException e) {
            throw new IncompatibleAggregateException(format("The field [%s.%s] is not accessible.",
                                                            getClass().getSimpleName(),
                                                            identifierField.getName()), e);
        }
    }

    private Field locateIdentifierField(AbstractAnnotatedAggregateRoot instance) {
        for (Field candidate : fieldsOf(instance.getClass())) {
            if (containsIdentifierAnotation(candidate.getAnnotations())) {
                ensureAccessible(candidate);
                return candidate;
            }
        }
        throw new IncompatibleAggregateException(format("The aggregate class [%s] does not specify an Identifier. "
                                                                + "Ensure that the field containing the aggregate "
                                                                + "identifier is annotated with @AggregateIdentifier.",
                                                        instance.getClass().getSimpleName()));
    }

    private boolean containsIdentifierAnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof AggregateIdentifier) {
                return true;
            } else if ("java.persistence.Id".equals(annotation.getClass().getName())) {
                return true;
            }
        }
        return false;
    }
}
