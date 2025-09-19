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

package org.axonframework.eventhandling.annotation;

import org.axonframework.eventhandling.annotations.SequencingPolicy;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;

import java.lang.reflect.Method;
import jakarta.annotation.Nonnull;

/**
 * Definition of handlers that are annotated with {@link SequencingPolicy}. These handlers are wrapped with a
 * {@link SequencingPolicyEventMessageHandlingMember} that provides access to the configured sequencing policy.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MethodSequencingPolicyEventMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public @Nonnull <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original.unwrap(Method.class)
                       .map(method -> {
                           // Check method-level annotation first
                           SequencingPolicy methodAnnotation = method.getAnnotation(SequencingPolicy.class);
                           if (methodAnnotation != null) {
                               return (MessageHandlingMember<T>)
                                       new SequencingPolicyEventMessageHandlingMember<>(original, methodAnnotation);
                           }

                           // Check class-level annotation
                           SequencingPolicy classAnnotation = method.getDeclaringClass()
                                   .getAnnotation(SequencingPolicy.class);
                           if (classAnnotation != null) {
                               return (MessageHandlingMember<T>)
                                       new SequencingPolicyEventMessageHandlingMember<>(original, classAnnotation);
                           }

                           return original;
                       })
                       .orElse(original);
    }
}