/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.model;

import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.common.annotation.MessageHandlerInvocationException;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.messaging.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;

public abstract class AbstractMessageHandler<T> implements MessageHandler<T>, AnnotatedElement {

    private final Class<?> payloadType;
    private final int parameterCount;
    private final ParameterResolver[] parameterResolvers;
    private final Executable executable;

    public AbstractMessageHandler(Executable executable, ParameterResolverFactory parameterResolverFactory) {
        this.executable = executable;
        ReflectionUtils.ensureAccessible(this.executable);
        Parameter[] parameters = executable.getParameters();
        this.parameterCount = executable.getParameterCount();
        parameterResolvers = new ParameterResolver[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            Parameter parameter = parameters[i];
            parameterResolvers[i] = (parameterResolverFactory.createInstance(executable.getAnnotations(), parameter.getType(), parameter.getAnnotations()));
        }
        if (parameterResolvers[0] == null) {
            this.payloadType = executable.getParameterTypes()[0];
            parameterResolvers[0] = new ParameterResolver() {
                @Override
                public Object resolveParameterValue(Message message) {
                    return message.getPayload();
                }

                @Override
                public boolean matches(Message message) {
                    return payloadType.isAssignableFrom(message.getPayloadType());
                }
            };
        } else {
            this.payloadType = null; // TODO: Expect payload type from annotation
        }
    }

    @Override
    public Class<?> payloadType() {
        return payloadType;
    }

    @Override
    public int priority() {
        return parameterCount;
    }

    @Override
    public boolean canHandle(Message<?> message) {
        return typeMatches(message) && parametersMatch(message);
    }

    protected abstract boolean typeMatches(Message<?> message);

    protected boolean parametersMatch(Message<?> message) {
        for (ParameterResolver resolver : parameterResolvers) {
            if (!resolver.matches(message)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object handle(Message<?> message, T target) {
        try {
            if (executable instanceof Method) {
                return ((Method) executable).invoke(target, resolveParameterValues(message));
            } else if (executable instanceof Constructor) {
                return ((Constructor) executable).newInstance(resolveParameterValues(message));
            } else {
                throw new IllegalStateException("What kind of handler is this?");
            }
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new MessageHandlerInvocationException("Error invoking CommandHandler method", e.getCause());
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
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return executable.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return executable.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return executable.getDeclaredAnnotations();
    }

    @Override
    public <A extends Annotation> A[] getDeclaredAnnotationsByType(Class<A> annotationClass) {
        return executable.getDeclaredAnnotationsByType(annotationClass);
    }

    @Override
    public <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass) {
        return executable.getDeclaredAnnotation(annotationClass);
    }

    @Override
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationClass) {
        return executable.getAnnotationsByType(annotationClass);
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return executable.isAnnotationPresent(annotationClass);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + executable.toGenericString();
    }
}
