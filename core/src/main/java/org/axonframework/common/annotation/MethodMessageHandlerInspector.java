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
 * Utility class that inspects handler methods for a given class and handler definition. For each handler method, it
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
        return getInstance(handlerClass, parameterResolverFactory, allowDuplicates,
                           new AnnotatedHandlerDefinition<T>(annotationType));
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
     * @param parameterResolverFactory The strategy for resolving parameter value for handler methods
     * @param allowDuplicates          Indicates whether to accept multiple handlers listening to Messages with the
     *                                 same payload type
     * @param handlerDefinition        The definition indicating which methods are message handlers
     * @return a MethodMessageHandlerInspector providing access to the handler methods
     */
    public static MethodMessageHandlerInspector getInstance(Class<?> handlerClass,
                                                            ParameterResolverFactory parameterResolverFactory,
                                                            boolean allowDuplicates,
                                                            HandlerDefinition<? super Method> handlerDefinition) {
        String key = handlerDefinition.toString() + "@" + handlerClass.getName();
        MethodMessageHandlerInspector inspector = INSPECTORS.get(key);
        while (inspector == null
                || !handlerClass.equals(inspector.getTargetType())
                || !inspector.parameterResolver.equals(parameterResolverFactory)) {
            final MethodMessageHandlerInspector newInspector = new MethodMessageHandlerInspector(
                    parameterResolverFactory,
                    handlerClass,
                    allowDuplicates,
                    handlerDefinition);
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
     * Initialize an MethodMessageHandlerInspector, where the given <code>handlerDefinition</code> is used to detect
     * which methods are message handlers.
     *
     * @param targetType        The targetType to inspect methods on
     * @param allowDuplicates   Whether of not duplicate handlers (handlers for the same message types) are allowed
     * @param handlerDefinition The definition indicating which methods are message handlers
     */
    private MethodMessageHandlerInspector(ParameterResolverFactory parameterResolverFactory,
                                          Class<?> targetType, boolean allowDuplicates,
                                          HandlerDefinition<? super Method> handlerDefinition) {
        this.parameterResolver = parameterResolverFactory;
        this.targetType = targetType;
        Iterable<Method> methods = methodsOf(targetType);
        NavigableSet<MethodMessageHandler> uniqueHandlers = new TreeSet<MethodMessageHandler>();
        for (Method method : methods) {
            if (handlerDefinition.isMessageHandler(method)) {
                final Class<?> explicitPayloadType = handlerDefinition.resolvePayloadFor(method);
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

    private static class AnnotatedHandlerDefinition<T extends Annotation>
            extends AbstractAnnotatedHandlerDefinition<T> {

        /**
         * Initialize the Definition, using where handlers are annotated with given <code>annotationType</code>.
         *
         * @param annotationType The type of annotation that marks the handlers
         */
        protected AnnotatedHandlerDefinition(Class<T> annotationType) {
            super(annotationType);
        }

        @Override
        protected Class<?> getDefinedPayload(T annotation) {
            return null;
        }
    }
}
