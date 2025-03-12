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
 * Functional interface describing how to build a {@link Module} of type {@code M} using a parent
 * {@link NewConfiguration} during construction.
 *
 * @param <M> The type of module created by this builder.
 * @author Allard Buijze
 * @author Steven van Beelen
 * @since 5.0.0
 */
public interface ModuleBuilder<M extends Module<M>> {

    /**
     * Builds a {@link Module} using the given {@code parent} {@link NewConfiguration} during construction.
     *
     * @param parent The configuration from which other components can be retrieved to build the result.
     * @return A {@link Module} of type {@code M} using the given {@code parent} during construction.
     */
    M build(NewConfiguration parent);
}
