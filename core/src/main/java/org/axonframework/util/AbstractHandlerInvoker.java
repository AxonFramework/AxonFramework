/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.util.reflection.MethodAccessibilityCallback;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.security.AccessController.doPrivileged;

/**
 * Abstract class to support implementations that need to invoke methods based on an annotation.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public abstract class AbstractHandlerInvoker extends AbstractHandlerInspector {

    private final Object target;

    /**
     * Initialize a handler invoker for the given <code>target</code> object that has handler method annotated with
     * given <code>annotationType</code>.
     *
     * @param target         The target to invoke methods on
     * @param annotationType The type of annotation used to demarcate the handler methods
     */
    public AbstractHandlerInvoker(Object target, Class<? extends Annotation> annotationType) {
        super(annotationType);
        this.target = target;
    }

    /**
     * Invoke the handler demarcated with the given <code>annotationClass</code> on the target for the given
     * <code>event</code>.
     *
     * @param parameter the event to handle
     * @return the return value of the invocation
     *
     * @throws IllegalAccessException    When a security policy prevents invocation using reflection
     * @throws InvocationTargetException If the handler method threw an exception
     */
    protected Object invokeHandlerMethod(Object parameter) throws InvocationTargetException, IllegalAccessException {
        return invokeHandlerMethod(parameter, null);
    }

    /**
     * Invoke the handler demarcated with the given <code>annotationClass</code> on the target for the given
     * <code>event</code> and an optional <code>secondHandlerParameter</code>.
     *
     * @param parameter              the event to handle
     * @param secondHandlerParameter An optional second parameter allowed on the annotated method
     * @return the return value of the invocation
     *
     * @throws IllegalAccessException    When a security policy prevents invocation using reflection
     * @throws InvocationTargetException If the handler method threw an exception
     */
    protected Object invokeHandlerMethod(Object parameter, Object secondHandlerParameter)
            throws InvocationTargetException, IllegalAccessException {
        final Method m = findHandlerMethod(target.getClass(), parameter.getClass());
        if (m == null) {
            // event listener doesn't support this type of event
            return onNoMethodFound(parameter.getClass());
        }
        if (!m.isAccessible()) {
            doPrivileged(new MethodAccessibilityCallback(m));
        }
        Object retVal;
        if (m.getParameterTypes().length == 1) {
            retVal = m.invoke(target, parameter);
        } else {
            retVal = m.invoke(target, parameter, secondHandlerParameter);
        }
        // let's make a clear distinction between null return value and void methods
        if (Void.TYPE.equals(m.getReturnType())) {
            return Void.TYPE;
        }
        return retVal;
    }

    /**
     * Indicates what needs to happen when no handler is found for a given parameter. The default behavior is to return
     * {@link Void#TYPE}.
     *
     * @param parameterType The type of parameter for which no handler could be found
     * @return the value to return when no handler method is found. Defaults to {@link Void#TYPE}.
     */
    protected Object onNoMethodFound(Class<?> parameterType) {
        return Void.TYPE;
    }

    /**
     * Returns the target on which handler methods are invoked.
     *
     * @return the target on which handler methods are invoked
     */
    public Object getTarget() {
        return target;
    }
}
