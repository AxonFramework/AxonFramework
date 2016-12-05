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

package org.axonframework.messaging.annotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Inspector for a message handling target of type {@link T} that uses annotations on the target to inspect the
 * capabilities of the target.
 *
 * @param <T> the target type
 */
public class AnnotatedHandlerInspector<T> {

    private final Class<T> inspectedType;
    private final ParameterResolverFactory parameterResolverFactory;
    private final Map<Class<?>, AnnotatedHandlerInspector> registry;
    private final List<AnnotatedHandlerInspector<? super T>> superClassInspectors;
    private final List<MessageHandlingMember<? super T>> handlers;

    private AnnotatedHandlerInspector(Class<T> inspectedType,
                                      List<AnnotatedHandlerInspector<? super T>> superClassInspectors,
                                      ParameterResolverFactory parameterResolverFactory,
                                      Map<Class<?>, AnnotatedHandlerInspector> registry) {
        this.inspectedType = inspectedType;
        this.parameterResolverFactory = parameterResolverFactory;
        this.registry = registry;
        this.superClassInspectors = new ArrayList<>(superClassInspectors);
        this.handlers = new ArrayList<>();
    }

    /**
     * Create an inspector for given {@code handlerType} that uses a {@link ClasspathParameterResolverFactory} to
     * resolve method parameters.
     *
     * @param handlerType the target handler type
     * @param <T>         the handler's type
     * @return a new inspector instance for the inspected class
     */
    public static <T> AnnotatedHandlerInspector<T> inspectType(Class<T> handlerType) {
        return inspectType(handlerType, ClasspathParameterResolverFactory.forClass(handlerType));
    }

    /**
     * Create an inspector for given {@code handlerType} that uses given {@code parameterResolverFactory} to resolve
     * method parameters.
     *
     * @param handlerType              the target handler type
     * @param parameterResolverFactory the resolver factory to use during detection
     * @param <T>                      the handler's type
     * @return a new inspector instance for the inspected class
     */
    public static <T> AnnotatedHandlerInspector<T> inspectType(Class<T> handlerType,
                                                               ParameterResolverFactory parameterResolverFactory) {
        return createInspector(handlerType, parameterResolverFactory, new HashMap<>());
    }


    private static <T> AnnotatedHandlerInspector<T> createInspector(Class<? extends T> inspectedType,
                                                                    ParameterResolverFactory parameterResolverFactory,
                                                                    Map<Class<?>, AnnotatedHandlerInspector> registry) {
        //noinspection unchecked
        return registry.computeIfAbsent(inspectedType, k -> AnnotatedHandlerInspector
                .initialize(inspectedType, parameterResolverFactory, registry));
    }

    private static <T> AnnotatedHandlerInspector<T> initialize(Class<T> inspectedType,
                                                               ParameterResolverFactory parameterResolverFactory,
                                                               Map<Class<?>, AnnotatedHandlerInspector> registry) {
        List<AnnotatedHandlerInspector<? super T>> parents = new ArrayList<>();
        for (Class<?> iFace : inspectedType.getInterfaces()) {
            //noinspection unchecked
            parents.add(createInspector(iFace, parameterResolverFactory, registry));
        }
        if (inspectedType.getSuperclass() != null && !Object.class.equals(inspectedType.getSuperclass())) {
            parents.add(createInspector(inspectedType.getSuperclass(), parameterResolverFactory, registry));
        }
        AnnotatedHandlerInspector<T> inspector =
                new AnnotatedHandlerInspector<>(inspectedType, parents, parameterResolverFactory, registry);
        inspector.initializeMessageHandlers(parameterResolverFactory);
        return inspector;
    }

    private void initializeMessageHandlers(ParameterResolverFactory parameterResolverFactory) {
        List<HandlerDefinition> definitions = new ArrayList<>();
        ServiceLoader.load(HandlerDefinition.class).forEach(definitions::add);
        List<HandlerEnhancerDefinition> wrapperDefinitions = new ArrayList<>();
        ServiceLoader.load(HandlerEnhancerDefinition.class).forEach(wrapperDefinitions::add);
        for (Method method : inspectedType.getDeclaredMethods()) {
            definitions.forEach(definition -> definition.createHandler(inspectedType, method, parameterResolverFactory)
                    .ifPresent(handler -> registerHandler(wrapped(handler, wrapperDefinitions))));
        }
        for (Constructor<?> constructor : inspectedType.getDeclaredConstructors()) {
            definitions.forEach(
                    definition -> definition.createHandler(inspectedType, constructor, parameterResolverFactory)
                            .ifPresent(handler -> registerHandler(wrapped(handler, wrapperDefinitions))));
        }
        superClassInspectors.forEach(sci -> handlers.addAll(sci.getHandlers()));
        Collections.sort(handlers, HandlerComparator.instance());
    }

    private MessageHandlingMember<T> wrapped(MessageHandlingMember<T> handler,
                                             Iterable<HandlerEnhancerDefinition> wrapperDefinitions) {
        MessageHandlingMember<T> wrappedHandler = handler;
        if (wrapperDefinitions != null) {
            for (HandlerEnhancerDefinition definition : wrapperDefinitions) {
                wrappedHandler = definition.wrapHandler(wrappedHandler);
            }
        }
        return wrappedHandler;
    }

    private void registerHandler(MessageHandlingMember<T> handler) {
        handlers.add(handler);
    }

    /**
     * Inspect another handler type and register the result to the inspector registry of this inspector. This is used
     * by Axon to inspect child entities of an aggregate.
     *
     * @param entityType the type of the handler to inspect
     * @param <C> the handler's type
     * @return a new inspector for the given type
     */
    public <C> AnnotatedHandlerInspector<C> inspect(Class<? extends C> entityType) {
        return AnnotatedHandlerInspector.createInspector(entityType, parameterResolverFactory, registry);
    }

    /**
     * Returns a list of detected members of the inspected entity that are capable of handling certain messages.
     *
     * @return a list of detected message handlers
     */
    public List<MessageHandlingMember<? super T>> getHandlers() {
        return handlers;
    }
}
