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

import org.axonframework.common.CollectionUtils;
import org.axonframework.common.Priority;
import org.axonframework.messaging.Message;

import java.lang.annotation.Annotation;

import static org.axonframework.common.CollectionUtils.getAnnotation;

/**
 * Factory for the default parameter resolvers. This factory is capable for providing parameter resolvers for Message,
 * MetaData and @MetaData annotated parameters.
 *
 * @author Allard Buijze
 * @since 2.0
 */
@Priority(Priority.FIRST)
public class DefaultParameterResolverFactory implements ParameterResolverFactory {

    @Override
    public ParameterResolver createInstance(Annotation[] methodAnnotations, Class<?> parameterType,
                                            Annotation[] parameterAnnotations) {
        if (Message.class.isAssignableFrom(parameterType)) {
            return new MessageParameterResolver(parameterType);
        }
        if (getAnnotation(parameterAnnotations, MetaData.class) != null) {
            return new AnnotatedMetaDataParameterResolver(CollectionUtils.getAnnotation(parameterAnnotations,
                                                                                        MetaData.class), parameterType);
        }
        if (org.axonframework.messaging.MetaData.class.isAssignableFrom(parameterType)) {
            return MetaDataParameterResolver.INSTANCE;
        }
        return null;
    }

    private static class AnnotatedMetaDataParameterResolver implements ParameterResolver {

        private final MetaData metaData;
        private final Class parameterType;

        public AnnotatedMetaDataParameterResolver(MetaData metaData, Class parameterType) {
            this.metaData = metaData;
            this.parameterType = parameterType;
        }

        @Override
        public Object resolveParameterValue(Message message) {
            return message.getMetaData().get(metaData.value());
        }

        @Override
        public boolean matches(Message message) {
            return !(parameterType.isPrimitive() || metaData.required())
                    || (
                    message.getMetaData().containsKey(metaData.value())
                            && parameterType.isInstance(message.getMetaData().get(metaData.value()))
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
