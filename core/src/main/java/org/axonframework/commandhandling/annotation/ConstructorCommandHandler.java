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

package org.axonframework.commandhandling.annotation;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.unitofwork.UnitOfWork;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Command Handler that creates a new aggregate instance by invoking that aggregate's constructor.
 *
 * @author Allard Buijze
 * @since 1.2
 */
class ConstructorCommandHandler<T extends AggregateRoot> {

    private final Constructor<T> constructor;
    private final Class<?> parameterType;
    private final boolean optionalParameter;

    /**
     * Creates a new instance for the given <code>constructor</code>, accepting the given <code>parameterType</code>
     * and an <code>optionalParameter</code>.
     *
     * @param constructor       The constructor this handler should invoke
     * @param parameterType     The type of parameter accepted by the constructor
     * @param optionalParameter Indicator of whether this constructor accepts the optional second parameter
     */
    ConstructorCommandHandler(Constructor<T> constructor, Class<?> parameterType, boolean optionalParameter) {
        this.constructor = constructor;
        this.parameterType = parameterType;
        this.optionalParameter = optionalParameter;
    }

    /**
     * Invokes the constructor using the given <code>command</code> and optionally <code>unitOfWork</code>.
     *
     * @param command    The command to pass to the constructor
     * @param unitOfWork The optional unitOfWork to pass if the constructor contains the optional parameter
     * @return The constructed aggregate instance
     *
     * @throws IllegalAccessException if the security settings prevent access to the constructor
     * @throws InstantiationException if an error occurs creating the aggregate instance
     * @throws java.lang.reflect.InvocationTargetException
     *                                if the constructor threw an exception
     */
    public T invoke(Object command, UnitOfWork unitOfWork)
            throws InvocationTargetException, IllegalAccessException, InstantiationException {
        if (optionalParameter) {
            return constructor.newInstance(command, unitOfWork);
        } else {
            return constructor.newInstance(command);
        }
    }

    /**
     * Returns the type of command this handler handles.
     *
     * @return the type of command this handler handles
     */
    public Class<?> getCommandType() {
        return parameterType;
    }
}
