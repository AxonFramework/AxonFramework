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

package org.axonframework.modelling.command.inspection;

import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.CreationPolicy;

import javax.annotation.Nonnull;

/**
 * Implementation of {@link HandlerEnhancerDefinition} used for {@link CreationPolicy} annotated methods.
 *
 * @author Marc Gathier
 * @since 4.3
 */
public class MethodCreationPolicyDefinition implements HandlerEnhancerDefinition {

    @Override
    public @Nonnull <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original.<AggregateCreationPolicy>attribute(HandlerAttributes.AGGREGATE_CREATION_POLICY)
                       .map(creationPolicy -> (MessageHandlingMember<T>) new MethodCreationPolicyHandlingMember<>(
                               original, creationPolicy
                       ))
                       .orElse(original);
    }

    private static class MethodCreationPolicyHandlingMember<T>
            extends WrappedMessageHandlingMember<T>
            implements CreationPolicyMember<T> {

        private final AggregateCreationPolicy creationPolicy;

        private MethodCreationPolicyHandlingMember(MessageHandlingMember<T> delegate,
                                                   AggregateCreationPolicy creationPolicy) {
            super(delegate);
            this.creationPolicy = creationPolicy;
        }

        @Override
        public AggregateCreationPolicy creationPolicy() {
            return creationPolicy;
        }
    }
}
