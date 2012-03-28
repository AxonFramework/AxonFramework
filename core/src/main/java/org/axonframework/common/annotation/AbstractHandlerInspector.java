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

package org.axonframework.common.annotation;

import org.axonframework.domain.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.axonframework.common.ReflectionUtils.methodsOf;

/**
 * Abstract utility class that inspects handler methods.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractHandlerInspector {

    private final Class<?> targetType;
    private final SortedSet<MethodMessageHandler> handlers = new TreeSet<MethodMessageHandler>();

    /**
     * Initialize an AbstractHandlerInspector, where the given <code>annotationType</code> is used to annotate the
     * Handler methods.
     *
     * @param targetType     The targetType to inspect methods on
     * @param annotationType The annotation used on the Event Handler methods.
     */
    protected AbstractHandlerInspector(Class<?> targetType, Class<? extends Annotation> annotationType) {
        this.targetType = targetType;
        Iterable<Method> methods = methodsOf(targetType);
        for (Method method : methods) {
            if (method.getAnnotation(annotationType) != null) {
                MethodMessageHandler eventHandlerMethod = MethodMessageHandler.createFor(method);
                if (!handlers.add(eventHandlerMethod)) {
                    MethodMessageHandler existing = handlers.tailSet(eventHandlerMethod).first();
                    throw new UnsupportedHandlerException(
                            String.format("The class %s contains two handler methods (%s and %s) that listen "
                                                  + "to the same Event type: %s",
                                          method.getDeclaringClass().getSimpleName(),
                                          eventHandlerMethod.getMethodName(),
                                          existing.getMethodName(),
                                          eventHandlerMethod.getPayloadType().getSimpleName()), method);
                }
            }
        }
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
}
