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

package org.axonframework.messaging.annotation;

import java.lang.reflect.Executable;
import java.util.Optional;

/**
 * Interface that describes an object capable of inspecting a method to determine if the method is suitable for message
 * handling. If the method is suitable the definition returns a {@link MessageHandler} instance to invoke the method.
 */
public interface HandlerDefinition {

    /**
     * Create a {@link MessageHandlingMember} for the given {@code executable} method. Use the given {@code
     * parameterResolverFactory} to resolve the method's parameters.
     *
     * @param declaringType            The type of object declaring the given executable method
     * @param executable               The method to inspect
     * @param parameterResolverFactory Factory for a {@link ParameterResolver} of the method
     * @param <T>                      The type of the declaring object
     * @return An optional containing the handler if the method is suitable, or an empty Nullable otherwise
     */
    <T> Optional<MessageHandlingMember<T>> createHandler(Class<T> declaringType, Executable executable,
                                                         ParameterResolverFactory parameterResolverFactory);
}
