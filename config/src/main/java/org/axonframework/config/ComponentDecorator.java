/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.config;

/**
 * Functional interface that is able to decorate a {@link Component}, wrapping the original component.
 *
 * @author Mitchell Herrijgers
 * @since 4.7.0
 */
public interface ComponentDecorator {

    /**
     * Can decorate the given component, returning a decorated component or the original if there is no intention
     * of decorating it.
     *
     * @param configuration The Axon Configuration
     * @param component The component to be decorated
     * @return The decorated component
     */
    Object decorate(Configuration configuration, Object component);
}
