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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AbstractMessageHandler;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.annotation.MethodMessageHandlerInspector;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.common.property.Property;
import org.axonframework.common.property.PropertyAccessStrategy;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.Message;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.axonframework.common.ReflectionUtils.fieldsOf;

/**
 * Handler inspector that finds annotated constructors and methods on a given aggregate type and provides handlers for
 * those methods.
 *
 * @param <T> the type of aggregate inspected by this class
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateCommandHandlerInspector<T extends AggregateRoot> {

    private static final Logger logger = LoggerFactory.getLogger(AggregateCommandHandlerInspector.class);

    private final List<ConstructorCommandMessageHandler<T>> constructorCommandHandlers =
            new LinkedList<ConstructorCommandMessageHandler<T>>();
    private final List<AbstractMessageHandler> handlers;

    /**
     * Initialize an MethodMessageHandlerInspector, where the given <code>annotationType</code> is used to annotate the
     * Event Handler methods.
     *
     * @param targetType               The targetType to inspect methods on
     * @param parameterResolverFactory The strategy for resolving parameter values
     */
    @SuppressWarnings({"unchecked"})
    protected AggregateCommandHandlerInspector(Class<T> targetType, ParameterResolverFactory parameterResolverFactory) {
        MethodMessageHandlerInspector inspector = MethodMessageHandlerInspector.getInstance(targetType,
                                                                                            CommandHandler.class,
                                                                                            parameterResolverFactory,
                                                                                            true);
        handlers = new ArrayList<AbstractMessageHandler>(inspector.getHandlers());
        processNestedEntityCommandHandlers(targetType, parameterResolverFactory, new RootEntityAccessor(targetType));
        for (Constructor constructor : targetType.getConstructors()) {
            if (constructor.isAnnotationPresent(CommandHandler.class)) {
                constructorCommandHandlers.add(
                        ConstructorCommandMessageHandler.forConstructor(constructor, parameterResolverFactory));
            }
        }
    }

    private void processNestedEntityCommandHandlers(Class<?> targetType,
                                                    ParameterResolverFactory parameterResolverFactory,
                                                    final EntityAccessor entityAccessor) {
        for (final Field field : fieldsOf(targetType)) {
            EntityAccessor newEntityAccessor = null;
            if (field.isAnnotationPresent(CommandHandlingMember.class)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Field {}.{} is annotated with @CommandHandlingMember. "
                                         + "Checking {} for Command Handlers",
                                 targetType.getSimpleName(), field.getName(), field.getType().getSimpleName()
                    );
                }
                newEntityAccessor = new EntityFieldAccessor(entityAccessor, field);
            } else if (field.isAnnotationPresent(CommandHandlingMemberCollection.class)) {
                CommandHandlingMemberCollection annotation = field.getAnnotation(CommandHandlingMemberCollection.class);
                if (!Collection.class.isAssignableFrom(field.getType())) {
                    throw new AxonConfigurationException(String.format(
                            "Field %s.%s is annotated with @CommandHandlingMemberCollection, but the declared type of "
                                    + "the field is not assignable to java.util.Collection.",
                            targetType.getSimpleName(), field.getName()));
                }
                Class<?> entityType = annotation.entityType();
                if (AbstractAnnotatedEntity.class.equals(entityType)) {
                    final Type genericType = field.getGenericType();
                    if (genericType == null
                            || !(genericType instanceof ParameterizedType)
                            || ((ParameterizedType) genericType).getActualTypeArguments().length == 0) {
                        throw new AxonConfigurationException(String.format(
                                "Field %s.%s is annotated with @CommandHandlingMemberCollection, but the entity"
                                        + " type is not indicated on the annotation, "
                                        + "nor can it be deducted from the generic parameters",
                                targetType.getSimpleName(), field.getName()));
                    }
                    entityType = (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Field {}.{} is annotated with @CommandHandlingMemberCollection. "
                                         + "Checking {} for Command Handlers",
                                 targetType.getSimpleName(), field.getName(), entityType.getSimpleName()
                    );
                }
                newEntityAccessor = new EntityCollectionFieldAccessor(entityType, annotation, entityAccessor, field);
            }

            if (newEntityAccessor != null) {
                MethodMessageHandlerInspector fieldInspector = MethodMessageHandlerInspector
                        .getInstance(newEntityAccessor.entityType(),
                                     CommandHandler.class,
                                     parameterResolverFactory,
                                     true);
                for (MethodMessageHandler fieldHandler : fieldInspector.getHandlers()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Found a Command Handler in {} on field {}.{}",
                                     field.getType().getSimpleName(),
                                     entityAccessor.entityType().getName(),
                                     field.getName());
                    }
                    handlers.add(new EntityForwardingMethodMessageHandler(newEntityAccessor, fieldHandler));
                }
                processNestedEntityCommandHandlers(field.getType(), parameterResolverFactory,
                                                   newEntityAccessor);
            }
        }
    }

    /**
     * Returns a list of constructor handlers on the given aggregate type.
     *
     * @return a list of constructor handlers on the given aggregate type
     */
    public List<ConstructorCommandMessageHandler<T>> getConstructorHandlers() {
        return constructorCommandHandlers;
    }

    /**
     * Returns the list of handlers found on target type.
     *
     * @return the list of handlers found on target type
     */
    public List<AbstractMessageHandler> getHandlers() {
        return handlers;
    }

    private interface EntityAccessor {

        Object getInstance(Object aggregateRoot, CommandMessage<?> commandMessage) throws IllegalAccessException;

        Class<?> entityType();
    }

    private static class EntityForwardingMethodMessageHandler extends AbstractMessageHandler {

        private final AbstractMessageHandler handler;
        private final EntityAccessor entityAccessor;

        public EntityForwardingMethodMessageHandler(EntityAccessor entityAccessor, AbstractMessageHandler handler) {
            super(handler);
            this.entityAccessor = entityAccessor;
            this.handler = handler;
        }

        @Override
        public Object invoke(Object target, Message message) throws InvocationTargetException, IllegalAccessException {
            Object entity = entityAccessor.getInstance(target, (CommandMessage) message);
            if (entity == null) {
                throw new IllegalStateException("No appropriate entity available in the aggregate. "
                                                        + "The command cannot be handled.");
            }
            return handler.invoke(entity, message);
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
            return handler.getAnnotation(annotationType);
        }
    }

    private static class EntityFieldAccessor implements EntityAccessor {

        private final EntityAccessor entityAccessor;
        private final Field field;

        public EntityFieldAccessor(EntityAccessor parent, Field field) {
            this.entityAccessor = parent;
            this.field = field;
        }

        @Override
        public Class<?> entityType() {
            return field.getType();
        }

        @Override
        public Object getInstance(Object aggregateRoot, CommandMessage<?> commandMessage)
                throws IllegalAccessException {
            Object entity = entityAccessor.getInstance(aggregateRoot, commandMessage);
            return entity != null ? ReflectionUtils.getFieldValue(field, entity) : null;
        }
    }

    private static class RootEntityAccessor implements EntityAccessor {

        private final Class<?> entityType;

        private RootEntityAccessor(Class<?> entityType) {
            this.entityType = entityType;
        }

        @Override
        public Object getInstance(Object aggregateRoot, CommandMessage<?> commandMessage) {
            return aggregateRoot;
        }

        @Override
        public Class<?> entityType() {
            return entityType;
        }
    }

    private class EntityCollectionFieldAccessor implements EntityAccessor {

        private final Class<?> entityType;
        private final CommandHandlingMemberCollection annotation;
        private final EntityAccessor entityAccessor;
        private final Field field;
        private final Property<Object> entityProperty;

        @SuppressWarnings("unchecked")
        public EntityCollectionFieldAccessor(Class entityType, CommandHandlingMemberCollection annotation,
                                             EntityAccessor entityAccessor, Field field) {
            this.entityProperty = PropertyAccessStrategy.getProperty(entityType, annotation.entityId());
            this.entityType = entityType;
            this.annotation = annotation;
            this.entityAccessor = entityAccessor;
            this.field = field;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object getInstance(Object aggregateRoot, CommandMessage<?> command) throws IllegalAccessException {
            final Object parentEntity = entityAccessor.getInstance(aggregateRoot, command);
            if (parentEntity == null) {
                return null;
            }
            Collection<?> entityCollection = (Collection<?>) ReflectionUtils.getFieldValue(field, parentEntity);
            Property<Object> commandProperty = PropertyAccessStrategy.getProperty(command.getPayloadType(),
                                                                                  annotation.commandTargetProperty());

            if (commandProperty == null) {
                // TODO: Log failure. It seems weird that the property is not present
                return null;
            }
            Object commandId = commandProperty.getValue(command.getPayload());
            if (commandId == null) {
                return null;
            }
            for (Object entity : entityCollection) {
                Object entityId = entityProperty.getValue(entity);
                if (entityId != null && entityId.equals(commandId)) {
                    return entity;
                }
            }
            return null;
        }

        @Override
        public Class<?> entityType() {
            return entityType;
        }
    }
}
