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

package org.axonframework.modelling.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface to describe a way to create Aggregate instances based on an identifier when an instance has to be created
 * in order to be used in Command handlers annotated with {@link CreationPolicy}.
 *
 * @param <A> The type of aggregate this factory creates
 * @author Stefan Andjelkovic
 * @since 4.6.0
 */
@FunctionalInterface
public interface CreationPolicyAggregateFactory<A> {

    /**
     * Instantiates the aggregate instance based on the provided identifier. The identifier can be a null if no
     * identifier is found or provided in the command message.
     *
     * @param identifier the identifier extracted from the command message
     * @return an identifier initialized aggregate root instance ready to handle commands
     */
    @Nonnull
    A create(@Nullable Object identifier);
}
