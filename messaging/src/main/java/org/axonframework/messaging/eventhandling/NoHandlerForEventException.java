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

package org.axonframework.messaging.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.QualifiedName;

/**
 * Exception thrown whenever an {@link EventHandlingComponent} is given an {@link EventMessage} for which it does not
 * have a {@link EventHandlingComponent#subscribe(QualifiedName, EventHandler) subscribed} {@link EventHandler}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class NoHandlerForEventException extends RuntimeException {

    /**
     * Constructs a {@code NoHandlerForEventException} signaling there was no {@link EventHandler} for the given
     * {@code eventName} present in the component with the given {@code componentName}.
     *
     * @param eventName     The qualified name for which there was no {@link EventHandler}.
     * @param componentName The name of the component that did not have an {@link EventHandler} for the given
     *                      {@code eventName}.
     */
    public NoHandlerForEventException(@Nonnull QualifiedName eventName, @Nonnull String componentName) {
        super("No handler found for event with name [" + eventName + "] in component [" + componentName + "]");
    }
}
