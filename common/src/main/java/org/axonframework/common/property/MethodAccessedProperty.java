/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.common.property;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;

/**
 * Property implementation that invokes a method to obtain a value of a property for a given instance.
 *
 * @param <T> The type of object defining this property
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public class MethodAccessedProperty<T> implements Property<T> {

    private final Method method;
    private final String property;

    /**
     * Initialize a reader that uses given {@code accessorMethod} to access a property with given
     * {@code propertyName}.
     *
     * @param accessorMethod The method providing the property value
     * @param propertyName   The name of the property
     */
    public MethodAccessedProperty(Method accessorMethod, String propertyName) {
        property = propertyName;
        method = ensureAccessible(accessorMethod);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> V getValue(T target) {
        try {
            return (V) method.invoke(target);
        } catch (IllegalAccessException e) {
            throw new PropertyAccessException(format(
                    "Failed to get value of '%s' using method '%s()' of '%s'. Property methods should be accessible",
                    property, method.getName(), target.getClass().getName()), e);
        } catch (InvocationTargetException e) {
            throw new PropertyAccessException(format(
                    "Failed to get value of '%s' using method '%s()' of '%s'. "
                            + "Property methods should not throw exceptions.",
                    property, method.getName(), target.getClass().getName()), e);
        }
    }
}
