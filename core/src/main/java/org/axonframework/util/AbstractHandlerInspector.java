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
import java.util.LinkedList;
import java.util.List;

/**
 * Abstract utility class that inspects handler methods.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractHandlerInspector {

    private final Class<?> targetType;
    private final List<Handler> handlers = new LinkedList<Handler>();

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
        for (Method method : ReflectionUtils.methodsOf(targetType)) {
            if (method.isAnnotationPresent(annotationType)) {
                handlers.add(new Handler(method));
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
        Handler bestHandlerSoFar = null;
        for (Handler handler : handlers) {
            Handler foundSoFar = bestHandlerSoFar;
            Class<?> classUnderInvestigation = handler.getDeclaringClass();
            boolean bestInClassFound =
                    foundSoFar != null
                            && !classUnderInvestigation.equals(foundSoFar.getDeclaringClass())
                            && classUnderInvestigation.isAssignableFrom(foundSoFar.getDeclaringClass());
            if (!bestInClassFound && handler.getParameterType().isAssignableFrom(parameterType)) {
                // method is eligible, but is it the best?
                if (bestHandlerSoFar == null) {
                    // if we have none yet, this one is the best
                    bestHandlerSoFar = handler;
                } else if (bestHandlerSoFar.getDeclaringClass().equals(handler.getDeclaringClass())
                        && bestHandlerSoFar.getParameterType().isAssignableFrom(handler.getParameterType())) {
                    // this one is more specific, so it wins
                    bestHandlerSoFar = handler;
                }
            }
        }
        return bestHandlerSoFar;
    }

    /**
     * Returns the list of handlers found on target type.
     *
     * @return the list of handlers found on target type
     */
    public List<Handler> getHandlers() {
        return handlers;
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
