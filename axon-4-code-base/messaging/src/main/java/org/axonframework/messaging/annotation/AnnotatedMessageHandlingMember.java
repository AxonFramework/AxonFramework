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

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Implementation of a {@link MessageHandlingMember} that is used to invoke message handler methods on the target type.
 *
 * @param <T> the target type
 * @author Allard Buijze
 * @since 3.0
 */
public class AnnotatedMessageHandlingMember<T> implements MessageHandlingMember<T> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Class<?> payloadType;
    private final int parameterCount;
    private final ParameterResolver<?>[] parameterResolvers;
    private final Executable executable;
    private final Class<? extends Message<?>> messageType;
    private final HandlerAttributes attributes;

    /**
     * Initializes a new instance that will invoke the given {@code executable} (method) on a target to handle a message
     * of the given {@code messageType}.
     *
     * @param executable               the method to invoke on a target
     * @param messageType              the type of message that is expected by the target method
     * @param explicitPayloadType      the expected message payload type
     * @param parameterResolverFactory factory used to resolve method parameters
     */
    public AnnotatedMessageHandlingMember(Executable executable,
                                          @SuppressWarnings("rawtypes") Class<? extends Message> messageType,
                                          Class<?> explicitPayloadType,
                                          ParameterResolverFactory parameterResolverFactory) {
        this.executable = executable;
        //noinspection unchecked
        this.messageType = (Class<? extends Message<?>>) messageType;
        ReflectionUtils.ensureAccessible(this.executable);
        Parameter[] parameters = executable.getParameters();
        this.parameterCount = executable.getParameterCount();
        parameterResolvers = new ParameterResolver[parameterCount];
        Class<?> supportedPayloadType = explicitPayloadType;
        for (int i = 0; i < parameterCount; i++) {
            parameterResolvers[i] = parameterResolverFactory.createInstance(executable, parameters, i);
            if (parameterResolvers[i] == null) {
                throw new UnsupportedHandlerException(
                        "Unable to resolve parameter " + i + " (" + parameters[i].getType().getSimpleName() +
                                ") in handler " + executable.toGenericString() + ".", executable);
            }
            if (supportedPayloadType.isAssignableFrom(parameterResolvers[i].supportedPayloadType())) {
                supportedPayloadType = parameterResolvers[i].supportedPayloadType();
            } else if (!parameterResolvers[i].supportedPayloadType().isAssignableFrom(supportedPayloadType)) {
                throw new UnsupportedHandlerException(String.format(
                        "The method %s seems to have parameters that put conflicting requirements on the payload type" +
                                " applicable on that method: %s vs %s", executable.toGenericString(),
                        supportedPayloadType, parameterResolvers[i].supportedPayloadType()), executable);
            }
        }
        this.payloadType = supportedPayloadType;
        this.attributes = new AnnotatedHandlerAttributes(executable);
    }

    @Override
    public Class<?> payloadType() {
        return payloadType;
    }

    @Override
    public boolean canHandle(@Nonnull Message<?> message) {
        return typeMatches(message)
                && payloadType.isAssignableFrom(message.getPayloadType())
                && parametersMatch(message);
    }

    @Override
    public boolean canHandleType(@Nonnull Class<?> payloadType) {
        return this.payloadType.isAssignableFrom(payloadType);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean canHandleMessageType(@Nonnull Class<? extends Message> messageType) {
        return this.messageType.isAssignableFrom(messageType);
    }

    /**
     * Checks if this member can handle the type of the given {@code message}. This method does not check if the
     * parameter resolvers of this member are compatible with the given message. Use {@link #parametersMatch(Message)}
     * for that.
     *
     * @param message the message to check for
     * @return {@code true} if this member can handle the message type. {@code false} otherwise
     */
    protected boolean typeMatches(Message<?> message) {
        return messageType.isInstance(message);
    }

    /**
     * Checks if the parameter resolvers of this member are compatible with the given {@code message}.
     *
     * @param message the message to check for
     * @return {@code true} if the parameter resolvers can handle this message. {@code false} otherwise
     */
    protected boolean parametersMatch(Message<?> message) {
        for (ParameterResolver<?> resolver : parameterResolvers) {
            if (!resolver.matches(message)) {
                logger.debug("Parameter Resolver [{}] did not match message [{}] for payload type [{}].",
                             resolver.getClass(), message, message.getPayloadType());
                return false;
            }
        }
        return true;
    }

    @Override
    public Object handle(@Nonnull Message<?> message, T target) throws Exception {
        try {
            if (executable instanceof Method) {
                return ((Method) executable).invoke(target, resolveParameterValues(message));
            } else if (executable instanceof Constructor) {
                return ((Constructor<?>) executable).newInstance(resolveParameterValues(message));
            } else {
                throw new IllegalStateException("What kind of handler is this?");
            }
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            checkAndRethrowForExceptionOrError(e);
            throw new MessageHandlerInvocationException(
                    String.format("Error handling an object of type [%s]", message.getPayloadType()), e);
        }
    }

    private void checkAndRethrowForExceptionOrError(ReflectiveOperationException e) throws Exception {
        if (e.getCause() instanceof Exception) {
            throw (Exception) e.getCause();
        } else if (e.getCause() instanceof Error) {
            throw (Error) e.getCause();
        }
    }

    private Object[] resolveParameterValues(Message<?> message) {
        Object[] params = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            params[i] = parameterResolvers[i].resolveParameterValue(message);
        }
        return params;
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return AnnotationUtils.isAnnotationPresent(executable, annotationType);
    }

    @Override
    public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
        return AnnotationUtils.findAnnotationAttributes(executable, annotationType);
    }

    @Override
    public <R> Optional<R> attribute(String attributeKey) {
        return Optional.ofNullable(attributes.get(attributeKey));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <H> Optional<H> unwrap(Class<H> handlerType) {
        if (handlerType.isInstance(this)) {
            return (Optional<H>) Optional.of(this);
        }
        if (handlerType.isInstance(executable)) {
            return (Optional<H>) Optional.of(executable);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + executable.toGenericString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(executable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotatedMessageHandlingMember<?> that = (AnnotatedMessageHandlingMember<?>) o;
        return executable.equals(that.executable);
    }
}
