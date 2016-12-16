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

package org.axonframework.messaging.annotation;

import org.axonframework.common.Priority;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.Message;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;

/**
 * Factory for the default parameter resolvers. This factory is capable for providing parameter resolvers for Message,
 * MetaData and @MetaDataValue annotated parameters.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Priority(Priority.FIRST)
public class DefaultParameterResolverFactory implements ParameterResolverFactory {

    @Override
    public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {

        Class<?> parameterType = parameters[parameterIndex].getType();
        if (Message.class.isAssignableFrom(parameterType)) {
            return new MessageParameterResolver(parameterType);
        }
        MetaDataValue
                metaDataValueAnnotation = AnnotationUtils.findAnnotation(parameters[parameterIndex], MetaDataValue.class);
        if (metaDataValueAnnotation != null) {
            return new AnnotatedMetaDataParameterResolver(metaDataValueAnnotation, parameterType);
        }
        if (org.axonframework.messaging.MetaData.class.isAssignableFrom(parameterType)) {
            return MetaDataParameterResolver.INSTANCE;
        }
        if (parameterIndex == 0) {
            return new PayloadParameterResolver(parameterType);
        }
        return null;
    }

    private static class AnnotatedMetaDataParameterResolver implements ParameterResolver<Object> {

        private final MetaDataValue metaDataValue;
        private final Class parameterType;

        public AnnotatedMetaDataParameterResolver(MetaDataValue metaDataValue, Class parameterType) {
            this.metaDataValue = metaDataValue;
            this.parameterType = parameterType;
        }

        @Override
        public Object resolveParameterValue(Message<?> message) {
            return message.getMetaData().get(metaDataValue.value());
        }

        @Override
        public boolean matches(Message<?> message) {
            return !(parameterType.isPrimitive() || metaDataValue.required())
                    || (
                    message.getMetaData().containsKey(metaDataValue.value())
                            && parameterType.isInstance(message.getMetaData().get(metaDataValue.value()))
            );
        }
    }

    private static final class MetaDataParameterResolver implements ParameterResolver {

        private static final MetaDataParameterResolver INSTANCE = new MetaDataParameterResolver();

        private MetaDataParameterResolver() {
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return message.getMetaData();
        }

        @Override
        public boolean matches(Message message) {
            return true;
        }
    }

    private static class MessageParameterResolver implements ParameterResolver {

        private final Class<?> parameterType;

        public MessageParameterResolver(Class<?> parameterType) {
            this.parameterType = parameterType;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return message;
        }

        @Override
        public boolean matches(Message message) {
            return parameterType.isInstance(message);
        }
    }
}
