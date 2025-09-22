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
import org.axonframework.messaging.annotation.UnsupportedHandlerException;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Optional;
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

    private static class SequencingPolicyEventMessageHandlingMember<T>
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
                    // Find constructor that matches the total parameters needed
                    Constructor<?>[] constructors = policyType.getDeclaredConstructors();
                    Constructor<?> matchingConstructor = null;

                    for (Constructor<?> constructor : constructors) {
                        Class<?>[] paramTypes = constructor.getParameterTypes();

                        // Check if constructor has Class parameter as first parameter
                        boolean hasClassAsFirstParam = paramTypes.length > 0 && paramTypes[0] == Class.class;

                        // Count non-Class parameters (which need to be provided as strings)
                        long nonClassParameterCount;
                        if (hasClassAsFirstParam) {
                            // If first parameter is Class, count remaining parameters
                            nonClassParameterCount = paramTypes.length - 1;
                        } else {
                            // No Class parameter, count all parameters
                            nonClassParameterCount = paramTypes.length;
                        }

                        if (nonClassParameterCount == parameters.length) {
                            matchingConstructor = constructor;
                            break;
                        }
                    }

                    if (matchingConstructor == null) {
                        throw new UnsupportedHandlerException(
                                "No constructor found for SequencingPolicy " + policyType.getName() +
                                        " that matches " + parameters.length + " string parameters (excluding Class parameters)",
                                original.unwrap(Member.class).orElse(null)
                        );
                    }

                    // Parse parameters and invoke constructor
                    Object[] parsedParameters = parseParameters(matchingConstructor.getParameterTypes(), parameters, original);
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

        private Object[] parseParameters(Class<?>[] parameterTypes, String[] stringParameters, MessageHandlingMember<T> original) {
            Object[] parsedParameters = new Object[parameterTypes.length];
            int stringParameterIndex = 0;

            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> targetType = parameterTypes[i];

                if (targetType == Class.class) {
                    // Class parameter must be first parameter - use the payload type from the MessageHandlingMember
                    if (i != 0) {
                        throw new IllegalArgumentException(
                                "Class parameter must be the first parameter in constructor. Found at position: " + i
                        );
                    }
                    parsedParameters[i] = original.payloadType();
                } else {
                    // Parse from string parameters
                    if (stringParameterIndex >= stringParameters.length) {
                        throw new IllegalArgumentException(
                                "Not enough string parameters provided. Expected parameter for type: " + targetType.getName()
                        );
                    }
                    String stringValue = stringParameters[stringParameterIndex];
                    parsedParameters[i] = parseParameter(stringValue, targetType);
                    stringParameterIndex++;
                }
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
            } else if (targetType == Class.class) {
                try {
                    return Class.forName(stringValue);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Cannot find class: " + stringValue, e);
                }
            } else {
                throw new UnsupportedOperationException(
                        "Unsupported parameter type: " + targetType.getName() +
                                ". Only primitives, String, and Class are supported."
                );
            }
        }
    }
}