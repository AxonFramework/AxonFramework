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
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.Optional;

/**
 * Implementation of {@link EventHandlingMember} that wraps handlers annotated with {@link SequencingPolicy}. This
 * member creates and holds an instance of the sequencing policy specified in the annotation.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the message
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SequencingPolicyEventMessageHandlingMember<T>
        extends WrappedMessageHandlingMember<T>
        implements EventHandlingMember<T> {

    private final org.axonframework.eventhandling.sequencing.SequencingPolicy sequencingPolicy;

    /**
     * Constructs a new {@link SequencingPolicyEventMessageHandlingMember} by wrapping the given {@code original}
     * handler and creating a sequencing policy instance from the {@link SequencingPolicy} annotation.
     *
     * @param original         The original message handling member to wrap
     * @param policyAnnotation The {@link SequencingPolicy} annotation containing policy configuration
     */
    public SequencingPolicyEventMessageHandlingMember(MessageHandlingMember<T> original,
                                                      SequencingPolicy policyAnnotation) {
        super(original);
        this.sequencingPolicy = createSequencingPolicy(policyAnnotation, original);
    }

    /**
     * Returns the sequencing policy instance created from the annotation.
     *
     * @return An {@link Optional} containing the sequencing policy
     */
    public org.axonframework.eventhandling.sequencing.SequencingPolicy sequencingPolicy() {
        return sequencingPolicy;
    }

    private org.axonframework.eventhandling.sequencing.SequencingPolicy createSequencingPolicy(
            SequencingPolicy annotation,
            MessageHandlingMember<T> original
    ) {
        Class<? extends org.axonframework.eventhandling.sequencing.SequencingPolicy> policyType = annotation.type();
        String[] parameters = annotation.parameters();

        try {
            if (parameters.length == 0) {
                // Try no-arg constructor (including private ones)
                try {
                    Constructor<? extends org.axonframework.eventhandling.sequencing.SequencingPolicy> constructor =
                            policyType.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    return constructor.newInstance();
                } catch (NoSuchMethodException e) {
                    throw new UnsupportedHandlerException(
                            "SequencingPolicy " + policyType.getName() +
                                    " must have a no-arg constructor",
                            original.unwrap(Member.class).orElse(null)
                    );
                }
            } else {
                // Find constructor that matches parameter count
                Constructor<?>[] constructors = policyType.getDeclaredConstructors();
                Constructor<?> matchingConstructor = null;

                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterCount() == parameters.length) {
                        matchingConstructor = constructor;
                        break;
                    }
                }

                if (matchingConstructor == null) {
                    throw new UnsupportedHandlerException(
                            "No constructor found for SequencingPolicy " + policyType.getName() +
                                    " with " + parameters.length + " parameters",
                            original.unwrap(Member.class).orElse(null)
                    );
                }

                // Parse parameters and invoke constructor
                Object[] parsedParameters = parseParameters(matchingConstructor.getParameterTypes(), parameters);
                matchingConstructor.setAccessible(true);
                return (org.axonframework.eventhandling.sequencing.SequencingPolicy)
                        matchingConstructor.newInstance(parsedParameters);
            }
        } catch (Exception e) {
            throw new UnsupportedHandlerException(
                    "Failed to create SequencingPolicy instance: " + e.getMessage(),
                    original.unwrap(Member.class).orElse(null)
            );
        }
    }

    private Object[] parseParameters(Class<?>[] parameterTypes, String[] stringParameters) {
        Object[] parsedParameters = new Object[stringParameters.length];

        for (int i = 0; i < stringParameters.length; i++) {
            String stringValue = stringParameters[i];
            Class<?> targetType = parameterTypes[i];

            parsedParameters[i] = parseParameter(stringValue, targetType);
        }

        return parsedParameters;
    }

    private Object parseParameter(String stringValue, Class<?> targetType) {
        if (targetType == String.class) {
            return stringValue;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(stringValue);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(stringValue);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(stringValue);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(stringValue);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(stringValue);
        } else if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(stringValue);
        } else if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(stringValue);
        } else if (targetType == char.class || targetType == Character.class) {
            if (stringValue.length() != 1) {
                throw new IllegalArgumentException("Character parameter must be exactly one character");
            }
            return stringValue.charAt(0);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported parameter type: " + targetType.getName() +
                            ". Only primitives and String are supported."
            );
        }
    }
}