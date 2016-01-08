/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.common.annotation;

import org.axonframework.common.Assert;
import org.axonframework.messaging.Message;

import java.lang.annotation.Annotation;
import java.util.Arrays;

/**
 * Abstract superclass for annotation based Message handlers. Handlers can be compared with on another to decide upon
 * their priority. Handlers that deal with unrelated payloads (i.e. have no parent-child relationship) are ordered
 * based on their payload type's class name.
 * <p/>
 * Handler invokers should always evaluate the first (smallest) suitable handler before evaluating the next.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractMessageHandler implements Comparable<AbstractMessageHandler> {

    private final Score score;
    private final Class<?> payloadType;
    private final ParameterResolver[] parameterValueResolvers;

    /**
     * Initializes the MessageHandler to handle messages with the given <code>payloadType</code>, declared in the given
     * <code>declaringClass</code> and using the given <code>parameterValueResolvers</code>.
     *
     * @param payloadType             The type of payload this handlers deals with
     * @param declaringClass          The class on which the handler is declared
     * @param parameterValueResolvers The resolvers for each of the handlers' parameters
     */
    protected AbstractMessageHandler(Class<?> payloadType, Class<?> declaringClass,
                                     ParameterResolver... parameterValueResolvers) {
        this.score = new Score(payloadType, declaringClass);
        this.payloadType = payloadType;
        this.parameterValueResolvers = Arrays.copyOf(parameterValueResolvers, parameterValueResolvers.length);
    }

    /**
     * Constructor used for implementations that delegate activity to another handler. Required information is gathered
     * from the target handler.
     *
     * @param delegate The handler to which actual invocations are being forwarded
     */
    protected AbstractMessageHandler(AbstractMessageHandler delegate) {
        this.score = delegate.score;
        this.payloadType = delegate.payloadType;
        this.parameterValueResolvers = delegate.parameterValueResolvers;
    }

    /**
     * Indicates whether this Handler is suitable for the given <code>message</code>.
     *
     * @param message The message to inspect
     * @return <code>true</code> if this handler can handle the message, otherwise <code>false</code>.
     */
    public boolean matches(Message message) {
        Assert.notNull(message, "Event may not be null");
        if (payloadType != null && !payloadType.isAssignableFrom(message.getPayloadType())) {
            return false;
        }
        for (ParameterResolver parameterResolver : parameterValueResolvers) {
            if (!parameterResolver.matches(message)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Invokes this handler for the given <code>target</code> instance, using the given <code>message</code> as
     * source object to provide parameter values.
     *
     * @param target  The target instance to invoke the Handler on.
     * @param message The message providing parameter values
     * @return The result of the handler invocation
     */
    public abstract Object invoke(Object target, Message message);

    /**
     * Returns the type of payload this handler expects.
     *
     * @return the type of payload this handler expects
     */
    public Class getPayloadType() {
        return payloadType;
    }

    @Override
    public int compareTo(AbstractMessageHandler o) {
        return score.compareTo(o.score);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof AbstractMessageHandler)
                && ((AbstractMessageHandler) obj).score.equals(score);
    }

    @Override
    public int hashCode() {
        return score.hashCode();
    }

    /**
     * Finds ParameterResolvers for the given Member details. The returning array contains as many elements as the
     * given <code>parameterTypes</code>, where each ParameterResolver corresponds with the parameter type at the same
     * location.
     *
     * @param parameterResolverFactory The factory to create the ParameterResolvers with
     * @param memberAnnotations        The annotations on the member (e.g. method)
     * @param parameterTypes           The parameter type of the member
     * @param parameterAnnotations     The annotations on each of the parameters
     * @param resolvePayload           Indicates whether the payload of the message should be resolved from the
     *                                 parameters
     * @return the parameter resolvers for the given Member details
     *
     * @see java.lang.reflect.Method
     * @see java.lang.reflect.Constructor
     */
    protected static ParameterResolver[] findResolvers(ParameterResolverFactory parameterResolverFactory,
                                                       Annotation[] memberAnnotations, Class<?>[] parameterTypes,
                                                       Annotation[][] parameterAnnotations, boolean resolvePayload) {
        int parameters = parameterTypes.length;
        ParameterResolver[] parameterValueResolvers = new ParameterResolver[parameters];
        for (int i = 0; i < parameters; i++) {
            // currently, the first parameter is considered the payload parameter
            final boolean isPayloadParameter = resolvePayload && i == 0;
            if (isPayloadParameter && !Message.class.isAssignableFrom(parameterTypes[i])) {
                parameterValueResolvers[i] = new PayloadParameterResolver(parameterTypes[i]);
            } else {
                parameterValueResolvers[i] = parameterResolverFactory.createInstance(memberAnnotations,
                                                                                     parameterTypes[i],
                                                                                     parameterAnnotations[i]);
            }
        }
        return parameterValueResolvers;
    }

    /**
     * Returns the parameter resolvers provided at construction time.
     *
     * @return the parameter resolvers provided at construction time
     */
    protected ParameterResolver[] getParameterValueResolvers() {
        return parameterValueResolvers;
    }

    /**
     * Returns the member-level annotation of given <code>annotationType</code>, or <code>null</code> if no such
     * annotation is present.
     *
     * @param annotationType The type of annotation to retrieve
     * @param <T>            The type of annotation to retrieve
     * @return the annotation instance, or <code>null</code> if no such annotation is present.
     */
    public abstract <T extends Annotation> T getAnnotation(Class<T> annotationType);

    private static class PayloadParameterResolver implements ParameterResolver {

        private final Class<?> payloadType;

        public PayloadParameterResolver(Class<?> payloadType) {
            this.payloadType = payloadType;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return message.getPayload();
        }

        @Override
        public boolean matches(Message message) {
            return message.getPayloadType() != null && payloadType.isAssignableFrom(message.getPayloadType());
        }
    }

    private static final class Score implements Comparable<Score> {

        private final int declarationDepth;
        private final int payloadDepth;
        private final String payloadName;

        private Score(Class payloadType, Class<?> declaringClass) {
            declarationDepth = superClassCount(declaringClass, 0);
            payloadDepth = superClassCount(payloadType, -255);
            payloadName = payloadType.getName();
        }

        private int superClassCount(Class<?> declaringClass, int interfaceScore) {
            if (declaringClass.isInterface()) {
                return interfaceScore;
            }
            int superClasses = 0;

            while (declaringClass != null) {
                superClasses++;
                declaringClass = declaringClass.getSuperclass();
            }
            return superClasses;
        }

        @Override
        public int compareTo(Score o) {
            if (declarationDepth != o.declarationDepth) {
                return (o.declarationDepth < declarationDepth) ? -1 : 1;
            } else if (payloadDepth != o.payloadDepth) {
                return (o.payloadDepth < payloadDepth) ? -1 : 1;
            } else {
                return payloadName.compareTo(o.payloadName);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Score score = (Score) o;
            return declarationDepth == score.declarationDepth
                    && payloadDepth == score.payloadDepth
                    && payloadName.equals(score.payloadName);
        }

        @Override
        public int hashCode() {
            int result = declarationDepth;
            result = 31 * result + payloadDepth;
            result = 31 * result + payloadName.hashCode();
            return result;
        }
    }
}
