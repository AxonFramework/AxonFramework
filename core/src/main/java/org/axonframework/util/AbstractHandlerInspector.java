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

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.security.AccessController.doPrivileged;

/**
 * Abstract utility class that inspects handler methods.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractHandlerInspector {

    private static final HandlerMethodComparator HANDLER_METHOD_COMPARATOR = new HandlerMethodComparator();

    private final Class<?> targetType;
    private final List<Handler> handlers = new ArrayList<Handler>();

    /**
     * Initialize an AbstractHandlerInspector, where the given <code>annotationType</code> is used to annotate the Event
     * Handler methods.
     *
     * @param targetType     The targetType to inspect methods on
     * @param annotationType The annotation used on the Event Handler methods.
     */
    protected AbstractHandlerInspector(Class<?> targetType, Class<? extends Annotation> annotationType) {
        this.targetType = targetType;
        for (Method method : ReflectionUtils.methodsOf(targetType)) {
            if (method.isAnnotationPresent(annotationType)) {
                handlers.add(new Handler(method));
                if (!method.isAccessible()) {
                    doPrivileged(new MethodAccessibilityCallback(method));
                }
            }
        }
        Collections.sort(handlers, HANDLER_METHOD_COMPARATOR);
    }

    /**
     * Returns the handler method that handles objects of the given <code>parameterType</code>. Returns
     * <code>null</code> is no such method is found.
     *
     * @param parameterType The parameter type to find a handler for
     * @return the  handler method for the given parameterType
     */
    protected Handler findHandlerMethod(final Class<?> parameterType) {
        for (Handler handler : handlers) {
            if (handler.getParameter().isAssignableFrom(parameterType)) {
                // method is eligible and first is best
                return handler;
            }
        }
        return null;
    }

    /**
     * Returns the targetType on which handler methods are invoked.
     *
     * @return the targetType on which handler methods are invoked
     */
    public Class<?> getTargetType() {
        return targetType;
    }

    private static class HandlerMethodComparator implements Comparator<Handler>, Serializable {

        private static final long serialVersionUID = 5042125127769533663L;

        @Override
        public int compare(Handler h1, Handler h2) {
            Method m1 = h1.getMethod();
            Method m2 = h2.getMethod();
            if (m1.getDeclaringClass().equals(m2.getDeclaringClass())) {
                // they're in the same class. Pick the most specific method.
                if (m1.getParameterTypes()[0].isAssignableFrom(m2.getParameterTypes()[0])) {
                    return 1;
                } else if (m2.getParameterTypes()[0].isAssignableFrom(m1.getParameterTypes()[0])) {
                    return -1;
                } else {
                    // the parameters are in a different hierarchy. The order doesn't matter.
                    return 0;
                }
            } else if (m1.getDeclaringClass().isAssignableFrom(m2.getDeclaringClass())) {
                return 1;
            } else {
                return -1;
            }
        }
    }
}
