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

package org.axonframework.messaging.unitofwork;

/**
 * Indicates that a component can be bound to a {@link ProcessingContext}. This allows the component to be decorated a
 * component of the same type that uses the given {@link ProcessingContext} for any calls made without a
 * {@link ProcessingContext} argument.
 *
 * @param <C> The type of component that can be bound to a {@link ProcessingContext}.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public interface ProcessingContextBindableComponent<C> {

    /**
     * Bind this {@code C} to the given {@code processingContext}. Components implementing this interface should return
     * a decorated component of the same type that uses the given {@code processingContext} for any calls made without a
     * {@link ProcessingContext} argument.
     *
     * @param processingContext The {@link ProcessingContext} to bind this component to.
     * @return The component that is bound to the given {@code processingContext}.
     */
    C forProcessingContext(ProcessingContext processingContext);
}
