/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * Abstract utility class that inspects handler methods.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractHandlerInspector {

    private final Class<? extends Annotation> annotationType;

    /**
     * Initialize an AbstractHandlerInspector, where the given <code>annotationType</code> is used to annotate the Event
     * Handler methods.
     *
     * @param annotationType The annotation used on the Event Handler methods.
     */
    public AbstractHandlerInspector(Class<? extends Annotation> annotationType) {
        this.annotationType = annotationType;
    }

    /**
     * Returns the handler method that handles objects of the given <code>parameterType</code>. Returns
     * <code>null</code> is no such method is found.
     *
     * @param targetType    The type on which to find a suitable handler
     * @param parameterType The parameter type to find a handler for
     * @return the  handler method for the given parameterType
     */
    protected Method findHandlerMethod(Class<?> targetType, final Class<?> parameterType) {
        Method bestMethodSoFar = null;
        for (Method method : ReflectionUtils.methodsOf(targetType)) {
            Method foundSoFar = bestMethodSoFar;
            Class<?> classUnderInvestigation = method.getDeclaringClass();
            boolean bestInClassFound =
                    foundSoFar != null
                            && !classUnderInvestigation.equals(foundSoFar.getDeclaringClass())
                            && classUnderInvestigation.isAssignableFrom(foundSoFar.getDeclaringClass());
            if (!bestInClassFound && method.isAnnotationPresent(annotationType)
                    && method.getParameterTypes()[0].isAssignableFrom(parameterType)) {
                // method is eligible, but is it the best?
                if (bestMethodSoFar == null) {
                    // if we have none yet, this one is the best
                    bestMethodSoFar = method;
                } else if (bestMethodSoFar.getDeclaringClass().equals(method.getDeclaringClass())
                        && bestMethodSoFar.getParameterTypes()[0].isAssignableFrom(method.getParameterTypes()[0])) {
                    // this one is more specific, so it wins
                    bestMethodSoFar = method;
                }
            }
        }
        return bestMethodSoFar;
    }
}
