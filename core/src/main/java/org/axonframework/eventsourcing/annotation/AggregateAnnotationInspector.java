/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.HandlerDefinition;
import org.axonframework.common.annotation.MessageHandlerInvoker;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventsourcing.EventSourcedEntity;
import org.axonframework.eventsourcing.IncompatibleAggregateException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;
import static org.axonframework.common.CollectionUtils.filterByType;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;
import static org.axonframework.common.ReflectionUtils.fieldsOf;

/**
 * Inspects objects to find aggregate specific annotations, such as {@link AggregateIdentifier} and {@link
 * EventSourcedMember}. The inspector can also create {@link org.axonframework.common.annotation.MessageHandlerInvoker}
 * instances to invoke {@link org.axonframework.eventsourcing.annotation.EventSourcingHandler} annotated methods.
 *
 * @author Allard Buijze
 * @since 2.0
 * @see org.axonframework.eventsourcing.annotation.EventSourcingHandler
 * @see org.axonframework.eventhandling.annotation.EventHandler
 */
public final class AggregateAnnotationInspector {

    private static final Map<Class<?>, AggregateAnnotationInspector> INSTANCES = new ConcurrentHashMap<>();
    private final Field[] childEntityFields;
    private final Field identifierField;
    private final ParameterResolverFactory parameterResolverFactory;

    /**
     * Returns (or creates) an inspector for the given <code>entityType</code>. If an instance is already created for
     * that type, that instance may be returned. Otherwise, a new inspector is created.
     *
     * @param entityType               The type of entity (aggregate root or simple member) to get an inspector for
     * @param parameterResolverFactory The factory providing access to the parameter resolvers
     * @return an inspector for the given entity type
     */
    public static AggregateAnnotationInspector getInspector(Class<?> entityType,
                                                            ParameterResolverFactory parameterResolverFactory) {
        AggregateAnnotationInspector inspector = INSTANCES.get(entityType);
        if (inspector == null || !parameterResolverFactory.equals(inspector.parameterResolverFactory)) {
            inspector = new AggregateAnnotationInspector(entityType, parameterResolverFactory);
            INSTANCES.put(entityType, inspector);
        }
        return inspector;
    }

    @SuppressWarnings("unchecked")
    private AggregateAnnotationInspector(Class<?> entityType, ParameterResolverFactory parameterResolverFactory) {
        List<Field> annotatedFields = new ArrayList<>();
        for (Field field : ReflectionUtils.fieldsOf(entityType)) {
            if (field.isAnnotationPresent(EventSourcedMember.class)) {
                annotatedFields.add(field);
            }
        }
        childEntityFields = annotatedFields.toArray(new Field[annotatedFields.size()]);
        // if entityType is an aggregate root, detect it's identifier field
        if (AbstractAnnotatedAggregateRoot.class.isAssignableFrom(entityType)) {
            identifierField = locateIdentifierField((Class<? extends AbstractAnnotatedAggregateRoot>) entityType);
        } else {
            identifierField = null;
        }
        this.parameterResolverFactory = parameterResolverFactory;
    }

    /**
     * Creates a new MessageHandlerInvoker that invokes methods on the given <code>instance</code>.
     *
     * @param instance The object (typically an entity) to create the MessageHandlerInvoker for
     * @return a MessageHandlerInvoker that invokes handler methods on given <code>instance</code>
     */
    public MessageHandlerInvoker createEventHandlerInvoker(Object instance) {
        return new MessageHandlerInvoker(instance, parameterResolverFactory, false,
                                         AggregatedEventSourcingHandlerDefinition.INSTANCE);
    }

    /**
     * Returns the child entities of given <code>instance</code>. Entities are detected if they are contained in fields
     * annotated with {@link EventSourcedMember}. If the annotated field is a collection, map or array, each member of
     * that collection, the map's key set, the map's value set or the array that implements the {@link
     * EventSourcedEntity} interface is returned.
     *
     * @param instance The instance to find child entities in
     * @return a collection of child entities found in the given <code>instance</code>.
     */
    public Collection<EventSourcedEntity> getChildEntities(Object instance) {
        if (childEntityFields.length == 0 || instance == null) {
            return null;
        }
        List<EventSourcedEntity> children = new ArrayList<>();
        for (Field childEntityField : childEntityFields) {
            Object fieldValue = ReflectionUtils.getFieldValue(childEntityField, instance);
            if (EventSourcedEntity.class.isInstance(fieldValue)) {
                children.add((EventSourcedEntity) fieldValue);
            } else if (Iterable.class.isInstance(fieldValue)) {
                // it's a collection
                Iterable<?> iterable = (Iterable<?>) fieldValue;
                children.addAll(filterByType(iterable, EventSourcedEntity.class));
            } else if (Map.class.isInstance(fieldValue)) {
                Map map = (Map) fieldValue;
                children.addAll(filterByType(map.keySet(), EventSourcedEntity.class));
                children.addAll(filterByType(map.values(), EventSourcedEntity.class));
            } else if (fieldValue != null && childEntityField.getType().isArray()) {
                for (int i = 0; i < Array.getLength(fieldValue); i++) {
                    Object value = Array.get(fieldValue, i);
                    if (EventSourcedEntity.class.isInstance(value)) {
                        children.add((EventSourcedEntity) value);
                    }
                }
            }
        }
        return children;
    }

    /**
     * Returns the identifier of the given <code>aggregateRoot</code>. Since only the aggregate root carries the
     * aggregate's identifier, this method cannot be invoked with any other entity than the aggregate's root.
     * <p/>
     * The field carrying the aggregate identifier must be annotated with {@link AggregateIdentifier}.
     *
     * @param aggregateRoot The aggregate root to find the aggregate on
     * @param <I>           The type of identifier declared on the aggregate root
     * @return the value contained in the field annotated with {@link AggregateIdentifier}
     */
    @SuppressWarnings("unchecked")
    public <I> I getIdentifier(AbstractAnnotatedAggregateRoot<I> aggregateRoot) {
        if (identifierField == null) {
            throw new IncompatibleAggregateException(
                    format("The aggregate class [%s] does not specify an Identifier. "
                                   + "Ensure that the field containing the aggregate "
                                   + "identifier is annotated with @AggregateIdentifier.",
                           aggregateRoot.getClass().getSimpleName()));
        }
        return (I) ReflectionUtils.getFieldValue(identifierField, aggregateRoot);
    }

    private Field locateIdentifierField(Class<? extends AbstractAnnotatedAggregateRoot> aggregateRootType) {
        for (Field candidate : fieldsOf(aggregateRootType)) {
            if (containsIdentifierAnnotation(candidate.getAnnotations())) {
                ensureAccessible(candidate);
                return candidate;
            }
        }
        return null;
    }

    private boolean containsIdentifierAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof AggregateIdentifier) {
                return true;
            } else if (annotation.toString().startsWith("@javax.persistence.Id(")) {
                // this way, the JPA annotations don't need to be on the classpath
                return true;
            }
        }
        return false;
    }

    private static class AggregatedEventSourcingHandlerDefinition implements HandlerDefinition<Method> {

        private static final AggregatedEventSourcingHandlerDefinition INSTANCE = new AggregatedEventSourcingHandlerDefinition();

        @Override
        public boolean isMessageHandler(Method member) {
            return member.isAnnotationPresent(EventSourcingHandler.class)
                    || member.isAnnotationPresent(EventHandler.class);
        }

        @Override
        public Class<?> resolvePayloadFor(Method member) {
            EventSourcingHandler handlerAnnotation = member.getAnnotation(EventSourcingHandler.class);
            Class<?> definedPayload = null;
            if (handlerAnnotation != null) {
                definedPayload = handlerAnnotation.eventType();
            } else {
                EventHandler legacyAnnotation = member.getAnnotation(EventHandler.class);
                if (legacyAnnotation != null) {
                    definedPayload = legacyAnnotation.eventType();
                }
            }
            return definedPayload == Void.class ? null : definedPayload;
        }

        @Override
        public String toString() {
            return "AnnotatedEventSourcingMemberDefinition";
        }
    }
}
