/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.modelling.command.AggregateRoot;
import org.axonframework.modelling.command.AggregateVersion;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierValidator;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

/**
 * AggregateMetaModelFactory implementation that uses annotations on the target aggregate's members to build up the
 * meta model of the aggregate.
 */
public class AnnotatedAggregateMetaModelFactory implements AggregateMetaModelFactory {

    private final Map<Class<?>, AnnotatedAggregateModel> registry;
    private final ParameterResolverFactory parameterResolverFactory;
    private final HandlerDefinition handlerDefinition;

    /**
     * Initializes an instance which uses the default, classpath based, ParameterResolverFactory to detect parameters
     * for annotated handlers.
     */
    public AnnotatedAggregateMetaModelFactory() {
        this(ClasspathParameterResolverFactory.forClassLoader(Thread.currentThread().getContextClassLoader()));
    }

    /**
     * Initializes an instance which uses the given {@code parameterResolverFactory} to detect parameters for
     * annotated handlers.
     *
     * @param parameterResolverFactory to resolve parameter values of annotated handlers with
     */
    public AnnotatedAggregateMetaModelFactory(ParameterResolverFactory parameterResolverFactory) {
        this(parameterResolverFactory,
             ClasspathHandlerDefinition.forClassLoader(Thread.currentThread().getContextClassLoader()));
    }

    /**
     * Initializes an instance which uses the given {@code parameterResolverFactory} to detect parameters for
     * annotated handlers and {@code handlerDefinition} to create concrete handlers.
     *
     * @param parameterResolverFactory to resolve parameter values of annotated handlers with
     * @param handlerDefinition        The handler definition used to create concrete handlers
     */
    public AnnotatedAggregateMetaModelFactory(ParameterResolverFactory parameterResolverFactory,
                                              HandlerDefinition handlerDefinition) {
        this.parameterResolverFactory = parameterResolverFactory;
        this.handlerDefinition = handlerDefinition;
        registry = new ConcurrentHashMap<>();
    }

    /**
     * Shorthand to create a factory instance and inspect the model for the given {@code aggregateType}.
     *
     * @param aggregateType The class of the aggregate to create the model for
     * @param <T>           The type of aggregate described in the model
     * @return The model describing the structure of the aggregate
     */
    public static <T> AggregateModel<T> inspectAggregate(Class<T> aggregateType) {
        return new AnnotatedAggregateMetaModelFactory().createModel(aggregateType);
    }

    /**
     * Shorthand to create a factory instance and inspect the model for the given {@code aggregateType}, using given
     * {@code parameterResolverFactory} to resolve parameter values for annotated handlers.
     *
     * @param aggregateType            The class of the aggregate to create the model for
     * @param parameterResolverFactory to resolve parameter values of annotated handlers with
     * @param <T>                      The type of aggregate described in the model
     * @return The model describing the structure of the aggregate
     */
    public static <T> AggregateModel<T> inspectAggregate(Class<T> aggregateType, ParameterResolverFactory parameterResolverFactory) {
        return new AnnotatedAggregateMetaModelFactory(parameterResolverFactory).createModel(aggregateType);
    }

    /**
     * Shorthand to create a factory instance and inspect the model for the given {@code aggregateType}, using given
     * {@code parameterResolverFactory} to resolve parameter values for annotated handlers and {@code handlerDefinition}
     * to create concrete handlers.
     *
     * @param aggregateType            The class of the aggregate to create the model for
     * @param parameterResolverFactory to resolve parameter values of annotated handlers with
     * @param handlerDefinition        The handler definition used to create concrete handlers
     * @param <T>                      The type of aggregate described in the model
     * @return The model describing the structure of the aggregate
     */
    public static <T> AggregateModel<T> inspectAggregate(Class<T> aggregateType,
                                                         ParameterResolverFactory parameterResolverFactory,
                                                         HandlerDefinition handlerDefinition) {
        return new AnnotatedAggregateMetaModelFactory(parameterResolverFactory, handlerDefinition)
                .createModel(aggregateType);
    }

    @Override
    public <T> AnnotatedAggregateModel<T> createModel(Class<? extends T> aggregateType) {
        if (!registry.containsKey(aggregateType)) {
            AnnotatedHandlerInspector<T> inspector = AnnotatedHandlerInspector.inspectType(aggregateType,
                                                                                           parameterResolverFactory,
                                                                                           handlerDefinition);
            AnnotatedAggregateModel<T> model = new AnnotatedAggregateModel<>(aggregateType, inspector);
            // Add the newly created inspector to the registry first to prevent a StackOverflowError:
            // another call to createInspector with the same inspectedType will return this instance of the inspector.
            registry.put(aggregateType, model);
            model.initialize();
        }
        //noinspection unchecked
        return registry.get(aggregateType);
    }

    private class AnnotatedAggregateModel<T> implements AggregateModel<T> {

        private final Class<? extends T> inspectedType;
        private final List<ChildEntity<T>> children;
        private final AnnotatedHandlerInspector<T> handlerInspector;
        private final List<MessageHandlingMember<? super T>> commandHandlerInterceptors;
        private final List<MessageHandlingMember<? super T>> commandHandlers;
        private final List<MessageHandlingMember<? super T>> eventHandlers;

        private String aggregateType;
        private Field identifierField;
        private Field versionField;
        private String routingKey;

        public AnnotatedAggregateModel(Class<? extends T> aggregateType, AnnotatedHandlerInspector<T> handlerInspector) {
            this.inspectedType = aggregateType;
            this.commandHandlerInterceptors = new ArrayList<>();
            this.commandHandlers = new ArrayList<>();
            this.eventHandlers = new ArrayList<>();
            this.children = new ArrayList<>();
            this.handlerInspector = handlerInspector;
        }

        private void initialize() {
            inspectAggregateType();
            inspectFields();
            prepareHandlers();
        }

        @SuppressWarnings("unchecked")
        private void prepareHandlers() {
            for (MessageHandlingMember<? super T> handler : handlerInspector.getHandlers()) {
                if (handler.unwrap(CommandMessageHandlingMember.class).isPresent()) {
                    commandHandlers.add(handler);
                } else if (handler.unwrap(CommandHandlerInterceptorHandlingMember.class).isPresent()) {
                    commandHandlerInterceptors.add(handler);
                } else {
                    eventHandlers.add(handler);
                }
            }
        }

        private void inspectAggregateType() {
            aggregateType = AnnotationUtils.findAnnotationAttributes(inspectedType, AggregateRoot.class)
                    .map(map -> (String) map.get("type")).filter(i -> i.length() > 0).orElse(inspectedType.getSimpleName());
        }

        private void inspectFields() {
            ServiceLoader<ChildEntityDefinition> childEntityDefinitions =
                    ServiceLoader.load(ChildEntityDefinition.class, inspectedType.getClassLoader());
            for (Field field : ReflectionUtils.fieldsOf(inspectedType)) {
                childEntityDefinitions.forEach(def -> def.createChildDefinition(field, this).ifPresent(child -> {
                    children.add(child);
                    commandHandlers.addAll(child.commandHandlers());
                }));

                AnnotationUtils.findAnnotationAttributes(field, EntityId.class).ifPresent(attributes -> {
                    identifierField = field;
                    if (!"".equals(attributes.get("routingKey"))) {
                        routingKey = (String) attributes.get("routingKey");
                    } else {
                        routingKey = field.getName();
                    }
                });
                if (identifierField == null) {
                    AnnotationUtils.findAnnotationAttributes(field, "javax.persistence.Id").ifPresent(a -> {
                        identifierField = field;
                        routingKey = field.getName();
                    });
                }
                if (identifierField != null) {
                    final Class<?> idClazz = identifierField.getType();
                    if (!IdentifierValidator.getInstance().isValidIdentifier(idClazz)) {
                        throw new AxonConfigurationException(format("Aggregate identifier type [%s] should override Object.toString()", idClazz.getName()));
                    }
                }
                AnnotationUtils.findAnnotationAttributes(field, AggregateVersion.class)
                        .ifPresent(attributes -> versionField = field);
            }
        }

        @SuppressWarnings("unchecked")
        private AnnotatedAggregateModel<T> runtimeModelOf(T target) {
            return modelOf((Class<T>) target.getClass());
        }

        @Override
        public List<MessageHandlingMember<? super T>> commandHandlers() {
            return Collections.unmodifiableList(commandHandlers);
        }

        @Override
        public <C> AnnotatedAggregateModel<C> modelOf(Class<? extends C> entityType) {
            return AnnotatedAggregateMetaModelFactory.this.createModel(entityType);
        }

        @Override
        public Class<? extends T> entityClass() {
            return inspectedType;
        }

        @Override
        public void publish(EventMessage<?> message, T target) {
            if (target != null) {
                runtimeModelOf(target).doPublish(message, target);
            }
        }

        private void doPublish(EventMessage<?> message, T target) {
            getHandler(message).ifPresent(h -> {
                try {
                    h.handle(message, target);
                } catch (Exception e) {
                    throw new MessageHandlerInvocationException(
                            format("Error handling event of type [%s] in aggregate", message.getPayloadType()), e);
                }
            });
            children.forEach(i -> i.publish(message, target));
        }

        @Override
        public String type() {
            return aggregateType;
        }

        @Override
        public Long getVersion(T target) {
            if (versionField != null) {
                return (Long) ReflectionUtils.getFieldValue(versionField, target);
            }
            return null;
        }

        @Override
        public List<MessageHandlingMember<? super T>> commandHandlerInterceptors() {
            return Collections.unmodifiableList(commandHandlerInterceptors);
        }

        /**
         * Returns the {@link MessageHandlingMember} that is capable of handling the given {@code message}. If no member is
         * found an empty optional is returned.
         *
         * @param message the message to find a handler for
         * @return the handler of the message if present on the model
         */
        @SuppressWarnings("unchecked")
        protected Optional<MessageHandlingMember<? super T>> getHandler(Message<?> message) {
            for (MessageHandlingMember<? super T> handler : eventHandlers) {
                if (handler.canHandle(message)) {
                    return Optional.of(handler);
                }
            }
            return Optional.empty();
        }

        @Override
        public Object getIdentifier(T target) {
            if (identifierField != null) {
                return ReflectionUtils.getFieldValue(identifierField, target);
            }
            return null;
        }

        @Override
        public String routingKey() {
            return routingKey;
        }


    }
}
