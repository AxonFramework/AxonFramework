/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * Abstract implementation of the PropertyAccessStrategy that uses a no-arg, public method to access the property
 * value. The name of the method can be derived from the name of the property.
 *
 * @author Maxim Fedorov
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractMethodPropertyAccessStrategy extends PropertyAccessStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public <T> Property<T> propertyFor(Class<? extends T> targetClass, String property) {
        String methodName = getterName(property);
        Optional<Method> method = getMethod(targetClass, methodName);
        if (!method.isPresent()) {
            logger.debug("No method with name '{}' found in {} to use as property accessor. " +
                                 "Attempting to fall back to other strategies.",
                         methodName, targetClass.getName());
            return null;
        } else {
            return new MethodAccessedProperty<>(method.get(), property);
        }
    }

    private <T> Optional<Method> getMethod(Class<T> targetClass, String methodName) {
        return Arrays.stream(targetClass.getMethods())
                     .filter(method -> method.getName().equals(methodName))
                     .filter(method -> method.getParameterCount() == 0)
                     .filter(method -> !method.getReturnType().equals(Void.TYPE))
                     .findFirst();
    }

    /**
     * Returns the name of the method that is used to access the property.
     *
     * @param property The property to access
     * @return the name of the method use as accessor
     */
    protected abstract String getterName(String property);
}
