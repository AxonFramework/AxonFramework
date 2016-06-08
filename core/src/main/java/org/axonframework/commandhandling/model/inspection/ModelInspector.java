/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.model.AggregateRoot;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.*;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.AggregateVersion;
import org.axonframework.eventsourcing.EntityId;
import org.axonframework.messaging.Message;

import java.lang.reflect.Field;
import java.util.*;

import static java.lang.String.format;

public class ModelInspector<T> implements AggregateModel<T> {

    private final Class<? extends T> inspectedType;
    private final Map<Class<?>, ModelInspector> registry;
    private final List<ChildEntity<T>> children;
    private final AnnotatedHandlerInspector<T> handlerInspector;
    private final Map<String, CommandMessageHandler<? super T>> commandHandlers;
    private final List<MessageHandler<? super T>> eventHandlers;

    private String aggregateType;
    private Field identifierField;
    private Field versionField;
    private String routingKey;

    private ModelInspector(Class<? extends T> inspectedType,
                           Map<Class<?>, ModelInspector> registry, AnnotatedHandlerInspector<T> handlerInspector) {
        this.inspectedType = inspectedType;
        this.registry = registry;
        this.commandHandlers = new HashMap<>();
        this.eventHandlers = new ArrayList<>();
        this.children = new ArrayList<>();
        this.handlerInspector = handlerInspector;
    }

    public static <AT> AggregateModel<AT> inspectAggregate(Class<AT> aggregateType) {
        return inspectAggregate(aggregateType,
                                ClasspathParameterResolverFactory.forClass(aggregateType));
    }

    public static <T> AggregateModel<T> inspectAggregate(Class<T> aggregateType,
                                                         ParameterResolverFactory parameterResolverFactory) {
        return createInspector(aggregateType,
                               AnnotatedHandlerInspector.inspectType(aggregateType, parameterResolverFactory),
                               new HashMap<>());
    }

    private static <T> ModelInspector<T> createInspector(Class<? extends T> inspectedType,
                                                         AnnotatedHandlerInspector<T> handlerInspector,
                                                         Map<Class<?>, ModelInspector> registry) {
        //noinspection unchecked
        return registry.computeIfAbsent(inspectedType, k -> ModelInspector.initialize(inspectedType, handlerInspector,
                                                                                      registry));
    }

    private static <T> ModelInspector<T> initialize(Class<? extends T> inspectedType,
                                                    AnnotatedHandlerInspector<T> handlerInspector,
                                                    Map<Class<?>, ModelInspector> registry) {
        ModelInspector<T> inspector = new ModelInspector<>(inspectedType, registry, handlerInspector);
        inspector.inspectAggregateType();
        inspector.inspectFields();
        inspector.prepareHandlers();
        return inspector;
    }

    @SuppressWarnings("unchecked")
    private void prepareHandlers() {
        for (MessageHandler<? super T> handler : handlerInspector.getHandlers()) {
            if (handler instanceof CommandMessageHandler) {
                CommandMessageHandler<T> commandMessageHandler = (CommandMessageHandler<T>) handler;
                commandHandlers.putIfAbsent(commandMessageHandler.commandName(), commandMessageHandler);
            } else {
                eventHandlers.add(handler);
            }
        }
    }

    private void inspectAggregateType() {
        aggregateType = AnnotationUtils.findAnnotationAttributes(inspectedType, AggregateRoot.class)
                .map(map -> (String) map.get("type"))
                .filter(String::isEmpty)
                .orElse(inspectedType.getSimpleName());
    }

    private void inspectFields() {
        ServiceLoader<ChildEntityDefinition> childEntityDefinitions = ServiceLoader.load(ChildEntityDefinition.class,
                                                                                         inspectedType.getClassLoader());
        for (Field field : ReflectionUtils.fieldsOf(inspectedType)) {
            childEntityDefinitions.forEach(def -> def.createChildDefinition(field, this).ifPresent(child -> {
                children.add(child);
                child.commandHandlers().forEach(commandHandlers::putIfAbsent);
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
                AnnotationUtils.findAnnotationAttributes(field, "javax.persistence.Id"
                )
                        .ifPresent(a -> {
                            identifierField = field;
                            routingKey = field.getName();
                        });
            }
            AnnotationUtils.findAnnotationAttributes(field, AggregateVersion.class)
                    .ifPresent(attributes -> versionField = field);
        }
    }

    @SuppressWarnings("unchecked")
    private ModelInspector<T> runtimeModelOf(T target) {
        return modelOf((Class<T>) target.getClass());
    }

    @Override
    public Map<String, CommandMessageHandler<? super T>> commandHandlers() {
        return Collections.unmodifiableMap(commandHandlers);
    }

    @Override
    public CommandMessageHandler<? super T> commandHandler(String commandName) {
        CommandMessageHandler<? super T> handler = commandHandlers.get(commandName);
        if (handler == null) {
            throw new NoHandlerForCommandException(format("No handler available to handle command [%s]", commandName));
        }
        return handler;
    }

    @Override
    public <C> ModelInspector<C> modelOf(Class<? extends C> entityType) {
        return ModelInspector.createInspector(entityType, handlerInspector.inspect(entityType), registry);
    }

    @Override
    public void publish(EventMessage<?> message, T target) {
        runtimeModelOf(target).doPublish(message, target);
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

    @SuppressWarnings("unchecked")
    protected Optional<MessageHandler<? super T>> getHandler(Message<?> message) {
        for (MessageHandler<? super T> handler : eventHandlers) {
            if (handler.canHandle(message)) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }


    @Override
    public String getIdentifier(T target) {
        if (identifierField != null) {
            return Objects.toString(ReflectionUtils.getFieldValue(identifierField, target), null);
        }
        return null;
    }

    @Override
    public String routingKey() {
        return routingKey;
    }
}
