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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.security.AccessController.doPrivileged;

/**
 * Represents a method recognized as a handler by the handler inspector (see {@link AbstractHandlerInspector}).
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class Handler {

    private final Method method;
    private final Class<?> parameterType;
    private final boolean optionalParameter;
    private final Class<?> declaringClass;

    /**
     * Create a handler instance for the given method. A method is regarded a handler method if it has either 1 or 2
     * parameters, of which the first parameter is primary parameter.
     *
     * @param method the method found to be a handler
     */
    public Handler(Method method) {
        this.method = method;
        Class<?>[] parameterTypes = method.getParameterTypes();
        this.parameterType = parameterTypes[0];
        this.optionalParameter = parameterTypes.length > 1;
        this.declaringClass = method.getDeclaringClass();
        if (!method.isAccessible()) {
            doPrivileged(new MemberAccessibilityCallback(method));
        }
    }

    /**
     * Returns the method found to be a handler.
     *
     * @return the method instance
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Returns the main parameter of the handler.
     *
     * @return the main parameter of the handler
     */
    public Class<?> getParameterType() {
        return parameterType;
    }

    /**
     * Returns the class on which the handler method is declared.
     *
     * @return the class on which the handler method is declared
     */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /**
     * Indicates whether or not this handler has an optional second parameter.
     *
     * @return true if the handler has a second parameter, false otherwise.
     */
    public boolean hasOptionalParameter() {
        return optionalParameter;
    }

    /**
     * Invokes the handler method on given <code>target</code> using given <code>parameter</code> and -when available
     * on the handler- the given <code>secondHandlerParameter</code>. If the handler only specifies a single
     * parameter, the <code>secondHandlerParameter</code> is ignored.
     *
     * @param target                 The instance to invoke the handler on
     * @param parameter              The first parameter of the handler method
     * @param secondHandlerParameter The (optional) second parameter of the handler method
     * @return The return value of the handler invocation
     *
     * @throws IllegalAccessException    If the handler method could not be accessed
     * @throws InvocationTargetException If the target handler threw an exception
     */
    public Object invoke(Object target, Object parameter, Object secondHandlerParameter)
            throws IllegalAccessException, InvocationTargetException {
        Object retVal;
        if (hasOptionalParameter()) {
            retVal = getMethod().invoke(target, parameter, secondHandlerParameter);
        } else {
            retVal = getMethod().invoke(target, parameter);
        }
        return retVal;
    }
}
