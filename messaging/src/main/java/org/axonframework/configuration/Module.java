/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.configuration;

/**
 * Interface describing a module of Axon Framework's Configuration.
 * <p>
 * Modules are relatively independent. They can be {@link NewConfigurer#registerModule(ModuleBuilder) registered} on a
 * parent {@link NewConfigurer} or registered in a nested style on another {@link Module} through the dedicated register
 * module operation. Furthermore, a module is able to access the registered {@link Component Components} from its
 * parent.
 *
 * @param <M> The type of module this implementation returns. This generic allows us to support fluent interfacing.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 3.0.0
 */
public interface Module<M extends Module<M>> extends NewConfigurer<M> {

    /**
     * Checks whether this configuration {@code Module} is of the given {@code type}.
     *
     * @param type A {@link Class} type to check the configuration {@code Module} against.
     * @return {@code true} when this configuration {@code Module} is of given {@code type}, {@code false} otherwise.
     */
    default boolean isType(Class<?> type) {
        return type.isInstance(this);
    }
}
