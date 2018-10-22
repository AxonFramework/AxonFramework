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

package org.axonframework.messaging.annotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Inspector for a message handling target of type {@code T} that uses annotations on the target to inspect the
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
    private final HandlerDefinition handlerDefinition;

    private AnnotatedHandlerInspector(Class<T> inspectedType,
                                      List<AnnotatedHandlerInspector<? super T>> superClassInspectors,
                                      ParameterResolverFactory parameterResolverFactory,
                                      HandlerDefinition handlerDefinition,
                                      Map<Class<?>, AnnotatedHandlerInspector> registry) {
        this.inspectedType = inspectedType;
        this.parameterResolverFactory = parameterResolverFactory;
        this.registry = registry;
        this.superClassInspectors = new ArrayList<>(superClassInspectors);
        this.handlers = new ArrayList<>();
        this.handlerDefinition = handlerDefinition;
    }

    /**
     * Create an inspector for given {@code handlerType} that uses a {@link ClasspathParameterResolverFactory} to
     * resolve method parameters and {@link ClasspathHandlerDefinition} to create handlers.
     *
     * @param handlerType the target handler type
     * @param <T>         the handler's type
     * @return a new inspector instance for the inspected class
     */
    public static <T> AnnotatedHandlerInspector<T> inspectType(Class<? extends T> handlerType) {
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
    public static <T> AnnotatedHandlerInspector<T> inspectType(Class<? extends T> handlerType,
                                                               ParameterResolverFactory parameterResolverFactory) {
        return inspectType(handlerType,
                           parameterResolverFactory,
                           ClasspathHandlerDefinition.forClass(handlerType));
    }

    /**
     * Create an inspector for given {@code handlerType} that uses given {@code parameterResolverFactory} to resolve
     * method parameters and given {@code handlerDefinition} to create handlers.
     *
     * @param handlerType              the target handler type
     * @param parameterResolverFactory the resolver factory to use during detection
     * @param handlerDefinition        the handler definition used to create concrete handlers
     * @param <T>                      the handler's type
     * @return a new inspector instance for the inspected class
     */
    public static <T> AnnotatedHandlerInspector<T> inspectType(Class<? extends T> handlerType,
                                                               ParameterResolverFactory parameterResolverFactory,
                                                               HandlerDefinition handlerDefinition) {
        return createInspector(handlerType,
                               parameterResolverFactory,
                               handlerDefinition,
                               new HashMap<>());
    }


    private static <T> AnnotatedHandlerInspector<T> createInspector(Class<? extends T> inspectedType,
                                                                    ParameterResolverFactory parameterResolverFactory,
                                                                    HandlerDefinition handlerDefinition,
                                                                    Map<Class<?>, AnnotatedHandlerInspector> registry) {
        if (!registry.containsKey(inspectedType)) {
            registry.put(inspectedType,
                         AnnotatedHandlerInspector.initialize(inspectedType,
                                                              parameterResolverFactory,
                                                              handlerDefinition,
                                                              registry));
        }
        //noinspection unchecked
        return registry.get(inspectedType);
    }

    private static <T> AnnotatedHandlerInspector<T> initialize(Class<T> inspectedType,
                                                               ParameterResolverFactory parameterResolverFactory,
                                                               HandlerDefinition handlerDefinition,
                                                               Map<Class<?>, AnnotatedHandlerInspector> registry) {
        List<AnnotatedHandlerInspector<? super T>> parents = new ArrayList<>();
        for (Class<?> iFace : inspectedType.getInterfaces()) {
            //noinspection unchecked
            parents.add(createInspector(iFace,
                                        parameterResolverFactory,
                                        handlerDefinition,
                                        registry));
        }
        if (inspectedType.getSuperclass() != null && !Object.class.equals(inspectedType.getSuperclass())) {
            parents.add(createInspector(inspectedType.getSuperclass(),
                                        parameterResolverFactory,
                                        handlerDefinition,
                                        registry));
        }
        AnnotatedHandlerInspector<T> inspector = new AnnotatedHandlerInspector<>(inspectedType,
                                                                                 parents,
                                                                                 parameterResolverFactory,
                                                                                 handlerDefinition,
                                                                                 registry);
        inspector.initializeMessageHandlers(parameterResolverFactory, handlerDefinition);
        return inspector;
    }

    private void initializeMessageHandlers(ParameterResolverFactory parameterResolverFactory,
                                           HandlerDefinition handlerDefinition) {
        for (Method method : inspectedType.getDeclaredMethods()) {
            handlerDefinition.createHandler(inspectedType, method, parameterResolverFactory)
                             .ifPresent(this::registerHandler);
        }
        for (Constructor<?> constructor : inspectedType.getDeclaredConstructors()) {
            handlerDefinition.createHandler(inspectedType, constructor, parameterResolverFactory)
                            .ifPresent(this::registerHandler);
        }
        superClassInspectors.forEach(sci -> handlers.addAll(sci.getHandlers()));
        handlers.sort(HandlerComparator.instance());
    }

    private void registerHandler(MessageHandlingMember<T> handler) {
        handlers.add(handler);
    }

    /**
     * Inspect another handler type and register the result to the inspector registry of this inspector. This is used
     * by Axon to inspect child entities of an aggregate.
     *
     * @param entityType the type of the handler to inspect
     * @param <C>        the handler's type
     * @return a new inspector for the given type
     */
    public <C> AnnotatedHandlerInspector<C> inspect(Class<? extends C> entityType) {
        return AnnotatedHandlerInspector.createInspector(entityType,
                                                         parameterResolverFactory,
                                                         handlerDefinition,
                                                         registry);
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
