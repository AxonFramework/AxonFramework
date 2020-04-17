/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.common.IdentifierValidator;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlerInvocationException;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.AggregateRoot;
import org.axonframework.modelling.command.AggregateVersion;
import org.axonframework.modelling.command.EntityId;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

/**
 * AggregateMetaModelFactory implementation that uses annotations on the target aggregate's members to build up the
 * meta model of the aggregate.
 */
public class AnnotatedAggregateMetaModelFactory implements AggregateMetaModelFactory {

    private final Map<Class<?>, AnnotatedAggregateModel> registry;
    private final ParameterResolverFactory parameterResolverFactory;
    private final HandlerDefinition handlerDefinition;

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
     * Shorthand to create a factory instance and inspect the model for the given {@code aggregateType} and its {@code subtypes}.
     *
     * @param aggregateType The class of the aggregate to create the model for
     * @param subtypes      Subtypes of this aggregate class
     * @param <T>           The type of aggregate described in the model
     * @return The model describing the structure of the aggregate
     */
    public static <T> AggregateModel<T> inspectAggregate(Class<T> aggregateType, Set<Class<? extends T>> subtypes) {
        return new AnnotatedAggregateMetaModelFactory().createModel(aggregateType, subtypes);
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

    /**
     * Shorthand to create a factory instance and inspect the model for the given {@code aggregateType} and its {@code
     * subytpes}, using given {@code parameterResolverFactory} to resolve parameter values for annotated handlers and
     * {@code handlerDefinition} to create concrete handlers.
     *
     * @param aggregateType            The class of the aggregate to create the model for
     * @param parameterResolverFactory to resolve parameter values of annotated handlers with
     * @param handlerDefinition        The handler definition used to create concrete handlers
     * @param subtypes                 Subtypes of this aggregate class
     * @param <T>                      The type of aggregate described in the model
     * @return The model describing the structure of the aggregate
     */
    public static <T> AggregateModel<T> inspectAggregate(Class<T> aggregateType,
                                                         ParameterResolverFactory parameterResolverFactory,
                                                         HandlerDefinition handlerDefinition,
                                                         Set<Class<? extends T>> subtypes) {
        return new AnnotatedAggregateMetaModelFactory(parameterResolverFactory, handlerDefinition)
                .createModel(aggregateType, subtypes);
    }

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

    @Override
    public <T> AnnotatedAggregateModel<T> createModel(Class<? extends T> aggregateType,
                                                      Set<Class<? extends T>> subtypes) {
        if (!registry.containsKey(aggregateType)) {
            AnnotatedHandlerInspector<T> inspector = AnnotatedHandlerInspector.inspectType(aggregateType,
                                                                                           parameterResolverFactory,
                                                                                           handlerDefinition,
                                                                                           subtypes);
            AnnotatedAggregateModel<T> model = new AnnotatedAggregateModel<>(aggregateType, inspector);
            // Add the newly created inspector to the registry first to prevent a StackOverflowError:
            // another call to createInspector with the same inspectedType will return this instance of the inspector.
            registry.put(aggregateType, model);
            model.initialize();
        }
        //noinspection unchecked
        return registry.get(aggregateType).whenReadSafe();
    }

    private class AnnotatedAggregateModel<T> implements AggregateModel<T> {

        private final Class<? extends T> inspectedType;
        private final List<ChildEntity<T>> children;
        private final AnnotatedHandlerInspector<T> handlerInspector;
        private final Map<Class<?>, List<MessageHandlingMember<? super T>>> allCommandHandlerInterceptors;
        private final Map<Class<?>, List<MessageHandlingMember<? super T>>> allCommandHandlers;
        private final Map<Class<?>, List<MessageHandlingMember<? super T>>> allEventHandlers;

        private final Map<String, Class<?>> types;
        private final Map<Class<?>, String> declaredTypes;
        private Field identifierField;
        private Field versionField;
        private String routingKey;
        private final ThreadLocal<Boolean> initializing = new ThreadLocal<>();
        private volatile boolean initialized;

        public AnnotatedAggregateModel(Class<? extends T> aggregateType, AnnotatedHandlerInspector<T> handlerInspector) {
            this.inspectedType = aggregateType;
            this.types = new HashMap<>();
            this.declaredTypes = new HashMap<>();
            this.allCommandHandlerInterceptors = new HashMap<>();
            this.allCommandHandlers = new HashMap<>();
            this.allEventHandlers = new HashMap<>();
            this.children = new ArrayList<>();
            this.handlerInspector = handlerInspector;
        }

        private void initialize() {
            initializing.set(Boolean.TRUE);
            inspectFields();
            prepareHandlers();
            inspectAggregateTypes();
            initialized = true;
            initializing.remove();
        }

        @SuppressWarnings("unchecked")
        private void prepareHandlers() {
            for (Map.Entry<Class<?>, SortedSet<MessageHandlingMember<? super T>>> handlersPerType
                    : handlerInspector.getAllHandlers().entrySet()) {
                Class<?> type = handlersPerType.getKey();
                for (MessageHandlingMember<? super T> handler : handlersPerType.getValue()) {
                    if (handler.unwrap(CommandMessageHandlingMember.class).isPresent()) {
                        if (Modifier.isAbstract(type.getModifiers()) && handler.unwrap(Constructor.class).isPresent()) {
                            throw new AggregateModellingException(format(
                                    "An abstract aggregate %s cannot have @CommandHandler on constructor.",
                                    type));
                        }
                        addHandler(allCommandHandlers, type, handler);
                    } else if (handler.unwrap(CommandHandlerInterceptorHandlingMember.class).isPresent()) {
                        addHandler(allCommandHandlerInterceptors, type, handler);
                    } else {
                        addHandler(allEventHandlers, type, handler);
                    }
                }
            }
            validateCommandHandlers();
        }

        private void addHandler(Map<Class<?>, List<MessageHandlingMember<? super T>>> handlers, Class<?> type,
                                MessageHandlingMember<? super T> handler) {
            handlers.computeIfAbsent(type, t -> new ArrayList<>())
                    .add(handler);
        }

        /**
         * In polymorphic aggregate hierarchy there must not be more than one creational (factory) command handler (of
         * the same command name) in more than one aggregate.
         */
        private void validateCommandHandlers() {
            List<List<MessageHandlingMember<? super T>>> handlers = new ArrayList<>(allCommandHandlers.values());
            for (int i = 0; i < handlers.size() - 1; i++) {
                List<CommandMessageHandlingMember<? super T>> factoryCommands1 = factoryCommands(handlers.get(i));
                List<CommandMessageHandlingMember<? super T>> factoryCommands2 = factoryCommands(handlers.get(i + 1));
                for (CommandMessageHandlingMember<? super T> handler1 : factoryCommands1) {
                    for (CommandMessageHandlingMember<? super T> handler2 : factoryCommands2) {
                        String commandName1 = handler1.commandName();
                        String commandName2 = handler2.commandName();
                        if (commandName1.equals(commandName2)) {
                            Class<?> declaringClass1 = handler1.declaringClass();
                            Class<?> declaringClass2 = handler2.declaringClass();
                            if (!declaringClass1.equals(declaringClass2)) {
                                throw new AggregateModellingException(format(
                                        "Aggregates %s and %s have the same creation @CommandHandler %s",
                                        declaringClass1,
                                        declaringClass2,
                                        commandName1));
                            }
                        }
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private List<CommandMessageHandlingMember<? super T>> factoryCommands(
                List<MessageHandlingMember<? super T>> handlers) {
            return handlers.stream()
                           .map(h -> h.unwrap(CommandMessageHandlingMember.class))
                           .filter(Optional::isPresent)
                           .map(Optional::get)
                           .filter(CommandMessageHandlingMember::isFactoryHandler)
                           .map(h -> (CommandMessageHandlingMember<? super T>) h)
                           .collect(toList());
        }

        private void inspectAggregateTypes() {
            for (Class<?> type : handlerInspector.getAllHandlers().keySet()) {
                String declaredType = findDeclaredType(type);
                types.put(declaredType, type);
                declaredTypes.put(type, declaredType);
            }
        }

        private String findDeclaredType(Class<?> type) {
            return AnnotationUtils.findAnnotationAttributes(type, AggregateRoot.class)
                                  .map(map -> (String) map.get("type")).filter(i -> i.length() > 0).orElse(type.getSimpleName());
        }

        private void inspectFields() {
            ServiceLoader<ChildEntityDefinition> childEntityDefinitions =
                    ServiceLoader.load(ChildEntityDefinition.class, inspectedType.getClassLoader());
            boolean persistenceId = false;
            for (Class<?> type : handlerInspector.getAllHandlers().keySet()) {
                for (Field field : ReflectionUtils.fieldsOf(type)) {
                    childEntityDefinitions.forEach(def -> def.createChildDefinition(field, this).ifPresent(child -> {
                        children.add(child);
                        child.commandHandlers().forEach(handler -> addHandler(allCommandHandlers,
                                                                              type,
                                                                              handler));
                    }));

                    if (AnnotationUtils.findAnnotationAttributes(field, EntityId.class).isPresent()) {
                        if (identifierField != null && !field.equals(identifierField) && !persistenceId) {
                            throw new AggregateModellingException(format(
                                    "Aggregate [%s] has two identifier fields [%s] and [%s].",
                                    inspectedType,
                                    identifierField,
                                    field));
                        }
                        persistenceId = false;
                        identifierField = field;
                        Map<String, Object> attributes =
                                AnnotationUtils.findAnnotationAttributes(field, EntityId.class).get();
                        if (!"".equals(attributes.get("routingKey"))) {
                            routingKey = (String) attributes.get("routingKey");
                        } else {
                            routingKey = field.getName();
                        }
                    }
                    if (identifierField == null
                            && AnnotationUtils.findAnnotationAttributes(field, "javax.persistence.Id")
                                              .isPresent()) {
                            persistenceId = true;
                            identifierField = field;
                            routingKey = field.getName();
                    }
                    AnnotationUtils.findAnnotationAttributes(field, AggregateVersion.class)
                                   .ifPresent(attributes -> {
                                       if (versionField != null && !field.equals(versionField)) {
                                           throw new AggregateModellingException(format(
                                                   "Aggregate [%s] has two version fields [%s] and [%s].",
                                                   inspectedType,
                                                   versionField,
                                                   field));
                                       }
                                       versionField = field;
                                   });
                }
            }
            if (identifierField != null) {
                final Class<?> idClazz = identifierField.getType();
                if (!IdentifierValidator.getInstance().isValidIdentifier(idClazz)) {
                    throw new AggregateModellingException(format(
                            "Aggregate identifier type [%s] should override Object.toString()",
                            idClazz.getName()));
                }
            }
        }

        @SuppressWarnings("unchecked")
        private AnnotatedAggregateModel<T> runtimeModelOf(T target) {
            return modelOf((Class<? extends T>) target.getClass());
        }

        @Override
        public Map<Class<?>, List<MessageHandlingMember<? super T>>> allCommandHandlers() {
            return Collections.unmodifiableMap(allCommandHandlers);
        }

        @Override
        public Stream<MessageHandlingMember<? super T>> commandHandlers(Class<? extends T> subtype) {
            return handlers(allCommandHandlers, subtype);
        }

        @Override
        public Stream<Class<?>> types() {
            return handlerInspector.getAllHandlers()
                                   .keySet()
                                   .stream();
        }

        @Override
        public <C> AnnotatedAggregateModel<C> modelOf(Class<? extends C> childEntityType) {
            // using empty list subtypes because this model is already in the registry, so it doesn't matter
            return AnnotatedAggregateMetaModelFactory.this.createModel(childEntityType, Collections.emptySet());
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
            getHandler(message, target.getClass()).ifPresent(h -> {
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
            return declaredTypes.get(inspectedType);
        }

        @Override
        public Optional<Class<?>> type(String declaredType) {
            return Optional.ofNullable(types.getOrDefault(declaredType, null));
        }

        @Override
        public Optional<String> declaredType(Class<?> type) {
            return Optional.ofNullable(declaredTypes.getOrDefault(type, null));
        }

        @Override
        public Long getVersion(T target) {
            if (versionField != null) {
                return (Long) ReflectionUtils.getFieldValue(versionField, target);
            }
            return null;
        }

        @Override
        public Map<Class<?>, List<MessageHandlingMember<? super T>>> allCommandHandlerInterceptors() {
            return Collections.unmodifiableMap(allCommandHandlerInterceptors);
        }

        @Override
        public Stream<MessageHandlingMember<? super T>> commandHandlerInterceptors(Class<? extends T> subtype) {
            return handlers(allCommandHandlerInterceptors, subtype);
        }

        /**
         * Returns the {@link MessageHandlingMember} that is capable of handling the given {@code message}. If no member
         * is found an empty optional is returned.
         *
         * @param message     the message to find a handler for
         * @param targetClass the target class that handler should be executed on
         * @return the handler of the message if present on the model
         */
        @SuppressWarnings("unchecked")
        protected Optional<MessageHandlingMember<? super T>> getHandler(Message<?> message, Class<?> targetClass) {
            return handlers(allEventHandlers, targetClass)
                    .filter(handler -> handler.canHandle(message))
                    .findAny();
        }

        @Override
        public Map<Class<?>, List<MessageHandlingMember<? super T>>> allEventHandlers() {
            return Collections.unmodifiableMap(allEventHandlers);
        }

        //backwards compatibility - if you don't specify a designated child,
        //you should at least get handlers of its first registered parent (if any)
        private Stream<MessageHandlingMember<? super T>> handlers(
                Map<Class<?>, List<MessageHandlingMember<? super T>>> handlers, Class<?> subtype) {
            Class<?> type = subtype;
            while (!handlers.containsKey(type) && !type.equals(Object.class)) {
                type = type.getSuperclass();
            }
            return handlers.getOrDefault(type, new ArrayList<>())
                           .stream();
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


        /**
         * Returns this instance when it it safe to read from. This is either if the current thread is already
         * initializing this instance, or when the instance is fully initialized.
         *
         * @return this instance, as soon as it is safe for reading
         */
        private AnnotatedAggregateModel<T> whenReadSafe() {
            if (Boolean.TRUE.equals(initializing.get())) {
                // This thread is initializing. To prevent deadlocks, we should return this instance immediately
                return this;
            }
            while (!initialized) {
                // another thread is initializing, it shouldn't take long...
                Thread.yield();
            }
            // we're safe to go
            return this;
        }
    }
}
