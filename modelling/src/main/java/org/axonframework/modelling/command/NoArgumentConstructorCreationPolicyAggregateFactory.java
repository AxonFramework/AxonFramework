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

package org.axonframework.modelling.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NoArgumentConstructorCreationPolicyAggregateFactory<A> implements CreationPolicyAggregateFactory<A> {

    private final Class<? extends A> aggregateRootClass;

    public NoArgumentConstructorCreationPolicyAggregateFactory(@Nonnull Class<? extends A> aggregateRootClass) {
        this.aggregateRootClass = aggregateRootClass;
    }

    @Nonnull
    @Override
    public A createAggregateRoot(@Nullable Object identifier) {
        try {
            return aggregateRootClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };
}
