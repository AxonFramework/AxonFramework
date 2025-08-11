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

package org.axonframework.modelling.event;

import org.axonframework.eventhandling.EventHandlerRegistry;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.StateManager;

import jakarta.annotation.Nonnull;

/**
 * Interface describing a registry of {@link StatefulEventHandler stateful event handlers}. These event handlers
 * receive a {@link StateManager state parameter} which can be used to load state during the execution of the event
 * handler.
 *
 * @param <S> The type of the registry itself.
 * @author Mateusz Nowak
 * @see StateManager
 * @since 5.0.0
 */
public interface StatefulEventHandlerRegistry<S extends StatefulEventHandlerRegistry<S>>
        extends EventHandlerRegistry {

    /**
     * Subscribe the given {@link StatefulEventHandler} for a {@link QualifiedName name}.
     *
     * @param name         The name of the given {@link StatefulEventHandler} can handle.
     * @param eventHandler The handler instance that handles {@link EventMessage events} for the given name.
     * @return This registry for fluent interfacing.
     */
    S subscribe(@Nonnull QualifiedName name, @Nonnull StatefulEventHandler eventHandler);
}