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

package org.axonframework.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.security.AccessController.doPrivileged;

/**
 * Abstract utility class that inspects handler methods.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractHandlerInspector {

    private final Class<?> targetType;
    private final HierarchicHandlerCollection handlers;
    private final Map<Class<?>, Handler> handlerCache = new HashMap<Class<?>, Handler>();

    /**
     * Initialize an AbstractHandlerInspector, where the given <code>annotationType</code> is used to annotate the
     * Event
     * Handler methods.
     *
     * @param targetType     The targetType to inspect methods on
     * @param annotationType The annotation used on the Event Handler methods.
     */
    protected AbstractHandlerInspector(Class<?> targetType, Class<? extends Annotation> annotationType) {
        this.targetType = targetType;
        handlers = new HierarchicHandlerCollection(targetType);
        for (Method method : ReflectionUtils.methodsOf(targetType)) {
            if (method.isAnnotationPresent(annotationType)) {
                handlers.add(new Handler(method));
                if (!method.isAccessible()) {
                    doPrivileged(new MethodAccessibilityCallback(method));
                }
            }
        }
    }

    /**
     * Returns the handler method that handles objects of the given <code>parameterType</code>. Returns
     * <code>null</code> is no such method is found.
     *
     * @param parameterType The parameter type to find a handler for
     * @return the  handler method for the given parameterType
     */
    protected Handler findHandlerMethod(final Class<?> parameterType) {
        if (!handlerCache.containsKey(parameterType)) {
            handlerCache.put(parameterType, handlers.findHandler(parameterType));
        }
        return handlerCache.get(parameterType);
    }

    /**
     * Returns the targetType on which handler methods are invoked.
     *
     * @return the targetType on which handler methods are invoked
     */
    public Class<?> getTargetType() {
        return targetType;
    }

    private static class HierarchicHandlerCollection {

        private final ConcurrentMap<Class<?>, Handler> knownHandlers = new ConcurrentHashMap<Class<?>, Handler>();
        private final Class<?> levelType;
        private volatile HierarchicHandlerCollection parent;

        public HierarchicHandlerCollection(Class<?> handlerType) {
            this.levelType = handlerType;
        }

        public Handler findHandler(Class<?> parameterType) {
            Handler found = knownHandlers.get(parameterType);
            if (found == null) {
                found = findHandlerOnLevel(parameterType);
                if (found == null && parent != null) {
                    found = parent.findHandler(parameterType);
                }
            }
            return found;
        }

        private Handler findHandlerOnLevel(Class<?> parameterType) {
            Handler found = knownHandlers.get(parameterType);
            if (found == null) {
                for (Class<?> iFace : parameterType.getInterfaces()) {
                    found = findHandlerOnLevel(iFace);
                }
            }

            if (found == null && parameterType != Object.class && parameterType.getSuperclass() != null) {
                found = findHandlerOnLevel(parameterType.getSuperclass());
            }
            return found;
        }

        public synchronized void add(Handler handler) {
            if (!handler.getDeclaringClass().isAssignableFrom(levelType)) {
                throw new IllegalArgumentException("Cannot add this handler. "
                                                           + "The subclass handlers must be added prior to handlers declared in the superclass.");
            }
            if (levelType.equals(handler.getDeclaringClass())) {
                knownHandlers.putIfAbsent(handler.getParameter(), handler);
            } else {
                if (parent == null) {
                    parent = new HierarchicHandlerCollection(handler.getDeclaringClass());
                }
                parent.add(handler);
            }
        }
    }
}
