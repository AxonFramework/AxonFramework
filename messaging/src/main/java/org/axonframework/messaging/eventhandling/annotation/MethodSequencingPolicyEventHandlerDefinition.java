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

package org.axonframework.messaging.eventhandling.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.core.annotation.MessageHandlingMember;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.core.annotation.WrappedMessageHandlingMember;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * Definition of {@link EventHandlingMember event handlers} that are annotated with {@link SequencingPolicy}. These
 * handlers are wrapped with a {@link SequencingPolicyEventMessageHandlingMember} that provides access to the configured
 * sequencing policy.
 * <p>
 * The {@link SequencingPolicy} annotation can be applied either directly to the handler method or to the declaring
 * class. When applied to the class, all handler methods in that class will inherit the sequencing policy. Method-level
 * annotations take precedence over class-level annotations.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MethodSequencingPolicyEventHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public @Nonnull <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        return original instanceof EventHandlingMember<T> eventHandlingMember
                ? eventHandlingMember.unwrap(Method.class)
                                     .flatMap(method -> optionalSequencingAwareMember(eventHandlingMember, method))
                                     .orElse(eventHandlingMember)
                : original;
    }

    private <T> Optional<MessageHandlingMember<T>> optionalSequencingAwareMember(
            EventHandlingMember<T> eventHandlingMember,
            Method method
    ) {
        return Optional.ofNullable(method.getAnnotation(SequencingPolicy.class))
                       .or(() -> Optional.ofNullable(method.getDeclaringClass().getAnnotation(SequencingPolicy.class)))
                       .map(annotation -> new SequencingPolicyEventMessageHandlingMember<>(
                               eventHandlingMember, annotation
                       ));
    }

    /**
     * Extracting {@link org.axonframework.messaging.core.sequencing.SequencingPolicy} from the
     * {@link SequencingPolicy} annotation.
     *
     * @param <T> The type of the declaring class of the event handling method.
     */
    @Internal
    static class SequencingPolicyEventMessageHandlingMember<T>
            extends WrappedMessageHandlingMember<T>
            implements EventHandlingMember<T> {

        private final EventHandlingMember<T> delegate;
        private final org.axonframework.messaging.core.sequencing.SequencingPolicy sequencingPolicy;

        /**
         * Constructs a new SequencingPolicyEventMessageHandlingMember by wrapping the given {@code original} handler
         * and creating a sequencing policy instance from the {@link SequencingPolicy} annotation.
         *
         * @param original         The original message handling member to wrap.
         * @param policyAnnotation The {@link SequencingPolicy} annotation containing policy configuration.
         */
        private SequencingPolicyEventMessageHandlingMember(EventHandlingMember<T> original,
                                                           SequencingPolicy policyAnnotation) {
            super(original);
            this.delegate = original;
            this.sequencingPolicy = createSequencingPolicy(policyAnnotation, original);
        }

        @Override
        public String eventName() {
            return delegate.eventName();
        }

        /**
         * Returns the sequencing policy instance created from the annotation.
         *
         * @return The sequencing policy
         */
        public org.axonframework.messaging.core.sequencing.SequencingPolicy sequencingPolicy() {
            return sequencingPolicy;
        }

        private org.axonframework.messaging.core.sequencing.SequencingPolicy createSequencingPolicy(
                SequencingPolicy annotation,
                MessageHandlingMember<T> original
        ) {
            var policyType = annotation.type();
            var parameters = annotation.parameters();

            try {
                return parameters.length == 0
                        ? createNoArgPolicy(policyType, original)
                        : createParameterizedPolicy(policyType, parameters, original);
            } catch (Exception e) {
                throw new UnsupportedHandlerException(
                        "Failed to create SequencingPolicy instance: " + e.getMessage(),
                        original.unwrap(Member.class).orElse(null)
                );
            }
        }

        private org.axonframework.messaging.core.sequencing.SequencingPolicy createNoArgPolicy(
                Class<? extends org.axonframework.messaging.core.sequencing.SequencingPolicy> policyType,
                MessageHandlingMember<T> original
        ) throws Exception {
            try {
                var constructor = policyType.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (NoSuchMethodException e) {
                throw new UnsupportedHandlerException(
                        "SequencingPolicy " + policyType.getName() + " must have a no-arg constructor",
                        original.unwrap(Member.class).orElse(null)
                );
            }
        }

        private org.axonframework.messaging.core.sequencing.SequencingPolicy createParameterizedPolicy(
                Class<? extends org.axonframework.messaging.core.sequencing.SequencingPolicy> policyType,
                String[] parameters,
                MessageHandlingMember<T> original
        ) throws Exception {
            var matchingConstructor = findMatchingConstructor(policyType, parameters.length, original);
            var parsedParameters = parseParameters(matchingConstructor.getParameterTypes(), parameters, original);
            matchingConstructor.setAccessible(true);
            return (org.axonframework.messaging.core.sequencing.SequencingPolicy) matchingConstructor.newInstance(
                    parsedParameters);
        }

        private Constructor<?> findMatchingConstructor(
                Class<? extends org.axonframework.messaging.core.sequencing.SequencingPolicy> policyType,
                int parameterCount,
                MessageHandlingMember<T> original
        ) {
            return Arrays.stream(policyType.getDeclaredConstructors())
                         .filter(constructor -> countNonClassParameters(constructor) == parameterCount)
                         .findFirst()
                         .orElseThrow(() -> new UnsupportedHandlerException(
                                 "No constructor found for SequencingPolicy " + policyType.getName() +
                                         " that matches " + parameterCount
                                         + " string parameters (excluding Class parameters)",
                                 original.unwrap(Member.class).orElse(null)
                         ));
        }

        private long countNonClassParameters(Constructor<?> constructor) {
            var paramTypes = constructor.getParameterTypes();
            var hasClassAsFirstParam = paramTypes.length > 0 && paramTypes[0] == Class.class;
            return hasClassAsFirstParam ? paramTypes.length - 1 : paramTypes.length;
        }

        private Object[] parseParameters(Class<?>[] parameterTypes, String[] stringParameters,
                                         MessageHandlingMember<T> original) {
            var parsedParameters = new Object[parameterTypes.length];
            var stringParameterIndex = 0;

            for (int i = 0; i < parameterTypes.length; i++) {
                var targetType = parameterTypes[i];

                if (targetType == Class.class) {
                    if (i != 0) {
                        throw new IllegalArgumentException(
                                "Class parameter must be the first parameter in constructor. Found at position: " + i
                        );
                    }
                    parsedParameters[i] = original.payloadType();
                } else {
                    if (stringParameterIndex >= stringParameters.length) {
                        throw new IllegalArgumentException(
                                "Not enough string parameters provided. Expected parameter for type: "
                                        + targetType.getName()
                        );
                    }
                    var stringValue = stringParameters[stringParameterIndex];
                    parsedParameters[i] = parseParameter(stringValue, targetType);
                    stringParameterIndex++;
                }
            }

            return parsedParameters;
        }

        private Object parseParameter(String stringValue, Class<?> targetType) {
            return switch (targetType.getName()) {
                case "java.lang.String" -> stringValue;
                case "int", "java.lang.Integer" -> Integer.parseInt(stringValue);
                case "long", "java.lang.Long" -> Long.parseLong(stringValue);
                case "double", "java.lang.Double" -> Double.parseDouble(stringValue);
                case "float", "java.lang.Float" -> Float.parseFloat(stringValue);
                case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(stringValue);
                case "byte", "java.lang.Byte" -> Byte.parseByte(stringValue);
                case "short", "java.lang.Short" -> Short.parseShort(stringValue);
                case "char", "java.lang.Character" -> parseCharacter(stringValue);
                case "java.lang.Class" -> parseClass(stringValue);
                default -> throw new UnsupportedOperationException(
                        "Unsupported parameter type: " + targetType.getName() +
                                ". Only primitives, String, and Class are supported."
                );
            };
        }

        private char parseCharacter(String stringValue) {
            if (stringValue.length() != 1) {
                throw new IllegalArgumentException("Character parameter must be exactly one character");
            }
            return stringValue.charAt(0);
        }

        private Class<?> parseClass(String stringValue) {
            try {
                return Class.forName(stringValue);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Cannot find class: " + stringValue, e);
            }
        }
    }
}