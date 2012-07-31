/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.Message;
import org.joda.time.DateTime;

import java.lang.annotation.Annotation;

import static org.axonframework.common.CollectionUtils.getAnnotation;

/**
 * ParameterResolverFactory implementation that accepts parameters of a {@link DateTime} type that have been annotated
 * with the {@link Timestamp} annotation and assigns the timestamp of the EventMessage.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class TimestampParameterResolverFactory extends ParameterResolverFactory implements ParameterResolver<DateTime> {

    @Override
    protected ParameterResolver createInstance(Annotation[] memberAnnotations, Class<?> parameterType,
                                               Annotation[] parameterAnnotations) {
        Timestamp annotation = getAnnotation(parameterAnnotations, Timestamp.class);
        if (parameterType.isAssignableFrom(DateTime.class) && annotation != null) {
            return this;
        }
        return null;
    }

    @Override
    public DateTime resolveParameterValue(Message message) {
        if (message instanceof EventMessage) {
            return ((EventMessage) message).getTimestamp();
        }
        return null;
    }

    @Override
    public boolean matches(Message message) {
        return message instanceof EventMessage;
    }

    @Override
    public boolean supportsPayloadResolution() {
        return false;
    }
}
