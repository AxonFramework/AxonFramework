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

package org.axonframework.messaging.annotation;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;

/**
 * ParameterResolver implementation that injects a fixed value. Useful for injecting parameter values that do not rely
 * on information contained in the incoming message itself.
 *
 * @param <T> The type of value resolved by this parameter
 * @author Allard Buijze
 * @since 2.0
 */
public class FixedValueParameterResolver<T> implements ParameterResolver<T> {

    private final T value;

    /**
     * Initialize the ParameterResolver to inject the given {@code value} for each incoming message.
     *
     * @param value The value to inject as parameter
     */
    public FixedValueParameterResolver(T value) {
        this.value = value;
    }

    @Override
    public T resolveParameterValue(Message message, ProcessingContext processingContext) {
        return value;
    }

    @Override
    public boolean matches(Message message, ProcessingContext processingContext) {
        return true;
    }
}
