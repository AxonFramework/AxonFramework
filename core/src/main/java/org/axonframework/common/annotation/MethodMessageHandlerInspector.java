/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.common.annotation;

import org.axonframework.domain.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.axonframework.common.ReflectionUtils.methodsOf;

/**
 * Utility class that inspects handler methods for a given class and annotation type. For each annotated method, it
 * keeps track of a MethodMessageHandler that describes the capabilities of that method (in terms of supported
 * messages).
 *
 * @author Allard Buijze
 * @since 2.0
 */
public final class MethodMessageHandlerInspector {

    private final Class<?> targetType;
    private final List<MethodMessageHandler> handlers = new ArrayList<MethodMessageHandler>();
    private final ParameterResolverFactory parameterResolver;

    private static final ConcurrentMap<String, MethodMessageHandlerInspector> INSPECTORS =
            new ConcurrentHashMap<String, MethodMessageHandlerInspector>();

    /**
     * Returns a MethodMessageHandlerInspector for the given <code>handlerClass</code> that contains handler methods
     * annotated with the given <code>annotationType</code>. The <code>allowDuplicates</code> will indicate whether it
     * is acceptable to have multiple handlers listening to Messages with the same payload type. Basically, this should
     * always be false, unless some a property other than the payload of the Message is used to route the Message to a
     * handler.
     *
     * @param handlerClass             The Class containing the handler methods to evaluate
     * @param annotationType           The annotation marking handler methods
     * @param parameterResolverFactory The strategy for resolving parameter value for handler methods
     * @param allowDuplicates          Indicates whether to accept multiple handlers listening to Messages with the
     *                                 same payload type
     * @param <T>                      The type of annotation used to mark handler methods
     * @return a MethodMessageHandlerInspector providing access to the handler methods
     */
    public static <T extends Annotation> MethodMessageHandlerInspector getInstance(
            Class<?> handlerClass, Class<T> annotationType, ParameterResolverFactory parameterResolverFactory,
            boolean allowDuplicates) {
        return getInstance(handlerClass, annotationType, parameterResolverFactory,
                           allowDuplicates,
                           new UndefinedPayloadResolver<T>());
    }

    /**
     * Returns a MethodMessageHandlerInspector for the given <code>handlerClass</code> that contains handler methods
     * annotated with the given <code>annotationType</code>. The <code>allowDuplicates</code> will indicate whether it
     * is acceptable to have multiple handlers listening to Messages with the same payload type. Basically, this should
     * always be false, unless some a property other than the payload of the Message is used to route the Message to a
     * handler.
     * <p/>
     * This method attempts to return an existing inspector instance. It will do so when it detects an instance for the
     * same handler class and for the same annotation type, that uses the same parameterResolverFactory.
     *
     * @param handlerClass             The Class containing the handler methods to evaluate
     * @param annotationType           The annotation marking handler methods
     * @param parameterResolverFactory The strategy for resolving parameter value for handler methods
     * @param allowDuplicates          Indicates whether to accept multiple handlers listening to Messages with the
     *                                 same payload type
     * @param payloadTypeResolver      The resolver providing the explicitly configured payload type of a method, if
     *                                 any
     * @param <T>                      The type of annotation used to mark handler methods
     * @return a MethodMessageHandlerInspector providing access to the handler methods
     */
    public static <T extends Annotation> MethodMessageHandlerInspector getInstance(
            Class<?> handlerClass, Class<T> annotationType, ParameterResolverFactory parameterResolverFactory,
            boolean allowDuplicates, HandlerPayloadTypeResolver<T> payloadTypeResolver) {
        String key = annotationType.getName() + "@" + handlerClass.getName();
        MethodMessageHandlerInspector inspector = INSPECTORS.get(key);
        while (inspector == null || !inspector.parameterResolver.equals(parameterResolverFactory)) {
            final MethodMessageHandlerInspector newInspector = new MethodMessageHandlerInspector(
                    parameterResolverFactory,
                    handlerClass,
                    annotationType,
                    allowDuplicates,
                    payloadTypeResolver);
            if (inspector == null) {
                INSPECTORS.putIfAbsent(key, newInspector);
            } else {
                INSPECTORS.replace(key, inspector, newInspector);
            }
            inspector = INSPECTORS.get(key);
        }
        return inspector;
    }

    /**
     * Initialize an MethodMessageHandlerInspector, where the given <code>annotationType</code> is used to annotate the
     * Handler methods.
     *
     * @param targetType          The targetType to inspect methods on
     * @param annotationType      The annotation used on the Event Handler methods.
     * @param payloadTypeResolver The resolver providing information about explicitly configured expected payload
     * @param <T>                 The type of annotation this inspector should check for
     */
    private <T extends Annotation> MethodMessageHandlerInspector(ParameterResolverFactory parameterResolverFactory,
                                                                 Class<?> targetType, Class<T> annotationType,
                                                                 boolean allowDuplicates,
                                                                 HandlerPayloadTypeResolver<T> payloadTypeResolver) {
        this.parameterResolver = parameterResolverFactory;
        this.targetType = targetType;
        Iterable<Method> methods = methodsOf(targetType);
        NavigableSet<MethodMessageHandler> uniqueHandlers = new TreeSet<MethodMessageHandler>();
        for (Method method : methods) {
            final T annotation = method.getAnnotation(annotationType);
            if (annotation != null) {
                final Class<?> explicitPayloadType = payloadTypeResolver.resolvePayloadFor(annotation);
                MethodMessageHandler handlerMethod = MethodMessageHandler.createFor(method,
                                                                                    explicitPayloadType,
                                                                                    parameterResolverFactory
                );
                handlers.add(handlerMethod);
                if (!allowDuplicates && !uniqueHandlers.add(handlerMethod)) {
                    MethodMessageHandler existing = uniqueHandlers.tailSet(handlerMethod).first();
                    throw new UnsupportedHandlerException(
                            String.format("The class %s contains two handler methods (%s and %s) that listen "
                                                  + "to the same Message type: %s",
                                          method.getDeclaringClass().getSimpleName(),
                                          handlerMethod.getMethodName(),
                                          existing.getMethodName(),
                                          handlerMethod.getPayloadType().getSimpleName()), method);
                }
            }
        }
        Collections.sort(handlers);
    }

    /**
     * Returns the handler method that handles objects of the given <code>parameterType</code>. Returns
     * <code>null</code> is no such method is found.
     *
     * @param message The message to find a handler for
     * @return the  handler method for the given parameterType
     */
    public MethodMessageHandler findHandlerMethod(final Message message) {
        for (MethodMessageHandler handler : handlers) {
            if (handler.matches(message)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * Returns the list of handlers found on target type.
     *
     * @return the list of handlers found on target type
     */
    public List<MethodMessageHandler> getHandlers() {
        return new ArrayList<MethodMessageHandler>(handlers);
    }

    /**
     * Returns the targetType on which handler methods are invoked.
     *
     * @return the targetType on which handler methods are invoked
     */
    public Class<?> getTargetType() {
        return targetType;
    }

    private static class UndefinedPayloadResolver<T extends Annotation> implements HandlerPayloadTypeResolver<T> {

        @Override
        public Class<?> resolvePayloadFor(T annotation) {
            return null;
        }
    }
}
